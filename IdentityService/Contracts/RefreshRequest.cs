using System.ComponentModel.DataAnnotations;

namespace IdentityService.Contracts;

public sealed class RefreshRequest
{
    [Required] public string RefreshToken { get; set; } = string.Empty;
}
