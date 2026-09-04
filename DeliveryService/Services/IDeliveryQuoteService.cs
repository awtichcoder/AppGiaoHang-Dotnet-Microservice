using DeliveryService.Contracts;

namespace DeliveryService.Services;

public interface IDeliveryQuoteService
{
    DeliveryQuoteResponse CalculateQuote(DeliveryQuoteRequest request);
}