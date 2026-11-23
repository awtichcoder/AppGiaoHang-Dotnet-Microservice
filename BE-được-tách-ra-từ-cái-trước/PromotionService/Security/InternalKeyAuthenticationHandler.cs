using System.Security.Claims;
using System.Security.Cryptography;
using System.Text;
using System.Text.Encodings.Web;
using Microsoft.AspNetCore.Authentication;
using Microsoft.Extensions.Options;

namespace PromotionService.Security;

public sealed class InternalKeyAuthenticationHandler(
    IOptionsMonitor<InternalKeyAuthenticationOptions> options,
    ILoggerFactory logger,
    UrlEncoder encoder) : AuthenticationHandler<InternalKeyAuthenticationOptions>(options, logger, encoder)
{
    public const string SchemeName = "InternalKey";

    protected override Task<AuthenticateResult> HandleAuthenticateAsync()
    {
        if (!Request.Headers.TryGetValue("X-Internal-Key", out var providedKey))
            return Task.FromResult(AuthenticateResult.NoResult());

        var provided = Encoding.UTF8.GetBytes(providedKey.ToString());
        var expected = Encoding.UTF8.GetBytes(Options.ExpectedKey);
        var valid = expected.Length > 0 && provided.Length == expected.Length
            && CryptographicOperations.FixedTimeEquals(provided, expected);

        if (!valid)
            return Task.FromResult(AuthenticateResult.Fail("X-Internal-Key không hợp lệ"));

        var identity = new ClaimsIdentity([new Claim(ClaimTypes.Role, "INTERNAL_SERVICE")], SchemeName);
        var principal = new ClaimsPrincipal(identity);
        return Task.FromResult(AuthenticateResult.Success(new AuthenticationTicket(principal, SchemeName)));
    }
}
