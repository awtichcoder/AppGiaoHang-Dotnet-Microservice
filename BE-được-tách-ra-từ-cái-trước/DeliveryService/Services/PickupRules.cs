namespace DeliveryService.Services;

public static class PickupRules
{
    public static bool IsNearPickup(decimal latitude, decimal longitude, decimal pickupLatitude,
        decimal pickupLongitude, double maxDistanceMeters)
    {
        if (latitude is < -90 or > 90 || longitude is < -180 or > 180) return false;
        static double Radians(decimal degrees) => (double)degrees * Math.PI / 180;
        var dLat = Radians(latitude - pickupLatitude);
        var dLon = Radians(longitude - pickupLongitude);
        var a = Math.Pow(Math.Sin(dLat / 2), 2) + Math.Cos(Radians(latitude))
            * Math.Cos(Radians(pickupLatitude)) * Math.Pow(Math.Sin(dLon / 2), 2);
        return 6_371_000 * 2 * Math.Asin(Math.Sqrt(Math.Clamp(a, 0, 1))) <= maxDistanceMeters;
    }
}
