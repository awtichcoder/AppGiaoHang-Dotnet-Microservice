using System.Net;
using System.Net.Http.Json;
using OrderService.Contracts;

namespace OrderService.Clients;

public sealed class DeliveryCommandClient(HttpClient httpClient, IConfiguration configuration)
    : IDeliveryCommandClient
{
    private readonly string _internalApiKey = configuration["INTERNAL_API_KEY"]
        ?? throw new InvalidOperationException("Không tìm thấy INTERNAL_API_KEY.");

    public async Task AssignAsync(DeliveryAssignRequest request, CancellationToken cancellationToken)
    {
        using var message = Create(HttpMethod.Post, "api/delivery/assign", request);
        using var response = await httpClient.SendAsync(message, cancellationToken);
        if (!response.IsSuccessStatusCode && response.StatusCode != HttpStatusCode.Conflict)
        {
            throw new HttpRequestException($"Delivery assign thất bại: {(int)response.StatusCode} {await response.Content.ReadAsStringAsync(cancellationToken)}");
        }
    }

    public async Task CancelAsync(Guid orderId, string reasonCode, CancellationToken cancellationToken)
    {
        using var message = Create(HttpMethod.Post, $"api/delivery/order/{orderId}/cancel", new { reasonCode });
        using var response = await httpClient.SendAsync(message, cancellationToken);
        if (!response.IsSuccessStatusCode && response.StatusCode != HttpStatusCode.NotFound)
        {
            throw new HttpRequestException($"Delivery cancel thất bại: {(int)response.StatusCode} {await response.Content.ReadAsStringAsync(cancellationToken)}");
        }
    }

    private HttpRequestMessage Create(HttpMethod method, string path, object payload)
    {
        var message = new HttpRequestMessage(method, path) { Content = JsonContent.Create(payload) };
        message.Headers.Add("X-Internal-Key", _internalApiKey);
        return message;
    }
}
