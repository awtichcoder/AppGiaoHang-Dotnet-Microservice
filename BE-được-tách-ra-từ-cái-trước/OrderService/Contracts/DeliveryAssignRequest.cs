namespace OrderService.Contracts;

public sealed class DeliveryAssignRequest
{
    public Guid OrderId { get; set; }
    public Guid CustomerId { get; set; }
    public string PickupAddress { get; set; } = string.Empty;
    public string DropoffAddress { get; set; } = string.Empty;
    public string ReceiverPhone { get; set; } = string.Empty;
    public GeoPointRequest PickupLocation { get; set; } = new();
    public GeoPointRequest DropoffLocation { get; set; } = new();
    public int OrderVersion { get; set; }
    public long TotalFee { get; set; }
}
