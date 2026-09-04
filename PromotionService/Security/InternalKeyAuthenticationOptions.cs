using Microsoft.AspNetCore.Authentication;

namespace PromotionService.Security;

public class InternalKeyAuthenticationOptions : AuthenticationSchemeOptions
{
    public string ExpectedKey { get; set; } = string.Empty;
}
