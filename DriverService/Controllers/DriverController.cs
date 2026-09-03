using Microsoft.AspNetCore.Mvc;
using DriverService.Contracts;
using DriverService.Entities;
// using Microsoft.AspNetCore.Authorization;

namespace DriverService.Controllers
{
    [ApiController]
    [Route("api/[controller]")] // Route: /api/driver
    public class DriverController : ControllerBase
    {
        private static readonly string InternalSecretKey = "super_secret_internal_key"; // Tạm fix cứng
        private static readonly Guid MockDriverId = Guid.Parse("33333333-3333-3333-3333-333333333333");

        // PATCH /api/driver/me/availability
        // [Authorize(Roles = "DRIVER")]
        [HttpPatch("me/availability")]
        public IActionResult UpdateAvailability([FromBody] UpdateAvailabilityRequest request)
        {
            var isDriver = true; // Mock role DRIVER
            var currentUserId = MockDriverId; 

            if (!isDriver)
                return Unauthorized(); // 401

            // BUSY chỉ do hệ thống đặt
            if (request.AvailabilityStatus == DriverStatus.BUSY)
                return BadRequest(new { message = "Không thể tự chuyển sang trạng thái BUSY." });

            var mockCurrentStatus = DriverStatus.OFFLINE; // Sửa thành OFFLINE để test ra 200 OK

            // 409: Nếu đang bận không thể chuyển thành OFFLINE / AVAILABLE
            if (mockCurrentStatus == DriverStatus.BUSY)
                return StatusCode(StatusCodes.Status409Conflict, new { message = "Đang bận (BUSY), không thể tự đổi trạng thái." });

            if (request.AvailabilityStatus != DriverStatus.OFFLINE && request.AvailabilityStatus != DriverStatus.AVAILABLE)
                return BadRequest(new { message = "Trạng thái không hợp lệ." });

            // TODO: Update status in DB

            return Ok(new 
            { 
                driverId = currentUserId,
                availabilityStatus = request.AvailabilityStatus.ToString(),
                updatedAt = DateTime.UtcNow
            });
        }

        // PUT /api/driver/me/location
        // [Authorize(Roles = "DRIVER")]
        [HttpPut("me/location")]
        public IActionResult UpdateLocation([FromBody] UpdateLocationRequest request)
        {
            var isDriver = true; // Mock role DRIVER

            if (!isDriver)
                return Unauthorized(); // 401

            // Validate coordinates
            if (request.Latitude < -90 || request.Latitude > 90 || request.Longitude < -180 || request.Longitude > 180)
                return BadRequest(new { message = "Tọa độ không hợp lệ." });

            // Validate time (không quá cũ - ví dụ 10 phút)
            if (request.RecordedAt < DateTime.UtcNow.AddMinutes(-10) || request.RecordedAt > DateTime.UtcNow.AddMinutes(1))
                return BadRequest(new { message = "Thời gian ghi nhận vị trí không hợp lệ hoặc quá cũ." });

            // TODO: Cập nhật vị trí vào DB

            return Ok(new 
            { 
                acceptedAt = DateTime.UtcNow 
            });
        }

        // GET /api/driver/nearby
        // API nội bộ cho DeliveryService
        [HttpGet("nearby")]
        public IActionResult GetNearbyDrivers([FromQuery] decimal latitude, [FromQuery] decimal longitude, [FromQuery] decimal radiusKm, [FromQuery] int limit = 10)
        {
            // Kiểm tra internal key
            var providedKey = Request.Headers["X-Internal-Key"].ToString();
            if (string.IsNullOrEmpty(providedKey) || providedKey != InternalSecretKey)
            {
                return Unauthorized(new { message = "Internal key không hợp lệ." }); // 401
            }

            if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180 || radiusKm <= 0 || limit <= 0)
                return BadRequest(new { message = "Tham số truy vấn không hợp lệ." }); // 400

            // TODO: Truy vấn MongoDB GeoSpatial để tìm tài xế AVAILABLE, online, GPS còn mới
            var mockDrivers = new List<NearbyDriverResponse>
            {
                new NearbyDriverResponse
                {
                    DriverId = Guid.NewGuid(),
                    DistanceKm = 1.2m,
                    LastSeenAt = DateTime.UtcNow.AddMinutes(-1)
                },
                new NearbyDriverResponse
                {
                    DriverId = Guid.NewGuid(),
                    DistanceKm = 2.5m,
                    LastSeenAt = DateTime.UtcNow.AddMinutes(-3)
                }
            };

            return Ok(mockDrivers);
        }
    }
}
