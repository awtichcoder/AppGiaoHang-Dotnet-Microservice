using ComplaintService.Data;
using ComplaintService.Entities;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

namespace ComplaintService.Services;

/// <summary>Xóa ảnh của khiếu nại đã RESOLVED/REJECTED quá thời hạn lưu tạm.</summary>
public sealed class ComplaintAttachmentCleaner(
    ComplaintDbContext db,
    IComplaintFileStore files,
    IOptions<ComplaintOptions> options,
    TimeProvider timeProvider,
    ILogger<ComplaintAttachmentCleaner> logger)
{
    private const int BatchSize = 200;

    public async Task<int> CleanAsync(CancellationToken cancellationToken)
    {
        var cutoff = timeProvider.GetUtcNow().UtcDateTime.AddDays(-options.Value.AttachmentRetentionDays);
        var removed = 0;
        var failed = new HashSet<Guid>();
        while (true)
        {
            var batch = await db.ComplaintAttachments
                .Where(x => (x.Complaint!.Status == ComplaintStatus.RESOLVED || x.Complaint.Status == ComplaintStatus.REJECTED)
                    && x.Complaint.ResolvedAt != null && x.Complaint.ResolvedAt < cutoff
                    && !failed.Contains(x.Id))
                .OrderBy(x => x.Id).Take(BatchSize).ToListAsync(cancellationToken);
            if (batch.Count == 0) return removed;

            foreach (var attachment in batch)
            {
                try
                {
                    // Xóa file trước: nếu dừng giữa chừng, lần chạy sau vẫn thấy dòng DB và dọn tiếp.
                    files.Delete(attachment.StoredFileName);
                    db.ComplaintAttachments.Remove(attachment);
                    removed++;
                }
                catch (Exception exception) when (exception is IOException or UnauthorizedAccessException)
                {
                    failed.Add(attachment.Id);
                    logger.LogWarning(exception, "Không xóa được ảnh khiếu nại {AttachmentId}, sẽ thử lại lần sau", attachment.Id);
                }
            }
            await db.SaveChangesAsync(cancellationToken);
            db.ChangeTracker.Clear();
        }
    }
}
