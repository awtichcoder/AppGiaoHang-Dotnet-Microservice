namespace ComplaintService.Services;

public sealed class ComplaintOptions
{
    public string UploadDirectory { get; set; } = string.Empty;
    public int AttachmentRetentionDays { get; set; } = ComplaintRules.AttachmentRetentionDays;
    public int CleanupIntervalMinutes { get; set; } = 360;
}
