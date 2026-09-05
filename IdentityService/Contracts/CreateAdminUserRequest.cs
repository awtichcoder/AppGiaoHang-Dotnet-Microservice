using System.ComponentModel.DataAnnotations;

namespace IdentityService.Contracts;

public sealed class CreateAdminUserRequest
{
    [Required, EmailAddress, MaxLength(256)] public string Email { get; set; } = string.Empty;
    [Required, MinLength(6), MaxLength(100)] public string Password { get; set; } = string.Empty;
    [Required, MaxLength(200)] public string FullName { get; set; } = string.Empty;
    [Required, Phone, MaxLength(20)] public string Phone { get; set; } = string.Empty;
    [Required] public string Role { get; set; } = string.Empty;
}
