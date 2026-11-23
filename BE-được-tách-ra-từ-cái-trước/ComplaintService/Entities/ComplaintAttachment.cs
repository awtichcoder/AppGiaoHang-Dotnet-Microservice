namespace ComplaintService.Entities;

public class ComplaintAttachment
{
    public Guid Id { get; set; }
    public Guid ComplaintId { get; set; }

    // Tên file do server sinh (GUID). Không bao giờ trả ra API.
    public string StoredFileName { get; set; } = string.Empty;

    // Chỉ để hiển thị, không dùng để truy cập đĩa.
    public string OriginalFileName { get; set; } = string.Empty;
    public string ContentType { get; set; } = string.Empty;
    public long SizeBytes { get; set; }
    public DateTime CreatedAt { get; set; }

    public Complaint? Complaint { get; set; }
}
