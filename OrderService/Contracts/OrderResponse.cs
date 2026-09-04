namespace OrderService.Contracts;

public class OrderResponse
{
    //Response Toan bo thong tin dc tra ve cho client, khong phai la request de tao order
    //Khong co thong tin ve driver, chi co thong tin ve order va customer
    public Guid OrderId { get; set; }
    public Guid CustomerId { get; set; }

    public string PickupAddress { get; set; } = string.Empty;
    public string DropoffAddress { get; set; } = string.Empty;
    public string ReceiverPhone { get; set; } = string.Empty;

    public decimal DistanceKm { get; set; }

    public long BaseFee { get; set; }
    public long Surcharge { get; set; }
    public long PlatformFee { get; set; }
    public long DiscountPromotion { get; set; }
    public long DiscountPoints { get; set; }
    public long TotalFee { get; set; }

    public string Status { get; set; } = string.Empty;
    public string MatchingStatus { get; set; } = string.Empty;

    public int Version { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime? UpdatedAt { get; set; }
}
