using System.Net.Http.Json;
using System.Text.Json;
using OrderService.Common;
using OrderService.Contracts;

namespace OrderService.Services;

// Map và Promotion gọi service thật. Loyalty tạm giữ adapter mock cho tới khi nhóm phụ trách cung cấp API.
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

    public Task<PricingReservation> ReservePointsAsync(
        Guid orderId, Guid customerId, int points, long remainingFee, CancellationToken cancellationToken)
    {
        if (!configuration.GetValue("DependencyMocks:LoyaltyEnabled", true))
            throw new ApiException(503, "LOYALTY_NOT_CONFIGURED", "LoyaltyService chưa có adapter tích điểm.");
        if (points <= 0) return Task.FromResult(new PricingReservation(0, null));

        var discount = Math.Min((long)points, remainingFee);
        return Task.FromResult(new PricingReservation(discount, Guid.NewGuid()));
    }

    public Task CommitPromotionAsync(Guid reservationId, CancellationToken cancellationToken) =>
        SendPromotionActionAsync(reservationId, "commit", cancellationToken);

    public Task ReleasePromotionAsync(Guid reservationId, CancellationToken cancellationToken) =>
        SendPromotionActionAsync(reservationId, "release", cancellationToken);

    private async Task SendPromotionActionAsync(Guid reservationId, string action, CancellationToken cancellationToken)
    {
        using var request = CreateInternalRequest(HttpMethod.Post, $"api/promotion/reservations/{reservationId}/{action}", null);
        using var response = await httpClientFactory.CreateClient("PromotionService").SendAsync(request, cancellationToken);
        await EnsureDependencySuccessAsync(response, "PROMOTION_RESERVATION_ERROR", cancellationToken);
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
}
