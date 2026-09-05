using LoyaltyService.Contracts;

namespace LoyaltyService.Services;

public interface ILoyaltyService
{
    Task<LoyaltyAccountResponse> GetMeAsync(Guid customerId, CancellationToken cancellationToken = default);
    Task<PreviewPointsResponse> PreviewAsync(Guid customerId, PreviewPointsRequest request, CancellationToken cancellationToken = default);
    Task<ReservePointsResponse> ReserveAsync(ReservePointsRequest request, CancellationToken cancellationToken = default);
    Task<ReservationActionResponse> CommitAsync(Guid reservationId, CancellationToken cancellationToken = default);
    Task<ReservationActionResponse> ReleaseAsync(Guid reservationId, CancellationToken cancellationToken = default);
    Task<EarnPointsResponse> EarnAsync(EarnPointsRequest request, CancellationToken cancellationToken = default);
}
