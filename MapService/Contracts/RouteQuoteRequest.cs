namespace MapService.Contracts;

public class RouteQuoteRequest
{
    public decimal PickupLatitude { get; set; }
    public decimal PickupLongitude { get; set; }
    public decimal DropoffLatitude { get; set; }
    public decimal DropoffLongitude { get; set; }
}
