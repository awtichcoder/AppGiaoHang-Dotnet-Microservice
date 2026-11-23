using ComplaintService.Contracts;

namespace ComplaintService.Services;

public interface IComplaintManager
{
    Task<ComplaintResponse> CreateAsync(CreateComplaintCommand command, Guid reporterId, string role, CancellationToken cancellationToken);
    Task<PagedResponse<ComplaintResponse>> GetMineAsync(Guid reporterId, Guid? orderId, int page, int pageSize, CancellationToken cancellationToken);
    Task<ComplaintResponse> GetByIdAsync(Guid id, Guid actorId, string role, CancellationToken cancellationToken);
    Task<ComplaintAttachmentContent> OpenAttachmentAsync(Guid id, Guid attachmentId, Guid actorId, string role, CancellationToken cancellationToken);
    Task<PagedResponse<ComplaintResponse>> GetForAdminAsync(string? status, int page, int pageSize, CancellationToken cancellationToken);
    Task<ComplaintResponse> UpdateStatusAsync(Guid id, UpdateComplaintStatusRequest request, Guid adminId, CancellationToken cancellationToken);
}
