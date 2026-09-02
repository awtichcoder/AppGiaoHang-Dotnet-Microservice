using System.Security.Claims;
using System.Text.Encodings.Web;
using Microsoft.AspNetCore.Authentication;
using Microsoft.Extensions.Options;

namespace MapService.Security;

// Xác thực service-to-service bằng header X-Internal-Key (INTERNAL_API_KEY).
// Dùng song song với JWT Bearer: endpoint có thể chấp nhận 1 trong 2 cách xác thực.
public class InternalKeyAuthenticationHandler : AuthenticationHandler<InternalKeyAuthenticationOptions>
{
    public const string SchemeName = "InternalKey";
    private const string HeaderName = "X-Internal-Key";

    public InternalKeyAuthenticationHandler(
        IOptionsMonitor<InternalKeyAuthenticationOptions> options,
        ILoggerFactory logger,
        UrlEncoder encoder)
        : base(options, logger, encoder)
    {
    }

    protected override Task<AuthenticateResult> HandleAuthenticateAsync()
    {
        if (!Request.Headers.TryGetValue(HeaderName, out var providedKey))
        {
            return Task.FromResult(AuthenticateResult.NoResult());
        }

        if (string.IsNullOrEmpty(Options.ExpectedKey) || providedKey.ToString() != Options.ExpectedKey)
        {
            return Task.FromResult(AuthenticateResult.Fail("X-Internal-Key không hợp lệ"));
        }

        var claims = new[] { new Claim(ClaimTypes.Role, "INTERNAL_SERVICE") };
        var identity = new ClaimsIdentity(claims, SchemeName);
        var principal = new ClaimsPrincipal(identity);
        var ticket = new AuthenticationTicket(principal, SchemeName);

        return Task.FromResult(AuthenticateResult.Success(ticket));
    }
}
