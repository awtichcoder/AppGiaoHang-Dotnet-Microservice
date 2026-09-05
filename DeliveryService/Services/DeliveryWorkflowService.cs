using System.Data;
using System.Text.Json;
using DeliveryService.Clients;
using DeliveryService.Common;
using DeliveryService.Contracts;
using DeliveryService.Data;
using DeliveryService.Entities;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

namespace DeliveryService.Services;

public sealed class DeliveryWorkflowService(
    DeliveryDbContext db,
    IDriverDirectoryClient drivers,
    IOptions<MatchingOptions> matchingOptions,
    ILogger<DeliveryWorkflowService> logger) : IDeliveryWorkflowService
{
    private readonly MatchingOptions _matching = matchingOptions.Value;

    public async Task<DeliveryResponse> AssignAsync(DeliveryAssignRequest request, CancellationToken cancellationToken)
    {
        var existing = await db.Deliveries.AsNoTracking().FirstOrDefaultAsync(x => x.OrderId == request.OrderId, cancellationToken);
        if (existing is not null) return ToResponse(existing, includePhone: true);
        var delivery = new Delivery
        {
            DeliveryId = Guid.NewGuid(),
            OrderId = request.OrderId,
            CustomerId = request.CustomerId,
            PickupAddress = request.PickupAddress,
            DropoffAddress = request.DropoffAddress,
            ReceiverPhone = request.ReceiverPhone,
            PickupLatitude = request.PickupLocation.Latitude,
            PickupLongitude = request.PickupLocation.Longitude,
            DropoffLatitude = request.DropoffLocation.Latitude,
            DropoffLongitude = request.DropoffLocation.Longitude,
            OrderVersion = request.OrderVersion,
            TotalFee = request.TotalFee,
            CurrentRadiusKm = _matching.RadiusStepsKm.FirstOrDefault(3),
            SearchStartedAt = DateTime.UtcNow,
            Status = DeliveryStatus.SEARCHING
        };
        db.Deliveries.Add(delivery);
        try { await db.SaveChangesAsync(cancellationToken); }
        catch (DbUpdateException)
        {
            db.ChangeTracker.Clear();
            existing = await db.Deliveries.AsNoTracking().FirstAsync(x => x.OrderId == request.OrderId, cancellationToken);
            return ToResponse(existing, includePhone: true);
        }
        return ToResponse(delivery, includePhone: true);
    }

    public async Task<PagedResponse<DeliveryOfferResponse>> GetMyOffersAsync(
        Guid driverId, int page, int pageSize, CancellationToken cancellationToken)
    {
        if (page < 1 || pageSize is < 1 or > 100)
            throw new ApiException(400, "INVALID_PAGINATION", "page phải >= 1 và pageSize trong khoảng 1..100.");
        var now = DateTime.UtcNow;
        var query = from offer in db.DeliveryOffers.AsNoTracking()
                          join delivery in db.Deliveries.AsNoTracking() on offer.DeliveryId equals delivery.DeliveryId
                          where offer.DriverId == driverId && offer.Status == DeliveryOfferStatus.OFFERED && offer.ExpiresAt > now
                          select new DeliveryOfferResponse
                          {
                              OfferId = offer.OfferId,
                              DeliveryId = delivery.DeliveryId,
                              OrderId = delivery.OrderId,
                              PickupAddress = delivery.PickupAddress,
                              DropoffAddress = delivery.DropoffAddress,
                              DistanceToPickupKm = offer.DistanceKm,
                              RadiusKm = offer.RadiusKm,
                              Status = offer.Status.ToString(),
                              ExpiresAt = offer.ExpiresAt,
                              SentAt = offer.OfferedAt,
                              IncomeEstimate = (long)Math.Round(delivery.TotalFee * 0.8m, MidpointRounding.AwayFromZero)
                          };
        var total = await query.CountAsync(cancellationToken);
        var rows = await query.OrderBy(x => x.ExpiresAt).Skip((page - 1) * pageSize).Take(pageSize)
            .ToListAsync(cancellationToken);
        return new PagedResponse<DeliveryOfferResponse>
        {
            Page = page,
            PageSize = pageSize,
            TotalItems = total,
            Items = rows
        };
    }

    public async Task<AcceptOfferResponse> AcceptOfferAsync(Guid offerId, Guid driverId, CancellationToken cancellationToken)
    {
        await using var transaction = await db.Database.BeginTransactionAsync(IsolationLevel.Serializable, cancellationToken);
        var offer = await db.DeliveryOffers.FirstOrDefaultAsync(x => x.OfferId == offerId && x.DriverId == driverId, cancellationToken)
            ?? throw new ApiException(404, "OFFER_NOT_FOUND", "Không tìm thấy offer.");
        var delivery = await db.Deliveries.FirstAsync(x => x.DeliveryId == offer.DeliveryId, cancellationToken);
        if (offer.Status != DeliveryOfferStatus.OFFERED || offer.ExpiresAt <= DateTime.UtcNow || delivery.Status != DeliveryStatus.SEARCHING)
            throw new ApiException(409, "OFFER_NOT_AVAILABLE", "Offer đã hết hạn hoặc đã có tài xế khác nhận.");

        await drivers.MarkBusyAsync(driverId, delivery.DeliveryId, delivery.OrderId, cancellationToken);
        try
        {
            offer.Status = DeliveryOfferStatus.ACCEPTED;
            offer.RespondedAt = DateTime.UtcNow;
            delivery.DriverId = driverId;
            delivery.Status = DeliveryStatus.ASSIGNED;
            delivery.AssignedAt = DateTime.UtcNow;
            delivery.SearchEndedAt = DateTime.UtcNow;
            delivery.UpdatedAt = DateTime.UtcNow;
            delivery.Version++;
            EnqueueOrderStatus(delivery, DeliveryStatus.ASSIGNED);
            await db.SaveChangesAsync(cancellationToken);
            await transaction.CommitAsync(cancellationToken);
        }
        catch
        {
            try
            {
                await drivers.MarkAvailableAsync(driverId, delivery.DeliveryId, delivery.OrderId, cancellationToken);
            }
            catch (Exception compensationException)
            {
                logger.LogError(compensationException,
                    "Không thể hoàn tác trạng thái BUSY của tài xế {DriverId} cho delivery {DeliveryId}",
                    driverId, delivery.DeliveryId);
            }
            throw;
        }
        return new AcceptOfferResponse
        {
            OfferId = offer.OfferId,
            DeliveryId = delivery.DeliveryId,
            Status = offer.Status.ToString()
        };
    }

    public async Task<RejectOfferResponse> RejectOfferAsync(
        Guid offerId, Guid driverId, string reasonCode, CancellationToken cancellationToken)
    {
        var offer = await db.DeliveryOffers.FirstOrDefaultAsync(x => x.OfferId == offerId && x.DriverId == driverId, cancellationToken)
            ?? throw new ApiException(404, "OFFER_NOT_FOUND", "Không tìm thấy offer.");
        if (offer.Status != DeliveryOfferStatus.OFFERED)
            throw new ApiException(409, "OFFER_NOT_AVAILABLE", "Offer không còn hiệu lực.");
        offer.Status = DeliveryOfferStatus.REJECTED;
        offer.RejectReasonCode = reasonCode;
        offer.RespondedAt = DateTime.UtcNow;
        await db.SaveChangesAsync(cancellationToken);
        return new RejectOfferResponse
        {
            OfferId = offer.OfferId,
            OfferStatus = offer.Status.ToString(),
            ReasonCode = reasonCode
        };
    }

    public async Task<DeliveryResponse> ChangeDriverStageAsync(Guid deliveryId, Guid driverId, string action, CancellationToken cancellationToken)
    {
        var delivery = await db.Deliveries.FirstOrDefaultAsync(x => x.DeliveryId == deliveryId, cancellationToken)
            ?? throw new ApiException(404, "DELIVERY_NOT_FOUND", "Không tìm thấy delivery.");
        if (delivery.DriverId != driverId)
            throw new ApiException(403, "DELIVERY_NOT_ASSIGNED", "Delivery không được giao cho tài xế này.");

        var target = action switch
        {
            "pickup" => DeliveryStatus.PICKED_UP,
            "start" => DeliveryStatus.DELIVERING,
            "complete" => DeliveryStatus.COMPLETED,
            _ => throw new ApiException(400, "INVALID_ACTION", "Thao tác không hợp lệ.")
        };
        if (delivery.Status == target) return ToResponse(delivery, includePhone: true);
        var valid = (delivery.Status, target) switch
        {
            (DeliveryStatus.ASSIGNED, DeliveryStatus.PICKED_UP) => true,
            (DeliveryStatus.PICKED_UP, DeliveryStatus.DELIVERING) => true,
            (DeliveryStatus.DELIVERING, DeliveryStatus.COMPLETED) => true,
            _ => false
        };
        if (!valid) throw new ApiException(409, "INVALID_DELIVERY_TRANSITION", $"Không thể chuyển từ {delivery.Status} sang {target}.");

        delivery.Status = target;
        delivery.UpdatedAt = DateTime.UtcNow;
        delivery.Version++;
        if (target == DeliveryStatus.PICKED_UP) delivery.PickedUpAt = DateTime.UtcNow;
        if (target == DeliveryStatus.DELIVERING) delivery.DeliveringAt = DateTime.UtcNow;
        if (target == DeliveryStatus.COMPLETED) delivery.CompletedAt = DateTime.UtcNow;
        EnqueueOrderStatus(delivery, target);
        if (target == DeliveryStatus.COMPLETED)
        {
            db.OutboxMessages.Add(new OutboxMessage
            {
                Type = "LoyaltyEarn",
                Payload = JsonSerializer.Serialize(new { delivery.OrderId, delivery.CustomerId, Amount = delivery.TotalFee })
            });
            EnqueueDriverStatus(delivery, "DriverAvailable");
        }
        await db.SaveChangesAsync(cancellationToken);
        return ToResponse(delivery, includePhone: true);
    }

    public async Task<DeliveryResponse> CancelByOrderIdAsync(Guid orderId, CancellationToken cancellationToken)
    {
        var delivery = await db.Deliveries.FirstOrDefaultAsync(x => x.OrderId == orderId, cancellationToken)
            ?? throw new ApiException(404, "DELIVERY_NOT_FOUND", "Không tìm thấy delivery của đơn.");
        return await CancelAsync(delivery, cancellationToken);
    }

    public async Task<DeliveryResponse> CancelByDeliveryIdAsync(Guid deliveryId, CancellationToken cancellationToken)
    {
        var delivery = await db.Deliveries.FirstOrDefaultAsync(x => x.DeliveryId == deliveryId, cancellationToken)
            ?? throw new ApiException(404, "DELIVERY_NOT_FOUND", "Không tìm thấy delivery.");
        return await CancelAsync(delivery, cancellationToken);
    }

    private async Task<DeliveryResponse> CancelAsync(Delivery delivery, CancellationToken cancellationToken)
    {
        if (delivery.Status is DeliveryStatus.CANCELLED or DeliveryStatus.COMPLETED or DeliveryStatus.NO_DRIVER_FOUND)
            return ToResponse(delivery, includePhone: true);
        if (delivery.Status is DeliveryStatus.PICKED_UP or DeliveryStatus.DELIVERING)
            throw new ApiException(409, "DELIVERY_CANNOT_CANCEL", "Không thể hủy sau khi đã lấy hàng.");
        delivery.Status = DeliveryStatus.CANCELLED;
        delivery.CancelledAt = DateTime.UtcNow;
        delivery.SearchEndedAt ??= DateTime.UtcNow;
        delivery.UpdatedAt = DateTime.UtcNow;
        delivery.Version++;
        var offers = await db.DeliveryOffers.Where(x => x.DeliveryId == delivery.DeliveryId && x.Status == DeliveryOfferStatus.OFFERED).ToListAsync(cancellationToken);
        foreach (var offer in offers) offer.Status = DeliveryOfferStatus.CANCELLED;
        if (delivery.DriverId is not null) EnqueueDriverStatus(delivery, "DriverAvailable");
        await db.SaveChangesAsync(cancellationToken);
        return ToResponse(delivery, includePhone: true);
    }

    public async Task<DeliveryResponse?> GetByOrderAsync(Guid orderId, Guid actorId, string role, CancellationToken cancellationToken)
    {
        var delivery = await db.Deliveries.AsNoTracking().FirstOrDefaultAsync(x => x.OrderId == orderId, cancellationToken);
        if (delivery is null) return null;
        EnsureCanRead(delivery, actorId, role);
        return ToResponse(delivery, includePhone: delivery.Status != DeliveryStatus.SEARCHING || role == "CUSTOMER" || role == "ADMIN");
    }

    public async Task<MatchingStatusResponse?> GetMatchingStatusAsync(Guid orderId, Guid actorId, string role, CancellationToken cancellationToken)
    {
        var delivery = await db.Deliveries.AsNoTracking().FirstOrDefaultAsync(x => x.OrderId == orderId, cancellationToken);
        if (delivery is null) return null;
        EnsureCanRead(delivery, actorId, role);
        var invited = await db.DeliveryOffers.CountAsync(x => x.DeliveryId == delivery.DeliveryId, cancellationToken);
        var candidates = await db.DeliveryCandidates.CountAsync(x => x.DeliveryId == delivery.DeliveryId, cancellationToken);
        var remaining = await db.DeliveryCandidates.CountAsync(
            x => x.DeliveryId == delivery.DeliveryId && !x.Invited, cancellationToken);
        return new MatchingStatusResponse
        {
            OrderId = orderId,
            Status = delivery.Status.ToString(),
            SearchStatus = delivery.Status.ToString(),
            CurrentRadiusKm = delivery.CurrentRadiusKm,
            InvitedDrivers = invited,
            AttemptCount = invited,
            CandidateCount = candidates,
            RemainingCandidateCount = remaining,
            SearchStartedAt = delivery.SearchStartedAt,
            SearchEndedAt = delivery.SearchEndedAt
        };
    }

    public async Task<DriverLocationResponse> GetLocationAsync(Guid deliveryId, Guid actorId, string role, CancellationToken cancellationToken)
    {
        var delivery = await db.Deliveries.AsNoTracking().FirstOrDefaultAsync(x => x.DeliveryId == deliveryId, cancellationToken)
            ?? throw new ApiException(404, "DELIVERY_NOT_FOUND", "Không tìm thấy delivery.");
        EnsureCanRead(delivery, actorId, role);
        if (delivery.Status is not (DeliveryStatus.ASSIGNED or DeliveryStatus.PICKED_UP or DeliveryStatus.DELIVERING) || delivery.DriverId is null)
            throw new ApiException(409, "LOCATION_NOT_AVAILABLE", "Vị trí chỉ có sau khi tài xế nhận đơn và trước khi kết thúc.");
        var location = await drivers.GetLocationAsync(delivery.DriverId.Value, cancellationToken)
            ?? throw new ApiException(404, "DRIVER_LOCATION_NOT_FOUND", "Chưa có vị trí tài xế.");
        if (DateTime.UtcNow - location.RecordedAt > TimeSpan.FromSeconds(_matching.MaxLocationAgeSeconds))
            throw new ApiException(404, "DRIVER_LOCATION_STALE", "Vị trí tài xế đã quá cũ.");
        return new DriverLocationResponse
        {
            DeliveryId = deliveryId,
            DriverId = delivery.DriverId.Value,
            Latitude = location.Latitude,
            Longitude = location.Longitude,
            RecordedAt = location.RecordedAt,
            AccuracyM = location.AccuracyM
        };
    }

    private void EnqueueOrderStatus(Delivery delivery, DeliveryStatus status)
    {
        db.OutboxMessages.Add(new OutboxMessage
        {
            Type = "OrderStatus",
            Payload = JsonSerializer.Serialize(new OrderStatusPayload(delivery.OrderId, status.ToString(), delivery.OrderVersion, delivery.DriverId))
        });
        delivery.OrderVersion++;
    }

    private void EnqueueDriverStatus(Delivery delivery, string type)
    {
        db.OutboxMessages.Add(new OutboxMessage
        {
            Type = type,
            Payload = JsonSerializer.Serialize(new { delivery.DriverId, delivery.DeliveryId, delivery.OrderId })
        });
    }

    private static void EnsureCanRead(Delivery delivery, Guid actorId, string role)
    {
        if (role == "ADMIN" || delivery.CustomerId == actorId || delivery.DriverId == actorId) return;
        throw new ApiException(403, "DELIVERY_FORBIDDEN", "Bạn không có quyền xem delivery này.");
    }

    private static DeliveryResponse ToResponse(Delivery delivery, bool includePhone) => new()
    {
        DeliveryId = delivery.DeliveryId,
        OrderId = delivery.OrderId,
        CustomerId = delivery.CustomerId,
        DriverId = delivery.DriverId,
        PickupAddress = delivery.PickupAddress,
        DropoffAddress = delivery.DropoffAddress,
        ReceiverPhone = includePhone ? delivery.ReceiverPhone : null,
        Status = delivery.Status.ToString(),
        CurrentRadiusKm = delivery.CurrentRadiusKm,
        SearchStartedAt = delivery.SearchStartedAt,
        SearchEndedAt = delivery.SearchEndedAt,
        Version = delivery.Version,
        TotalFee = delivery.TotalFee,
        AssignedAt = delivery.AssignedAt,
        PickedUpAt = delivery.PickedUpAt,
        DeliveringAt = delivery.DeliveringAt,
        CompletedAt = delivery.CompletedAt,
        CancelledAt = delivery.CancelledAt
    };

    public sealed record OrderStatusPayload(Guid OrderId, string Status, int Version, Guid? DriverId);
}
