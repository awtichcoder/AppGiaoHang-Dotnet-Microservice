using System.Data;
using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Security.Cryptography;
using System.Text;
using IdentityService.Common;
using IdentityService.Contracts;
using IdentityService.Data;
using IdentityService.Entities;
using Microsoft.AspNetCore.Identity;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;

namespace IdentityService.Services;

public sealed class IdentityManager(
    IdentityDbContext db,
    IPasswordHasher<ApplicationUser> passwordHasher,
    IConfiguration configuration) : IIdentityManager
{
    private const string Issuer = "deliveryapp-identity";

    public async Task<UserResponse> RegisterAsync(RegisterRequest request, CancellationToken cancellationToken)
        => await CreateUserAsync(request.Email, request.Password, request.FullName, request.Phone, "CUSTOMER", cancellationToken);

    public async Task<TokenResponse> LoginAsync(LoginRequest request, CancellationToken cancellationToken)
    {
        var user = await FindByEmailAsync(request.Email, cancellationToken);
        var verification = passwordHasher.VerifyHashedPassword(user, user.PasswordHash, request.Password);
        if (verification == PasswordVerificationResult.Failed)
            throw new ApiException(401, "INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng.");
        return await IssueTokenPairAsync(user, cancellationToken);
    }

    public async Task<TokenResponse> RefreshAsync(RefreshRequest request, CancellationToken cancellationToken)
    {
        var tokenHash = HashToken(request.RefreshToken);
        await using var transaction = await db.Database.BeginTransactionAsync(IsolationLevel.Serializable, cancellationToken);
        var stored = await db.RefreshTokens.Include(x => x.User)
            .SingleOrDefaultAsync(x => x.TokenHash == tokenHash, cancellationToken)
            ?? throw new ApiException(401, "INVALID_REFRESH_TOKEN", "Refresh token không hợp lệ.");

        if (stored.RevokedAt is not null)
            throw new ApiException(401, "REFRESH_TOKEN_REVOKED", "Refresh token đã bị thu hồi.");
        if (stored.ExpiresAt <= DateTime.UtcNow)
            throw new ApiException(401, "REFRESH_TOKEN_EXPIRED", "Refresh token đã hết hạn.");

        var (rawToken, replacement) = CreateRefreshToken(stored.UserId);
        stored.RevokedAt = DateTime.UtcNow;
        stored.ReplacedByTokenHash = replacement.TokenHash;
        db.RefreshTokens.Add(replacement);
        await db.SaveChangesAsync(cancellationToken);
        await transaction.CommitAsync(cancellationToken);

        var (accessToken, expiresAt) = CreateAccessToken(stored.User);
        return new TokenResponse(accessToken, rawToken, expiresAt);
    }

    public async Task<LogoutResponse> LogoutAsync(LogoutRequest request, CancellationToken cancellationToken)
    {
        var tokenHash = HashToken(request.RefreshToken);
        var stored = await db.RefreshTokens.SingleOrDefaultAsync(x => x.TokenHash == tokenHash, cancellationToken)
            ?? throw new ApiException(401, "INVALID_REFRESH_TOKEN", "Refresh token không hợp lệ.");
        stored.RevokedAt ??= DateTime.UtcNow;
        await db.SaveChangesAsync(cancellationToken);
        return new LogoutResponse(true, stored.RevokedAt.Value);
    }

    public async Task<UserResponse> GetMeAsync(Guid userId, CancellationToken cancellationToken)
    {
        var user = await db.Users.AsNoTracking().SingleOrDefaultAsync(x => x.Id == userId, cancellationToken)
            ?? throw new ApiException(404, "USER_NOT_FOUND", "Không tìm thấy tài khoản.");
        return ToResponse(user);
    }

    public async Task<UserResponse> CreatePrivilegedUserAsync(CreateAdminUserRequest request, CancellationToken cancellationToken)
    {
        var role = request.Role.Trim().ToUpperInvariant();
        if (role is not ("DRIVER" or "ADMIN"))
            throw new ApiException(400, "INVALID_ROLE", "Role chỉ được phép là DRIVER hoặc ADMIN.");
        return await CreateUserAsync(request.Email, request.Password, request.FullName, request.Phone, role, cancellationToken);
    }

    public async Task SeedDevelopmentUsersAsync(CancellationToken cancellationToken)
    {
        if (!configuration.GetValue("DevelopmentSeed:Enabled", false)) return;
        await EnsureSeedUserAsync(
            configuration["DevelopmentSeed:AdminEmail"], configuration["DevelopmentSeed:AdminPassword"],
            "Quản trị viên", "0900000000", "ADMIN", cancellationToken);
        await EnsureSeedUserAsync(
            configuration["DevelopmentSeed:DriverEmail"], configuration["DevelopmentSeed:DriverPassword"],
            "Tài xế phát triển", "0911111111", "DRIVER", cancellationToken);
        await EnsureSeedUserAsync(
            configuration["DevelopmentSeed:CustomerEmail"], configuration["DevelopmentSeed:CustomerPassword"],
            "Khách hàng phát triển", "0922222222", "CUSTOMER", cancellationToken,
            Guid.Parse("20000000-0000-0000-0000-000000000001"));
    }

    private async Task<UserResponse> CreateUserAsync(
        string email, string password, string fullName, string phone, string role,
        CancellationToken cancellationToken, Guid? userId = null)
    {
        var normalizedEmail = NormalizeEmail(email);
        if (await db.Users.AnyAsync(x => x.NormalizedEmail == normalizedEmail, cancellationToken))
            throw new ApiException(409, "EMAIL_ALREADY_EXISTS", "Email đã tồn tại.");

        var user = new ApplicationUser
        {
            Id = userId ?? Guid.NewGuid(),
            Email = email.Trim(),
            NormalizedEmail = normalizedEmail,
            FullName = fullName.Trim(),
            Phone = phone.Trim(),
            Role = role,
            CreatedAt = DateTime.UtcNow
        };
        user.PasswordHash = passwordHasher.HashPassword(user, password);
        db.Users.Add(user);
        await db.SaveChangesAsync(cancellationToken);
        return ToResponse(user);
    }

    private async Task EnsureSeedUserAsync(
        string? email, string? password, string fullName, string phone, string role,
        CancellationToken cancellationToken, Guid? userId = null)
    {
        if (string.IsNullOrWhiteSpace(email) || string.IsNullOrWhiteSpace(password)) return;
        var normalizedEmail = NormalizeEmail(email);
        if (await db.Users.AnyAsync(
            x => x.NormalizedEmail == normalizedEmail || (userId != null && x.Id == userId), cancellationToken)) return;
        await CreateUserAsync(email, password, fullName, phone, role, cancellationToken, userId);
    }

    private async Task<ApplicationUser> FindByEmailAsync(string email, CancellationToken cancellationToken)
        => await db.Users.SingleOrDefaultAsync(x => x.NormalizedEmail == NormalizeEmail(email), cancellationToken)
            ?? throw new ApiException(401, "INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng.");

    private async Task<TokenResponse> IssueTokenPairAsync(ApplicationUser user, CancellationToken cancellationToken)
    {
        var (accessToken, expiresAt) = CreateAccessToken(user);
        var (rawRefreshToken, refreshToken) = CreateRefreshToken(user.Id);
        db.RefreshTokens.Add(refreshToken);
        await db.SaveChangesAsync(cancellationToken);
        return new TokenResponse(accessToken, rawRefreshToken, expiresAt);
    }

    private (string Token, DateTime ExpiresAt) CreateAccessToken(ApplicationUser user)
    {
        var secret = configuration["JWT_SECRET"]
            ?? throw new InvalidOperationException("Không tìm thấy JWT_SECRET.");
        var expiresAt = DateTime.UtcNow.AddMinutes(configuration.GetValue("Jwt:AccessTokenMinutes", 60));
        var claims = new[]
        {
            new Claim(JwtRegisteredClaimNames.Sub, user.Id.ToString()),
            new Claim(JwtRegisteredClaimNames.Email, user.Email),
            new Claim("role", user.Role),
            new Claim(JwtRegisteredClaimNames.Jti, Guid.NewGuid().ToString())
        };
        var token = new JwtSecurityToken(
            issuer: Issuer,
            audience: null,
            claims: claims,
            notBefore: DateTime.UtcNow,
            expires: expiresAt,
            signingCredentials: new SigningCredentials(
                new SymmetricSecurityKey(Encoding.UTF8.GetBytes(secret)),
                SecurityAlgorithms.HmacSha256));
        return (new JwtSecurityTokenHandler().WriteToken(token), expiresAt);
    }

    private (string RawToken, RefreshToken Entity) CreateRefreshToken(Guid userId)
    {
        var raw = Convert.ToBase64String(RandomNumberGenerator.GetBytes(48));
        return (raw, new RefreshToken
        {
            Id = Guid.NewGuid(),
            UserId = userId,
            TokenHash = HashToken(raw),
            CreatedAt = DateTime.UtcNow,
            ExpiresAt = DateTime.UtcNow.AddDays(configuration.GetValue("Jwt:RefreshTokenDays", 7))
        });
    }

    private static string HashToken(string token) =>
        Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(token)));
    private static string NormalizeEmail(string email) => email.Trim().ToUpperInvariant();
    private static UserResponse ToResponse(ApplicationUser user) =>
        new(user.Id, user.FullName, user.Email, user.Phone, user.Role);
}
