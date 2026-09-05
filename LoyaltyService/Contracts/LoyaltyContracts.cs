using System.ComponentModel.DataAnnotations;

namespace LoyaltyService.Contracts;

public sealed class LoyaltyAccountResponse
{
    public Guid CustomerId { get; set; }
    public int Points { get; set; }
    public string Tier { get; set; } = string.Empty;
    public IReadOnlyList<LoyaltyTransactionResponse> RecentTransactions { get; set; } = [];
}

public sealed class LoyaltyTransactionResponse
{
    public Guid TransactionId { get; set; }
    public Guid? OrderId { get; set; }
    public string Type { get; set; } = string.Empty;
    public int PointsDelta { get; set; }
    public int BalanceAfter { get; set; }
    public DateTime CreatedAt { get; set; }
}

public sealed class PreviewPointsRequest
{
    [Range(0, int.MaxValue)] public int Points { get; set; }
    [Range(0, long.MaxValue)] public long OrderAmount { get; set; }
}

public sealed class PointsPreviewResponse
{
    public int RequestedPoints { get; set; }
    public int UsablePoints { get; set; }
    public int AvailablePoints { get; set; }
    public long DiscountAmount { get; set; }
}

public sealed class ReservePointsRequest
{
    public Guid OrderId { get; set; }
    public Guid CustomerId { get; set; }
    [Range(1, int.MaxValue)] public int Points { get; set; }
    [Range(1, long.MaxValue)] public long OrderAmount { get; set; }
}

public sealed class LoyaltyReservationResponse
{
    public Guid ReservationId { get; set; }
    public Guid OrderId { get; set; }
    public Guid CustomerId { get; set; }
    public int Points { get; set; }
    public long DiscountAmount { get; set; }
    public string Status { get; set; } = string.Empty;
    public DateTime ExpiresAt { get; set; }
}

public sealed class EarnPointsRequest
{
    public Guid OrderId { get; set; }
    public Guid CustomerId { get; set; }
    [Range(0, long.MaxValue)] public long Amount { get; set; }
}

public sealed class EarnPointsResponse
{
    public Guid OrderId { get; set; }
    public Guid CustomerId { get; set; }
    public int PointsEarned { get; set; }
    public int Balance { get; set; }
    public string Tier { get; set; } = string.Empty;
}
