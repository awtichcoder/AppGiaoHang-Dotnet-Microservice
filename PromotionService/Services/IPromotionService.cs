using PromotionService.Contracts;

namespace PromotionService.Services;

public interface IPromotionService
{
    Task<ValidatePromotionResponse> ValidateAsync(ValidatePromotionRequest request, CancellationToken ct = default);
    Task<ReservePromotionResponse> ReserveAsync(ReservePromotionRequest request, CancellationToken ct = default);
    Task<CommitReservationResponse> CommitAsync(Guid reservationId, CancellationToken ct = default);
    Task<ReleaseReservationResponse> ReleaseAsync(Guid reservationId, CancellationToken ct = default);
}
