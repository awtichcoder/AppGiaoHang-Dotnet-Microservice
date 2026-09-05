using Microsoft.AspNetCore.Authentication;

namespace LoyaltyService.Security;

public sealed class InternalKeyAuthenticationOptions : AuthenticationSchemeOptions
{
    public string ExpectedKey { get; set; } = string.Empty;
}
