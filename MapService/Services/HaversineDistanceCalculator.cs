namespace MapService.Services;

// Bản lõi MVP: tính khoảng cách đường chim bay bằng công thức Haversine.
// Khi nâng cấp OSRM, thêm OsrmDistanceCalculator implement cùng interface
// và đổi DI registration trong Program.cs — không cần sửa Controller.
public class HaversineDistanceCalculator : IDistanceCalculator
{
    private readonly double _averageSpeedKmh;

    public HaversineDistanceCalculator(IConfiguration configuration)
    {
        _averageSpeedKmh = configuration.GetValue<double?>("MapSettings:AverageSpeedKmh") ?? 30d;
    }

    public string ProviderName => "HAVERSINE";

    public (decimal DistanceKm, decimal DurationMin) Calculate(
        decimal pickupLat, decimal pickupLng,
        decimal dropoffLat, decimal dropoffLng)
    {
        const double earthRadiusKm = 6371.0;

        double lat1 = (double)pickupLat * Math.PI / 180.0;
        double lat2 = (double)dropoffLat * Math.PI / 180.0;
        double deltaLat = ((double)dropoffLat - (double)pickupLat) * Math.PI / 180.0;
        double deltaLng = ((double)dropoffLng - (double)pickupLng) * Math.PI / 180.0;

        double a = Math.Sin(deltaLat / 2) * Math.Sin(deltaLat / 2) +
                   Math.Cos(lat1) * Math.Cos(lat2) *
                   Math.Sin(deltaLng / 2) * Math.Sin(deltaLng / 2);
        double c = 2 * Math.Atan2(Math.Sqrt(a), Math.Sqrt(1 - a));
        double distanceKm = earthRadiusKm * c;

        double durationMin = _averageSpeedKmh > 0
            ? (distanceKm / _averageSpeedKmh) * 60.0
            : 0;

        return (Math.Round((decimal)distanceKm, 2), Math.Round((decimal)durationMin, 1));
    }
}
