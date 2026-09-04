using System.Net.Http.Json;
using OrderService.Contracts;

namespace OrderService.Clients;

public class DeliveryQuoteClient : IDeliveryQuoteClient
{
    private readonly HttpClient _httpClient;
    private readonly string _internalApiKey;

    public DeliveryQuoteClient(
        HttpClient httpClient,
        IConfiguration configuration)
    {
        _httpClient = httpClient;

        _internalApiKey =
            configuration["INTERNAL_API_KEY"]
            ?? throw new InvalidOperationException(
                "Không tìm thấy INTERNAL_API_KEY.");
    }

    public async Task<DeliveryQuoteResponse> GetQuoteAsync(
        decimal distanceKm,
        CancellationToken cancellationToken = default)
    {
        using var message = new HttpRequestMessage(
            HttpMethod.Post,
            "api/delivery/quote");

        message.Headers.Add(
            "X-Internal-Key",
            _internalApiKey);

        message.Content = JsonContent.Create(
            new DeliveryQuoteRequest
            {
                DistanceKm = distanceKm
            });

        using var response = await _httpClient.SendAsync(
            message,
            cancellationToken);

        if (!response.IsSuccessStatusCode)
        {
            var errorContent =
                await response.Content.ReadAsStringAsync(
                    cancellationToken);

            throw new HttpRequestException(
                $"DeliveryService trả về {(int)response.StatusCode}: " +
                errorContent);
        }

        var result =
            await response.Content
                .ReadFromJsonAsync<DeliveryQuoteResponse>(
                    cancellationToken);

        return result
            ?? throw new InvalidOperationException(
                "DeliveryService không trả dữ liệu báo giá.");
    }
}