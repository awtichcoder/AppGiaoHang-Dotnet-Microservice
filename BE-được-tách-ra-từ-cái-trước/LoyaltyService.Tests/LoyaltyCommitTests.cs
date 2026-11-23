using LoyaltyService.Common;
using LoyaltyService.Contracts;
using LoyaltyService.Data;
using LoyaltyService.Entities;
using LoyaltyService.Services;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Xunit;

namespace LoyaltyService.Tests;

public sealed class LoyaltyCommitTests : IAsyncLifetime
{
    private readonly SqliteConnection _connection = new("Data Source=:memory:");
    private LoyaltyDbContext _db = null!;
    private LoyaltyManager _manager = null!;
    private readonly Guid _customer = Guid.NewGuid();

    public async Task InitializeAsync()
    {
        await _connection.OpenAsync();
        _db = new LoyaltyDbContext(new DbContextOptionsBuilder<LoyaltyDbContext>().UseSqlite(_connection).Options);
        await _db.Database.EnsureCreatedAsync();
        _manager = new LoyaltyManager(_db);
        // 100 points.
        await _manager.EarnAsync(new EarnPointsRequest { OrderId = Guid.NewGuid(), CustomerId = _customer, Amount = 1_000_000 }, default);
    }

    public async Task DisposeAsync()
    {
        await _db.DisposeAsync();
        await _connection.DisposeAsync();
    }

    private Task<LoyaltyReservationResponse> Reserve(int points) => _manager.ReserveAsync(new ReservePointsRequest
    {
        OrderId = Guid.NewGuid(), CustomerId = _customer, Points = points, OrderAmount = 1_000_000
    }, default);

    private async Task Expire(Guid reservationId)
    {
        _db.ChangeTracker.Clear();
        var reservation = await _db.Reservations.SingleAsync(x => x.ReservationId == reservationId);
        reservation.ExpiresAt = DateTime.UtcNow.AddMinutes(-1);
        await _db.SaveChangesAsync();
    }

    private async Task<int> Balance()
    {
        _db.ChangeTracker.Clear();
        return (await _db.Accounts.SingleAsync(x => x.CustomerId == _customer)).Points;
    }

    [Fact]
    public async Task Expired_but_unreleased_hold_is_committed_when_balance_allows()
    {
        var hold = await Reserve(30);
        await Expire(hold.ReservationId);

        var committed = await _manager.CommitAsync(hold.ReservationId, default);

        Assert.Equal("COMMITTED", committed.Status);
        Assert.Equal(70, await Balance());
    }

    [Fact]
    public async Task Expired_hold_cannot_take_points_held_by_another_pending_order()
    {
        var stale = await Reserve(60);
        await Expire(stale.ReservationId);
        // The expired hold no longer counts, so a new order may reserve 60 of the 100 points.
        await Reserve(60);

        var error = await Assert.ThrowsAsync<ApiException>(() => _manager.CommitAsync(stale.ReservationId, default));
        Assert.Equal("LOYALTY_POINTS_INSUFFICIENT", error.Code);
        Assert.Equal(100, await Balance());
    }

    [Fact]
    public async Task Concurrent_unexpired_holds_both_commit_when_reserved_within_balance()
    {
        var first = await Reserve(40);
        var second = await Reserve(60);

        await _manager.CommitAsync(first.ReservationId, default);
        await _manager.CommitAsync(second.ReservationId, default);

        Assert.Equal(0, await Balance());
    }
}
