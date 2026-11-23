using ComplaintService.Clients;
using ComplaintService.Common;
using ComplaintService.Contracts;
using ComplaintService.Data;
using ComplaintService.Entities;
using Microsoft.EntityFrameworkCore;

namespace ComplaintService.Services;

public sealed class ComplaintManager(
    ComplaintDbContext db,
    IDeliveryContextClient deliveries,
    IComplaintFileStore files,
    TimeProvider timeProvider,
    ILogger<ComplaintManager> logger) : IComplaintManager
{
    private const string AdminRole = "ADMIN";

    public async Task<ComplaintResponse> CreateAsync(
        CreateComplaintCommand command, Guid reporterId, string role, CancellationToken cancellationToken)
    {
        if (!ComplaintRules.TryParseName<ComplaintReporterRole>(role, out var reporterRole))
            throw new ApiException(403, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này.");
        if (command.OrderId is not Guid orderId || orderId == Guid.Empty)
            throw new ApiException(400, "VALIDATION_ERROR", "Thiếu mã đơn hàng hợp lệ.");
        if (!ComplaintRules.TryParseName<ComplaintCategory>(command.Category, out var category))
            throw new ApiException(400, "COMPLAINT_INVALID_CATEGORY", "Loại vấn đề không hợp lệ.");
        var description = command.Description?.Trim() ?? string.Empty;
        if (description.Length == 0)
            throw new ApiException(400, "COMPLAINT_INVALID_DESCRIPTION", "Vui lòng nhập mô tả vấn đề.");
        if (description.Length > ComplaintRules.MaxDescriptionLength)
            throw new ApiException(400, "COMPLAINT_INVALID_DESCRIPTION", $"Mô tả tối đa {ComplaintRules.MaxDescriptionLength} ký tự.");
        var uploads = await ComplaintFileValidator.ValidateAsync(command.Files, cancellationToken);

        var delivery = await deliveries.GetAsync(orderId, cancellationToken)
            ?? throw new ApiException(404, "ORDER_NOT_FOUND", "Không tìm thấy đơn hàng.");
        var isOwner = reporterRole == ComplaintReporterRole.CUSTOMER
            ? delivery.CustomerId == reporterId
            : delivery.DriverId == reporterId;
        if (!isOwner)
            throw new ApiException(403, "COMPLAINT_FORBIDDEN", "Bạn không có quyền khiếu nại đơn này.");
        if (delivery.Status != "COMPLETED" || delivery.CompletedAt is not DateTime completedAt)
            throw new ApiException(409, "COMPLAINT_ORDER_NOT_COMPLETED", "Chỉ được khiếu nại đơn đã hoàn thành.");
        if (await db.Complaints.AnyAsync(x => x.OrderId == orderId && x.ReporterRole == reporterRole, cancellationToken))
            throw AlreadyExists();

        var now = UtcNow();
        if (!ComplaintRules.IsWithinSubmissionWindow(AsUtc(completedAt), now))
            throw new ApiException(409, "COMPLAINT_WINDOW_CLOSED", "Đã quá 24 giờ kể từ khi đơn hoàn thành nên không thể gửi khiếu nại.");

        var complaint = new Complaint
        {
            Id = Guid.NewGuid(),
            OrderId = orderId,
            ReporterRole = reporterRole,
            ReporterId = reporterId,
            Category = category,
            Description = description,
            Status = ComplaintStatus.OPEN,
            CreatedAt = now,
            UpdatedAt = now
        };

        var saved = new List<string>(uploads.Count);
        try
        {
            foreach (var upload in uploads)
            {
                await using var stream = upload.Upload.OpenReadStream();
                var storedFileName = await files.SaveAsync(stream, upload.Extension, cancellationToken);
                saved.Add(storedFileName);
                complaint.Attachments.Add(new ComplaintAttachment
                {
                    Id = Guid.NewGuid(),
                    ComplaintId = complaint.Id,
                    StoredFileName = storedFileName,
                    OriginalFileName = upload.DisplayName,
                    ContentType = upload.ContentType,
                    SizeBytes = upload.Upload.Length,
                    CreatedAt = now
                });
            }
            db.Complaints.Add(complaint);
            await db.SaveChangesAsync(cancellationToken);
        }
        catch (DbUpdateException)
        {
            DeleteQuietly(saved);
            db.ChangeTracker.Clear();
            // Hai yêu cầu đồng thời: unique index chặn yêu cầu thứ hai.
            if (await db.Complaints.AnyAsync(x => x.OrderId == orderId && x.ReporterRole == reporterRole, cancellationToken))
                throw AlreadyExists();
            throw;
        }
        catch
        {
            DeleteQuietly(saved);
            throw;
        }
        return ToResponse(complaint, includeAdminFields: false);
    }

    public async Task<PagedResponse<ComplaintResponse>> GetMineAsync(
        Guid reporterId, Guid? orderId, int page, int pageSize, CancellationToken cancellationToken)
    {
        var query = db.Complaints.AsNoTracking().Where(x => x.ReporterId == reporterId);
        if (orderId is Guid value) query = query.Where(x => x.OrderId == value);
        return await PageAsync(query, page, pageSize, includeAdminFields: false, cancellationToken);
    }

    public async Task<ComplaintResponse> GetByIdAsync(Guid id, Guid actorId, string role, CancellationToken cancellationToken)
    {
        var complaint = await db.Complaints.AsNoTracking().Include(x => x.Attachments)
            .AsSplitQuery().FirstOrDefaultAsync(x => x.Id == id, cancellationToken) ?? throw NotFound();
        EnsureCanRead(complaint, actorId, role);
        return ToResponse(complaint, includeAdminFields: role == AdminRole);
    }

    public async Task<ComplaintAttachmentContent> OpenAttachmentAsync(
        Guid id, Guid attachmentId, Guid actorId, string role, CancellationToken cancellationToken)
    {
        var complaint = await db.Complaints.AsNoTracking().FirstOrDefaultAsync(x => x.Id == id, cancellationToken) ?? throw NotFound();
        EnsureCanRead(complaint, actorId, role);
        var attachment = await db.ComplaintAttachments.AsNoTracking()
            .FirstOrDefaultAsync(x => x.Id == attachmentId && x.ComplaintId == id, cancellationToken)
            ?? throw AttachmentNotFound();
        var stream = files.OpenRead(attachment.StoredFileName) ?? throw AttachmentNotFound();
        return new ComplaintAttachmentContent(stream, attachment.ContentType, attachment.OriginalFileName);
    }

    public async Task<PagedResponse<ComplaintResponse>> GetForAdminAsync(
        string? status, int page, int pageSize, CancellationToken cancellationToken)
    {
        var query = db.Complaints.AsNoTracking();
        if (!string.IsNullOrWhiteSpace(status))
        {
            if (!ComplaintRules.TryParseName<ComplaintStatus>(status, out var parsed))
                throw new ApiException(400, "COMPLAINT_INVALID_STATUS", $"Trạng thái '{status}' không hợp lệ.");
            query = query.Where(x => x.Status == parsed);
        }
        return await PageAsync(query, page, pageSize, includeAdminFields: true, cancellationToken);
    }

    public async Task<ComplaintResponse> UpdateStatusAsync(
        Guid id, UpdateComplaintStatusRequest request, Guid adminId, CancellationToken cancellationToken)
    {
        if (!ComplaintRules.TryParseName<ComplaintStatus>(request.Status, out var target))
            throw new ApiException(400, "COMPLAINT_INVALID_STATUS", $"Trạng thái '{request.Status}' không hợp lệ.");
        var note = string.IsNullOrWhiteSpace(request.AdminNote) ? null : request.AdminNote.Trim();
        if (note?.Length > ComplaintRules.MaxAdminNoteLength)
            throw new ApiException(400, "COMPLAINT_INVALID_ADMIN_NOTE", $"Ghi chú tối đa {ComplaintRules.MaxAdminNoteLength} ký tự.");

        var complaint = await db.Complaints.Include(x => x.Attachments)
            .AsSplitQuery().FirstOrDefaultAsync(x => x.Id == id, cancellationToken) ?? throw NotFound();
        if (!ComplaintRules.CanTransition(complaint.Status, target))
            throw new ApiException(409, "COMPLAINT_INVALID_TRANSITION", $"Không thể chuyển từ {complaint.Status} sang {target}.");
        if (target == ComplaintStatus.REJECTED && note is null)
            throw new ApiException(400, "COMPLAINT_ADMIN_NOTE_REQUIRED", "Cần nhập ghi chú khi từ chối khiếu nại.");

        var now = UtcNow();
        complaint.Status = target;
        complaint.AdminNote = note ?? complaint.AdminNote;
        complaint.HandledByAdminId = adminId;
        complaint.UpdatedAt = now;
        if (ComplaintRules.IsFinal(target)) complaint.ResolvedAt = now;
        await db.SaveChangesAsync(cancellationToken);
        return ToResponse(complaint, includeAdminFields: true);
    }

    private static async Task<PagedResponse<ComplaintResponse>> PageAsync(
        IQueryable<Complaint> query, int page, int pageSize, bool includeAdminFields, CancellationToken cancellationToken)
    {
        if (page < 1 || pageSize is < 1 or > 100)
            throw new ApiException(400, "INVALID_PAGINATION", "page phải >= 1 và pageSize trong khoảng 1..100.");
        var total = await query.CountAsync(cancellationToken);
        var items = await query.Include(x => x.Attachments).AsSplitQuery()
            .OrderByDescending(x => x.CreatedAt).ThenBy(x => x.Id)
            .Skip((page - 1) * pageSize).Take(pageSize)
            .ToListAsync(cancellationToken);
        return new PagedResponse<ComplaintResponse>
        {
            Page = page,
            PageSize = pageSize,
            TotalItems = total,
            Items = items.Select(x => ToResponse(x, includeAdminFields)).ToList()
        };
    }

    private static void EnsureCanRead(Complaint complaint, Guid actorId, string role)
    {
        if (role == AdminRole || complaint.ReporterId == actorId) return;
        throw new ApiException(403, "COMPLAINT_FORBIDDEN", "Bạn không có quyền xem khiếu nại này.");
    }

    private void DeleteQuietly(IEnumerable<string> storedFileNames)
    {
        foreach (var name in storedFileNames)
        {
            try { files.Delete(name); }
            catch (Exception exception) when (exception is IOException or UnauthorizedAccessException)
            {
                logger.LogWarning(exception, "Không xóa được file tạm {StoredFileName}", name);
            }
        }
    }

    private DateTime UtcNow() => timeProvider.GetUtcNow().UtcDateTime;

    // SQL Server datetime2 trả về Kind = Unspecified; toàn bộ thời gian trong hệ thống là UTC.
    private static DateTime AsUtc(DateTime value) => value.Kind switch
    {
        DateTimeKind.Utc => value,
        DateTimeKind.Local => value.ToUniversalTime(),
        _ => DateTime.SpecifyKind(value, DateTimeKind.Utc)
    };

    private static ComplaintResponse ToResponse(Complaint complaint, bool includeAdminFields) => new()
    {
        Id = complaint.Id,
        OrderId = complaint.OrderId,
        ReporterRole = complaint.ReporterRole.ToString(),
        ReporterId = complaint.ReporterId,
        Category = complaint.Category.ToString(),
        Description = complaint.Description,
        Status = complaint.Status.ToString(),
        AdminNote = complaint.AdminNote,
        HandledByAdminId = includeAdminFields ? complaint.HandledByAdminId : null,
        CreatedAt = AsUtc(complaint.CreatedAt),
        UpdatedAt = AsUtc(complaint.UpdatedAt),
        ResolvedAt = complaint.ResolvedAt is DateTime resolvedAt ? AsUtc(resolvedAt) : null,
        Attachments = complaint.Attachments.OrderBy(x => x.CreatedAt).ThenBy(x => x.Id).Select(x => new ComplaintAttachmentResponse
        {
            Id = x.Id,
            FileName = x.OriginalFileName,
            ContentType = x.ContentType,
            SizeBytes = x.SizeBytes,
            CreatedAt = AsUtc(x.CreatedAt)
        }).ToList()
    };

    private static ApiException AlreadyExists() =>
        new(409, "COMPLAINT_ALREADY_EXISTS", "Bạn đã gửi khiếu nại cho đơn này.");

    private static ApiException NotFound() =>
        new(404, "COMPLAINT_NOT_FOUND", "Không tìm thấy khiếu nại.");

    private static ApiException AttachmentNotFound() =>
        new(404, "COMPLAINT_ATTACHMENT_NOT_FOUND", "Không tìm thấy ảnh đính kèm.");
}
