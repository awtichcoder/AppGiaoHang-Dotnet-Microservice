namespace LoyaltyService.Services;

public sealed class LoyaltyOptions
{
    public int PointValueVnd { get; set; } = 1000;
    public int EarnVndPerPoint { get; set; } = 10000;
    public int ReservationTtlMinutes { get; set; } = 120;
    public int SilverThresholdPoints { get; set; } = 100;
    public int GoldThresholdPoints { get; set; } = 500;
}
