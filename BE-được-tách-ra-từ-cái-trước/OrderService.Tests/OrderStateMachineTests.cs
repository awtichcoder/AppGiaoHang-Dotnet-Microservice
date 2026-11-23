using OrderService.Common;
using OrderService.Entities;
using Xunit;

namespace OrderService.Tests;

public sealed class OrderStateMachineTests
{
    [Theory]
    [InlineData(OrderStatus.PENDING, OrderStatus.ASSIGNED)]
    [InlineData(OrderStatus.ASSIGNED, OrderStatus.PICKED_UP)]
    [InlineData(OrderStatus.PICKED_UP, OrderStatus.DELIVERING)]
    [InlineData(OrderStatus.DELIVERING, OrderStatus.COMPLETED)]
    public void CanTransition_AllowsHappyPath(OrderStatus current, OrderStatus target)
        => Assert.True(OrderStateMachine.CanTransition(current, target));

    [Theory]
    [InlineData(OrderStatus.PICKED_UP)]
    [InlineData(OrderStatus.DELIVERING)]
    [InlineData(OrderStatus.COMPLETED)]
    public void CanCustomerCancel_RejectsAfterPickup(OrderStatus status)
        => Assert.False(OrderStateMachine.CanCustomerCancel(status));
}
