namespace LoyaltyService.Services;

public static class LoyaltyRules
{
    public const int PointValue = 1000;

    public static int CalculateUsablePoints(int requested, long orderAmount, int available)
    {
        var pointsForAmount = orderAmount / PointValue + (orderAmount % PointValue == 0 ? 0 : 1);
        return Math.Min(Math.Min(requested, available), checked((int)Math.Min(int.MaxValue, pointsForAmount)));
    }

    public static string CalculateTier(int points) => points switch
    {
        >= 500 => "GOLD",
        >= 100 => "SILVER",
        _ => "BRONZE"
    };

    // Informational only: tier discounts are not deducted in the MVP.
    public static int TierDiscountPercent(int points) => points >= 500 ? 10 : points >= 100 ? 5 : 0;
}
