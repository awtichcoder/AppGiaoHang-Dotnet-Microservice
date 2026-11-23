using ComplaintService.Entities;
using ComplaintService.Services;
using Xunit;

namespace ComplaintService.Tests;

public sealed class ComplaintRulesTests
{
    private static readonly DateTime CompletedAt = new(2026, 11, 20, 8, 0, 0, DateTimeKind.Utc);

    [Theory]
    [InlineData(0, true)]
    [InlineData(1, true)]
    [InlineData(86_399_999, true)]
    [InlineData(86_400_000, true)] // đúng mốc 24:00:00
    [InlineData(86_400_001, false)]
    [InlineData(172_800_000, false)]
    public void Window_is_inclusive_at_exactly_24_hours(long elapsedMs, bool expected)
        => Assert.Equal(expected, ComplaintRules.IsWithinSubmissionWindow(CompletedAt, CompletedAt.AddMilliseconds(elapsedMs)));

    [Theory]
    [InlineData(ComplaintStatus.OPEN, ComplaintStatus.IN_REVIEW, true)]
    [InlineData(ComplaintStatus.IN_REVIEW, ComplaintStatus.RESOLVED, true)]
    [InlineData(ComplaintStatus.IN_REVIEW, ComplaintStatus.REJECTED, true)]
    [InlineData(ComplaintStatus.OPEN, ComplaintStatus.RESOLVED, false)]
    [InlineData(ComplaintStatus.OPEN, ComplaintStatus.REJECTED, false)]
    [InlineData(ComplaintStatus.OPEN, ComplaintStatus.OPEN, false)]
    [InlineData(ComplaintStatus.IN_REVIEW, ComplaintStatus.OPEN, false)]
    [InlineData(ComplaintStatus.IN_REVIEW, ComplaintStatus.IN_REVIEW, false)]
    [InlineData(ComplaintStatus.RESOLVED, ComplaintStatus.IN_REVIEW, false)]
    [InlineData(ComplaintStatus.RESOLVED, ComplaintStatus.REJECTED, false)]
    [InlineData(ComplaintStatus.REJECTED, ComplaintStatus.RESOLVED, false)]
    [InlineData(ComplaintStatus.REJECTED, ComplaintStatus.OPEN, false)]
    public void Status_flow_only_moves_forward(ComplaintStatus from, ComplaintStatus to, bool expected)
        => Assert.Equal(expected, ComplaintRules.CanTransition(from, to));

    [Theory]
    [InlineData("LATE_DELIVERY", true)]
    [InlineData("OTHER", true)]
    [InlineData("other", false)]
    [InlineData("1", false)] // Enum.TryParse sẽ nhận chuỗi số
    [InlineData("", false)]
    [InlineData(null, false)]
    public void Category_must_be_an_exact_enum_name(string? value, bool expected)
        => Assert.Equal(expected, ComplaintRules.TryParseName<ComplaintCategory>(value, out _));
}
