using System.Security.Claims;

namespace LoyaltyService.Common;

public static class ClaimsPrincipalExtensions
{
    public static Guid GetRequiredUserId(this ClaimsPrincipal principal)
    {
        var value = principal.FindFirstValue("sub");
        return Guid.TryParse(value, out var userId)
            ? userId
            : throw new ApiException(401, "INVALID_TOKEN", "Token kh?ng ch?a userId h?p l?.");
    }
}
