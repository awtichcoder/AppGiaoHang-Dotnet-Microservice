using DeliveryService.Services;
using Xunit;

namespace DeliveryService.Tests;

public sealed class PickupRulesTests
{
    [Theory]
    [InlineData(10.776889, 106.700806, true)]
    [InlineData(10.777, 106.701, true)]
    [InlineData(10.79, 106.72, false)]
    [InlineData(91, 106.7, false)]
    public void Pickup_requires_a_valid_location_near_the_pickup(decimal lat, decimal lon, bool expected)
        => Assert.Equal(expected, PickupRules.IsNearPickup(lat, lon, 10.776889m, 106.700806m, 200));
}
