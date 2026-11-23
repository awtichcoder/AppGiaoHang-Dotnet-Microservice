namespace PromotionService.Contracts;

public sealed class ReservePromotionRequest
{
    public string Code { get; set; } = string.Empty;
    public Guid CustomerId { get; set; }
    public Guid OrderId { get; set; }
    public int OrderAmount { get; set; }
}

public sealed class ReservePromotionResponse
{
    public Guid ReservationId { get; set; }
    public int DiscountAmount { get; set; }
    public DateTime ExpiresAt { get; set; }
}
