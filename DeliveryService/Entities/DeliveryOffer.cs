namespace DeliveryService.Entities;

public sealed class DeliveryOffer
{
    public Guid OfferId { get; set; } = Guid.NewGuid();
    public Guid DeliveryId { get; set; }
    public Guid DriverId { get; set; }
    public decimal RadiusKm { get; set; }
    public decimal DistanceKm { get; set; }
    public DeliveryOfferStatus Status { get; set; } = DeliveryOfferStatus.OFFERED;
    public DateTime OfferedAt { get; set; } = DateTime.UtcNow;
    public DateTime ExpiresAt { get; set; }
    public DateTime? RespondedAt { get; set; }
}
