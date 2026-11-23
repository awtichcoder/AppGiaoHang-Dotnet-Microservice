using System.Security.Claims;
using System.Security.Cryptography;
using System.Text;

namespace DeliveryService.Common;

public static class RequestSecurity
{
    public static Guid SubjectId(this ClaimsPrincipal user)
        => Guid.TryParse(user.FindFirstValue("sub"), out var id)
            ? id : throw new ApiException(401, "INVALID_TOKEN", "Token không chứa subject hợp lệ.");

    public static bool HasValidInternalKey(this HttpRequest request, IConfiguration configuration)
    {
        var expected = configuration["INTERNAL_API_KEY"];
        var provided = request.Headers["X-Internal-Key"].ToString();
        return !string.IsNullOrEmpty(expected) && !string.IsNullOrEmpty(provided)
            && CryptographicOperations.FixedTimeEquals(Encoding.UTF8.GetBytes(expected), Encoding.UTF8.GetBytes(provided));
    }
}
