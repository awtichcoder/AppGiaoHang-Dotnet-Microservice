using System.Data;
using DriverService.Common;
using DriverService.Contracts;
using DriverService.Data;
using DriverService.Entities;
using Microsoft.EntityFrameworkCore;

namespace DriverService.Services;

public sealed class DriverManager(DriverDbContext db, IConfiguration configuration) : IDriverManager
{
    public async Task<DriverProfileResponse> UpdateAvailabilityAsync(
        Guid driverId, UpdateAvailabilityRequest request, CancellationToken cancellationToken)
    {
        if (request.AvailabilityStatus is not (DriverStatus.OFFLINE or DriverStatus.AVAILABLE))
            throw new ApiException(400, "INVALID_DRIVER_STATUS", "Tài xế chỉ có thể tự chuyển OFFLINE hoặc AVAILABLE.");

        var driver = await GetOrCreateAsync(driverId, cancellationToken);
        if (driver.Status == DriverStatus.BUSY)
            throw new ApiException(409, "DRIVER_IS_BUSY", "Tài xế đang giao đơn và không thể tự đổi trạng thái.");

        driver.Status = request.AvailabilityStatus;
        driver.ActiveDeliveryId = null;
        Touch(driver);
        await db.SaveChangesAsync(cancellationToken);
        return ToProfileResponse(driver);
    }

    public async Task<DriverLocationResponse> UpdateLocationAsync(
        Guid driverId, UpdateLocationRequest request, CancellationToken cancellationToken)
    {
        var recordedAt = request.RecordedAt.Kind == DateTimeKind.Unspecified
            ? DateTime.SpecifyKind(request.RecordedAt, DateTimeKind.Utc)
            : request.RecordedAt.ToUniversalTime();
        var now = DateTime.UtcNow;
        var maxAgeSeconds = Math.Max(1, configuration.GetValue("Driver:MaxLocationAgeSeconds", 60));
        if (recordedAt < now.AddSeconds(-maxAgeSeconds) || recordedAt > now.AddMinutes(1))
            throw new ApiException(400, "INVALID_LOCATION_TIME", $"Vị trí phải được ghi nhận trong vòng {maxAgeSeconds} giây gần nhất.");

        var driver = await GetOrCreateAsync(driverId, cancellationToken);
        driver.Latitude = request.Latitude;
        driver.Longitude = request.Longitude;
        driver.AccuracyM = request.AccuracyM;
        driver.LocationUpdatedAt = recordedAt;
        Touch(driver);
        await db.SaveChangesAsync(cancellationToken);
        return ToLocationResponse(driver);
    }

    public async Task<IReadOnlyList<NearbyDriverResponse>> GetNearbyAsync(
        decimal latitude, decimal longitude, decimal radiusKm, int? limit, CancellationToken cancellationToken)
    {
        if (latitude is < -90 or > 90 || longitude is < -180 or > 180 || radiusKm <= 0)
            throw new ApiException(400, "INVALID_QUERY", "Tọa độ hoặc bán kính không hợp lệ.");

        var freshAfter = DateTime.UtcNow.AddSeconds(-Math.Max(1, configuration.GetValue("Driver:MaxLocationAgeSeconds", 60)));
        var candidates = await db.Drivers.AsNoTracking()
            .Where(x => x.Status == DriverStatus.AVAILABLE
                && x.ActiveDeliveryId == null
                && x.Latitude != null
                && x.Longitude != null
                && x.LocationUpdatedAt >= freshAfter)
            .ToListAsync(cancellationToken);

        var result = candidates
            .Select(x => new NearbyDriverResponse
            {
                DriverId = x.DriverId,
                DistanceKm = HaversineKm(latitude, longitude, x.Latitude!.Value, x.Longitude!.Value),
                LastSeenAt = x.LocationUpdatedAt!.Value
            })
            .Where(x => x.DistanceKm <= radiusKm)
            .OrderBy(x => x.DistanceKm);
        if (limit is not null)
        {
            if (limit is < 1 or > 500)
                throw new ApiException(400, "INVALID_LIMIT", "limit phải trong khoảng 1..500.");
            result = result.Take(limit.Value).OrderBy(x => x.DistanceKm);
        }
        return result.ToList();
    }

    public async Task<DriverLocationResponse> GetLocationAsync(Guid driverId, CancellationToken cancellationToken)
    {
        var driver = await db.Drivers.AsNoTracking().SingleOrDefaultAsync(x => x.DriverId == driverId, cancellationToken)
            ?? throw new ApiException(404, "DRIVER_NOT_FOUND", "Không tìm thấy tài xế.");
        if (driver.Latitude is null || driver.Longitude is null || driver.LocationUpdatedAt is null)
            throw new ApiException(404, "DRIVER_LOCATION_NOT_FOUND", "Tài xế chưa cập nhật vị trí.");
        return ToLocationResponse(driver);
    }

    public async Task<DriverProfileResponse> MarkBusyAsync(
        Guid driverId, DriverAssignmentRequest request, CancellationToken cancellationToken)
    {
        if (request.DeliveryId == Guid.Empty || request.OrderId == Guid.Empty)
            throw new ApiException(400, "INVALID_ASSIGNMENT", "DeliveryId và OrderId là bắt buộc.");

        await using var transaction = await db.Database.BeginTransactionAsync(IsolationLevel.Serializable, cancellationToken);
        var driver = await db.Drivers.SingleOrDefaultAsync(x => x.DriverId == driverId, cancellationToken)
            ?? throw new ApiException(404, "DRIVER_NOT_FOUND", "Không tìm thấy tài xế.");
        if (driver.Status == DriverStatus.BUSY && driver.ActiveDeliveryId == request.DeliveryId)
            return ToProfileResponse(driver);
        if (driver.Status != DriverStatus.AVAILABLE || driver.ActiveDeliveryId is not null)
            throw new ApiException(409, "DRIVER_NOT_AVAILABLE", "Tài xế không còn sẵn sàng nhận đơn.");

        driver.Status = DriverStatus.BUSY;
        driver.ActiveDeliveryId = request.DeliveryId;
        Touch(driver);
        await db.SaveChangesAsync(cancellationToken);
        await transaction.CommitAsync(cancellationToken);
        return ToProfileResponse(driver);
    }

    public async Task<DriverProfileResponse> MarkAvailableAsync(
        Guid driverId, DriverAssignmentRequest request, CancellationToken cancellationToken)
    {
        var driver = await db.Drivers.SingleOrDefaultAsync(x => x.DriverId == driverId, cancellationToken)
            ?? throw new ApiException(404, "DRIVER_NOT_FOUND", "Không tìm thấy tài xế.");
        if (driver.ActiveDeliveryId is not null && driver.ActiveDeliveryId != request.DeliveryId)
            throw new ApiException(409, "DELIVERY_MISMATCH", "Tài xế đang được gán cho chuyến giao khác.");
        if (driver.Status == DriverStatus.AVAILABLE && driver.ActiveDeliveryId is null)
            return ToProfileResponse(driver);

        driver.Status = DriverStatus.AVAILABLE;
        driver.ActiveDeliveryId = null;
        Touch(driver);
        await db.SaveChangesAsync(cancellationToken);
        return ToProfileResponse(driver);
    }

    private async Task<DriverProfile> GetOrCreateAsync(Guid driverId, CancellationToken cancellationToken)
    {
        var driver = await db.Drivers.SingleOrDefaultAsync(x => x.DriverId == driverId, cancellationToken);
        if (driver is not null) return driver;
        driver = new DriverProfile { DriverId = driverId };
        db.Drivers.Add(driver);
        return driver;
    }

    private static void Touch(DriverProfile driver)
    {
        driver.UpdatedAt = DateTime.UtcNow;
        driver.Version++;
    }

    private static decimal HaversineKm(decimal lat1, decimal lon1, decimal lat2, decimal lon2)
    {
        const double earthRadiusKm = 6371.0088;
        static double Radians(decimal degrees) => (double)degrees * Math.PI / 180d;
        var deltaLat = Radians(lat2 - lat1);
        var deltaLon = Radians(lon2 - lon1);
        var a = Math.Pow(Math.Sin(deltaLat / 2), 2)
            + Math.Cos(Radians(lat1)) * Math.Cos(Radians(lat2)) * Math.Pow(Math.Sin(deltaLon / 2), 2);
        return Math.Round((decimal)(earthRadiusKm * 2 * Math.Atan2(Math.Sqrt(a), Math.Sqrt(1 - a))), 3);
    }

    private static DriverProfileResponse ToProfileResponse(DriverProfile driver) => new()
    {
        DriverId = driver.DriverId,
        AvailabilityStatus = driver.Status.ToString(),
        ActiveDeliveryId = driver.ActiveDeliveryId,
        UpdatedAt = driver.UpdatedAt
    };

    private static DriverLocationResponse ToLocationResponse(DriverProfile driver) => new()
    {
        DriverId = driver.DriverId,
        Latitude = driver.Latitude!.Value,
        Longitude = driver.Longitude!.Value,
        AccuracyM = driver.AccuracyM,
        RecordedAt = driver.LocationUpdatedAt!.Value
    };
}
