using System.Security.Cryptography;
using System.Text;

namespace OrderService.Common;

public static class InternalApiKey
{
    public static bool IsValid(HttpRequest request, IConfiguration configuration)
    {
        var expected = configuration["INTERNAL_API_KEY"];
        var provided = request.Headers["X-Internal-Key"].ToString();
        return !string.IsNullOrEmpty(expected) && !string.IsNullOrEmpty(provided)
            && CryptographicOperations.FixedTimeEquals(Encoding.UTF8.GetBytes(expected), Encoding.UTF8.GetBytes(provided));
    }
}
