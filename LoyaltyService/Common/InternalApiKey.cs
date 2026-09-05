using System.Security.Claims;
using System.Security.Cryptography;
using System.Text;
using System.Text.Encodings.Web;
using Microsoft.AspNetCore.Authentication;
using Microsoft.Extensions.Options;

namespace LoyaltyService.Common;

public sealed class InternalApiKeyOptions : AuthenticationSchemeOptions
{
    public string ExpectedKey { get; set; } = string.Empty;
}

public sealed class InternalApiKeyHandler(
    IOptionsMonitor<InternalApiKeyOptions> options,
    ILoggerFactory logger,
    UrlEncoder encoder)
    : AuthenticationHandler<InternalApiKeyOptions>(options, logger, encoder)
{
    public const string SchemeName = "InternalApiKey";
    private const string HeaderName = "X-Internal-Key";

    protected override Task<AuthenticateResult> HandleAuthenticateAsync()
    {
        if (!Request.Headers.TryGetValue(HeaderName, out var values))
            return Task.FromResult(AuthenticateResult.NoResult());

        var providedKey = values.FirstOrDefault();
        if (string.IsNullOrWhiteSpace(providedKey) || string.IsNullOrWhiteSpace(Options.ExpectedKey))
            return Task.FromResult(AuthenticateResult.Fail("Internal API key is missing."));

        if (!FixedTimeEquals(providedKey, Options.ExpectedKey))
            return Task.FromResult(AuthenticateResult.Fail("Internal API key is invalid."));

        var claims = new[]
        {
            new Claim(ClaimTypes.NameIdentifier, "internal-service"),
            new Claim("role", "INTERNAL")
        };
        var identity = new ClaimsIdentity(claims, SchemeName);
        return Task.FromResult(AuthenticateResult.Success(new AuthenticationTicket(new ClaimsPrincipal(identity), SchemeName)));
    }

    private static bool FixedTimeEquals(string left, string right)
    {
        var leftBytes = Encoding.UTF8.GetBytes(left);
        var rightBytes = Encoding.UTF8.GetBytes(right);
        return leftBytes.Length == rightBytes.Length && CryptographicOperations.FixedTimeEquals(leftBytes, rightBytes);
    }
}
