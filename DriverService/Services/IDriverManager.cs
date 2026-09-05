using DriverService.Contracts;

namespace DriverService.Services;

public interface IDriverManager
{
    Task<DriverProfileResponse> UpdateAvailabilityAsync(Guid driverId, UpdateAvailabilityRequest request, CancellationToken cancellationToken);
    Task<DriverLocationResponse> UpdateLocationAsync(Guid driverId, UpdateLocationRequest request, CancellationToken cancellationToken);
    Task<IReadOnlyList<NearbyDriverResponse>> GetNearbyAsync(decimal latitude, decimal longitude, decimal radiusKm, int? limit, CancellationToken cancellationToken);
    Task<DriverLocationResponse> GetLocationAsync(Guid driverId, CancellationToken cancellationToken);
    Task<DriverProfileResponse> MarkBusyAsync(Guid driverId, DriverAssignmentRequest request, CancellationToken cancellationToken);
    Task<DriverProfileResponse> MarkAvailableAsync(Guid driverId, DriverAssignmentRequest request, CancellationToken cancellationToken);
}
