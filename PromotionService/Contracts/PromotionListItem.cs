namespace PromotionService.Contracts;

public sealed record PromotionListItem(string Code, string Description, int? DiscountAmount,
    decimal? DiscountPercent, int? MaxDiscount, int MinOrderAmount, DateTime EndAt);
