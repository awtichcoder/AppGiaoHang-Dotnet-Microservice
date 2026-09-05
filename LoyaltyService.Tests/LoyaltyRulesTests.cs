using LoyaltyService.Services;
using Xunit;

namespace LoyaltyService.Tests;

public sealed class LoyaltyRulesTests
{
    [Theory]
    [InlineData(20, 59_520, 100, 20)]
    [InlineData(100, 2_500, 100, 3)]
    [InlineData(20, 100_000, 5, 5)]
    [InlineData(0, 100_000, 100, 0)]
    public void Usable_points_never_exceed_request_balance_or_order_amount(
        int requested, long amount, int available, int expected)
    {
        Assert.Equal(expected, LoyaltyRules.CalculateUsablePoints(requested, amount, available));
    }

    [Theory]
    [InlineData(0, "BRONZE")]
    [InlineData(999, "BRONZE")]
    [InlineData(1000, "SILVER")]
    [InlineData(5000, "GOLD")]
    public void Tier_is_derived_from_current_balance(int points, string expected)
    {
        Assert.Equal(expected, LoyaltyRules.CalculateTier(points));
    }
}
