using System.Data;
using LoyaltyService.Common;
using LoyaltyService.Contracts;
using LoyaltyService.Data;
using LoyaltyService.Entities;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

namespace LoyaltyService.Services;

public sealed class LoyaltyManager(LoyaltyDbContext dbContext, IOptions<LoyaltyOptions> options) : ILoyaltyService
{
    private readonly LoyaltyOptions _options = options.Value;

    public async Task<LoyaltyAccountResponse> GetMeAsync(Guid customerId, CancellationToken cancellationToken = default)
    {
        await using var transaction = await dbContext.Database.BeginTransactionAsync(IsolationLevel.Serializable, cancellationToken);
        var now = DateTime.UtcNow;
        var account = await GetOrCreateAccountAsync(customerId, now, cancellationToken);
        await ExpireReservationsAsync(account, now, cancellationToken);
        await dbContext.SaveChangesAsync(cancellationToken);
        await transaction.CommitAsync(cancellationToken);
        return ToAccountResponse(account);
    }

    public async Task<PreviewPointsResponse> PreviewAsync(
        Guid customerId,
        PreviewPointsRequest request,
        CancellationToken cancellationToken = default)
    {
        if (request.Points <= 0)
            throw new ApiException(400, "INVALID_POINTS", "S? ?i?m kh?ng h?p l?.");

        await using var transaction = await dbContext.Database.BeginTransactionAsync(IsolationLevel.Serializable, cancellationToken);
        var now = DateTime.UtcNow;
        var account = await GetOrCreateAccountAsync(customerId, now, cancellationToken);
        await ExpireReservationsAsync(account, now, cancellationToken);

        if (account.AvailablePoints < request.Points)
            throw new ApiException(409, "INSUFFICIENT_POINTS", "Kh?ng ?? ?i?m kh? d?ng.");

        await dbContext.SaveChangesAsync(cancellationToken);
        await transaction.CommitAsync(cancellationToken);
        return new PreviewPointsResponse
        {
            Points = request.Points,
            DiscountAmount = checked(request.Points * _options.PointValueVnd),
            AvailablePoints = account.AvailablePoints
        };
    }

    public async Task<ReservePointsResponse> ReserveAsync(
        ReservePointsRequest request,
        CancellationToken cancellationToken = default)
    {
        ValidateReserveRequest(request);

        await using var transaction = await dbContext.Database.BeginTransactionAsync(IsolationLevel.Serializable, cancellationToken);
        var existing = await dbContext.PointReservations
            .SingleOrDefaultAsync(x => x.OrderId == request.OrderId, cancellationToken);

        if (existing is not null)
        {
            if (existing.CustomerId != request.CustomerId)
                throw new ApiException(409, "ORDER_ALREADY_RESERVED", "Order n?y ?? c? reservation ?i?m c?a customer kh?c.");
            if (existing.Status is PointReservationStatus.RELEASED or PointReservationStatus.EXPIRED)
                throw new ApiException(409, "RESERVATION_NOT_ACTIVE", "Reservation ?i?m c?a order n?y kh?ng c?n hi?u l?c.");

            await transaction.CommitAsync(cancellationToken);
            return ToReserveResponse(existing);
        }

        var now = DateTime.UtcNow;
        var account = await GetOrCreateAccountAsync(request.CustomerId, now, cancellationToken);
        await ExpireReservationsAsync(account, now, cancellationToken);

        if (account.AvailablePoints < request.Points)
            throw new ApiException(409, "INSUFFICIENT_POINTS", "Kh?ng ?? ?i?m kh? d?ng.");

        var discountAmount = checked((int)Math.Min((long)request.Points * _options.PointValueVnd, request.OrderAmount));
        account.AvailablePoints -= request.Points;
        account.ReservedPoints += request.Points;
        account.UpdatedAt = now;

        var reservation = new PointReservation
        {
            Id = Guid.NewGuid(),
            CustomerId = request.CustomerId,
            OrderId = request.OrderId,
            Points = request.Points,
            DiscountAmount = discountAmount,
            Status = PointReservationStatus.RESERVED,
            CreatedAt = now,
            ExpiresAt = now.AddMinutes(_options.ReservationTtlMinutes)
        };

        dbContext.PointReservations.Add(reservation);
        AddTransaction(account, PointTransactionType.RESERVE, -request.Points, discountAmount, request.OrderId, reservation.Id, now,
            "Gi? ?i?m khi t?o ??n.");

        await dbContext.SaveChangesAsync(cancellationToken);
        await transaction.CommitAsync(cancellationToken);
        return ToReserveResponse(reservation);
    }

    public async Task<ReservationActionResponse> CommitAsync(Guid reservationId, CancellationToken cancellationToken = default)
    {
        await using var transaction = await dbContext.Database.BeginTransactionAsync(IsolationLevel.Serializable, cancellationToken);
        var reservation = await FindReservationAsync(reservationId, cancellationToken);
        var account = await dbContext.LoyaltyAccounts
            .SingleAsync(x => x.CustomerId == reservation.CustomerId, cancellationToken);

        if (reservation.Status == PointReservationStatus.COMMITTED)
        {
            await transaction.CommitAsync(cancellationToken);
            return new ReservationActionResponse
            {
                ReservationId = reservation.Id,
                AppliedAt = reservation.CommittedAt ?? reservation.CreatedAt
            };
        }

        if (reservation.Status == PointReservationStatus.RELEASED)
            throw new ApiException(409, "RESERVATION_RELEASED", "Reservation ?i?m ?? ???c ho?n tr?.");
        if (reservation.Status == PointReservationStatus.EXPIRED || reservation.ExpiresAt <= DateTime.UtcNow)
        {
            if (reservation.Status == PointReservationStatus.RESERVED)
                ExpireReservation(account, reservation, DateTime.UtcNow);
            await dbContext.SaveChangesAsync(cancellationToken);
            await transaction.CommitAsync(cancellationToken);
            throw new ApiException(409, "RESERVATION_EXPIRED", "Reservation ?i?m ?? h?t h?n.");
        }
        if (account.ReservedPoints < reservation.Points)
            throw new ApiException(409, "POINT_BALANCE_INVALID", "S? ?i?m ?ang gi? kh?ng h?p l?.");

        var now = DateTime.UtcNow;
        account.ReservedPoints -= reservation.Points;
        account.UpdatedAt = now;
        reservation.Status = PointReservationStatus.COMMITTED;
        reservation.CommittedAt = now;
        AddTransaction(account, PointTransactionType.COMMIT, 0, reservation.DiscountAmount, reservation.OrderId, reservation.Id, now,
            "X?c nh?n s? d?ng ?i?m khi t?i x? l?y h?ng.");

        await dbContext.SaveChangesAsync(cancellationToken);
        await transaction.CommitAsync(cancellationToken);
        return new ReservationActionResponse { ReservationId = reservation.Id, AppliedAt = now };
    }

    public async Task<ReservationActionResponse> ReleaseAsync(Guid reservationId, CancellationToken cancellationToken = default)
    {
        await using var transaction = await dbContext.Database.BeginTransactionAsync(IsolationLevel.Serializable, cancellationToken);
        var reservation = await FindReservationAsync(reservationId, cancellationToken);
        var account = await dbContext.LoyaltyAccounts
            .SingleAsync(x => x.CustomerId == reservation.CustomerId, cancellationToken);

        if (reservation.Status == PointReservationStatus.COMMITTED)
            throw new ApiException(409, "RESERVATION_COMMITTED", "Reservation ?i?m ?? commit n?n kh?ng th? ho?n tr?.");
        if (reservation.Status is PointReservationStatus.RELEASED or PointReservationStatus.EXPIRED)
        {
            await transaction.CommitAsync(cancellationToken);
            return new ReservationActionResponse
            {
                ReservationId = reservation.Id,
                AppliedAt = reservation.ReleasedAt ?? reservation.ExpiresAt
            };
        }
        if (account.ReservedPoints < reservation.Points)
            throw new ApiException(409, "POINT_BALANCE_INVALID", "S? ?i?m ?ang gi? kh?ng h?p l?.");

        var now = DateTime.UtcNow;
        account.ReservedPoints -= reservation.Points;
        account.AvailablePoints += reservation.Points;
        account.UpdatedAt = now;
        reservation.Status = PointReservationStatus.RELEASED;
        reservation.ReleasedAt = now;
        AddTransaction(account, PointTransactionType.RELEASE, reservation.Points, reservation.DiscountAmount, reservation.OrderId, reservation.Id, now,
            "Ho?n ?i?m do ??n b? h?y ho?c kh?ng c? t?i x?.");

        await dbContext.SaveChangesAsync(cancellationToken);
        await transaction.CommitAsync(cancellationToken);
        return new ReservationActionResponse { ReservationId = reservation.Id, AppliedAt = now };
    }

    public async Task<EarnPointsResponse> EarnAsync(EarnPointsRequest request, CancellationToken cancellationToken = default)
    {
        if (request.CustomerId == Guid.Empty || request.OrderId == Guid.Empty || request.OrderAmount < 0)
            throw new ApiException(400, "INVALID_REQUEST", "Thi?u customerId, orderId ho?c orderAmount kh?ng h?p l?.");

        await using var transaction = await dbContext.Database.BeginTransactionAsync(IsolationLevel.Serializable, cancellationToken);
        var existingEarn = await dbContext.PointTransactions
            .AsNoTracking()
            .SingleOrDefaultAsync(x => x.Type == PointTransactionType.EARN && x.OrderId == request.OrderId, cancellationToken);

        if (existingEarn is not null)
        {
            var existingAccount = await dbContext.LoyaltyAccounts.AsNoTracking()
                .SingleAsync(x => x.CustomerId == existingEarn.CustomerId, cancellationToken);
            await transaction.CommitAsync(cancellationToken);
            return new EarnPointsResponse
            {
                OrderId = request.OrderId,
                EarnedPoints = existingEarn.Points,
                AvailablePoints = existingAccount.AvailablePoints,
                Tier = existingAccount.Tier.ToString()
            };
        }

        var now = DateTime.UtcNow;
        var account = await GetOrCreateAccountAsync(request.CustomerId, now, cancellationToken);
        await ExpireReservationsAsync(account, now, cancellationToken);
        var earnedPoints = request.OrderAmount / _options.EarnVndPerPoint;

        account.AvailablePoints += earnedPoints;
        account.LifetimeEarnedPoints += earnedPoints;
        account.Tier = CalculateTier(account.LifetimeEarnedPoints);
        account.UpdatedAt = now;
        AddTransaction(account, PointTransactionType.EARN, earnedPoints, 0, request.OrderId, null, now,
            "C?ng ?i?m sau khi ??n ho?n th?nh.");

        await dbContext.SaveChangesAsync(cancellationToken);
        await transaction.CommitAsync(cancellationToken);
        return new EarnPointsResponse
        {
            OrderId = request.OrderId,
            EarnedPoints = earnedPoints,
            AvailablePoints = account.AvailablePoints,
            Tier = account.Tier.ToString()
        };
    }

    private static void ValidateReserveRequest(ReservePointsRequest request)
    {
        if (request.CustomerId == Guid.Empty || request.OrderId == Guid.Empty)
            throw new ApiException(400, "INVALID_REQUEST", "Thi?u customerId ho?c orderId.");
        if (request.Points <= 0)
            throw new ApiException(400, "INVALID_POINTS", "S? ?i?m kh?ng h?p l?.");
        if (request.OrderAmount <= 0)
            throw new ApiException(400, "INVALID_ORDER_AMOUNT", "S? ti?n ??n h?ng kh?ng h?p l?.");
    }

    private async Task<LoyaltyAccount> GetOrCreateAccountAsync(Guid customerId, DateTime now, CancellationToken cancellationToken)
    {
        var account = await dbContext.LoyaltyAccounts
            .SingleOrDefaultAsync(x => x.CustomerId == customerId, cancellationToken);
        if (account is not null) return account;

        account = new LoyaltyAccount
        {
            CustomerId = customerId,
            Tier = MembershipTier.BRONZE,
            CreatedAt = now,
            UpdatedAt = now
        };
        dbContext.LoyaltyAccounts.Add(account);
        return account;
    }

    private async Task<PointReservation> FindReservationAsync(Guid reservationId, CancellationToken cancellationToken)
    {
        return await dbContext.PointReservations.SingleOrDefaultAsync(x => x.Id == reservationId, cancellationToken)
            ?? throw new ApiException(404, "RESERVATION_NOT_FOUND", "Kh?ng t?m th?y reservation ?i?m.");
    }

    private async Task ExpireReservationsAsync(LoyaltyAccount account, DateTime now, CancellationToken cancellationToken)
    {
        var reservations = await dbContext.PointReservations
            .Where(x => x.CustomerId == account.CustomerId
                && x.Status == PointReservationStatus.RESERVED
                && x.ExpiresAt <= now)
            .ToListAsync(cancellationToken);

        foreach (var reservation in reservations)
        {
            ExpireReservation(account, reservation, now);
        }
    }

    private void ExpireReservation(LoyaltyAccount account, PointReservation reservation, DateTime now)
    {
        if (account.ReservedPoints < reservation.Points)
            throw new ApiException(409, "POINT_BALANCE_INVALID", "S? ?i?m ?ang gi? kh?ng h?p l?.");

        account.ReservedPoints -= reservation.Points;
        account.AvailablePoints += reservation.Points;
        account.UpdatedAt = now;
        reservation.Status = PointReservationStatus.EXPIRED;
        reservation.ReleasedAt = now;
        AddTransaction(account, PointTransactionType.EXPIRE, reservation.Points, reservation.DiscountAmount, reservation.OrderId, reservation.Id, now,
            "Ho?n ?i?m do reservation h?t h?n.");
    }

    private void AddTransaction(
        LoyaltyAccount account,
        PointTransactionType type,
        int points,
        int discountAmount,
        Guid? orderId,
        Guid? reservationId,
        DateTime now,
        string description)
    {
        dbContext.PointTransactions.Add(new PointTransaction
        {
            Id = Guid.NewGuid(),
            CustomerId = account.CustomerId,
            OrderId = orderId,
            ReservationId = reservationId,
            Type = type,
            Points = points,
            DiscountAmount = discountAmount,
            AvailablePointsAfter = account.AvailablePoints,
            ReservedPointsAfter = account.ReservedPoints,
            CreatedAt = now,
            Description = description
        });
    }

    private MembershipTier CalculateTier(int lifetimeEarnedPoints)
    {
        if (lifetimeEarnedPoints >= _options.GoldThresholdPoints) return MembershipTier.GOLD;
        if (lifetimeEarnedPoints >= _options.SilverThresholdPoints) return MembershipTier.SILVER;
        return MembershipTier.BRONZE;
    }

    private static LoyaltyAccountResponse ToAccountResponse(LoyaltyAccount account) => new()
    {
        CustomerId = account.CustomerId,
        AvailablePoints = account.AvailablePoints,
        ReservedPoints = account.ReservedPoints,
        LifetimeEarnedPoints = account.LifetimeEarnedPoints,
        Tier = account.Tier.ToString()
    };

    private static ReservePointsResponse ToReserveResponse(PointReservation reservation) => new()
    {
        ReservationId = reservation.Id,
        OrderId = reservation.OrderId,
        Points = reservation.Points,
        DiscountAmount = reservation.DiscountAmount,
        ExpiresAt = reservation.ExpiresAt
    };
}
