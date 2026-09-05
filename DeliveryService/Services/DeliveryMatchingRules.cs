namespace DeliveryService.Services;

public static class DeliveryMatchingRules
{
    public static bool IsInRadiusStep(decimal distanceKm, decimal previousRadiusKm, decimal radiusKm, bool firstStep) =>
        distanceKm <= radiusKm && (firstStep || distanceKm > previousRadiusKm);
}
