using System.Data;
using Microsoft.EntityFrameworkCore;
using PromotionService.Common;
using PromotionService.Contracts;
using PromotionService.Data;
using PromotionService.Entities;

namespace PromotionService.Services;

public sealed class PromotionManager(PromotionDbContext dbContext) : IPromotionService
{
    private static readonly TimeSpan ReservationLifetime = TimeSpan.FromMinutes(120);

    public async Task<ValidatePromotionResponse> ValidateAsync(ValidatePromotionRequest request, CancellationToken cancellationToken = default)
    {
        var promotion = await FindPromotionAsync(request.Code, cancellationToken);
        var evaluation = await EvaluateAsync(promotion, request.CustomerId, request.OrderAmount, cancellationToken);
        return new ValidatePromotionResponse
        {
            Valid = evaluation.Valid,
            DiscountAmount = evaluation.DiscountAmount,
            Reason = evaluation.Reason
        };
    }

    public async Task<ReservePromotionResponse> ReserveAsync(ReservePromotionRequest request, CancellationToken cancellationToken = default)
    {
        await using var transaction = await dbContext.Database.BeginTransactionAsync(IsolationLevel.Serializable, cancellationToken);
        var existing = await dbContext.PromotionReservations
            .SingleOrDefaultAsync(x => x.OrderId == request.OrderId, cancellationToken);

        if (existing is not null)
        {
            if (existing.Status == PromotionReservationStatus.RELEASED)
                throw new ApiException(409, "RESERVATION_RELEASED", "Khuyến mãi của đơn hàng này đã được hoàn trả.");

            await transaction.CommitAsync(cancellationToken);
            return ToReserveResponse(existing);
        }

        var promotion = await FindPromotionAsync(request.Code, cancellationToken);
        var evaluation = await EvaluateAsync(promotion, request.CustomerId, request.OrderAmount, cancellationToken);
        if (!evaluation.Valid)
            throw new ApiException(409, "PROMOTION_INVALID", evaluation.Reason ?? "Mã khuyến mãi không hợp lệ.");

        var now = DateTime.UtcNow;
        var reservation = new PromotionReservation
        {
            Id = Guid.NewGuid(),
            PromotionId = promotion.Id,
            OrderId = request.OrderId,
            CustomerId = request.CustomerId,
            DiscountAmount = evaluation.DiscountAmount,
            Status = PromotionReservationStatus.RESERVED,
            CreatedAt = now,
            ExpiresAt = now.Add(ReservationLifetime)
        };

        dbContext.PromotionReservations.Add(reservation);
        await dbContext.SaveChangesAsync(cancellationToken);
        await transaction.CommitAsync(cancellationToken);
        return ToReserveResponse(reservation);
    }

    public async Task<CommitReservationResponse> CommitAsync(Guid reservationId, CancellationToken cancellationToken = default)
    {
        await using var transaction = await dbContext.Database.BeginTransactionAsync(IsolationLevel.Serializable, cancellationToken);
        var reservation = await dbContext.PromotionReservations
            .SingleOrDefaultAsync(x => x.Id == reservationId, cancellationToken)
            ?? throw new ApiException(404, "RESERVATION_NOT_FOUND", "Không tìm thấy lượt giữ khuyến mãi.");

        if (reservation.Status == PromotionReservationStatus.COMMITTED)
        {
            await transaction.CommitAsync(cancellationToken);
            return new CommitReservationResponse
            {
                ReservationId = reservation.Id,
                CommittedAt = reservation.CommittedAt ?? reservation.CreatedAt
            };
        }

        if (reservation.Status == PromotionReservationStatus.RELEASED)
            throw new ApiException(409, "RESERVATION_RELEASED", "Lượt giữ khuyến mãi đã được hoàn trả.");
        if (reservation.ExpiresAt <= DateTime.UtcNow)
            throw new ApiException(409, "RESERVATION_EXPIRED", "Lượt giữ khuyến mãi đã hết hạn.");

        var promotion = await dbContext.Promotions.SingleAsync(x => x.Id == reservation.PromotionId, cancellationToken);
        if (promotion.UsageLimitTotal is int totalLimit && promotion.UsageCount >= totalLimit)
            throw new ApiException(409, "PROMOTION_USAGE_LIMIT", "Mã khuyến mãi đã hết lượt sử dụng.");

        var customerUsage = await dbContext.PromotionCustomerUsages.SingleOrDefaultAsync(
            x => x.PromotionId == promotion.Id && x.CustomerId == reservation.CustomerId,
            cancellationToken);
        if (promotion.UsageLimitPerCustomer is int customerLimit && (customerUsage?.CommittedCount ?? 0) >= customerLimit)
            throw new ApiException(409, "CUSTOMER_USAGE_LIMIT", "Bạn đã dùng hết lượt cho mã khuyến mãi này.");

        promotion.UsageCount++;
        if (customerUsage is null)
        {
            customerUsage = new PromotionCustomerUsage
            {
                PromotionId = promotion.Id,
                CustomerId = reservation.CustomerId,
                CommittedCount = 1
            };
            dbContext.PromotionCustomerUsages.Add(customerUsage);
        }
        else
        {
            customerUsage.CommittedCount++;
        }

        reservation.Status = PromotionReservationStatus.COMMITTED;
        reservation.CommittedAt = DateTime.UtcNow;
        await dbContext.SaveChangesAsync(cancellationToken);
        await transaction.CommitAsync(cancellationToken);
        return new CommitReservationResponse
        {
            ReservationId = reservation.Id,
            CommittedAt = reservation.CommittedAt.Value
        };
    }

    public async Task<ReleaseReservationResponse> ReleaseAsync(Guid reservationId, CancellationToken cancellationToken = default)
    {
        await using var transaction = await dbContext.Database.BeginTransactionAsync(IsolationLevel.Serializable, cancellationToken);
        var reservation = await dbContext.PromotionReservations
            .SingleOrDefaultAsync(x => x.Id == reservationId, cancellationToken)
            ?? throw new ApiException(404, "RESERVATION_NOT_FOUND", "Không tìm thấy lượt giữ khuyến mãi.");

        if (reservation.Status == PromotionReservationStatus.COMMITTED)
            throw new ApiException(409, "RESERVATION_COMMITTED", "Khuyến mãi đã được xác nhận nên không thể hoàn trả.");

        if (reservation.Status != PromotionReservationStatus.RELEASED)
        {
            reservation.Status = PromotionReservationStatus.RELEASED;
            reservation.ReleasedAt = DateTime.UtcNow;
            await dbContext.SaveChangesAsync(cancellationToken);
        }

        await transaction.CommitAsync(cancellationToken);
        return new ReleaseReservationResponse
        {
            ReservationId = reservation.Id,
            ReleasedAt = reservation.ReleasedAt ?? DateTime.UtcNow
        };
    }

    private async Task<Promotion> FindPromotionAsync(string code, CancellationToken cancellationToken)
    {
        var normalizedCode = code.Trim().ToUpperInvariant();
        return await dbContext.Promotions.SingleOrDefaultAsync(x => x.Code == normalizedCode, cancellationToken)
            ?? throw new ApiException(404, "PROMOTION_NOT_FOUND", "Không tìm thấy mã khuyến mãi.");
    }

    private async Task<Evaluation> EvaluateAsync(Promotion promotion, Guid customerId, int orderAmount, CancellationToken cancellationToken)
    {
        var now = DateTime.UtcNow;
        if (!promotion.IsActive || now < promotion.StartAt || now > promotion.EndAt)
            return Evaluation.Invalid("Mã khuyến mãi chưa có hiệu lực hoặc đã hết hạn.");
        if (orderAmount < promotion.MinOrderValue)
            return Evaluation.Invalid($"Giá trị đơn tối thiểu là {promotion.MinOrderValue}.");
        if (promotion.UsageLimitTotal is int totalLimit && promotion.UsageCount >= totalLimit)
            return Evaluation.Invalid("Mã khuyến mãi đã hết lượt sử dụng.");

        if (promotion.UsageLimitPerCustomer is int customerLimit)
        {
            var committedCount = await dbContext.PromotionCustomerUsages
                .Where(x => x.PromotionId == promotion.Id && x.CustomerId == customerId)
                .Select(x => x.CommittedCount)
                .SingleOrDefaultAsync(cancellationToken);
            if (committedCount >= customerLimit)
                return Evaluation.Invalid("Bạn đã dùng hết lượt cho mã khuyến mãi này.");
        }

        var discount = promotion.DiscountType switch
        {
            PromotionDiscountType.FIXED => (int)promotion.DiscountValue,
            PromotionDiscountType.PERCENT => (int)Math.Floor(orderAmount * promotion.DiscountValue / 100m),
            _ => 0
        };
        if (promotion.MaxDiscountAmount is int maximum)
            discount = Math.Min(discount, maximum);

        return new Evaluation(true, Math.Clamp(discount, 0, orderAmount), null);
    }

    private static ReservePromotionResponse ToReserveResponse(PromotionReservation reservation) => new()
    {
        ReservationId = reservation.Id,
        DiscountAmount = reservation.DiscountAmount,
        ExpiresAt = reservation.ExpiresAt
    };

    private sealed record Evaluation(bool Valid, int DiscountAmount, string? Reason)
    {
        public static Evaluation Invalid(string reason) => new(false, 0, reason);
    }
}
