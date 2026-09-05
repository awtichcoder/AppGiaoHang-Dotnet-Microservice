using OrderService.Contracts;

namespace OrderService.Services;

public sealed record PricingReservation(long Discount, Guid? ReservationId);

public interface IOrderPricingDependencies
{
    Task<decimal> GetDistanceKmAsync(GeoPointRequest pickup, GeoPointRequest dropoff, CancellationToken cancellationToken);
    Task<PricingReservation> ReservePromotionAsync(Guid orderId, Guid customerId, string? code, long shippingFee, CancellationToken cancellationToken);
    Task<PricingReservation> ReservePointsAsync(Guid orderId, Guid customerId, int points, long remainingFee, CancellationToken cancellationToken);
    Task CommitPromotionAsync(Guid reservationId, CancellationToken cancellationToken);
    Task ReleasePromotionAsync(Guid reservationId, CancellationToken cancellationToken);
    Task CommitPointsAsync(Guid reservationId, CancellationToken cancellationToken);
    Task ReleasePointsAsync(Guid reservationId, CancellationToken cancellationToken);
    Task EarnPointsAsync(Guid orderId, Guid customerId, long orderAmount, CancellationToken cancellationToken);
}
