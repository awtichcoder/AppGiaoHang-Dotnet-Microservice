namespace OrderService.Contracts;

public sealed record OrderPricePreview(decimal DistanceKm, long BaseFee, long Surcharge,
    long PlatformFee, long ShippingFee, long DiscountPromotion, long AmountAfterPromotion);
