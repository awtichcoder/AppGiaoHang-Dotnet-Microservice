using DeliveryService.Contracts;

namespace DeliveryService.Services;

public interface IDeliveryWorkflowService
{
    Task<DeliveryResponse> AssignAsync(DeliveryAssignRequest request, CancellationToken cancellationToken);
    Task<PagedResponse<DeliveryOfferResponse>> GetMyOffersAsync(Guid driverId, int page, int pageSize, CancellationToken cancellationToken);
    Task<AcceptOfferResponse> AcceptOfferAsync(Guid offerId, Guid driverId, CancellationToken cancellationToken);
    Task<RejectOfferResponse> RejectOfferAsync(Guid offerId, Guid driverId, string reasonCode, CancellationToken cancellationToken);
    Task<DeliveryResponse> ChangeDriverStageAsync(Guid deliveryId, Guid driverId, string action, CancellationToken cancellationToken);
    Task<DeliveryResponse> CancelByOrderIdAsync(Guid orderId, CancellationToken cancellationToken);
    Task<DeliveryResponse> CancelByDeliveryIdAsync(Guid deliveryId, CancellationToken cancellationToken);
    Task<DeliveryResponse?> GetByOrderAsync(Guid orderId, Guid actorId, string role, CancellationToken cancellationToken);
    Task<MatchingStatusResponse?> GetMatchingStatusAsync(Guid orderId, Guid actorId, string role, CancellationToken cancellationToken);
    Task<DriverLocationResponse> GetLocationAsync(Guid deliveryId, Guid actorId, string role, CancellationToken cancellationToken);
}
