using System.ComponentModel.DataAnnotations;

namespace IdentityService.Contracts;

public sealed class LogoutRequest
{
    [Required] public string RefreshToken { get; set; } = string.Empty;
}
