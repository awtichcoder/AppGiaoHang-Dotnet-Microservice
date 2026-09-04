using System.Net;
using System.Net.Http.Json;

namespace DeliveryService.Clients;

public sealed class OrderStatusClient(HttpClient httpClient, IConfiguration configuration) : IOrderStatusClient
{
    private readonly string _internalApiKey = configuration["INTERNAL_API_KEY"]
        ?? throw new InvalidOperationException("Không tìm thấy INTERNAL_API_KEY.");

    public async Task UpdateAsync(Guid orderId, string status, int version, Guid? driverId, CancellationToken cancellationToken)
    {
        using var message = new HttpRequestMessage(HttpMethod.Patch, $"api/order/{orderId}/status")
        {
            Content = JsonContent.Create(new { status, version, driverId })
        };
        message.Headers.Add("X-Internal-Key", _internalApiKey);
        using var response = await httpClient.SendAsync(message, cancellationToken);
        if (!response.IsSuccessStatusCode && response.StatusCode != HttpStatusCode.Conflict)
            throw new HttpRequestException($"Order status update thất bại: {(int)response.StatusCode} {await response.Content.ReadAsStringAsync(cancellationToken)}");
    }
}
