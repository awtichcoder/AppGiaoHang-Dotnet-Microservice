namespace DeliveryService.Contracts;

public class DeliveryQuoteResponse
{
    public long BaseFee { get; set; }
    public long Surcharge { get; set; }
    public long PlatformFee { get; set; }
    public long ShippingFee { get; set; }
}
