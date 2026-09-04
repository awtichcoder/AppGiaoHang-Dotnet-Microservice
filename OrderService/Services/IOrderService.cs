using OrderService.Contracts;

namespace OrderService.Services;

public interface IOrderService
{
    Task<OrderResponse> CreateOrderAsync(CreateOrderRequest request, Guid customerId, CancellationToken cancellationToken = default);
    Task<OrderResponse?> GetOrderByIdAsync(Guid orderId, Guid actorId, string role, CancellationToken cancellationToken = default);
    Task<IReadOnlyList<OrderResponse>> GetOrdersAsync(Guid actorId, string role, int page, int pageSize, CancellationToken cancellationToken = default);
    Task<OrderResponse> CancelOrderAsync(Guid orderId, Guid customerId, int expectedVersion, string reasonCode, CancellationToken cancellationToken = default);
    Task<OrderResponse> UpdateOrderStatusAsync(Guid orderId, string newStatus, int expectedVersion, Guid? driverId, CancellationToken cancellationToken = default);
}
