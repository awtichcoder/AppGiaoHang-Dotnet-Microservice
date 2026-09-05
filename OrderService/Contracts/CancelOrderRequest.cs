using System.ComponentModel.DataAnnotations;

namespace OrderService.Contracts;

public class CancelOrderRequest
{
    [Required]
    [MaxLength(100)]
    public string ReasonCode { get; set; } = string.Empty;

    [Range(1, int.MaxValue)]
    public int? Version { get; set; }
}
