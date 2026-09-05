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
        >= 5000 => "GOLD",
        >= 1000 => "SILVER",
        _ => "BRONZE"
    };
}
