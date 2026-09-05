using DeliveryService.Services;
using Xunit;

namespace DeliveryService.Tests;

public sealed class DeliveryMatchingRulesTests
{
    [Theory]
    [InlineData(0, 0, 3, true, true)]
    [InlineData(3, 0, 3, true, true)]
    [InlineData(3, 3, 5, false, false)]
    [InlineData(3.001, 3, 5, false, true)]
    [InlineData(5.001, 3, 5, false, false)]
    public void Radius_steps_include_the_correct_boundaries(
        decimal distance, decimal previous, decimal current, bool firstStep, bool expected)
    {
        Assert.Equal(expected, DeliveryMatchingRules.IsInRadiusStep(distance, previous, current, firstStep));
    }
}
