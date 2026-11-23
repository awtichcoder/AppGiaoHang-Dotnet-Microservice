using System.Net.Http.Json;

namespace DeliveryService.Clients;

public sealed class LoyaltyClient(HttpClient httpClient, IConfiguration configuration) : ILoyaltyClient
{
    public async Task EarnAsync(
        Guid orderId, Guid customerId, long amount, CancellationToken cancellationToken)
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, "api/loyalty/earn")
        {
            Content = JsonContent.Create(new { orderId, customerId, amount })
        };
        request.Headers.Add("X-Internal-Key", configuration["INTERNAL_API_KEY"]
            ?? throw new InvalidOperationException("Không tìm thấy INTERNAL_API_KEY."));
        using var response = await httpClient.SendAsync(request, cancellationToken);
        if (response.IsSuccessStatusCode) return;
        var body = await response.Content.ReadAsStringAsync(cancellationToken);
        throw new HttpRequestException(
            $"LoyaltyService trả về {(int)response.StatusCode}: {body}", null, response.StatusCode);
    }
}
