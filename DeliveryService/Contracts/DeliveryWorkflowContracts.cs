using System.ComponentModel.DataAnnotations;

namespace DeliveryService.Contracts;

public sealed class GeoPointRequest
{
    [Range(-90, 90)] public decimal Latitude { get; set; }
    [Range(-180, 180)] public decimal Longitude { get; set; }
}

public sealed class DeliveryAssignRequest
{
    public Guid OrderId { get; set; }
    public Guid CustomerId { get; set; }
    [Required, MaxLength(500)] public string PickupAddress { get; set; } = string.Empty;
    [Required, MaxLength(500)] public string DropoffAddress { get; set; } = string.Empty;
    [Required, MaxLength(20)] public string ReceiverPhone { get; set; } = string.Empty;
    [Required] public GeoPointRequest PickupLocation { get; set; } = new();
    [Required] public GeoPointRequest DropoffLocation { get; set; } = new();
    [Range(1, int.MaxValue)] public int OrderVersion { get; set; }
}

public sealed class CancelDeliveryRequest
{
    [Required, MaxLength(100)] public string ReasonCode { get; set; } = string.Empty;
}

public sealed class DeliveryResponse
{
    public Guid DeliveryId { get; set; }
    public Guid OrderId { get; set; }
    public Guid CustomerId { get; set; }
    public Guid? DriverId { get; set; }
    public string PickupAddress { get; set; } = string.Empty;
    public string DropoffAddress { get; set; } = string.Empty;
    public string? ReceiverPhone { get; set; }
    public string Status { get; set; } = string.Empty;
    public decimal CurrentRadiusKm { get; set; }
    public DateTime SearchStartedAt { get; set; }
    public DateTime? SearchEndedAt { get; set; }
    public int Version { get; set; }
}

public sealed class DeliveryOfferResponse
{
    public Guid OfferId { get; set; }
    public Guid DeliveryId { get; set; }
    public Guid OrderId { get; set; }
    public string PickupAddress { get; set; } = string.Empty;
    public string DropoffAddress { get; set; } = string.Empty;
    public decimal DistanceToPickupKm { get; set; }
    public decimal RadiusKm { get; set; }
    public string Status { get; set; } = string.Empty;
    public DateTime ExpiresAt { get; set; }
}

public sealed class MatchingStatusResponse
{
    public Guid OrderId { get; set; }
    public string Status { get; set; } = string.Empty;
    public decimal CurrentRadiusKm { get; set; }
    public int InvitedDrivers { get; set; }
    public DateTime SearchStartedAt { get; set; }
    public DateTime? SearchEndedAt { get; set; }
}

public sealed class DriverLocationResponse
{
    public Guid DeliveryId { get; set; }
    public Guid DriverId { get; set; }
    public decimal Latitude { get; set; }
    public decimal Longitude { get; set; }
    public DateTime RecordedAt { get; set; }
}
