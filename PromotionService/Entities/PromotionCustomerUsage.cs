namespace PromotionService.Entities;

public sealed class PromotionCustomerUsage
{
    public Guid PromotionId { get; set; }
    public Guid CustomerId { get; set; }
    public int CommittedCount { get; set; }
}
