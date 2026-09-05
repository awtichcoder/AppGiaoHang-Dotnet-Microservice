namespace LoyaltyService.Entities;

public sealed class LoyaltyEarnRecord
{
    public Guid EarnRecordId { get; set; }
    public Guid OrderId { get; set; }
    public Guid CustomerId { get; set; }
    public long Amount { get; set; }
    public int PointsEarned { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
