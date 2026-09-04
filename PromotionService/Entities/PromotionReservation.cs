namespace PromotionService.Entities;

public sealed class PromotionReservation
{
    public Guid Id { get; set; }
    public Guid PromotionId { get; set; }
    public Promotion? Promotion { get; set; }
    public Guid OrderId { get; set; }
    public Guid CustomerId { get; set; }
    public int DiscountAmount { get; set; }
    public PromotionReservationStatus Status { get; set; } = PromotionReservationStatus.RESERVED;
    public DateTime CreatedAt { get; set; }
    public DateTime ExpiresAt { get; set; }
    public DateTime? CommittedAt { get; set; }
    public DateTime? ReleasedAt { get; set; }
}
