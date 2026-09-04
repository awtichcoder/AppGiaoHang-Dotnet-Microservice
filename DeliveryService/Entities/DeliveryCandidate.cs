namespace DeliveryService.Entities;

public sealed class DeliveryCandidate
{
    public Guid DeliveryCandidateId { get; set; } = Guid.NewGuid();
    public Guid DeliveryId { get; set; }
    public Guid DriverId { get; set; }
    public decimal RadiusKm { get; set; }
    public decimal DistanceKm { get; set; }
    public int Sequence { get; set; }
    public bool Invited { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
