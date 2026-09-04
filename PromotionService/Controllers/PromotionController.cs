using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using PromotionService.Common;
using PromotionService.Contracts;
using PromotionService.Services;

namespace PromotionService.Controllers;

[ApiController]
[Route("api/promotion")]
public sealed class PromotionController(IPromotionService promotionService) : ControllerBase
{
    [HttpPost("validate")]
    [Authorize(Policy = "ServiceOrUser")]
    public async Task<IActionResult> Validate(ValidatePromotionRequest request, CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.Code) || request.CustomerId == Guid.Empty)
            throw new ApiException(400, "INVALID_REQUEST", "Thiếu code hoặc customerId.");
        return Ok(await promotionService.ValidateAsync(request, cancellationToken));
    }

    [HttpPost("reserve")]
    [Authorize(Policy = "InternalOnly")]
    public async Task<IActionResult> Reserve(ReservePromotionRequest request, CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(request.Code) || request.CustomerId == Guid.Empty || request.OrderId == Guid.Empty)
            throw new ApiException(400, "INVALID_REQUEST", "Thiếu tham số reserve.");
        return Ok(await promotionService.ReserveAsync(request, cancellationToken));
    }

    [HttpPost("reservations/{id:guid}/commit")]
    [Authorize(Policy = "InternalOnly")]
    public async Task<IActionResult> Commit(Guid id, CancellationToken cancellationToken)
        => Ok(await promotionService.CommitAsync(id, cancellationToken));

    [HttpPost("reservations/{id:guid}/release")]
    [Authorize(Policy = "InternalOnly")]
    public async Task<IActionResult> Release(Guid id, CancellationToken cancellationToken)
        => Ok(await promotionService.ReleaseAsync(id, cancellationToken));
}
