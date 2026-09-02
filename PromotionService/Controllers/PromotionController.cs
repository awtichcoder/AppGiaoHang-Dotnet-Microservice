using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using PromotionService.Contracts;
using PromotionService.Services;

namespace PromotionService.Controllers;

[ApiController]
[Route("api/promotion")]
public class PromotionController : ControllerBase
{
    private readonly IPromotionService _promotionService;

    public PromotionController(IPromotionService promotionService)
    {
        _promotionService = promotionService;
    }

    // CustomerApp gọi để preview giảm giá, hoặc OrderService gọi nội bộ trước khi reserve.
    [HttpPost("validate")]
    [Authorize(Policy = "ServiceOrUser")]
    public async Task<IActionResult> Validate([FromBody] ValidatePromotionRequest request, CancellationToken ct)
    {
        if (string.IsNullOrWhiteSpace(request.Code) || request.CustomerId == Guid.Empty)
        {
            return BadRequest(new { code = "INVALID_REQUEST", message = "Thiếu tham số", details = (object?)null });
        }

        var result = await _promotionService.ValidateAsync(request, ct);
        return Ok(result);
    }

    // Chỉ OrderService (nội bộ) được gọi reserve/commit/release.
    [HttpPost("reserve")]
    [Authorize(Policy = "InternalOnly")]
    public async Task<IActionResult> Reserve([FromBody] ReservePromotionRequest request, CancellationToken ct)
    {
        if (string.IsNullOrWhiteSpace(request.Code) || request.CustomerId == Guid.Empty || request.OrderId == Guid.Empty)
        {
            return BadRequest(new { code = "INVALID_REQUEST", message = "Thiếu tham số", details = (object?)null });
        }

        var result = await _promotionService.ReserveAsync(request, ct);
        return Ok(result);
    }

    [HttpPost("reservations/{id:guid}/commit")]
    [Authorize(Policy = "InternalOnly")]
    public async Task<IActionResult> Commit(Guid id, CancellationToken ct)
    {
        var result = await _promotionService.CommitAsync(id, ct);
        return Ok(result);
    }

    [HttpPost("reservations/{id:guid}/release")]
    [Authorize(Policy = "InternalOnly")]
    public async Task<IActionResult> Release(Guid id, CancellationToken ct)
    {
        var result = await _promotionService.ReleaseAsync(id, ct);
        return Ok(result);
    }
}
