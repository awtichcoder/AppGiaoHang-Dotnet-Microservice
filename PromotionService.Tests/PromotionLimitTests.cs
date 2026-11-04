using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using PromotionService.Common;
using PromotionService.Contracts;
using PromotionService.Data;
using PromotionService.Entities;
using PromotionService.Services;
using Xunit;

namespace PromotionService.Tests;

public sealed class PromotionLimitTests : IAsyncLifetime
{
    private readonly SqliteConnection _connection = new("Data Source=:memory:");
    private PromotionDbContext _db = null!;
    private PromotionManager _manager = null!;
    private readonly Guid _customer = Guid.NewGuid();

    public async Task InitializeAsync()
    {
        await _connection.OpenAsync();
        _db = new PromotionDbContext(new DbContextOptionsBuilder<PromotionDbContext>().UseSqlite(_connection).Options);
        await _db.Database.EnsureCreatedAsync();
        _manager = new PromotionManager(_db);
    }

    public async Task DisposeAsync()
    {
        await _db.DisposeAsync();
        await _connection.DisposeAsync();
    }

    private async Task<Promotion> AddPromotion(int? perCustomer, int? total)
    {
        var promotion = new Promotion
        {
            Id = Guid.NewGuid(), Code = "TEST" + Guid.NewGuid().ToString("N")[..6].ToUpperInvariant(),
            DiscountType = PromotionDiscountType.FIXED, DiscountValue = 5000, MinOrderValue = 0,
            StartAt = DateTime.UtcNow.AddDays(-1), EndAt = DateTime.UtcNow.AddYears(5),
            UsageLimitPerCustomer = perCustomer, UsageLimitTotal = total
        };
        _db.Promotions.Add(promotion);
        await _db.SaveChangesAsync();
        return promotion;
    }

    private Task<ReservePromotionResponse> Reserve(Promotion promotion, Guid? customer = null, Guid? order = null) =>
        _manager.ReserveAsync(new ReservePromotionRequest
        {
            Code = promotion.Code, CustomerId = customer ?? _customer, OrderId = order ?? Guid.NewGuid(), OrderAmount = 50000
        });

    private async Task Expire(Guid reservationId)
    {
        _db.ChangeTracker.Clear();
        var reservation = await _db.PromotionReservations.SingleAsync(x => x.Id == reservationId);
        reservation.ExpiresAt = DateTime.UtcNow.AddMinutes(-1);
        await _db.SaveChangesAsync();
    }

    [Fact]
    public async Task Pending_hold_blocks_a_second_order_over_the_per_customer_limit()
    {
        var promotion = await AddPromotion(perCustomer: 1, total: null);
        await Reserve(promotion);

        var error = await Assert.ThrowsAsync<ApiException>(() => Reserve(promotion));
        Assert.Equal("PROMOTION_INVALID", error.Code);
        var validation = await _manager.ValidateAsync(new ValidatePromotionRequest { Code = promotion.Code, CustomerId = _customer, OrderAmount = 50000 });
        Assert.False(validation.Valid);
        // Other customers are not affected by this customer's hold.
        await Reserve(promotion, customer: Guid.NewGuid());
    }

    [Fact]
    public async Task Pending_holds_count_towards_the_total_limit()
    {
        var promotion = await AddPromotion(perCustomer: null, total: 2);
        await Reserve(promotion, customer: Guid.NewGuid());
        await Reserve(promotion, customer: Guid.NewGuid());

        var error = await Assert.ThrowsAsync<ApiException>(() => Reserve(promotion, customer: Guid.NewGuid()));
        Assert.Equal("PROMOTION_INVALID", error.Code);
    }

    [Fact]
    public async Task Released_or_expired_holds_free_the_quota()
    {
        var promotion = await AddPromotion(perCustomer: 1, total: null);
        var released = await Reserve(promotion);
        await _manager.ReleaseAsync(released.ReservationId);
        var expired = await Reserve(promotion);
        await Expire(expired.ReservationId);

        await Reserve(promotion);
    }

    [Fact]
    public async Task Committed_use_still_counts_against_the_limit()
    {
        var promotion = await AddPromotion(perCustomer: 1, total: null);
        var hold = await Reserve(promotion);
        await _manager.CommitAsync(hold.ReservationId);

        await Assert.ThrowsAsync<ApiException>(() => Reserve(promotion));
    }

    [Fact]
    public async Task Expired_but_unreleased_hold_can_still_be_committed_when_quota_remains()
    {
        var promotion = await AddPromotion(perCustomer: 1, total: null);
        var hold = await Reserve(promotion);
        await Expire(hold.ReservationId);

        await _manager.CommitAsync(hold.ReservationId);

        _db.ChangeTracker.Clear();
        Assert.Equal(PromotionReservationStatus.COMMITTED, (await _db.PromotionReservations.SingleAsync(x => x.Id == hold.ReservationId)).Status);
        Assert.Equal(1, (await _db.Promotions.SingleAsync(x => x.Id == promotion.Id)).UsageCount);
    }

    [Fact]
    public async Task Expired_hold_is_rejected_at_commit_when_another_order_used_the_quota()
    {
        var promotion = await AddPromotion(perCustomer: 1, total: null);
        var stale = await Reserve(promotion);
        await Expire(stale.ReservationId);
        var fresh = await Reserve(promotion);
        await _manager.CommitAsync(fresh.ReservationId);

        var error = await Assert.ThrowsAsync<ApiException>(() => _manager.CommitAsync(stale.ReservationId));
        Assert.Equal("CUSTOMER_USAGE_LIMIT", error.Code);
    }
}
