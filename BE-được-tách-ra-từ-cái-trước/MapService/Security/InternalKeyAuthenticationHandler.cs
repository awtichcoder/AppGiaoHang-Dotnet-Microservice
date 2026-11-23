using System.Security.Claims;
using System.Security.Cryptography;
using System.Text;
using System.Text.Encodings.Web;
using Microsoft.AspNetCore.Authentication;
using Microsoft.Extensions.Options;

namespace MapService.Security;

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
        var provided = providedKey.ToString();
        var valid = !string.IsNullOrEmpty(Options.ExpectedKey)
            && CryptographicOperations.FixedTimeEquals(Encoding.UTF8.GetBytes(provided), Encoding.UTF8.GetBytes(Options.ExpectedKey));
        if (!valid) return Task.FromResult(AuthenticateResult.Fail("X-Internal-Key không hợp lệ"));
        var identity = new ClaimsIdentity([new Claim(ClaimTypes.Role, "INTERNAL_SERVICE")], SchemeName);
        return Task.FromResult(AuthenticateResult.Success(new AuthenticationTicket(new ClaimsPrincipal(identity), SchemeName)));
    }
}
