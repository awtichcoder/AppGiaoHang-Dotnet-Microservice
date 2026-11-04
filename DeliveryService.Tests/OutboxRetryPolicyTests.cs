using System.Net;
using DeliveryService.Entities;
using DeliveryService.Services;
using Xunit;

namespace DeliveryService.Tests;

public sealed class OutboxRetryPolicyTests
{
    private static readonly DateTime Now = new(2026, 11, 4, 10, 0, 0, DateTimeKind.Utc);

    [Fact]
    public void Rejected_order_status_update_is_parked_after_the_retry_budget()
    {
        var message = new OutboxMessage { Type = "OrderStatus" };
        var error = new HttpRequestException("Order status update thất bại: 404", null, HttpStatusCode.NotFound);
        for (var attempt = 1; attempt < OutboxRetryPolicy.MaxBusinessFailures; attempt++)
            Assert.False(OutboxRetryPolicy.RecordFailure(message, error, Now));

        Assert.True(OutboxRetryPolicy.RecordFailure(message, error, Now));
        Assert.Equal(OutboxRetryPolicy.ParkedUntil, message.NextAttemptAt);
        Assert.StartsWith(OutboxRetryPolicy.ParkedPrefix, message.LastError);
    }

    [Fact]
    public void Unavailable_dependency_keeps_retrying_every_minute()
    {
        var message = new OutboxMessage { RetryCount = 500 };
        Assert.False(OutboxRetryPolicy.RecordFailure(message,
            new HttpRequestException("DriverService trả về 503", null, HttpStatusCode.ServiceUnavailable), Now));
        Assert.Equal(Now.AddSeconds(60), message.NextAttemptAt);
    }
}
