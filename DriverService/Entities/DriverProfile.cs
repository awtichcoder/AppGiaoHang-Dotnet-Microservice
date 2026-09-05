namespace DriverService.Entities;

public sealed class DriverProfile
{
    public Guid DriverId { get; set; }
    public DriverStatus Status { get; set; } = DriverStatus.OFFLINE;
    public decimal? Latitude { get; set; }
    public decimal? Longitude { get; set; }
    public decimal? AccuracyM { get; set; }
    public DateTime? LocationUpdatedAt { get; set; }
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
    public Guid? ActiveDeliveryId { get; set; }
    public int Version { get; set; } = 1;
}
