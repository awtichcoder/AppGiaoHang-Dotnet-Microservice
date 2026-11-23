namespace DeliveryService.Clients;

public interface IOrderStatusClient
{
    Task UpdateAsync(Guid orderId, string status, int version, Guid? driverId, CancellationToken cancellationToken);
}
