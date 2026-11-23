using DriverService.Contracts;
using DriverService.Common;
using DriverService.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using System.Security.Claims;

namespace DriverService.Controllers;

[ApiController]
[Route("api/driver")]
public sealed class DriverController(IDriverManager drivers) : ControllerBase
{
    [Authorize(Roles = "DRIVER")]
    [HttpPatch("me/availability")]
    public async Task<ActionResult<DriverProfileResponse>> UpdateAvailability(
        UpdateAvailabilityRequest request, CancellationToken cancellationToken)
        => Ok(await drivers.UpdateAvailabilityAsync(CurrentDriverId(), request, cancellationToken));

    [Authorize(Roles = "DRIVER")]
    [HttpPut("me/location")]
    public async Task<ActionResult<LocationAcceptedResponse>> UpdateLocation(
        UpdateLocationRequest request, CancellationToken cancellationToken)
    {
        var result = await drivers.UpdateLocationAsync(CurrentDriverId(), request, cancellationToken);
        return Ok(new LocationAcceptedResponse { AcceptedAt = result.RecordedAt });
    }

    [Authorize(Policy = "InternalOnly")]
    [HttpGet("nearby")]
    public async Task<ActionResult<IReadOnlyList<NearbyDriverResponse>>> GetNearby(
        [FromQuery] decimal latitude, [FromQuery] decimal longitude, [FromQuery] decimal radiusKm,
        [FromQuery] int? limit,
        CancellationToken cancellationToken)
        => Ok(await drivers.GetNearbyAsync(latitude, longitude, radiusKm, limit, cancellationToken));

    [Authorize(Policy = "InternalOnly")]
    [HttpGet("{driverId:guid}/location")]
    public async Task<ActionResult<DriverLocationResponse>> GetLocation(Guid driverId, CancellationToken cancellationToken)
        => Ok(await drivers.GetLocationAsync(driverId, cancellationToken));

    [Authorize(Policy = "InternalOnly")]
    [HttpPost("{driverId:guid}/busy")]
    public async Task<ActionResult<DriverProfileResponse>> MarkBusy(
        Guid driverId, DriverAssignmentRequest request, CancellationToken cancellationToken)
        => Ok(await drivers.MarkBusyAsync(driverId, request, cancellationToken));

    [Authorize(Policy = "InternalOnly")]
    [HttpPost("{driverId:guid}/available")]
    public async Task<ActionResult<DriverProfileResponse>> MarkAvailable(
        Guid driverId, DriverAssignmentRequest request, CancellationToken cancellationToken)
        => Ok(await drivers.MarkAvailableAsync(driverId, request, cancellationToken));

    private Guid CurrentDriverId()
    {
        var value = User.FindFirstValue("sub");
        return Guid.TryParse(value, out var driverId)
            ? driverId
            : throw new ApiException(401, "INVALID_TOKEN_SUBJECT", "JWT không chứa mã tài xế hợp lệ.");
    }
}
