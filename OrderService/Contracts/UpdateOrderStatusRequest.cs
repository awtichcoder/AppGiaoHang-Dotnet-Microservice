using System.ComponentModel.DataAnnotations;

namespace OrderService.Contracts
{
    public class UpdateOrderStatusRequest
    {
    [Required]
    public string Status { get; set; } = string.Empty;

    [Range(1, int.MaxValue)]
    public int Version { get; set; }
    public Guid? DriverId { get; set; }
    }
}
