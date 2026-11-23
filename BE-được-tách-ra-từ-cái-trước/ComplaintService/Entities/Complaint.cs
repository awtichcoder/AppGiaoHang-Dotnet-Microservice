namespace ComplaintService.Entities;

public class Complaint
{
    public Guid Id { get; set; }
    public Guid OrderId { get; set; }
    public ComplaintReporterRole ReporterRole { get; set; }
    public Guid ReporterId { get; set; }
    public ComplaintCategory Category { get; set; }
    public string Description { get; set; } = string.Empty;
    public ComplaintStatus Status { get; set; } = ComplaintStatus.OPEN;
    public string? AdminNote { get; set; }
    public Guid? HandledByAdminId { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }

    // Thời điểm kết thúc xử lý (RESOLVED hoặc REJECTED), dùng để tính hạn dọn ảnh 30 ngày.
    public DateTime? ResolvedAt { get; set; }

    public List<ComplaintAttachment> Attachments { get; set; } = [];
}
