using DeliveryService.Common;
using DeliveryService.Contracts;
using DeliveryService.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace DeliveryService.Controllers;

[ApiController]
[Route("api/delivery")]
public sealed class DeliveryController(
    IDeliveryQuoteService quoteService,
    IDeliveryWorkflowService workflow,
    IConfiguration configuration) : ControllerBase
{
    [AllowAnonymous]
    [HttpPost("quote")]
    public ActionResult<DeliveryQuoteResponse> GetQuote(DeliveryQuoteRequest request)
    {
        RequireInternalKey();
        try { return Ok(quoteService.CalculateQuote(request)); }
        catch (ArgumentException exception) { throw new ApiException(400, "INVALID_DISTANCE", exception.Message); }
    }

    [AllowAnonymous]
    [HttpPost("assign")]
    public async Task<ActionResult<DeliveryResponse>> Assign(DeliveryAssignRequest request, CancellationToken cancellationToken)
    {
        RequireInternalKey();
        var response = await workflow.AssignAsync(request, cancellationToken);
        return StatusCode(StatusCodes.Status201Created, response);
    }

    [Authorize(Roles = "DRIVER")]
    [HttpGet("offers/me")]
    public async Task<ActionResult<IReadOnlyList<DeliveryOfferResponse>>> GetMyOffers(CancellationToken cancellationToken)
        => Ok(await workflow.GetMyOffersAsync(User.SubjectId(), cancellationToken));

    [Authorize(Roles = "DRIVER")]
    [HttpPost("offers/{offerId:guid}/accept")]
    public async Task<ActionResult<DeliveryResponse>> Accept(Guid offerId, CancellationToken cancellationToken)
        => Ok(await workflow.AcceptOfferAsync(offerId, User.SubjectId(), cancellationToken));

    [Authorize(Roles = "DRIVER")]
    [HttpPost("offers/{offerId:guid}/reject")]
    public async Task<IActionResult> Reject(Guid offerId, CancellationToken cancellationToken)
    {
        await workflow.RejectOfferAsync(offerId, User.SubjectId(), cancellationToken);
        return NoContent();
    }

    [Authorize(Roles = "DRIVER")]
    [HttpPost("{deliveryId:guid}/pickup")]
    public Task<ActionResult<DeliveryResponse>> Pickup(Guid deliveryId, CancellationToken cancellationToken)
        => Stage(deliveryId, "pickup", cancellationToken);

    [Authorize(Roles = "DRIVER")]
    [HttpPost("{deliveryId:guid}/start")]
    public Task<ActionResult<DeliveryResponse>> Start(Guid deliveryId, CancellationToken cancellationToken)
        => Stage(deliveryId, "start", cancellationToken);

    [Authorize(Roles = "DRIVER")]
    [HttpPost("{deliveryId:guid}/complete")]
    public Task<ActionResult<DeliveryResponse>> Complete(Guid deliveryId, CancellationToken cancellationToken)
        => Stage(deliveryId, "complete", cancellationToken);

    [Authorize(Roles = "CUSTOMER,DRIVER,ADMIN")]
    [HttpGet("order/{orderId:guid}")]
    public async Task<ActionResult<DeliveryResponse>> GetByOrder(Guid orderId, CancellationToken cancellationToken)
    {
        var result = await workflow.GetByOrderAsync(orderId, User.SubjectId(), Role(), cancellationToken);
        return result is null ? throw new ApiException(404, "DELIVERY_NOT_FOUND", "Không tìm thấy delivery.") : Ok(result);
    }

    [Authorize(Roles = "CUSTOMER,DRIVER,ADMIN")]
    [HttpGet("order/{orderId:guid}/matching-status")]
    public async Task<ActionResult<MatchingStatusResponse>> GetMatchingStatus(Guid orderId, CancellationToken cancellationToken)
    {
        var result = await workflow.GetMatchingStatusAsync(orderId, User.SubjectId(), Role(), cancellationToken);
        return result is null ? throw new ApiException(404, "DELIVERY_NOT_FOUND", "Không tìm thấy delivery.") : Ok(result);
    }

    [Authorize(Roles = "CUSTOMER,ADMIN")]
    [HttpGet("{deliveryId:guid}/location")]
    public async Task<ActionResult<DriverLocationResponse>> GetLocation(Guid deliveryId, CancellationToken cancellationToken)
        => Ok(await workflow.GetLocationAsync(deliveryId, User.SubjectId(), Role(), cancellationToken));

    [AllowAnonymous]
    [HttpPost("order/{orderId:guid}/cancel")]
    public async Task<ActionResult<DeliveryResponse>> CancelByOrder(Guid orderId, CancelDeliveryRequest request, CancellationToken cancellationToken)
    {
        RequireInternalKey();
        return Ok(await workflow.CancelByOrderIdAsync(orderId, cancellationToken));
    }

    [AllowAnonymous]
    [HttpPost("{orderId:guid}/cancel")]
    public Task<ActionResult<DeliveryResponse>> Cancel(Guid orderId, CancelDeliveryRequest request, CancellationToken cancellationToken)
        => CancelByOrder(orderId, request, cancellationToken);

    private async Task<ActionResult<DeliveryResponse>> Stage(Guid deliveryId, string action, CancellationToken cancellationToken)
        => Ok(await workflow.ChangeDriverStageAsync(deliveryId, User.SubjectId(), action, cancellationToken));

    private string Role() => User.FindFirst("role")?.Value ?? string.Empty;

    private void RequireInternalKey()
    {
        if (!Request.HasValidInternalKey(configuration))
            throw new ApiException(401, "INVALID_INTERNAL_KEY", "Internal API key không hợp lệ.");
    }
}
