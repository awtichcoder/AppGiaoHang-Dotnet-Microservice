using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using Microsoft.EntityFrameworkCore;
using OrderService.Clients;
using OrderService.Common;
using OrderService.Contracts;
using OrderService.Data;
using OrderService.Entities;

namespace OrderService.Services;

public sealed class OrderService(
    OrderDbContext db,
    IDeliveryQuoteClient deliveryQuoteClient,
    IOrderPricingDependencies pricingDependencies) : IOrderService
{
    public async Task<OrderResponse> CreateOrderAsync(CreateOrderRequest request, Guid customerId, CancellationToken cancellationToken = default)
    {
        if (request.ClientRequestId == Guid.Empty)
            throw new ApiException(400, "CLIENT_REQUEST_ID_REQUIRED", "clientRequestId không được để trống.");

        var fingerprint = Fingerprint(request);
        var existing = await db.Orders.AsNoTracking().FirstOrDefaultAsync(
            x => x.CustomerId == customerId && x.ClientRequestId == request.ClientRequestId, cancellationToken);
        if (existing is not null)
        {
            if (!string.IsNullOrEmpty(existing.RequestFingerprint)
                && !string.Equals(existing.RequestFingerprint, fingerprint, StringComparison.Ordinal))
                throw new ApiException(409, "IDEMPOTENCY_KEY_REUSED", "clientRequestId đã được dùng với nội dung khác.");
            return ToResponse(existing);
        }

        var orderId = Guid.NewGuid();
        var distanceKm = await pricingDependencies.GetDistanceKmAsync(request.PickupLocation, request.DropoffLocation, cancellationToken);
        var quote = await deliveryQuoteClient.GetQuoteAsync(distanceKm, cancellationToken);
        var promotion = await pricingDependencies.ReservePromotionAsync(orderId, customerId, request.PromotionCode, quote.ShippingFee, cancellationToken);
        var afterPromotion = Math.Max(0, quote.ShippingFee - promotion.Discount);
        var points = await pricingDependencies.ReservePointsAsync(orderId, customerId, request.PointsToUse, afterPromotion, cancellationToken);

        var order = new Order
        {
            OrderId = orderId,
            CustomerId = customerId,
            PickupAddress = request.PickupAddress.Trim(),
            DropoffAddress = request.DropoffAddress.Trim(),
            PickupLatitude = request.PickupLocation.Latitude,
            PickupLongitude = request.PickupLocation.Longitude,
            DropoffLatitude = request.DropoffLocation.Latitude,
            DropoffLongitude = request.DropoffLocation.Longitude,
            ReceiverPhone = request.ReceiverPhone.Trim(),
            PromotionCode = request.PromotionCode?.Trim(),
            PointsToUse = request.PointsToUse,
            DistanceKm = distanceKm,
            BaseFee = quote.BaseFee,
            Surcharge = quote.Surcharge,
            PlatformFee = quote.PlatformFee,
            DiscountPromotion = promotion.Discount,
            DiscountPoints = points.Discount,
            TotalFee = Math.Max(0, quote.ShippingFee - promotion.Discount - points.Discount),
            PromotionReservationId = promotion.ReservationId,
            LoyaltyReservationId = points.ReservationId,
            ClientRequestId = request.ClientRequestId,
            RequestFingerprint = fingerprint,
            Status = OrderStatus.PENDING,
            MatchingStatus = "NOT_STARTED"
        };

        db.Orders.Add(order);
        db.OutboxMessages.Add(new OutboxMessage
        {
            Type = "DeliveryAssign",
            Payload = JsonSerializer.Serialize(new DeliveryAssignRequest
            {
                OrderId = order.OrderId,
                CustomerId = customerId,
                PickupAddress = order.PickupAddress,
                DropoffAddress = order.DropoffAddress,
                ReceiverPhone = order.ReceiverPhone,
                PickupLocation = request.PickupLocation,
                DropoffLocation = request.DropoffLocation,
                OrderVersion = order.Version
            })
        });

        try
        {
            await db.SaveChangesAsync(cancellationToken);
        }
        catch (DbUpdateException)
        {
            db.ChangeTracker.Clear();
            existing = await db.Orders.AsNoTracking().FirstOrDefaultAsync(
                x => x.CustomerId == customerId && x.ClientRequestId == request.ClientRequestId, cancellationToken);
            if (existing is null) throw;
            if (!string.IsNullOrEmpty(existing.RequestFingerprint) && existing.RequestFingerprint != fingerprint)
                throw new ApiException(409, "IDEMPOTENCY_KEY_REUSED", "clientRequestId đã được dùng với nội dung khác.");
            return ToResponse(existing);
        }

        return ToResponse(order);
    }

    public async Task<IReadOnlyList<OrderResponse>> GetOrdersAsync(Guid actorId, string role, int page, int pageSize, CancellationToken cancellationToken = default)
    {
        page = Math.Max(page, 1);
        pageSize = Math.Clamp(pageSize, 1, 100);
        var query = db.Orders.AsNoTracking();
        query = role switch
        {
            "ADMIN" => query,
            "DRIVER" => query.Where(x => x.AssignedDriverId == actorId),
            _ => query.Where(x => x.CustomerId == actorId)
        };
        var orders = await query.OrderByDescending(x => x.CreatedAt).Skip((page - 1) * pageSize).Take(pageSize)
            .ToListAsync(cancellationToken);
        return orders.Select(ToResponse).ToList();
    }

    public async Task<OrderResponse?> GetOrderByIdAsync(Guid orderId, Guid actorId, string role, CancellationToken cancellationToken = default)
    {
        var query = db.Orders.AsNoTracking().Where(x => x.OrderId == orderId);
        query = role switch
        {
            "ADMIN" => query,
            "DRIVER" => query.Where(x => x.AssignedDriverId == actorId),
            _ => query.Where(x => x.CustomerId == actorId)
        };
        var order = await query.FirstOrDefaultAsync(cancellationToken);
        return order is null ? null : ToResponse(order);
    }

    public async Task<OrderResponse> CancelOrderAsync(Guid orderId, Guid customerId, int expectedVersion, string reasonCode, CancellationToken cancellationToken = default)
    {
        var order = await db.Orders.FirstOrDefaultAsync(x => x.OrderId == orderId && x.CustomerId == customerId, cancellationToken)
            ?? throw new ApiException(404, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng.");
        if (order.Status == OrderStatus.CANCELLED) return ToResponse(order);
        if (order.Version != expectedVersion)
            throw new ApiException(409, "ORDER_VERSION_CONFLICT", $"Version hiện tại là {order.Version}.");
        if (!OrderStateMachine.CanCustomerCancel(order.Status))
            throw new ApiException(409, "ORDER_CANNOT_CANCEL", $"Không thể hủy đơn ở trạng thái {order.Status}.");

        order.Status = OrderStatus.CANCELLED;
        order.MatchingStatus = "CANCELLED";
        order.CancelReasonCode = reasonCode;
        order.Version++;
        order.UpdatedAt = DateTime.UtcNow;
        db.OutboxMessages.Add(new OutboxMessage
        {
            Type = "DeliveryCancel",
            Payload = JsonSerializer.Serialize(new OrderOutboxWorker.CancelPayload(orderId, reasonCode))
        });
        EnqueueReservationAction(order, "ReleaseReservations");
        await db.SaveChangesAsync(cancellationToken);
        return ToResponse(order);
    }

    public async Task<OrderResponse> UpdateOrderStatusAsync(Guid orderId, string newStatus, int expectedVersion, Guid? driverId, CancellationToken cancellationToken = default)
    {
        var order = await db.Orders.FirstOrDefaultAsync(x => x.OrderId == orderId, cancellationToken)
            ?? throw new ApiException(404, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng.");
        if (!Enum.TryParse<OrderStatus>(newStatus, true, out var target))
            throw new ApiException(400, "INVALID_ORDER_STATUS", $"Trạng thái '{newStatus}' không hợp lệ.");
        if (order.Status == target) return ToResponse(order);
        if (order.Version != expectedVersion)
            throw new ApiException(409, "ORDER_VERSION_CONFLICT", $"Version hiện tại là {order.Version}.");
        if (!OrderStateMachine.CanTransition(order.Status, target))
            throw new ApiException(409, "INVALID_ORDER_TRANSITION", $"Không thể chuyển từ {order.Status} sang {target}.");

        order.Status = target;
        if (target == OrderStatus.ASSIGNED) order.AssignedDriverId = driverId;
        order.MatchingStatus = target is OrderStatus.NO_DRIVER_FOUND or OrderStatus.CANCELLED ? target.ToString() :
            target == OrderStatus.ASSIGNED ? "MATCHED" : order.MatchingStatus;
        order.Version++;
        order.UpdatedAt = DateTime.UtcNow;
        if (target == OrderStatus.PICKED_UP) EnqueueReservationAction(order, "CommitReservations");
        if (target == OrderStatus.NO_DRIVER_FOUND) EnqueueReservationAction(order, "ReleaseReservations");
        await db.SaveChangesAsync(cancellationToken);
        return ToResponse(order);
    }

    private static string Fingerprint(CreateOrderRequest request)
    {
        var canonical = JsonSerializer.Serialize(new
        {
            pickupAddress = request.PickupAddress.Trim(),
            dropoffAddress = request.DropoffAddress.Trim(),
            pickup = request.PickupLocation,
            dropoff = request.DropoffLocation,
            receiverPhone = request.ReceiverPhone.Trim(),
            promotionCode = request.PromotionCode?.Trim().ToUpperInvariant(),
            request.PointsToUse
        });
        return Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(canonical)));
    }

    private void EnqueueReservationAction(Order order, string type)
    {
        db.OutboxMessages.Add(new OutboxMessage
        {
            Type = type,
            Payload = JsonSerializer.Serialize(new
            {
                order.OrderId,
                order.CustomerId,
                order.PromotionReservationId,
                order.LoyaltyReservationId
            })
        });
    }

    private static OrderResponse ToResponse(Order order) => new()
    {
        OrderId = order.OrderId,
        CustomerId = order.CustomerId,
        PickupAddress = order.PickupAddress,
        DropoffAddress = order.DropoffAddress,
        ReceiverPhone = order.ReceiverPhone,
        DistanceKm = order.DistanceKm,
        BaseFee = order.BaseFee,
        Surcharge = order.Surcharge,
        PlatformFee = order.PlatformFee,
        DiscountPromotion = order.DiscountPromotion,
        DiscountPoints = order.DiscountPoints,
        TotalFee = order.TotalFee,
        Status = order.Status.ToString(),
        MatchingStatus = order.MatchingStatus,
        Version = order.Version,
        CreatedAt = order.CreatedAt,
        UpdatedAt = order.UpdatedAt
    };
}
