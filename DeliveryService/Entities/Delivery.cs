namespace DeliveryService.Entities;

public class Delivery
{
    public Guid DeliveryId { get; set; }

    // Một Order chỉ có một Delivery
    public Guid OrderId { get; set; }
    public Guid CustomerId { get; set; }

    // Chưa tìm được tài xế thì DriverId = null
    public Guid? DriverId { get; set; }
    public string PickupAddress { get; set; } = string.Empty;
    public string DropoffAddress { get; set; } = string.Empty;
    public string ReceiverPhone { get; set; } = string.Empty;
    public int OrderVersion { get; set; } = 1;
    public long TotalFee { get; set; }
    public int SearchRadiusIndex { get; set; }

    public decimal PickupLatitude { get; set; }
    public decimal PickupLongitude { get; set; }

    public decimal DropoffLatitude { get; set; }
    public decimal DropoffLongitude { get; set; }

    public DeliveryStatus Status { get; set; }
        = DeliveryStatus.SEARCHING;

    // Bắt đầu tìm trong bán kính 3 km
    public decimal CurrentRadiusKm { get; set; } = 3;

    public DateTime SearchStartedAt { get; set; }
        = DateTime.UtcNow;

    public DateTime? SearchEndedAt { get; set; }

    public DateTime? AssignedAt { get; set; }
    public DateTime? PickedUpAt { get; set; }
    public DateTime? DeliveringAt { get; set; }
    public DateTime? CompletedAt { get; set; }
    public DateTime? CancelledAt { get; set; }

    // Chống hai thao tác cập nhật cùng lúc
    public int Version { get; set; } = 1;

    public DateTime CreatedAt { get; set; }
        = DateTime.UtcNow;

    public DateTime? UpdatedAt { get; set; }
}
