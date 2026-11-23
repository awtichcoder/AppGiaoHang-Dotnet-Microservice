using System.Net;
using OrderService.Common;
using OrderService.Entities;
using OrderService.Services;
using Xunit;

namespace OrderService.Tests;

public sealed class OutboxRetryPolicyTests
{
    private static readonly DateTime Now = new(2026, 11, 4, 10, 0, 0, DateTimeKind.Utc);

    [Fact]
    public void Business_rejection_is_parked_after_the_retry_budget()
    {
        var message = new OutboxMessage { Type = "CommitReservations" };
        var error = new ApiException(409, "CUSTOMER_USAGE_LIMIT", "Bạn đã dùng hết lượt cho mã khuyến mãi này.");

        for (var attempt = 1; attempt < OutboxRetryPolicy.MaxBusinessFailures; attempt++)
        {
            Assert.False(OutboxRetryPolicy.RecordFailure(message, error, Now));
            Assert.True(message.NextAttemptAt <= Now.AddSeconds(60));
        }

        Assert.True(OutboxRetryPolicy.RecordFailure(message, error, Now));
        Assert.Equal(OutboxRetryPolicy.ParkedUntil, message.NextAttemptAt);
        Assert.StartsWith(OutboxRetryPolicy.ParkedPrefix, message.LastError);
        Assert.Null(message.ProcessedAt);
    }

    [Theory]
    [InlineData(HttpStatusCode.NotFound)]
    [InlineData(HttpStatusCode.BadRequest)]
    [InlineData(HttpStatusCode.UnprocessableEntity)]
    public void Http_business_status_is_parked(HttpStatusCode status)
    {
        var message = new OutboxMessage { RetryCount = OutboxRetryPolicy.MaxBusinessFailures - 1 };
        Assert.True(OutboxRetryPolicy.RecordFailure(message, new HttpRequestException("rejected", null, status), Now));
    }

    [Theory]
    [InlineData(502)]
    [InlineData(401)]
    public void Outages_and_configuration_errors_keep_retrying(int status)
    {
        var message = new OutboxMessage { RetryCount = 500 };
        Assert.False(OutboxRetryPolicy.RecordFailure(message, new ApiException(status, "X", "down"), Now));
        Assert.Equal(Now.AddSeconds(60), message.NextAttemptAt);
    }

    [Fact]
    public void Network_errors_without_status_keep_retrying()
    {
        var message = new OutboxMessage { RetryCount = 500 };
        Assert.False(OutboxRetryPolicy.RecordFailure(message, new HttpRequestException("connection refused"), Now));
        Assert.False(OutboxRetryPolicy.RecordFailure(message, new TaskCanceledException(), Now));
    }

    [Fact]
    public void Error_text_is_truncated()
    {
        var message = new OutboxMessage();
        OutboxRetryPolicy.RecordFailure(message, new Exception(new string('x', 5000)), Now);
        Assert.Equal(2000, message.LastError!.Length);
    }
}
