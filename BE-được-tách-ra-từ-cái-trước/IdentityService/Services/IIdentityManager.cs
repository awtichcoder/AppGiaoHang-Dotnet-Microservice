using IdentityService.Contracts;

namespace IdentityService.Services;

public interface IIdentityManager
{
    Task<UserResponse> RegisterAsync(RegisterRequest request, CancellationToken cancellationToken);
    Task<TokenResponse> LoginAsync(LoginRequest request, CancellationToken cancellationToken);
    Task<TokenResponse> RefreshAsync(RefreshRequest request, CancellationToken cancellationToken);
    Task<LogoutResponse> LogoutAsync(LogoutRequest request, CancellationToken cancellationToken);
    Task<UserResponse> GetMeAsync(Guid userId, CancellationToken cancellationToken);
    Task<UserResponse> CreatePrivilegedUserAsync(CreateAdminUserRequest request, CancellationToken cancellationToken);
    Task SeedDevelopmentUsersAsync(CancellationToken cancellationToken);
}
