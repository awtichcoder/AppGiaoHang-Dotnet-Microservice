using LoyaltyService.Common;
using LoyaltyService.Contracts;
using LoyaltyService.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace LoyaltyService.Controllers;

[ApiController]
[Route("api/loyalty")]
public sealed class LoyaltyController(ILoyaltyService loyaltyService) : ControllerBase
{
    [HttpGet("me")]
    [Authorize(Policy = "CustomerOnly")]
    public Task<LoyaltyAccountResponse> GetMe(CancellationToken cancellationToken)
        => loyaltyService.GetMeAsync(User.GetRequiredUserId(), cancellationToken);

    [HttpPost("preview")]
    [Authorize(Policy = "CustomerOnly")]
    public Task<PreviewPointsResponse> Preview(PreviewPointsRequest request, CancellationToken cancellationToken)
        => loyaltyService.PreviewAsync(User.GetRequiredUserId(), request, cancellationToken);

    [HttpPost("reserve")]
    [Authorize(Policy = "InternalOnly")]
    public Task<ReservePointsResponse> Reserve(ReservePointsRequest request, CancellationToken cancellationToken)
        => loyaltyService.ReserveAsync(request, cancellationToken);

    [HttpPost("reservations/{id:guid}/commit")]
    [Authorize(Policy = "InternalOnly")]
    public Task<ReservationActionResponse> Commit(Guid id, CancellationToken cancellationToken)
        => loyaltyService.CommitAsync(id, cancellationToken);

    [HttpPost("reservations/{id:guid}/release")]
    [Authorize(Policy = "InternalOnly")]
    public Task<ReservationActionResponse> Release(Guid id, CancellationToken cancellationToken)
        => loyaltyService.ReleaseAsync(id, cancellationToken);

    [HttpPost("earn")]
    [Authorize(Policy = "InternalOnly")]
    public Task<EarnPointsResponse> Earn(EarnPointsRequest request, CancellationToken cancellationToken)
        => loyaltyService.EarnAsync(request, cancellationToken);
}
