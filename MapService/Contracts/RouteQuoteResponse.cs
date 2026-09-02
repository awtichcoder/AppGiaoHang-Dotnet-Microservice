namespace MapService.Contracts;

public class RouteQuoteResponse
{
    public decimal DistanceKm { get; set; }
    public decimal DurationMin { get; set; }
    public string Provider { get; set; } = "HAVERSINE";
}
