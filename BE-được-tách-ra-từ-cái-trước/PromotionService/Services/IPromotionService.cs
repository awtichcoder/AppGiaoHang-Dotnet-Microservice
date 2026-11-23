using PromotionService.Contracts;

namespace PromotionService.Services;

public interface IPromotionService
{
    Task<IReadOnlyList<PromotionListItem>> ListActiveAsync(CancellationToken cancellationToken);
    Task ReleaseByOrderAsync(Guid orderId, CancellationToken cancellationToken);
    Task<ValidatePromotionResponse> ValidateAsync(ValidatePromotionRequest request, CancellationToken cancellationToken = default);
    Task<ReservePromotionResponse> ReserveAsync(ReservePromotionRequest request, CancellationToken cancellationToken = default);
    Task<CommitReservationResponse> CommitAsync(Guid reservationId, CancellationToken cancellationToken = default);
    Task<ReleaseReservationResponse> ReleaseAsync(Guid reservationId, CancellationToken cancellationToken = default);
}
