namespace LoyaltyService.Entities;

public sealed class LoyaltyReservation
{
    public Guid ReservationId { get; set; }
    public Guid OrderId { get; set; }
    public Guid CustomerId { get; set; }
    public int Points { get; set; }
    public long DiscountAmount { get; set; }
    public LoyaltyReservationStatus Status { get; set; } = LoyaltyReservationStatus.RESERVED;
    public DateTime ExpiresAt { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public DateTime? FinalizedAt { get; set; }
}
