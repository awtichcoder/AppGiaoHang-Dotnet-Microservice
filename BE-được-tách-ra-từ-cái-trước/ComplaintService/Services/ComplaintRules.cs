using ComplaintService.Entities;

namespace ComplaintService.Services;

public static class ComplaintRules
{
    public const int MaxDescriptionLength = 1000;
    public const int MaxAdminNoteLength = 1000;
    public const int MaxFiles = 3;
    public const long MaxFileSizeBytes = 5 * 1024 * 1024;
    public const int AttachmentRetentionDays = 30;

    // 3 ảnh tối đa cộng phần dư cho các trường text và ranh giới multipart.
    public const long MaxRequestBodyBytes = MaxFiles * MaxFileSizeBytes + 1024 * 1024;

    public static readonly TimeSpan SubmissionWindow = TimeSpan.FromHours(24);

    /// <summary>Đúng mốc 24:00:00 vẫn được gửi; chỉ khi đã quá 24 giờ mới đóng.</summary>
    public static bool IsWithinSubmissionWindow(DateTime completedAtUtc, DateTime nowUtc)
        => nowUtc - completedAtUtc <= SubmissionWindow;

    public static bool CanTransition(ComplaintStatus from, ComplaintStatus to) => (from, to) switch
    {
        (ComplaintStatus.OPEN, ComplaintStatus.IN_REVIEW) => true,
        (ComplaintStatus.IN_REVIEW, ComplaintStatus.RESOLVED) => true,
        (ComplaintStatus.IN_REVIEW, ComplaintStatus.REJECTED) => true,
        _ => false
    };

    public static bool IsFinal(ComplaintStatus status)
        => status is ComplaintStatus.RESOLVED or ComplaintStatus.REJECTED;

    /// <summary>Chỉ nhận đúng tên enum. Enum.TryParse sẽ chấp nhận cả chuỗi số như "1".</summary>
    public static bool TryParseName<TEnum>(string? value, out TEnum result) where TEnum : struct, Enum
    {
        result = default;
        if (value is null || !Enum.GetNames<TEnum>().Contains(value, StringComparer.Ordinal)) return false;
        result = Enum.Parse<TEnum>(value);
        return true;
    }
}
