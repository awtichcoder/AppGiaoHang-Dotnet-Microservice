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
    [Range(0, long.MaxValue)] public long TotalFee { get; set; }
}

public sealed class CancelDeliveryRequest
{
    [Required, MaxLength(100)] public string ReasonCode { get; set; } = string.Empty;
}

public sealed class RejectOfferRequest
{
    [Required, MaxLength(100)] public string ReasonCode { get; set; } = string.Empty;
}

public sealed class PickupDeliveryRequest
{
    [Range(-90, 90)] public decimal? GpsLatitude { get; set; }
    [Range(-180, 180)] public decimal? GpsLongitude { get; set; }
}

public sealed class PagedResponse<T>
{
    public int Page { get; set; }
    public int PageSize { get; set; }
    public int TotalItems { get; set; }
    public IReadOnlyList<T> Items { get; set; } = [];
}

public sealed class AcceptOfferResponse
{
    public Guid OfferId { get; set; }
    public Guid DeliveryId { get; set; }
    public string Status { get; set; } = string.Empty;
}

public sealed class RejectOfferResponse
{
    public Guid OfferId { get; set; }
    public string OfferStatus { get; set; } = string.Empty;
    public string ReasonCode { get; set; } = string.Empty;
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
    public long TotalFee { get; set; }
    public DateTime? AssignedAt { get; set; }
    public DateTime? PickedUpAt { get; set; }
    public DateTime? DeliveringAt { get; set; }
    public DateTime? CompletedAt { get; set; }
    public DateTime? CancelledAt { get; set; }
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
    public DateTime SentAt { get; set; }
    public long IncomeEstimate { get; set; }
}

public sealed class MatchingStatusResponse
{
    public Guid OrderId { get; set; }
    public string Status { get; set; } = string.Empty;
    public string SearchStatus { get; set; } = string.Empty;
    public decimal CurrentRadiusKm { get; set; }
    public int InvitedDrivers { get; set; }
    public int AttemptCount { get; set; }
    public int CandidateCount { get; set; }
    public int RemainingCandidateCount { get; set; }
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
    public decimal? AccuracyM { get; set; }
}
