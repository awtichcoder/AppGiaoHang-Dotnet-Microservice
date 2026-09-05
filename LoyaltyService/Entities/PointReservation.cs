namespace LoyaltyService.Entities;

public sealed class PointReservation
{
    public Guid Id { get; set; }
    public Guid CustomerId { get; set; }
    public Guid OrderId { get; set; }
    public int Points { get; set; }
    public int DiscountAmount { get; set; }
    public PointReservationStatus Status { get; set; } = PointReservationStatus.RESERVED;
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public DateTime ExpiresAt { get; set; }
    public DateTime? CommittedAt { get; set; }
    public DateTime? ReleasedAt { get; set; }

    public LoyaltyAccount Account { get; set; } = null!;
}
