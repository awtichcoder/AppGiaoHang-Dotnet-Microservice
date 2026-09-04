namespace DeliveryService.Clients;

// Adapter mô phỏng DriverService trong lúc service của nhóm khác chưa hoàn tất.
// Danh sách có thể thay qua Matching:MockDrivers trong appsettings/biến môi trường.
public sealed class ConfiguredDriverDirectoryClient(IConfiguration configuration) : IDriverDirectoryClient
{
    private static readonly Guid[] DefaultDrivers =
    [
        Guid.Parse("10000000-0000-0000-0000-000000000001"),
        Guid.Parse("10000000-0000-0000-0000-000000000002"),
        Guid.Parse("10000000-0000-0000-0000-000000000003")
    ];

    public Task<IReadOnlyList<DriverCandidate>> GetEligibleDriversAsync(decimal latitude, decimal longitude, decimal radiusKm, CancellationToken cancellationToken)
    {
        var configured = configuration.GetSection("Matching:MockDrivers").Get<string[]>() ?? [];
        var ids = configured.Select(value => Guid.TryParse(value, out var id) ? id : Guid.Empty).Where(x => x != Guid.Empty).ToArray();
        if (ids.Length == 0) ids = DefaultDrivers;
        IReadOnlyList<DriverCandidate> result = ids.Select((id, index) => new DriverCandidate(id, 1.2m + index * 0.8m))
            .Where(x => x.DistanceKm <= radiusKm).ToList();
        return Task.FromResult(result);
    }

    public Task<DriverLocation?> GetLocationAsync(Guid driverId, CancellationToken cancellationToken)
    {
        var location = new DriverLocation(
            configuration.GetValue("Matching:MockLocation:Latitude", 10.7769m),
            configuration.GetValue("Matching:MockLocation:Longitude", 106.7009m),
            DateTime.UtcNow);
        return Task.FromResult<DriverLocation?>(location);
    }
}
