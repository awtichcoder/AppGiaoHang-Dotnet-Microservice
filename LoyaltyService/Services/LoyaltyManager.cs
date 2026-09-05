using System.Data;
using LoyaltyService.Common;
using LoyaltyService.Contracts;
using LoyaltyService.Data;
using LoyaltyService.Entities;
using Microsoft.EntityFrameworkCore;

namespace LoyaltyService.Services;

public sealed class LoyaltyManager(LoyaltyDbContext db) : ILoyaltyManager
{
    private static readonly TimeSpan ReservationTtl = TimeSpan.FromSeconds(120);

    public async Task<LoyaltyAccountResponse> GetAccountAsync(Guid customerId, CancellationToken cancellationToken)
    {
        var account = await db.Accounts.AsNoTracking().FirstOrDefaultAsync(x => x.CustomerId == customerId, cancellationToken)
            ?? new LoyaltyAccount { CustomerId = customerId };
        var history = await db.Transactions.AsNoTracking()
            .Where(x => x.CustomerId == customerId)
            .OrderByDescending(x => x.CreatedAt)
            .Take(20)
            .Select(x => new LoyaltyTransactionResponse
            {
                TransactionId = x.TransactionId,
                OrderId = x.OrderId,
                Type = x.Type,
                PointsDelta = x.PointsDelta,
                BalanceAfter = x.BalanceAfter,
                CreatedAt = x.CreatedAt
            }).ToListAsync(cancellationToken);
        return ToAccountResponse(account, history);
    }

    public async Task<PointsPreviewResponse> PreviewAsync(
        Guid customerId, PreviewPointsRequest request, CancellationToken cancellationToken)
    {
        var available = await GetAvailablePointsAsync(customerId, cancellationToken);
        var usable = LoyaltyRules.CalculateUsablePoints(request.Points, request.OrderAmount, available);
        return new PointsPreviewResponse
        {
            RequestedPoints = request.Points,
            UsablePoints = usable,
            AvailablePoints = available,
            DiscountAmount = Math.Min(request.OrderAmount, checked((long)usable * LoyaltyRules.PointValue))
        };
    }

    public async Task<LoyaltyReservationResponse> ReserveAsync(
        ReservePointsRequest request, CancellationToken cancellationToken)
    {
        if (request.OrderId == Guid.Empty || request.CustomerId == Guid.Empty)
            throw new ApiException(400, "LOYALTY_INVALID_REFERENCE", "orderId và customerId không được để trống.");

        await using var transaction = await db.Database.BeginTransactionAsync(IsolationLevel.Serializable, cancellationToken);
        var existing = await db.Reservations.FirstOrDefaultAsync(x => x.OrderId == request.OrderId, cancellationToken);
        if (existing is not null)
        {
            if (existing.CustomerId != request.CustomerId || existing.Points != request.Points)
                throw new ApiException(409, "LOYALTY_IDEMPOTENCY_CONFLICT", "orderId đã được giữ điểm với nội dung khác.");
            return ToReservationResponse(existing);
        }

        var account = await GetOrCreateAccountAsync(request.CustomerId, cancellationToken);
        var now = DateTime.UtcNow;
        var activeReserved = await db.Reservations
            .Where(x => x.CustomerId == request.CustomerId
                && x.Status == LoyaltyReservationStatus.RESERVED
                && x.ExpiresAt > now)
            .SumAsync(x => (int?)x.Points, cancellationToken) ?? 0;
        var available = Math.Max(0, account.Points - activeReserved);
        var usable = LoyaltyRules.CalculateUsablePoints(request.Points, request.OrderAmount, available);
        if (usable != request.Points)
            throw new ApiException(409, "LOYALTY_POINTS_INSUFFICIENT", $"Chỉ có thể dùng tối đa {usable} điểm cho đơn này.");

        var reservation = new LoyaltyReservation
        {
            ReservationId = Guid.NewGuid(),
            OrderId = request.OrderId,
            CustomerId = request.CustomerId,
            Points = usable,
            DiscountAmount = Math.Min(request.OrderAmount, checked((long)usable * LoyaltyRules.PointValue)),
            ExpiresAt = now.Add(ReservationTtl)
        };
        db.Reservations.Add(reservation);
        await db.SaveChangesAsync(cancellationToken);
        await transaction.CommitAsync(cancellationToken);
        return ToReservationResponse(reservation);
    }

    public async Task<LoyaltyReservationResponse> CommitAsync(Guid reservationId, CancellationToken cancellationToken)
    {
        await using var transaction = await db.Database.BeginTransactionAsync(IsolationLevel.Serializable, cancellationToken);
        var reservation = await FindReservationAsync(reservationId, cancellationToken);
        if (reservation.Status == LoyaltyReservationStatus.COMMITTED) return ToReservationResponse(reservation);
        if (reservation.Status != LoyaltyReservationStatus.RESERVED)
            throw new ApiException(409, "LOYALTY_RESERVATION_FINALIZED", $"Reservation đã ở trạng thái {reservation.Status}.");
        if (reservation.ExpiresAt <= DateTime.UtcNow)
            throw new ApiException(409, "LOYALTY_RESERVATION_EXPIRED", "Reservation điểm đã hết hạn.");

        var account = await GetOrCreateAccountAsync(reservation.CustomerId, cancellationToken);
        if (account.Points < reservation.Points)
            throw new ApiException(409, "LOYALTY_POINTS_INSUFFICIENT", "Số dư điểm không đủ để commit.");
        account.Points -= reservation.Points;
        account.Tier = LoyaltyRules.CalculateTier(account.Points);
        account.Version++;
        account.UpdatedAt = DateTime.UtcNow;
        reservation.Status = LoyaltyReservationStatus.COMMITTED;
        reservation.FinalizedAt = DateTime.UtcNow;
        db.Transactions.Add(new LoyaltyTransaction
        {
            TransactionId = Guid.NewGuid(),
            CustomerId = account.CustomerId,
            OrderId = reservation.OrderId,
            Type = "REDEEM",
            PointsDelta = -reservation.Points,
            BalanceAfter = account.Points
        });
        await db.SaveChangesAsync(cancellationToken);
        await transaction.CommitAsync(cancellationToken);
        return ToReservationResponse(reservation);
    }

    public async Task<LoyaltyReservationResponse> ReleaseAsync(Guid reservationId, CancellationToken cancellationToken)
    {
        var reservation = await FindReservationAsync(reservationId, cancellationToken);
        if (reservation.Status == LoyaltyReservationStatus.RELEASED) return ToReservationResponse(reservation);
        if (reservation.Status == LoyaltyReservationStatus.COMMITTED)
            throw new ApiException(409, "LOYALTY_RESERVATION_COMMITTED", "Không thể release reservation đã commit.");
        reservation.Status = LoyaltyReservationStatus.RELEASED;
        reservation.FinalizedAt = DateTime.UtcNow;
        await db.SaveChangesAsync(cancellationToken);
        return ToReservationResponse(reservation);
    }

    public async Task<EarnPointsResponse> EarnAsync(EarnPointsRequest request, CancellationToken cancellationToken)
    {
        if (request.OrderId == Guid.Empty || request.CustomerId == Guid.Empty)
            throw new ApiException(400, "LOYALTY_INVALID_REFERENCE", "orderId và customerId không được để trống.");

        await using var transaction = await db.Database.BeginTransactionAsync(IsolationLevel.Serializable, cancellationToken);
        var existing = await db.EarnRecords.AsNoTracking().FirstOrDefaultAsync(x => x.OrderId == request.OrderId, cancellationToken);
        if (existing is not null)
        {
            if (existing.CustomerId != request.CustomerId || existing.Amount != request.Amount)
                throw new ApiException(409, "LOYALTY_EARN_CONFLICT", "orderId đã được cộng điểm với nội dung khác.");
            var current = await GetOrCreateAccountAsync(request.CustomerId, cancellationToken);
            return ToEarnResponse(existing, current);
        }

        var account = await GetOrCreateAccountAsync(request.CustomerId, cancellationToken);
        var earned = checked((int)Math.Min(int.MaxValue, request.Amount / 10_000));
        account.Points = checked(account.Points + earned);
        account.Tier = LoyaltyRules.CalculateTier(account.Points);
        account.Version++;
        account.UpdatedAt = DateTime.UtcNow;
        var record = new LoyaltyEarnRecord
        {
            EarnRecordId = Guid.NewGuid(),
            OrderId = request.OrderId,
            CustomerId = request.CustomerId,
            Amount = request.Amount,
            PointsEarned = earned
        };
        db.EarnRecords.Add(record);
        db.Transactions.Add(new LoyaltyTransaction
        {
            TransactionId = Guid.NewGuid(),
            CustomerId = request.CustomerId,
            OrderId = request.OrderId,
            Type = "EARN",
            PointsDelta = earned,
            BalanceAfter = account.Points
        });
        await db.SaveChangesAsync(cancellationToken);
        await transaction.CommitAsync(cancellationToken);
        return ToEarnResponse(record, account);
    }

    private async Task<int> GetAvailablePointsAsync(Guid customerId, CancellationToken cancellationToken)
    {
        var points = await db.Accounts.AsNoTracking().Where(x => x.CustomerId == customerId)
            .Select(x => (int?)x.Points).FirstOrDefaultAsync(cancellationToken) ?? 0;
        var now = DateTime.UtcNow;
        var reserved = await db.Reservations.AsNoTracking()
            .Where(x => x.CustomerId == customerId && x.Status == LoyaltyReservationStatus.RESERVED && x.ExpiresAt > now)
            .SumAsync(x => (int?)x.Points, cancellationToken) ?? 0;
        return Math.Max(0, points - reserved);
    }

    private async Task<LoyaltyAccount> GetOrCreateAccountAsync(Guid customerId, CancellationToken cancellationToken)
    {
        var account = await db.Accounts.FirstOrDefaultAsync(x => x.CustomerId == customerId, cancellationToken);
        if (account is not null) return account;
        account = new LoyaltyAccount { CustomerId = customerId };
        db.Accounts.Add(account);
        return account;
    }

    private async Task<LoyaltyReservation> FindReservationAsync(Guid reservationId, CancellationToken cancellationToken) =>
        await db.Reservations.FirstOrDefaultAsync(x => x.ReservationId == reservationId, cancellationToken)
        ?? throw new ApiException(404, "LOYALTY_RESERVATION_NOT_FOUND", "Không tìm thấy reservation điểm.");

    private static LoyaltyAccountResponse ToAccountResponse(
        LoyaltyAccount account, IReadOnlyList<LoyaltyTransactionResponse> history) => new()
    {
        CustomerId = account.CustomerId,
        Points = account.Points,
        Tier = account.Tier,
        RecentTransactions = history
    };

    private static LoyaltyReservationResponse ToReservationResponse(LoyaltyReservation reservation) => new()
    {
        ReservationId = reservation.ReservationId,
        OrderId = reservation.OrderId,
        CustomerId = reservation.CustomerId,
        Points = reservation.Points,
        DiscountAmount = reservation.DiscountAmount,
        Status = reservation.Status.ToString(),
        ExpiresAt = reservation.ExpiresAt
    };

    private static EarnPointsResponse ToEarnResponse(LoyaltyEarnRecord record, LoyaltyAccount account) => new()
    {
        OrderId = record.OrderId,
        CustomerId = record.CustomerId,
        PointsEarned = record.PointsEarned,
        Balance = account.Points,
        Tier = account.Tier
    };
}
