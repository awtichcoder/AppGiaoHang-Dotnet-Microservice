using Microsoft.EntityFrameworkCore;
using PromotionService.Common;
using PromotionService.Contracts;
using PromotionService.Data;
using PromotionService.Entities;

namespace PromotionService.Services;

// Đặt tên khác PromotionService (tên project/namespace) để tránh trùng danh xưng.
public class PromotionManager : IPromotionService
{
    // TTL reservation = 120 phút theo yêu cầu chung (Word), không theo ví dụ ngắn hơn trong Excel.
    private static readonly TimeSpan ReservationTtl = TimeSpan.FromMinutes(120);

    private readonly PromotionDbContext _db;

    public PromotionManager(PromotionDbContext db)
    {
        _db = db;
    }

    public async Task<ValidatePromotionResponse> ValidateAsync(ValidatePromotionRequest request, CancellationToken ct = default)
    {
        var promotion = await _db.Promotions
            .AsNoTracking()
            .FirstOrDefaultAsync(p => p.Code == request.Code, ct);

        if (promotion is null)
        {
            throw new ApiException(StatusCodes.Status404NotFound, "PROMOTION_NOT_FOUND", "Mã khuyến mại không tồn tại");
        }

        var (isValid, discountAmount, reason) = await EvaluateAsync(promotion, request.CustomerId, request.OrderAmount, ct);

        return new ValidatePromotionResponse
        {
            Valid = isValid,
            DiscountAmount = isValid ? discountAmount : 0,
            Reason = reason
        };
    }

    public async Task<ReservePromotionResponse> ReserveAsync(ReservePromotionRequest request, CancellationToken ct = default)
    {
        // clientRequestId/orderId là khóa chống trùng: gọi lại reserve cùng orderId trả về
        // đúng reservation cũ thay vì tạo bản ghi mới (idempotent).
        var existing = await _db.PromotionReservations
            .FirstOrDefaultAsync(r => r.OrderId == request.OrderId, ct);

        if (existing is not null && existing.Status != PromotionReservationStatus.RELEASED)
        {
            return new ReservePromotionResponse
            {
                ReservationId = existing.Id,
                DiscountAmount = existing.DiscountAmount,
                ExpiresAt = existing.ExpiresAt
            };
        }

        var promotion = await _db.Promotions
            .FirstOrDefaultAsync(p => p.Code == request.Code, ct);

        if (promotion is null)
        {
            throw new ApiException(StatusCodes.Status404NotFound, "PROMOTION_NOT_FOUND", "Mã khuyến mại không tồn tại");
        }

        var (isValid, discountAmount, reason) = await EvaluateAsync(promotion, request.CustomerId, request.OrderAmount, ct);

        if (!isValid)
        {
            throw new ApiException(StatusCodes.Status409Conflict, "PROMOTION_EXHAUSTED", reason ?? "Mã không đủ điều kiện hoặc hết lượt");
        }

        var reservation = new PromotionReservation
        {
            Id = Guid.NewGuid(),
            PromotionId = promotion.Id,
            OrderId = request.OrderId,
            CustomerId = request.CustomerId,
            DiscountAmount = discountAmount,
            Status = PromotionReservationStatus.RESERVED,
            CreatedAt = DateTime.UtcNow,
            ExpiresAt = DateTime.UtcNow.Add(ReservationTtl)
        };

        _db.PromotionReservations.Add(reservation);
        await _db.SaveChangesAsync(ct);

        return new ReservePromotionResponse
        {
            ReservationId = reservation.Id,
            DiscountAmount = reservation.DiscountAmount,
            ExpiresAt = reservation.ExpiresAt
        };
    }

    public async Task<CommitReservationResponse> CommitAsync(Guid reservationId, CancellationToken ct = default)
    {
        var reservation = await _db.PromotionReservations
            .FirstOrDefaultAsync(r => r.Id == reservationId, ct);

        if (reservation is null)
        {
            throw new ApiException(StatusCodes.Status404NotFound, "RESERVATION_NOT_FOUND", "Không tìm thấy reservation");
        }

        // Idempotent: gọi lại commit trên reservation đã COMMITTED không được cộng lượt lần nữa.
        if (reservation.Status == PromotionReservationStatus.COMMITTED)
        {
            return new CommitReservationResponse
            {
                ReservationId = reservation.Id,
                CommittedAt = reservation.CommittedAt ?? DateTime.UtcNow
            };
        }

        if (reservation.Status == PromotionReservationStatus.RELEASED)
        {
            throw new ApiException(StatusCodes.Status409Conflict, "RESERVATION_ALREADY_RELEASED", "Reservation đã được release trước đó");
        }

        if (reservation.ExpiresAt < DateTime.UtcNow)
        {
            throw new ApiException(StatusCodes.Status409Conflict, "RESERVATION_EXPIRED", "Reservation đã hết hạn");
        }

        await using var transaction = await _db.Database.BeginTransactionAsync(ct);

        var promotion = await _db.Promotions.FirstAsync(p => p.Id == reservation.PromotionId, ct);

        // Tăng UsageCount có điều kiện ngay trong 1 câu UPDATE (ExecuteUpdateAsync) để tránh
        // race condition khi nhiều request commit cùng lúc gần chạm giới hạn UsageLimitTotal.
        var updatedRows = await _db.Promotions
            .Where(p => p.Id == promotion.Id &&
                        (promotion.UsageLimitTotal == null || p.UsageCount < promotion.UsageLimitTotal))
            .ExecuteUpdateAsync(setters => setters.SetProperty(p => p.UsageCount, p => p.UsageCount + 1), ct);

        if (updatedRows == 0)
        {
            throw new ApiException(StatusCodes.Status409Conflict, "PROMOTION_EXHAUSTED", "Mã không còn lượt sử dụng");
        }

        var usage = await _db.PromotionCustomerUsages
            .FirstOrDefaultAsync(u => u.PromotionId == promotion.Id && u.CustomerId == reservation.CustomerId, ct);

        if (usage is null)
        {
            usage = new PromotionCustomerUsage
            {
                PromotionId = promotion.Id,
                CustomerId = reservation.CustomerId,
                CommittedCount = 1
            };
            _db.PromotionCustomerUsages.Add(usage);
        }
        else
        {
            usage.CommittedCount += 1;
        }

        reservation.Status = PromotionReservationStatus.COMMITTED;
        reservation.CommittedAt = DateTime.UtcNow;

        await _db.SaveChangesAsync(ct);
        await transaction.CommitAsync(ct);

        return new CommitReservationResponse
        {
            ReservationId = reservation.Id,
            CommittedAt = reservation.CommittedAt.Value
        };
    }

    public async Task<ReleaseReservationResponse> ReleaseAsync(Guid reservationId, CancellationToken ct = default)
    {
        var reservation = await _db.PromotionReservations
            .FirstOrDefaultAsync(r => r.Id == reservationId, ct);

        if (reservation is null)
        {
            throw new ApiException(StatusCodes.Status404NotFound, "RESERVATION_NOT_FOUND", "Không tìm thấy reservation");
        }

        // Idempotent: gọi lại release trên reservation đã RELEASED trả cùng kết quả cũ.
        if (reservation.Status == PromotionReservationStatus.RELEASED)
        {
            return new ReleaseReservationResponse
            {
                ReservationId = reservation.Id,
                ReleasedAt = reservation.ReleasedAt ?? DateTime.UtcNow
            };
        }

        if (reservation.Status == PromotionReservationStatus.COMMITTED)
        {
            throw new ApiException(StatusCodes.Status409Conflict, "RESERVATION_ALREADY_COMMITTED", "Reservation đã commit, không thể release");
        }

        reservation.Status = PromotionReservationStatus.RELEASED;
        reservation.ReleasedAt = DateTime.UtcNow;

        await _db.SaveChangesAsync(ct);

        return new ReleaseReservationResponse
        {
            ReservationId = reservation.Id,
            ReleasedAt = reservation.ReleasedAt.Value
        };
    }

    private async Task<(bool IsValid, int DiscountAmount, string? Reason)> EvaluateAsync(
        Promotion promotion, Guid customerId, int orderAmount, CancellationToken ct)
    {
        var now = DateTime.UtcNow;

        if (!promotion.IsActive)
        {
            return (false, 0, "Mã khuyến mại hiện không hoạt động");
        }

        if (now < promotion.StartAt || now > promotion.EndAt)
        {
            return (false, 0, "Mã khuyến mại đã hết hạn hoặc chưa bắt đầu");
        }

        if (orderAmount < promotion.MinOrderValue)
        {
            return (false, 0, $"Đơn chưa đạt giá trị tối thiểu {promotion.MinOrderValue:N0}đ");
        }

        if (promotion.UsageLimitTotal is not null && promotion.UsageCount >= promotion.UsageLimitTotal)
        {
            return (false, 0, "Mã đã hết lượt sử dụng");
        }

        if (promotion.UsageLimitPerCustomer is not null)
        {
            var usedCount = await _db.PromotionCustomerUsages
                .Where(u => u.PromotionId == promotion.Id && u.CustomerId == customerId)
                .Select(u => u.CommittedCount)
                .FirstOrDefaultAsync(ct);

            if (usedCount >= promotion.UsageLimitPerCustomer)
            {
                return (false, 0, "Bạn đã dùng hết lượt cho mã này");
            }
        }

        var discountAmount = promotion.DiscountType == PromotionDiscountType.FIXED
            ? (int)promotion.DiscountValue
            : (int)Math.Floor(orderAmount * (promotion.DiscountValue / 100m));

        if (promotion.MaxDiscountAmount is not null && discountAmount > promotion.MaxDiscountAmount)
        {
            discountAmount = promotion.MaxDiscountAmount.Value;
        }

        return (true, discountAmount, null);
    }
}
