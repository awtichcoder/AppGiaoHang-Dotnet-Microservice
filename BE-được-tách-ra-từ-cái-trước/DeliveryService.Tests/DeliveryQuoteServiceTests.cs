using DeliveryService.Contracts;
using DeliveryService.Services;
using Microsoft.Extensions.Options;
using Xunit;

namespace DeliveryService.Tests;

public sealed class DeliveryQuoteServiceTests
{
    [Fact]
    public void CalculateQuote_UsesConfiguredFormula()
    {
        var service = new DeliveryQuoteService(Options.Create(new DeliveryFeeOptions
        {
            PricePerKm = 10_000,
            SurchargeRate = 0.20m,
            PlatformFee = 3_000
        }));

        var result = service.CalculateQuote(new DeliveryQuoteRequest { DistanceKm = 5.2m });

        Assert.Equal(52_000, result.BaseFee);
        Assert.Equal(10_400, result.Surcharge);
        Assert.Equal(3_000, result.PlatformFee);
        Assert.Equal(65_400, result.ShippingFee);
    }

    [Fact]
    public void CalculateQuote_RejectsNonPositiveDistance()
    {
        var service = new DeliveryQuoteService(Options.Create(new DeliveryFeeOptions()));
        Assert.Throws<ArgumentException>(() => service.CalculateQuote(new DeliveryQuoteRequest { DistanceKm = 0 }));
    }
}
