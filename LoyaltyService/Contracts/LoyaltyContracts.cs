namespace LoyaltyService.Contracts;

public sealed class LoyaltyAccountResponse
{
    public Guid CustomerId { get; set; }
    public int AvailablePoints { get; set; }
    public int ReservedPoints { get; set; }
    public int LifetimeEarnedPoints { get; set; }
    public string Tier { get; set; } = "BRONZE";
}

public sealed class PreviewPointsRequest
{
    public int Points { get; set; }
}

public sealed class PreviewPointsResponse
{
    public int Points { get; set; }
    public int DiscountAmount { get; set; }
    public int AvailablePoints { get; set; }
}

public sealed class ReservePointsRequest
{
    public Guid CustomerId { get; set; }
    public Guid OrderId { get; set; }
    public int Points { get; set; }
    public int OrderAmount { get; set; }
}

public sealed class ReservePointsResponse
{
    public Guid ReservationId { get; set; }
    public Guid OrderId { get; set; }
    public int Points { get; set; }
    public int DiscountAmount { get; set; }
    public DateTime ExpiresAt { get; set; }
}

public sealed class ReservationActionResponse
{
    public Guid ReservationId { get; set; }
    public DateTime AppliedAt { get; set; }
}

public sealed class EarnPointsRequest
{
    public Guid CustomerId { get; set; }
    public Guid OrderId { get; set; }
    public int OrderAmount { get; set; }
}

public sealed class EarnPointsResponse
{
    public Guid OrderId { get; set; }
    public int EarnedPoints { get; set; }
    public int AvailablePoints { get; set; }
    public string Tier { get; set; } = "BRONZE";
}
