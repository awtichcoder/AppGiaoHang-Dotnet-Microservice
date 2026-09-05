namespace LoyaltyService.Entities;

public sealed class LoyaltyAccount
{
    public Guid CustomerId { get; set; }
    public int AvailablePoints { get; set; }
    public int ReservedPoints { get; set; }
    public int LifetimeEarnedPoints { get; set; }
    public MembershipTier Tier { get; set; } = MembershipTier.BRONZE;
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
    public byte[] RowVersion { get; set; } = [];

    public ICollection<PointReservation> Reservations { get; set; } = new List<PointReservation>();
    public ICollection<PointTransaction> Transactions { get; set; } = new List<PointTransaction>();
}
