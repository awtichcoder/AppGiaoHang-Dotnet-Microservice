namespace MapService.Services;

public interface IDistanceCalculator
{
    string ProviderName { get; }

    (decimal DistanceKm, decimal DurationMin) Calculate(
        decimal pickupLat, decimal pickupLng,
        decimal dropoffLat, decimal dropoffLng);
}
