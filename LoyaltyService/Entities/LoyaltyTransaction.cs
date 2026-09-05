namespace LoyaltyService.Entities;

public sealed class LoyaltyTransaction
{
    public Guid TransactionId { get; set; }
    public Guid CustomerId { get; set; }
    public Guid? OrderId { get; set; }
    public string Type { get; set; } = string.Empty;
    public int PointsDelta { get; set; }
    public int BalanceAfter { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
