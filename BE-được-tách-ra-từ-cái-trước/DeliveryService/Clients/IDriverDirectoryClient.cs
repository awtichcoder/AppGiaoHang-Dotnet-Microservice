namespace DeliveryService.Clients;

public sealed record DriverCandidate(Guid DriverId, decimal DistanceKm);
public sealed record DriverLocation(decimal Latitude, decimal Longitude, decimal? AccuracyM, DateTime RecordedAt);

public interface IDriverDirectoryClient
{
    Task<IReadOnlyList<DriverCandidate>> GetEligibleDriversAsync(decimal latitude, decimal longitude, decimal radiusKm, CancellationToken cancellationToken);
    Task<DriverLocation?> GetLocationAsync(Guid driverId, CancellationToken cancellationToken);
    Task MarkBusyAsync(Guid driverId, Guid deliveryId, Guid orderId, CancellationToken cancellationToken);
    Task MarkAvailableAsync(Guid driverId, Guid deliveryId, Guid orderId, CancellationToken cancellationToken);
}
