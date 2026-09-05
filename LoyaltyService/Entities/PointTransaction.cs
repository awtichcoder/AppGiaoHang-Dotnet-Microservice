namespace LoyaltyService.Entities;

public sealed class PointTransaction
{
    public Guid Id { get; set; }
    public Guid CustomerId { get; set; }
    public Guid? OrderId { get; set; }
    public Guid? ReservationId { get; set; }
    public PointTransactionType Type { get; set; }
    public int Points { get; set; }
    public int DiscountAmount { get; set; }
    public int AvailablePointsAfter { get; set; }
    public int ReservedPointsAfter { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public string? Description { get; set; }

    public LoyaltyAccount Account { get; set; } = null!;
    public PointReservation? Reservation { get; set; }
}
