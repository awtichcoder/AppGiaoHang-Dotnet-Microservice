using System.Globalization;
using System.Net;
using System.Net.Http.Json;

namespace DeliveryService.Clients;

public sealed class HttpDriverDirectoryClient(HttpClient httpClient, IConfiguration configuration)
    : IDriverDirectoryClient
{
    public async Task<IReadOnlyList<DriverCandidate>> GetEligibleDriversAsync(
        decimal latitude, decimal longitude, decimal radiusKm, CancellationToken cancellationToken)
    {
        var url = string.Create(CultureInfo.InvariantCulture,
            $"api/driver/nearby?latitude={latitude}&longitude={longitude}&radiusKm={radiusKm}");
        using var request = CreateRequest(HttpMethod.Get, url);
        using var response = await httpClient.SendAsync(request, cancellationToken);
        await EnsureSuccessAsync(response, cancellationToken);
        return await response.Content.ReadFromJsonAsync<List<DriverCandidate>>(cancellationToken: cancellationToken) ?? [];
    }

    public async Task<DriverLocation?> GetLocationAsync(Guid driverId, CancellationToken cancellationToken)
    {
        using var request = CreateRequest(HttpMethod.Get, $"api/driver/{driverId}/location");
        using var response = await httpClient.SendAsync(request, cancellationToken);
        if (response.StatusCode == HttpStatusCode.NotFound) return null;
        await EnsureSuccessAsync(response, cancellationToken);
        return await response.Content.ReadFromJsonAsync<DriverLocation>(cancellationToken: cancellationToken);
    }

    public Task MarkBusyAsync(Guid driverId, Guid deliveryId, Guid orderId, CancellationToken cancellationToken) =>
        UpdateAssignmentAsync(driverId, "busy", deliveryId, orderId, cancellationToken);

    public Task MarkAvailableAsync(Guid driverId, Guid deliveryId, Guid orderId, CancellationToken cancellationToken) =>
        UpdateAssignmentAsync(driverId, "available", deliveryId, orderId, cancellationToken);

    private async Task UpdateAssignmentAsync(
        Guid driverId, string action, Guid deliveryId, Guid orderId, CancellationToken cancellationToken)
    {
        using var request = CreateRequest(HttpMethod.Post, $"api/driver/{driverId}/{action}");
        request.Content = JsonContent.Create(new { deliveryId, orderId });
        using var response = await httpClient.SendAsync(request, cancellationToken);
        await EnsureSuccessAsync(response, cancellationToken);
    }

    private HttpRequestMessage CreateRequest(HttpMethod method, string url)
    {
        var request = new HttpRequestMessage(method, url);
        request.Headers.Add("X-Internal-Key", configuration["INTERNAL_API_KEY"]
            ?? throw new InvalidOperationException("Không tìm thấy INTERNAL_API_KEY."));
        return request;
    }

    private static async Task EnsureSuccessAsync(HttpResponseMessage response, CancellationToken cancellationToken)
    {
        if (response.IsSuccessStatusCode) return;
        var body = await response.Content.ReadAsStringAsync(cancellationToken);
        throw new HttpRequestException(
            $"DriverService trả về {(int)response.StatusCode}: {body}", null, response.StatusCode);
    }
}
