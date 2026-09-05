namespace IdentityService.Contracts;

public sealed record UserResponse(Guid UserId, string FullName, string Email, string Phone, string Role);
public sealed record TokenResponse(string AccessToken, string RefreshToken, DateTime ExpiresAt);
public sealed record LogoutResponse(bool Success, DateTime RevokedAt);
