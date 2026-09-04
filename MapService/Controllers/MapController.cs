using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using MapService.Common;
using MapService.Contracts;
using MapService.Services;

namespace MapService.Controllers;

[ApiController]
[Route("api/map")]
public class MapController : ControllerBase
{
    private readonly IDistanceCalculator _distanceCalculator;

    public MapController(IDistanceCalculator distanceCalculator)
    {
        _distanceCalculator = distanceCalculator;
    }

    // Backend tự tính khoảng cách/thời gian — không nhận distanceKm do client tự gửi.
    [HttpPost("route/quote")]
    [Authorize(Policy = "ServiceOrUser")]
    public IActionResult GetRouteQuote([FromBody] RouteQuoteRequest request)
    {
        ValidateCoordinate(request.PickupLatitude, request.PickupLongitude, "pickup");
        ValidateCoordinate(request.DropoffLatitude, request.DropoffLongitude, "dropoff");

        var (distanceKm, durationMin) = _distanceCalculator.Calculate(
            request.PickupLatitude, request.PickupLongitude,
            request.DropoffLatitude, request.DropoffLongitude);

        return Ok(new RouteQuoteResponse
        {
            DistanceKm = distanceKm,
            DurationMin = durationMin,
            Provider = _distanceCalculator.ProviderName
        });
    }

    private static void ValidateCoordinate(decimal latitude, decimal longitude, string label)
    {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180)
        {
            throw new ApiException(
                StatusCodes.Status400BadRequest,
                "INVALID_COORDINATES",
                $"Toạ độ {label} không hợp lệ",
                new { latitude, longitude });
        }
    }
}
