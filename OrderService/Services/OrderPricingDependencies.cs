using System.Net.Http.Json;
using System.Text.Json;
using OrderService.Common;
using OrderService.Contracts;

namespace OrderService.Services;

// Map, Promotion và Loyalty đều gọi service thật qua HTTP nội bộ.
public sealed class OrderPricingDependencies(
    IHttpClientFactory httpClientFactory,
    IConfiguration configuration) : IOrderPricingDependencies
{
    private readonly string _internalApiKey = configuration["INTERNAL_API_KEY"]
        ?? throw new InvalidOperationException("Không tìm thấy INTERNAL_API_KEY.");

    public async Task<decimal> GetDistanceKmAsync(GeoPointRequest pickup, GeoPointRequest dropoff, CancellationToken cancellationToken)
    {
        using var request = CreateInternalRequest(HttpMethod.Post, "api/map/route/quote", new
        {
            pickupLatitude = pickup.Latitude,
            pickupLongitude = pickup.Longitude,
            dropoffLatitude = dropoff.Latitude,
            dropoffLongitude = dropoff.Longitude
        });
        using var response = await httpClientFactory.CreateClient("MapService").SendAsync(request, cancellationToken);
        await EnsureDependencySuccessAsync(response, "MAP_SERVICE_ERROR", cancellationToken);
        var quote = await response.Content.ReadFromJsonAsync<MapQuoteResponse>(cancellationToken)
            ?? throw new ApiException(502, "MAP_SERVICE_INVALID_RESPONSE", "MapService không trả dữ liệu khoảng cách.");
        return quote.DistanceKm;
    }

    public async Task<PricingReservation> ReservePromotionAsync(
        Guid orderId, Guid customerId, string? code, long shippingFee, CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(code)) return new PricingReservation(0, null);

        using var request = CreateInternalRequest(HttpMethod.Post, "api/promotion/reserve", new
        {
            code = code.Trim(),
            customerId,
            orderId,
            orderAmount = checked((int)Math.Min(shippingFee, int.MaxValue))
        });
        using var response = await httpClientFactory.CreateClient("PromotionService").SendAsync(request, cancellationToken);
        await EnsureDependencySuccessAsync(response, "PROMOTION_REJECTED", cancellationToken);
        var reservation = await response.Content.ReadFromJsonAsync<PromotionReservationResponse>(cancellationToken)
            ?? throw new ApiException(502, "PROMOTION_INVALID_RESPONSE", "PromotionService không trả dữ liệu giữ mã.");
        return new PricingReservation(reservation.DiscountAmount, reservation.ReservationId);
    }

    public async Task<PricingReservation> ReservePointsAsync(
        Guid orderId, Guid customerId, int points, long remainingFee, CancellationToken cancellationToken)
    {
        if (points <= 0 || remainingFee <= 0) return new PricingReservation(0, null);

        using var request = CreateInternalRequest(HttpMethod.Post, "api/loyalty/reserve", new
        {
            customerId,
            orderId,
            points,
            orderAmount = checked((int)Math.Min(remainingFee, int.MaxValue))
        });
        using var response = await httpClientFactory.CreateClient("LoyaltyService").SendAsync(request, cancellationToken);
        await EnsureDependencySuccessAsync(response, "LOYALTY_REJECTED", cancellationToken);
        var reservation = await response.Content.ReadFromJsonAsync<LoyaltyReservationResponse>(cancellationToken)
            ?? throw new ApiException(502, "LOYALTY_INVALID_RESPONSE", "LoyaltyService không trả dữ liệu giữ điểm.");
        return new PricingReservation(reservation.DiscountAmount, reservation.ReservationId);
    }

    public Task CommitPromotionAsync(Guid reservationId, CancellationToken cancellationToken) =>
        SendPromotionActionAsync(reservationId, "commit", cancellationToken);

    public Task ReleasePromotionAsync(Guid reservationId, CancellationToken cancellationToken) =>
        SendPromotionActionAsync(reservationId, "release", cancellationToken);

    public Task CommitPointsAsync(Guid reservationId, CancellationToken cancellationToken) =>
        SendLoyaltyActionAsync(reservationId, "commit", cancellationToken);

    public Task ReleasePointsAsync(Guid reservationId, CancellationToken cancellationToken) =>
        SendLoyaltyActionAsync(reservationId, "release", cancellationToken);

    public async Task EarnPointsAsync(Guid orderId, Guid customerId, long orderAmount, CancellationToken cancellationToken)
    {
        if (orderAmount <= 0) return;

        using var request = CreateInternalRequest(HttpMethod.Post, "api/loyalty/earn", new
        {
            customerId,
            orderId,
            orderAmount = checked((int)Math.Min(orderAmount, int.MaxValue))
        });
        using var response = await httpClientFactory.CreateClient("LoyaltyService").SendAsync(request, cancellationToken);
        await EnsureDependencySuccessAsync(response, "LOYALTY_EARN_ERROR", cancellationToken);
    }

    private async Task SendPromotionActionAsync(Guid reservationId, string action, CancellationToken cancellationToken)
    {
        using var request = CreateInternalRequest(HttpMethod.Post, $"api/promotion/reservations/{reservationId}/{action}", null);
        using var response = await httpClientFactory.CreateClient("PromotionService").SendAsync(request, cancellationToken);
        await EnsureDependencySuccessAsync(response, "PROMOTION_RESERVATION_ERROR", cancellationToken);
    }

    private async Task SendLoyaltyActionAsync(Guid reservationId, string action, CancellationToken cancellationToken)
    {
        using var request = CreateInternalRequest(HttpMethod.Post, $"api/loyalty/reservations/{reservationId}/{action}", null);
        using var response = await httpClientFactory.CreateClient("LoyaltyService").SendAsync(request, cancellationToken);
        await EnsureDependencySuccessAsync(response, "LOYALTY_RESERVATION_ERROR", cancellationToken);
    }

    private HttpRequestMessage CreateInternalRequest(HttpMethod method, string path, object? body)
    {
        var request = new HttpRequestMessage(method, path);
        request.Headers.Add("X-Internal-Key", _internalApiKey);
        if (body is not null) request.Content = JsonContent.Create(body);
        return request;
    }

    private static async Task EnsureDependencySuccessAsync(
        HttpResponseMessage response, string fallbackCode, CancellationToken cancellationToken)
    {
        if (response.IsSuccessStatusCode) return;

        var raw = await response.Content.ReadAsStringAsync(cancellationToken);
        string? upstreamCode = null;
        string? upstreamMessage = null;
        try
        {
            using var document = JsonDocument.Parse(raw);
            var root = document.RootElement;
            if (root.TryGetProperty("code", out var code)) upstreamCode = code.GetString();
            if (root.TryGetProperty("message", out var message)) upstreamMessage = message.GetString();
        }
        catch (JsonException)
        {
            // Dùng thông báo dự phòng khi upstream không trả JSON chuẩn.
        }

        var statusCode = (int)response.StatusCode >= 500 ? 502 : (int)response.StatusCode;
        throw new ApiException(
            statusCode,
            upstreamCode ?? fallbackCode,
            upstreamMessage ?? $"Service phụ thuộc trả về HTTP {(int)response.StatusCode}.");
    }

    private sealed class MapQuoteResponse
    {
        public decimal DistanceKm { get; set; }
    }

    private sealed class PromotionReservationResponse
    {
        public Guid ReservationId { get; set; }
        public long DiscountAmount { get; set; }
    }

    private sealed class LoyaltyReservationResponse
    {
        public Guid ReservationId { get; set; }
        public long DiscountAmount { get; set; }
    }
}
