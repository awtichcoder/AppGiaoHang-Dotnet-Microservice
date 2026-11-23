namespace DeliveryService.Clients;

public interface ILoyaltyClient
{
    Task EarnAsync(Guid orderId, Guid customerId, long amount, CancellationToken cancellationToken);
}
