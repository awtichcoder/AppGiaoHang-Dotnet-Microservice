using System.ComponentModel.DataAnnotations;
using DriverService.Entities;

namespace DriverService.Contracts;

public sealed class UpdateAvailabilityRequest
{
    public DriverStatus AvailabilityStatus { get; set; }
}

public sealed class UpdateLocationRequest
{
    [Range(-90, 90)] public decimal Latitude { get; set; }
    [Range(-180, 180)] public decimal Longitude { get; set; }
    [Range(0, 10000)] public decimal AccuracyM { get; set; }
    public DateTime RecordedAt { get; set; }
}

public sealed class DriverAssignmentRequest
{
    public Guid DeliveryId { get; set; }
    public Guid OrderId { get; set; }
}
