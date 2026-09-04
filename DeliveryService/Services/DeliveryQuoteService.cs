using DeliveryService.Contracts;
using Microsoft.Extensions.Options;

namespace DeliveryService.Services;

public class DeliveryQuoteService(IOptions<DeliveryFeeOptions> options) : IDeliveryQuoteService
{
    private readonly DeliveryFeeOptions _options = options.Value;

    public DeliveryQuoteResponse CalculateQuote(
        DeliveryQuoteRequest request)
    {
        if (request.DistanceKm <= 0)
        {
            throw new ArgumentException(
                "distanceKm phải lớn hơn 0.");
        }

        var baseFee = (long)Math.Round(
            request.DistanceKm * _options.PricePerKm,
            0,
            MidpointRounding.AwayFromZero);

        var surcharge = (long)Math.Round(
            baseFee * _options.SurchargeRate,
            0,
            MidpointRounding.AwayFromZero);

        var shippingFee =
            baseFee +
            surcharge +
            _options.PlatformFee;

        return new DeliveryQuoteResponse
        {
            BaseFee = baseFee,
            Surcharge = surcharge,
            PlatformFee = _options.PlatformFee,
            ShippingFee = shippingFee
        };
    }
}
