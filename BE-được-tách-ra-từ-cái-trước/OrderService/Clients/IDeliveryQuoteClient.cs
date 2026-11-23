using OrderService.Contracts;

namespace OrderService.Clients;

public interface IDeliveryQuoteClient
{
    Task<DeliveryQuoteResponse> GetQuoteAsync(
        decimal distanceKm,
        CancellationToken cancellationToken = default);
}