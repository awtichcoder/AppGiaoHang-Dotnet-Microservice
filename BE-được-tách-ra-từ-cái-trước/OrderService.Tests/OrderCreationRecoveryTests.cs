using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging.Abstractions;
using OrderService.Clients;
using OrderService.Contracts;
using OrderService.Data;
using OrderService.Entities;
using OrderService.Services;
using Xunit;

namespace OrderService.Tests;

public sealed class OrderCreationRecoveryTests
{
    [Theory]
    [InlineData("points")]
    [InlineData("save")]
    [InlineData("lost-response")]
    [InlineData("cleanup-offline")]
    public async Task Failed_creation_releases_by_order_and_keeps_durable_recovery(string failure)
    {
        await using var connection = new SqliteConnection("Data Source=:memory:");
        await connection.OpenAsync();
        await using var db = new FailingDb(new DbContextOptionsBuilder<OrderDbContext>().UseSqlite(connection).Options);
        await db.Database.EnsureCreatedAsync();
        var dependencies = new Pricing(db, failure);
        var service = new OrderService.Services.OrderService(db, new Quote(), dependencies,
            NullLogger<OrderService.Services.OrderService>.Instance);

        await Assert.ThrowsAnyAsync<Exception>(() => service.CreateOrderAsync(Request(), Guid.NewGuid()));

        Assert.Empty(await db.Orders.ToListAsync());
        Assert.Equal(dependencies.OrderId, dependencies.ReleasedOrderId);
        var recovery = await db.OutboxMessages.SingleAsync();
        Assert.Equal("ReleaseUncreatedOrder", recovery.Type);
        Assert.Null(recovery.ProcessedAt);
        Assert.Contains(dependencies.OrderId.ToString(), recovery.Payload);
    }

    [Fact]
    public async Task Success_keeps_reservations_and_idempotent_retry_returns_same_order()
    {
        await using var connection = new SqliteConnection("Data Source=:memory:");
        await connection.OpenAsync();
        await using var db = new FailingDb(new DbContextOptionsBuilder<OrderDbContext>().UseSqlite(connection).Options);
        await db.Database.EnsureCreatedAsync();
        var dependencies = new Pricing(db, "none");
        var service = new OrderService.Services.OrderService(db, new Quote(), dependencies,
            NullLogger<OrderService.Services.OrderService>.Instance);
        var customer = Guid.NewGuid();
        var request = Request();
        var first = await service.CreateOrderAsync(request, customer);
        var second = await service.CreateOrderAsync(request, customer);
        Assert.Equal(first.OrderId, second.OrderId);
        Assert.Equal(38000, first.TotalFee);
        Assert.Null(dependencies.ReleasedOrderId);
        Assert.Single(await db.Orders.ToListAsync());
        Assert.NotNull((await db.OutboxMessages.SingleAsync(m => m.Type == "ReleaseUncreatedOrder")).ProcessedAt);
        Assert.Single(await db.OutboxMessages.Where(m => m.Type == "DeliveryAssign").ToListAsync());
    }

    private static CreateOrderRequest Request() => new()
    {
        ClientRequestId = Guid.NewGuid(), PickupAddress = "Pickup", DropoffAddress = "Dropoff",
        ReceiverPhone = "0912345678", PromotionCode = "TEST", PointsToUse = 2,
        PickupLocation = new() { Latitude = 10.77m, Longitude = 106.7m },
        DropoffLocation = new() { Latitude = 10.78m, Longitude = 106.71m }
    };

    private sealed class FailingDb(DbContextOptions<OrderDbContext> options) : OrderDbContext(options)
    {
        public bool FailOrders { get; set; }
        public override Task<int> SaveChangesAsync(CancellationToken cancellationToken = default)
        {
            if (FailOrders && ChangeTracker.Entries<Order>().Any(e => e.State == EntityState.Added))
                throw new DbUpdateException("Simulated order insert failure");
            return base.SaveChangesAsync(cancellationToken);
        }
    }

    private sealed class Quote : IDeliveryQuoteClient
    {
        public Task<DeliveryQuoteResponse> GetQuoteAsync(decimal distanceKm, CancellationToken cancellationToken)
            => Task.FromResult(new DeliveryQuoteResponse { ShippingFee = 50000, BaseFee = 40000, Surcharge = 7000, PlatformFee = 3000 });
    }

    private sealed class Pricing(FailingDb db, string failure) : IOrderPricingDependencies
    {
        public Guid OrderId { get; private set; }
        public Guid? ReleasedOrderId { get; private set; }
        public Task<decimal> GetDistanceKmAsync(GeoPointRequest a, GeoPointRequest b, CancellationToken ct) => Task.FromResult(4m);
        public Task<long> PreviewPromotionAsync(Guid customerId, string? code, long amount, CancellationToken ct) => Task.FromResult(10000L);
        public async Task<PricingReservation> ReservePromotionAsync(Guid orderId, Guid customerId, string? code, long amount, CancellationToken ct)
        {
            OrderId = orderId;
            // Recovery must already exist before any remote call can reserve resources.
            Assert.True(await db.OutboxMessages.AnyAsync(m => m.Type == "ReleaseUncreatedOrder", ct));
            if (failure == "lost-response") throw new HttpRequestException("Remote reserve committed but response was lost");
            return new(10000, Guid.NewGuid());
        }
        public Task<PricingReservation> ReservePointsAsync(Guid orderId, Guid customerId, int points, long amount, CancellationToken ct)
        {
            if (failure is "points" or "cleanup-offline") throw new InvalidOperationException("Points unavailable");
            db.FailOrders = failure == "save";
            return Task.FromResult(new PricingReservation(2000, Guid.NewGuid()));
        }
        public Task ReleaseByOrderAsync(Guid orderId, CancellationToken ct)
        {
            Assert.False(ct.IsCancellationRequested);
            ReleasedOrderId = orderId;
            if (failure == "cleanup-offline") throw new HttpRequestException("Cleanup service unavailable");
            return Task.CompletedTask;
        }
        public Task CommitPromotionAsync(Guid id, CancellationToken ct) => Task.CompletedTask;
        public Task ReleasePromotionAsync(Guid id, CancellationToken ct) => Task.CompletedTask;
        public Task CommitPointsAsync(Guid id, CancellationToken ct) => Task.CompletedTask;
        public Task ReleasePointsAsync(Guid id, CancellationToken ct) => Task.CompletedTask;
    }
}
