namespace PromotionService.Contracts;

public class CommitReservationResponse
{
    public Guid ReservationId { get; set; }
    public DateTime CommittedAt { get; set; }
}

public class ReleaseReservationResponse
{
    public Guid ReservationId { get; set; }
    public DateTime ReleasedAt { get; set; }
}
