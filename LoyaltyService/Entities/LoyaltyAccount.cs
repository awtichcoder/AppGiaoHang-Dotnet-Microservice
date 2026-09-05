namespace LoyaltyService.Entities;

public sealed class LoyaltyAccount
{
    public Guid CustomerId { get; set; }
    public int Points { get; set; }
    public string Tier { get; set; } = "BRONZE";
    public int Version { get; set; } = 1;
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
