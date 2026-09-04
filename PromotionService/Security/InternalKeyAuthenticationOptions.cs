using Microsoft.AspNetCore.Authentication;

namespace PromotionService.Security;

public sealed class InternalKeyAuthenticationOptions : AuthenticationSchemeOptions
{
    public string ExpectedKey { get; set; } = string.Empty;
}
