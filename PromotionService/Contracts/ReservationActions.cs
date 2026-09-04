namespace PromotionService.Contracts;

public sealed class CommitReservationResponse
{
    public Guid ReservationId { get; set; }
    public DateTime CommittedAt { get; set; }
}

public sealed class ReleaseReservationResponse
{
    public Guid ReservationId { get; set; }
    public DateTime ReleasedAt { get; set; }
}
