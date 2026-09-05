using Microsoft.AspNetCore.Authentication;

namespace DriverService.Security;

public sealed class InternalKeyAuthenticationOptions : AuthenticationSchemeOptions
{
    public string ExpectedKey { get; set; } = string.Empty;
}
