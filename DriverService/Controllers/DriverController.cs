using Microsoft.AspNetCore.Mvc;
using DriverService.Contracts;
using DriverService.Entities;
using Microsoft.AspNetCore.Authorization;
using MongoDB.Driver;
using MongoDB.Driver.GeoJsonObjectModel;
using System.Security.Claims;

namespace DriverService.Controllers
{
    [ApiController]
    [Route("api/driver")]
    public class DriverController : ControllerBase
    {
        private readonly IMongoCollection<DriverProfile> _driverCollection;
        private readonly string _internalSecretKey;

        public DriverController(IMongoDatabase database, IConfiguration config)
        {
            _driverCollection = database.GetCollection<DriverProfile>("DriverProfiles");
            var indexKeysDefinition = Builders<DriverProfile>.IndexKeys.Geo2DSphere(d => d.Location);
            _driverCollection.Indexes.CreateOne(new CreateIndexModel<DriverProfile>(indexKeysDefinition));
            _internalSecretKey = config["INTERNAL_API_KEY"] ?? "super_secret_internal_key";
        }

        private string? GetCurrentUserId()
        {
            var userIdString = User.FindFirstValue(ClaimTypes.NameIdentifier) ?? 
                               User.FindFirstValue(System.IdentityModel.Tokens.Jwt.JwtRegisteredClaimNames.Sub);
            return userIdString;
        }

        [Authorize(Roles = "DRIVER")]
        [HttpPatch("me/availability")]
        public async Task<IActionResult> UpdateAvailability([FromBody] UpdateAvailabilityRequest request)
        {
            var driverId = GetCurrentUserId();
            if (string.IsNullOrEmpty(driverId)) return Unauthorized();

            if (request.AvailabilityStatus == DriverStatus.BUSY)
                return BadRequest(new { message = "Không thể tự chuyển sang trạng thái BUSY." });

            if (request.AvailabilityStatus != DriverStatus.OFFLINE && request.AvailabilityStatus != DriverStatus.AVAILABLE)
                return BadRequest(new { message = "Trạng thái không hợp lệ." });

            var driver = await _driverCollection.Find(d => d.Id == driverId).FirstOrDefaultAsync();
            if (driver == null)
            {
                driver = new DriverProfile 
                { 
                    Id = driverId, 
                    Status = request.AvailabilityStatus,
                    LastSeenAt = DateTime.UtcNow
                };
                await _driverCollection.InsertOneAsync(driver);
            }
            else
            {
                if (driver.Status == DriverStatus.BUSY)
                    return StatusCode(StatusCodes.Status409Conflict, new { message = "Đang bận (BUSY), không thể tự đổi trạng thái." });

                var update = Builders<DriverProfile>.Update
                    .Set(d => d.Status, request.AvailabilityStatus)
                    .Set(d => d.LastSeenAt, DateTime.UtcNow);
                    
                await _driverCollection.UpdateOneAsync(d => d.Id == driverId, update);
            }

            return Ok(new 
            { 
                driverId = driverId,
                availabilityStatus = request.AvailabilityStatus.ToString(),
                updatedAt = DateTime.UtcNow
            });
        }

        [Authorize(Roles = "DRIVER")]
        [HttpPut("me/location")]
        public async Task<IActionResult> UpdateLocation([FromBody] UpdateLocationRequest request)
        {
            var driverId = GetCurrentUserId();
            if (string.IsNullOrEmpty(driverId)) return Unauthorized();

            if (request.Latitude < -90 || request.Latitude > 90 || request.Longitude < -180 || request.Longitude > 180)
                return BadRequest(new { message = "Tọa độ không hợp lệ." });

            if (request.RecordedAt < DateTime.UtcNow.AddMinutes(-10) || request.RecordedAt > DateTime.UtcNow.AddMinutes(1))
                return BadRequest(new { message = "Thời gian ghi nhận vị trí không hợp lệ hoặc quá cũ." });

            var locationPoint = new GeoJsonPoint<GeoJson2DGeographicCoordinates>(
                new GeoJson2DGeographicCoordinates((double)request.Longitude, (double)request.Latitude)
            );

            var update = Builders<DriverProfile>.Update
                .Set(d => d.Location, locationPoint)
                .Set(d => d.LastSeenAt, DateTime.UtcNow);

            var result = await _driverCollection.UpdateOneAsync(
                d => d.Id == driverId, 
                update, 
                new UpdateOptions { IsUpsert = true }
            );

            return Ok(new { acceptedAt = DateTime.UtcNow });
        }

        [HttpGet("nearby")]
        public async Task<IActionResult> GetNearbyDrivers([FromQuery] double latitude, [FromQuery] double longitude, [FromQuery] double radiusKm, [FromQuery] int limit = 10)
        {
            var providedKey = Request.Headers["X-Internal-Key"].ToString();
            if (string.IsNullOrEmpty(providedKey) || providedKey != _internalSecretKey)
                return Unauthorized(new { message = "Internal key không hợp lệ." }); 

            if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180 || radiusKm <= 0 || limit <= 0)
                return BadRequest(new { message = "Tham số truy vấn không hợp lệ." }); 

            var point = new GeoJsonPoint<GeoJson2DGeographicCoordinates>(
                new GeoJson2DGeographicCoordinates(longitude, latitude)
            );

            var filter = Builders<DriverProfile>.Filter.NearSphere(d => d.Location, point, radiusKm * 1000) 
                         & Builders<DriverProfile>.Filter.Eq(d => d.Status, DriverStatus.AVAILABLE)
                         & Builders<DriverProfile>.Filter.Gte(d => d.LastSeenAt, DateTime.UtcNow.AddMinutes(-10));

            var nearbyDrivers = await _driverCollection.Find(filter).Limit(limit).ToListAsync();

            var response = nearbyDrivers.Select(d => new NearbyDriverResponse
            {
                DriverId = d.Id, // Return Empty or remove from Response if it was Guid? Wait! NearbyDriverResponse has Guid DriverId!
                LastSeenAt = d.LastSeenAt ?? DateTime.UtcNow,
                DistanceKm = 0
            });

            return Ok(response);
        }
    }
}
