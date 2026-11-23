using System.ComponentModel.DataAnnotations;

namespace OrderService.Contracts;

public class CreateOrderRequest
{
    [Required]
    [MaxLength(500)]
    public string PickupAddress { get; set; } = string.Empty;

    [Required]
    [MaxLength(500)]
    public string DropoffAddress { get; set; } = string.Empty;

    [Required]
    public GeoPointRequest PickupLocation { get; set; } = new();

    [Required]
    public GeoPointRequest DropoffLocation { get; set; } = new();

    [Required]
    [Phone]
    [MaxLength(20)]
    public string ReceiverPhone { get; set; } = string.Empty;

    [MaxLength(50)]
    public string? PromotionCode { get; set; }

    [Range(0, int.MaxValue)]
    public int PointsToUse { get; set; }

    public Guid ClientRequestId { get; set; }
}