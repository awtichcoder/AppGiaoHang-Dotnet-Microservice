using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.Filters;

namespace ComplaintService.Common;

/// <summary>
/// Đọc form trước model binding. Nếu để MVC tự đọc, lỗi vượt giới hạn dung lượng bị gộp thành
/// một lỗi ModelState có key rỗng, client không nhận được mã lỗi rõ ràng.
/// Chạy sau RequestSizeLimit vì filter đó thuộc nhóm authorization.
/// </summary>
[AttributeUsage(AttributeTargets.Method)]
public sealed class ComplaintFormGuardAttribute : Attribute, IAsyncResourceFilter
{
    public async Task OnResourceExecutionAsync(ResourceExecutingContext context, ResourceExecutionDelegate next)
    {
        var request = context.HttpContext.Request;
        if (request.HasFormContentType)
        {
            try
            {
                await request.ReadFormAsync(context.HttpContext.RequestAborted);
            }
            catch (BadHttpRequestException exception) when (exception.StatusCode == StatusCodes.Status413PayloadTooLarge)
            {
                context.Result = Error(413, "COMPLAINT_FILE_TOO_LARGE", "Dữ liệu tải lên vượt quá giới hạn cho phép.");
                return;
            }
            catch (InvalidDataException)
            {
                context.Result = Error(400, "VALIDATION_ERROR", "Dữ liệu tải lên không hợp lệ.");
                return;
            }
        }
        await next();
    }

    private static ObjectResult Error(int status, string code, string message) =>
        new(new { code, message, details = (object?)null }) { StatusCode = status };
}
