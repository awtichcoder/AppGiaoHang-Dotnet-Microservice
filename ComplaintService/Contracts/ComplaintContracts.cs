using System.ComponentModel.DataAnnotations;

namespace ComplaintService.Contracts;

public sealed class CreateComplaintForm
{
    public Guid? OrderId { get; set; }
    public string? Category { get; set; }
    public string? Description { get; set; }
    public List<IFormFile>? Files { get; set; }
}

public sealed class UpdateComplaintStatusRequest
{
    [Required] public string Status { get; set; } = string.Empty;
    [MaxLength(1000)] public string? AdminNote { get; set; }
}

public sealed class PagedResponse<T>
{
    public int Page { get; set; }
    public int PageSize { get; set; }
    public int TotalItems { get; set; }
    public IReadOnlyList<T> Items { get; set; } = [];
}

public sealed class ComplaintAttachmentResponse
{
    public Guid Id { get; set; }
    public string FileName { get; set; } = string.Empty;
    public string ContentType { get; set; } = string.Empty;
    public long SizeBytes { get; set; }
    public DateTime CreatedAt { get; set; }
}

public sealed class ComplaintResponse
{
    public Guid Id { get; set; }
    public Guid OrderId { get; set; }
    public string ReporterRole { get; set; } = string.Empty;
    public Guid ReporterId { get; set; }
    public string Category { get; set; } = string.Empty;
    public string Description { get; set; } = string.Empty;
    public string Status { get; set; } = string.Empty;
    public string? AdminNote { get; set; }

    // Chỉ trả cho ADMIN.
    public Guid? HandledByAdminId { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
    public DateTime? ResolvedAt { get; set; }
    public IReadOnlyList<ComplaintAttachmentResponse> Attachments { get; set; } = [];
}

/// <summary>Thông tin đơn lấy từ API nội bộ của DeliveryService.</summary>
public sealed class DeliveryComplaintContext
{
    public Guid OrderId { get; set; }
    public Guid CustomerId { get; set; }
    public Guid? DriverId { get; set; }
    public string Status { get; set; } = string.Empty;
    public DateTime? CompletedAt { get; set; }
}

public sealed record ComplaintUpload(string FileName, string ContentType, long Length, Func<Stream> OpenReadStream);

public sealed record CreateComplaintCommand(
    Guid? OrderId, string? Category, string? Description, IReadOnlyList<ComplaintUpload> Files);

public sealed record ComplaintAttachmentContent(Stream Content, string ContentType, string FileName);
