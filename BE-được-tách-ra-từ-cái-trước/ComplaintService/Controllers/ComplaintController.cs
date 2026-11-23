using ComplaintService.Common;
using ComplaintService.Contracts;
using ComplaintService.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace ComplaintService.Controllers;

[ApiController]
[Route("api/complaint")]
public sealed class ComplaintController(IComplaintManager complaints) : ControllerBase
{
    [Authorize(Roles = "CUSTOMER,DRIVER")]
    [HttpPost]
    [Consumes("multipart/form-data")]
    [RequestSizeLimit(ComplaintRules.MaxRequestBodyBytes)]
    [ComplaintFormGuard]
    public async Task<ActionResult<ComplaintResponse>> Create([FromForm] CreateComplaintForm form, CancellationToken cancellationToken)
    {
        var uploads = (form.Files ?? [])
            .Select(file => new ComplaintUpload(file.FileName, file.ContentType, file.Length, file.OpenReadStream))
            .ToList();
        var command = new CreateComplaintCommand(form.OrderId, form.Category, form.Description, uploads);
        var response = await complaints.CreateAsync(command, User.GetSubjectId(), Role(), cancellationToken);
        return CreatedAtAction(nameof(GetById), new { id = response.Id }, response);
    }

    [Authorize(Roles = "CUSTOMER,DRIVER")]
    [HttpGet("me")]
    public async Task<ActionResult<PagedResponse<ComplaintResponse>>> GetMine(
        [FromQuery] Guid? orderId = null, [FromQuery] int page = 1, [FromQuery] int pageSize = 20,
        CancellationToken cancellationToken = default)
        => Ok(await complaints.GetMineAsync(User.GetSubjectId(), orderId, page, pageSize, cancellationToken));

    [Authorize(Roles = "ADMIN")]
    [HttpGet("admin")]
    public async Task<ActionResult<PagedResponse<ComplaintResponse>>> GetForAdmin(
        [FromQuery] string? status = null, [FromQuery] int page = 1, [FromQuery] int pageSize = 20,
        CancellationToken cancellationToken = default)
        => Ok(await complaints.GetForAdminAsync(status, page, pageSize, cancellationToken));

    [Authorize(Roles = "ADMIN")]
    [HttpPatch("admin/{id:guid}/status")]
    public async Task<ActionResult<ComplaintResponse>> UpdateStatus(
        Guid id, UpdateComplaintStatusRequest request, CancellationToken cancellationToken)
        => Ok(await complaints.UpdateStatusAsync(id, request, User.GetSubjectId(), cancellationToken));

    [Authorize(Roles = "CUSTOMER,DRIVER,ADMIN")]
    [HttpGet("{id:guid}")]
    public async Task<ActionResult<ComplaintResponse>> GetById(Guid id, CancellationToken cancellationToken)
        => Ok(await complaints.GetByIdAsync(id, User.GetSubjectId(), Role(), cancellationToken));

    [Authorize(Roles = "CUSTOMER,DRIVER,ADMIN")]
    [HttpGet("{id:guid}/attachments/{attachmentId:guid}")]
    public async Task<IActionResult> GetAttachment(Guid id, Guid attachmentId, CancellationToken cancellationToken)
    {
        var attachment = await complaints.OpenAttachmentAsync(id, attachmentId, User.GetSubjectId(), Role(), cancellationToken);
        Response.Headers.CacheControl = "private, no-store";
        Response.Headers.XContentTypeOptions = "nosniff";
        return File(attachment.Content, attachment.ContentType, attachment.FileName);
    }

    private string Role() => User.FindFirst("role")?.Value ?? string.Empty;
}
