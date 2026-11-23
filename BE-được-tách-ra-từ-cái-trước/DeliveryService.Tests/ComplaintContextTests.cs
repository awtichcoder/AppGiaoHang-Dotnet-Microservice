using DeliveryService.Common;
using DeliveryService.Contracts;
using DeliveryService.Controllers;
using DeliveryService.Entities;
using DeliveryService.Services;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Configuration;
using Xunit;

namespace DeliveryService.Tests;

public sealed class ComplaintContextTests
{
    private const string Key = "test-internal-key";

    [Fact]
    public void Context_exposes_only_ownership_status_and_utc_completion_time()
    {
        var completedAt = new DateTime(2026, 11, 20, 8, 0, 0, DateTimeKind.Unspecified);
        var delivery = new Delivery
        {
            OrderId = Guid.NewGuid(), CustomerId = Guid.NewGuid(), DriverId = Guid.NewGuid(),
            Status = DeliveryStatus.COMPLETED, CompletedAt = completedAt, ReceiverPhone = "0912345678"
        };

        var context = DeliveryComplaintContextResponse.From(delivery);

        Assert.Equal(delivery.OrderId, context.OrderId);
        Assert.Equal(delivery.CustomerId, context.CustomerId);
        Assert.Equal(delivery.DriverId, context.DriverId);
        Assert.Equal("COMPLETED", context.Status);
        Assert.Equal(DateTimeKind.Utc, context.CompletedAt!.Value.Kind);
        Assert.Equal(completedAt.Ticks, context.CompletedAt.Value.Ticks);
        Assert.DoesNotContain(typeof(DeliveryComplaintContextResponse).GetProperties(), p => p.Name == nameof(Delivery.ReceiverPhone));
    }

    [Fact]
    public void Context_keeps_null_completion_time_for_unfinished_delivery()
        => Assert.Null(DeliveryComplaintContextResponse.From(new Delivery { Status = DeliveryStatus.DELIVERING }).CompletedAt);

    [Theory]
    [InlineData(null)]
    [InlineData("wrong-key")]
    public async Task Endpoint_requires_the_internal_key(string? providedKey)
    {
        var workflow = new StubWorkflow();
        var error = await Assert.ThrowsAsync<ApiException>(() => Controller(workflow, providedKey).GetComplaintContext(Guid.NewGuid(), default));
        Assert.Equal(401, error.StatusCode);
        Assert.Equal("INVALID_INTERNAL_KEY", error.Code);
        Assert.Equal(0, workflow.Calls);
    }

    [Fact]
    public async Task Endpoint_returns_context_for_valid_key()
    {
        var orderId = Guid.NewGuid();
        var workflow = new StubWorkflow { Result = new DeliveryComplaintContextResponse { OrderId = orderId, Status = "COMPLETED" } };
        var result = await Controller(workflow, Key).GetComplaintContext(orderId, default);
        var body = Assert.IsType<DeliveryComplaintContextResponse>(Assert.IsType<OkObjectResult>(result.Result).Value);
        Assert.Equal(orderId, body.OrderId);
    }

    [Fact]
    public async Task Endpoint_returns_404_when_order_has_no_delivery()
    {
        var error = await Assert.ThrowsAsync<ApiException>(() => Controller(new StubWorkflow(), Key).GetComplaintContext(Guid.NewGuid(), default));
        Assert.Equal(404, error.StatusCode);
        Assert.Equal("DELIVERY_NOT_FOUND", error.Code);
    }

    private static DeliveryController Controller(IDeliveryWorkflowService workflow, string? providedKey)
    {
        var configuration = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?> { ["INTERNAL_API_KEY"] = Key })
            .Build();
        var http = new DefaultHttpContext();
        if (providedKey is not null) http.Request.Headers["X-Internal-Key"] = providedKey;
        return new DeliveryController(null!, workflow, configuration)
        {
            ControllerContext = new ControllerContext { HttpContext = http }
        };
    }

    private sealed class StubWorkflow : IDeliveryWorkflowService
    {
        public DeliveryComplaintContextResponse? Result { get; init; }
        public int Calls { get; private set; }

        public Task<DeliveryComplaintContextResponse?> GetComplaintContextAsync(Guid orderId, CancellationToken cancellationToken)
        {
            Calls++;
            return Task.FromResult(Result);
        }

        public Task<DeliveryResponse> AssignAsync(DeliveryAssignRequest request, CancellationToken cancellationToken) => throw new NotSupportedException();
        public Task<PagedResponse<DeliveryOfferResponse>> GetMyOffersAsync(Guid driverId, int page, int pageSize, CancellationToken cancellationToken) => throw new NotSupportedException();
        public Task<PagedResponse<DeliveryResponse>> GetDriverHistoryAsync(Guid driverId, int page, int pageSize, CancellationToken cancellationToken) => throw new NotSupportedException();
        public Task<AcceptOfferResponse> AcceptOfferAsync(Guid offerId, Guid driverId, CancellationToken cancellationToken) => throw new NotSupportedException();
        public Task<RejectOfferResponse> RejectOfferAsync(Guid offerId, Guid driverId, string reasonCode, CancellationToken cancellationToken) => throw new NotSupportedException();
        public Task<DeliveryResponse> ChangeDriverStageAsync(Guid deliveryId, Guid driverId, string action, CancellationToken cancellationToken, PickupDeliveryRequest? pickup = null) => throw new NotSupportedException();
        public Task<DeliveryResponse> CancelByOrderIdAsync(Guid orderId, CancellationToken cancellationToken) => throw new NotSupportedException();
        public Task<DeliveryResponse> CancelByDeliveryIdAsync(Guid deliveryId, CancellationToken cancellationToken) => throw new NotSupportedException();
        public Task<DeliveryResponse?> GetByOrderAsync(Guid orderId, Guid actorId, string role, CancellationToken cancellationToken) => throw new NotSupportedException();
        public Task<MatchingStatusResponse?> GetMatchingStatusAsync(Guid orderId, Guid actorId, string role, CancellationToken cancellationToken) => throw new NotSupportedException();
        public Task<DriverLocationResponse> GetLocationAsync(Guid deliveryId, Guid actorId, string role, CancellationToken cancellationToken) => throw new NotSupportedException();
    }
}
