using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using ComplaintService.Common;
using ComplaintService.Contracts;

namespace ComplaintService.Clients;

public sealed class DeliveryContextClient(
    HttpClient httpClient,
    IConfiguration configuration,
    ILogger<DeliveryContextClient> logger) : IDeliveryContextClient
{
    private readonly string _internalApiKey = configuration["INTERNAL_API_KEY"]
        ?? throw new InvalidOperationException("Không tìm thấy INTERNAL_API_KEY.");

    public async Task<DeliveryComplaintContext?> GetAsync(Guid orderId, CancellationToken cancellationToken)
    {
        using var message = new HttpRequestMessage(HttpMethod.Get, $"api/delivery/order/{orderId}/complaint-context");
        message.Headers.Add("X-Internal-Key", _internalApiKey);
        try
        {
            using var response = await httpClient.SendAsync(message, cancellationToken);
            if (response.StatusCode == HttpStatusCode.NotFound) return null;
            if (!response.IsSuccessStatusCode)
            {
                logger.LogError("Delivery complaint-context trả {StatusCode}", (int)response.StatusCode);
                throw Unavailable();
            }
            return await response.Content.ReadFromJsonAsync<DeliveryComplaintContext>(cancellationToken)
                ?? throw Unavailable();
        }
        catch (Exception exception) when (
            exception is HttpRequestException or JsonException
            || (exception is TaskCanceledException && !cancellationToken.IsCancellationRequested))
        {
            logger.LogError(exception, "Không gọi được DeliveryService cho đơn {OrderId}", orderId);
            throw Unavailable();
        }
    }

    private static ApiException Unavailable() =>
        new(503, "DELIVERY_SERVICE_UNAVAILABLE", "Chưa kiểm tra được thông tin đơn hàng. Vui lòng thử lại sau.");
}
