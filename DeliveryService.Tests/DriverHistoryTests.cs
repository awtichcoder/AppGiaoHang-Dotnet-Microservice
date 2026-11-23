using DeliveryService.Common;
using DeliveryService.Data;
using DeliveryService.Entities;
using DeliveryService.Services;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging.Abstractions;
using Microsoft.Extensions.Options;
using Xunit;

namespace DeliveryService.Tests;

public sealed class DriverHistoryTests : IAsyncLifetime
{
    private static readonly DateTime Now = new(2026, 11, 20, 8, 0, 0, DateTimeKind.Utc);
    private readonly Guid _driverId = Guid.NewGuid();
    private readonly SqliteConnection _connection = new("Data Source=:memory:");
    private DeliveryDbContext _db = null!;
    private DeliveryWorkflowService _service = null!;

    public async Task InitializeAsync()
    {
        await _connection.OpenAsync();
        _db = new DeliveryDbContext(new DbContextOptionsBuilder<DeliveryDbContext>().UseSqlite(_connection).Options);
        await _db.Database.EnsureCreatedAsync();
        _service = new DeliveryWorkflowService(_db, null!, Options.Create(new MatchingOptions()),
            NullLogger<DeliveryWorkflowService>.Instance);
    }

    public async Task DisposeAsync()
    {
        await _db.DisposeAsync();
        await _connection.DisposeAsync();
    }

    [Fact]
    public async Task Returns_only_this_drivers_finished_trips_newest_first()
    {
        var older = Add(_driverId, DeliveryStatus.COMPLETED, completedAt: Now.AddDays(-2));
        var cancelled = Add(_driverId, DeliveryStatus.CANCELLED, cancelledAt: Now.AddDays(-1));
        var newest = Add(_driverId, DeliveryStatus.COMPLETED, completedAt: Now);
        Add(_driverId, DeliveryStatus.DELIVERING);
        Add(_driverId, DeliveryStatus.ASSIGNED);
        Add(Guid.NewGuid(), DeliveryStatus.COMPLETED, completedAt: Now);
        Add(null, DeliveryStatus.NO_DRIVER_FOUND);
        await _db.SaveChangesAsync();

        var result = await _service.GetDriverHistoryAsync(_driverId, 1, 20, default);

        Assert.Equal(3, result.TotalItems);
        Assert.Equal([newest.DeliveryId, cancelled.DeliveryId, older.DeliveryId], result.Items.Select(x => x.DeliveryId));
    }

    [Fact]
    public async Task Does_not_expose_the_receiver_phone()
    {
        Add(_driverId, DeliveryStatus.COMPLETED, completedAt: Now);
        await _db.SaveChangesAsync();

        var result = await _service.GetDriverHistoryAsync(_driverId, 1, 20, default);

        Assert.Null(Assert.Single(result.Items).ReceiverPhone);
    }

    [Fact]
    public async Task Pages_through_results()
    {
        for (var i = 0; i < 3; i++) Add(_driverId, DeliveryStatus.COMPLETED, completedAt: Now.AddHours(-i));
        await _db.SaveChangesAsync();

        var first = await _service.GetDriverHistoryAsync(_driverId, 1, 2, default);
        var second = await _service.GetDriverHistoryAsync(_driverId, 2, 2, default);

        Assert.Equal(3, first.TotalItems);
        Assert.Equal(2, first.Items.Count);
        Assert.Single(second.Items);
        Assert.DoesNotContain(second.Items[0].DeliveryId, first.Items.Select(x => x.DeliveryId));
    }

    [Theory]
    [InlineData(0, 20)]
    [InlineData(1, 0)]
    [InlineData(1, 101)]
    public async Task Rejects_invalid_pagination(int page, int pageSize)
    {
        var error = await Assert.ThrowsAsync<ApiException>(() => _service.GetDriverHistoryAsync(_driverId, page, pageSize, default));
        Assert.Equal(400, error.StatusCode);
        Assert.Equal("INVALID_PAGINATION", error.Code);
    }

    private Delivery Add(Guid? driverId, DeliveryStatus status, DateTime? completedAt = null, DateTime? cancelledAt = null)
    {
        var delivery = new Delivery
        {
            DeliveryId = Guid.NewGuid(), OrderId = Guid.NewGuid(), CustomerId = Guid.NewGuid(), DriverId = driverId,
            PickupAddress = "A", DropoffAddress = "B", ReceiverPhone = "0912345678", Status = status,
            CompletedAt = completedAt, CancelledAt = cancelledAt
        };
        _db.Deliveries.Add(delivery);
        return delivery;
    }
}
