namespace PromotionService.Entities;

public sealed class Promotion
{
    public Guid Id { get; set; }
    public string Code { get; set; } = string.Empty;
    public string? Description { get; set; }
    public PromotionDiscountType DiscountType { get; set; }
    public decimal DiscountValue { get; set; }
    public int? MaxDiscountAmount { get; set; }
    public int MinOrderValue { get; set; }
    public DateTime StartAt { get; set; }
    public DateTime EndAt { get; set; }
    public bool IsActive { get; set; } = true;
    public int? UsageLimitTotal { get; set; }
    public int UsageCount { get; set; }
    public int? UsageLimitPerCustomer { get; set; }
    public ICollection<PromotionReservation> Reservations { get; set; } = new List<PromotionReservation>();
}
