using Microsoft.AspNetCore.Authentication;

namespace MapService.Security;

public sealed class InternalKeyAuthenticationOptions : AuthenticationSchemeOptions
{
    public string ExpectedKey { get; set; } = string.Empty;
}
