using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using OrderService.Common;
using OrderService.Contracts;
using OrderService.Services;

namespace OrderService.Controllers;

[ApiController]
[Route("api/order")]
public sealed class OrdersController(IOrderService orderService, IConfiguration configuration) : ControllerBase
{
    [Authorize(Roles = "CUSTOMER")]
    [HttpPost]
    public async Task<ActionResult<OrderResponse>> CreateOrder(CreateOrderRequest request, CancellationToken cancellationToken)
    {
        var response = await orderService.CreateOrderAsync(request, User.GetSubjectId(), cancellationToken);
        return CreatedAtAction(nameof(GetOrderById), new { orderId = response.OrderId }, response);
    }

    [Authorize(Roles = "CUSTOMER,DRIVER,ADMIN")]
    [HttpGet]
    public async Task<ActionResult<PagedResponse<OrderResponse>>> GetOrders(
        [FromQuery] int page = 1, [FromQuery] int pageSize = 20, [FromQuery] string? status = null,
        CancellationToken cancellationToken = default)
    {
        var role = User.FindFirst("role")?.Value ?? "CUSTOMER";
        return Ok(await orderService.GetOrdersAsync(User.GetSubjectId(), role, page, pageSize, status, cancellationToken));
    }

    [Authorize(Roles = "CUSTOMER,DRIVER,ADMIN")]
    [HttpGet("{orderId:guid}")]
    public async Task<ActionResult<OrderResponse>> GetOrderById(Guid orderId, CancellationToken cancellationToken)
    {
        var role = User.FindFirst("role")?.Value ?? "CUSTOMER";
        var response = await orderService.GetOrderByIdAsync(orderId, User.GetSubjectId(), role, cancellationToken);
        return response is null
            ? throw new ApiException(404, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng.")
            : Ok(response);
    }

    [Authorize(Roles = "CUSTOMER")]
    [HttpPost("{orderId:guid}/cancel")]
    public async Task<ActionResult<OrderResponse>> CancelOrder(Guid orderId, CancelOrderRequest request, CancellationToken cancellationToken)
        => Ok(await orderService.CancelOrderAsync(orderId, User.GetSubjectId(), request.Version, request.ReasonCode, cancellationToken));

    [AllowAnonymous]
    [HttpPatch("{orderId:guid}/status")]
    public async Task<ActionResult<OrderResponse>> UpdateOrderStatus(Guid orderId, UpdateOrderStatusRequest request, CancellationToken cancellationToken)
    {
        if (!InternalApiKey.IsValid(Request, configuration))
            throw new ApiException(401, "INVALID_INTERNAL_KEY", "Internal API key không hợp lệ.");
        return Ok(await orderService.UpdateOrderStatusAsync(orderId, request.Status, request.Version, request.DriverId, cancellationToken));
    }
}
