using System.Security.Claims;
using LoyaltyService.Common;
using LoyaltyService.Contracts;
using LoyaltyService.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace LoyaltyService.Controllers;

[ApiController]
[Route("api/loyalty")]
public sealed class LoyaltyController(ILoyaltyManager loyalty) : ControllerBase
{
    [HttpGet("me")]
    [Authorize(Roles = "CUSTOMER")]
    public Task<LoyaltyAccountResponse> Me(CancellationToken cancellationToken) =>
        loyalty.GetAccountAsync(CurrentUserId(), cancellationToken);

    [HttpPost("preview")]
    [Authorize(Roles = "CUSTOMER")]
    public Task<PointsPreviewResponse> Preview(
        [FromBody] PreviewPointsRequest request, CancellationToken cancellationToken) =>
        loyalty.PreviewAsync(CurrentUserId(), request, cancellationToken);

    [HttpPost("reserve")]
    [Authorize(Policy = "InternalOnly")]
    public Task<LoyaltyReservationResponse> Reserve(
        [FromBody] ReservePointsRequest request, CancellationToken cancellationToken) =>
        loyalty.ReserveAsync(request, cancellationToken);

    [HttpPost("reservations/{reservationId:guid}/commit")]
    [Authorize(Policy = "InternalOnly")]
    public Task<LoyaltyReservationResponse> Commit(Guid reservationId, CancellationToken cancellationToken) =>
        loyalty.CommitAsync(reservationId, cancellationToken);

    [HttpPost("reservations/{reservationId:guid}/release")]
    [Authorize(Policy = "InternalOnly")]
    public Task<LoyaltyReservationResponse> Release(Guid reservationId, CancellationToken cancellationToken) =>
        loyalty.ReleaseAsync(reservationId, cancellationToken);

    [HttpPost("earn")]
    [Authorize(Policy = "InternalOnly")]
    public Task<EarnPointsResponse> Earn(
        [FromBody] EarnPointsRequest request, CancellationToken cancellationToken) =>
        loyalty.EarnAsync(request, cancellationToken);

    private Guid CurrentUserId()
    {
        var value = User.FindFirstValue("sub");
        return Guid.TryParse(value, out var id)
            ? id
            : throw new ApiException(401, "INVALID_TOKEN_SUBJECT", "Token không có subject hợp lệ.");
    }
}
