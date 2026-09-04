using MapService.Common;
using MapService.Contracts;
using MapService.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace MapService.Controllers;

[ApiController]
[Route("api/map")]
public sealed class MapController(IDistanceCalculator distanceCalculator) : ControllerBase
{
    [HttpPost("route/quote")]
    [Authorize(Policy = "ServiceOrUser")]
    public ActionResult<RouteQuoteResponse> GetRouteQuote(RouteQuoteRequest request)
    {
        ValidateCoordinate(request.PickupLatitude, request.PickupLongitude, "pickup");
        ValidateCoordinate(request.DropoffLatitude, request.DropoffLongitude, "dropoff");
        var (distanceKm, durationMin) = distanceCalculator.Calculate(
            request.PickupLatitude, request.PickupLongitude,
            request.DropoffLatitude, request.DropoffLongitude);
        return Ok(new RouteQuoteResponse
        {
            DistanceKm = distanceKm,
            DurationMin = durationMin,
            Provider = distanceCalculator.ProviderName
        });
    }

    private static void ValidateCoordinate(decimal latitude, decimal longitude, string label)
    {
        if (latitude is < -90 or > 90 || longitude is < -180 or > 180)
            throw new ApiException(400, "INVALID_COORDINATES", $"Tọa độ {label} không hợp lệ.", new { latitude, longitude });
    }
}
