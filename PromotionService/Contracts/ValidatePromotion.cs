namespace PromotionService.Contracts;

public sealed class ValidatePromotionRequest
{
    public string Code { get; set; } = string.Empty;
    public Guid CustomerId { get; set; }
    public int OrderAmount { get; set; }
}

public sealed class ValidatePromotionResponse
{
    public bool Valid { get; set; }
    public int DiscountAmount { get; set; }
    public string? Reason { get; set; }
}
