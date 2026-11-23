using OrderService.Contracts;

namespace OrderService.Clients;

public interface IDeliveryCommandClient
{
    Task AssignAsync(DeliveryAssignRequest request, CancellationToken cancellationToken);
    Task CancelAsync(Guid orderId, string reasonCode, CancellationToken cancellationToken);
}
