using LoyaltyService.Contracts;

namespace LoyaltyService.Services;

public interface ILoyaltyManager
{
    Task<LoyaltyAccountResponse> GetAccountAsync(Guid customerId, CancellationToken cancellationToken);
    Task<PointsPreviewResponse> PreviewAsync(Guid customerId, PreviewPointsRequest request, CancellationToken cancellationToken);
    Task<LoyaltyReservationResponse> ReserveAsync(ReservePointsRequest request, CancellationToken cancellationToken);
    Task<LoyaltyReservationResponse> CommitAsync(Guid reservationId, CancellationToken cancellationToken);
    Task<LoyaltyReservationResponse> ReleaseAsync(Guid reservationId, CancellationToken cancellationToken);
    Task<EarnPointsResponse> EarnAsync(EarnPointsRequest request, CancellationToken cancellationToken);
}
