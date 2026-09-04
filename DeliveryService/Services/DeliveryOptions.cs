namespace DeliveryService.Services;

public sealed class DeliveryFeeOptions
{
    public long PricePerKm { get; set; } = 10_000;
    public decimal SurchargeRate { get; set; } = 0.20m;
    public long PlatformFee { get; set; } = 3_000;
}

public sealed class MatchingOptions
{
    public decimal[] RadiusStepsKm { get; set; } = [3, 5];
    public int OfferTimeoutSeconds { get; set; } = 30;
    public int MaxLocationAgeSeconds { get; set; } = 60;
    public int WorkerIntervalSeconds { get; set; } = 2;
}
