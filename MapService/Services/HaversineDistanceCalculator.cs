namespace MapService.Services;

public sealed class HaversineDistanceCalculator(IConfiguration configuration) : IDistanceCalculator
{
    private readonly double _averageSpeedKmh = configuration.GetValue<double?>("MapSettings:AverageSpeedKmh") ?? 30d;

    public string ProviderName => "HAVERSINE";

    public (decimal DistanceKm, decimal DurationMin) Calculate(
        decimal pickupLat, decimal pickupLng, decimal dropoffLat, decimal dropoffLng)
    {
        const double earthRadiusKm = 6371.0;
        var lat1 = (double)pickupLat * Math.PI / 180.0;
        var lat2 = (double)dropoffLat * Math.PI / 180.0;
        var deltaLat = ((double)dropoffLat - (double)pickupLat) * Math.PI / 180.0;
        var deltaLng = ((double)dropoffLng - (double)pickupLng) * Math.PI / 180.0;
        var a = Math.Sin(deltaLat / 2) * Math.Sin(deltaLat / 2)
            + Math.Cos(lat1) * Math.Cos(lat2) * Math.Sin(deltaLng / 2) * Math.Sin(deltaLng / 2);
        var distanceKm = earthRadiusKm * 2 * Math.Atan2(Math.Sqrt(a), Math.Sqrt(1 - a));
        var durationMin = _averageSpeedKmh > 0 ? distanceKm / _averageSpeedKmh * 60.0 : 0;
        return (Math.Round((decimal)distanceKm, 2), Math.Round((decimal)durationMin, 1));
    }
}
