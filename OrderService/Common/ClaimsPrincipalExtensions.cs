using System.Security.Claims;

namespace OrderService.Common;

public static class ClaimsPrincipalExtensions
{
    public static Guid GetSubjectId(this ClaimsPrincipal user)
    {
        var value = user.FindFirstValue("sub");
        return Guid.TryParse(value, out var id)
            ? id
            : throw new ApiException(401, "INVALID_TOKEN", "Token không chứa subject hợp lệ.");
    }
}
