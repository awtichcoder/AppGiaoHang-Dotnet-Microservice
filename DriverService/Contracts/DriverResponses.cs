namespace DriverService.Contracts;

public sealed class DriverProfileResponse
{
    public Guid DriverId { get; set; }
    public string AvailabilityStatus { get; set; } = string.Empty;
    public Guid? ActiveDeliveryId { get; set; }
    public DateTime UpdatedAt { get; set; }
}

public sealed class NearbyDriverResponse
{
    public Guid DriverId { get; set; }
    public decimal DistanceKm { get; set; }
    public DateTime LastSeenAt { get; set; }
}

public sealed class DriverLocationResponse
{
    public Guid DriverId { get; set; }
    public decimal Latitude { get; set; }
    public decimal Longitude { get; set; }
    public decimal? AccuracyM { get; set; }
    public DateTime RecordedAt { get; set; }
}

public sealed class LocationAcceptedResponse
{
    public DateTime AcceptedAt { get; set; }
}
