using Microsoft.EntityFrameworkCore;

namespace DeliveryService.Common;

public sealed class ApiExceptionMiddleware(RequestDelegate next, ILogger<ApiExceptionMiddleware> logger)
{
    public async Task InvokeAsync(HttpContext context)
    {
        try { await next(context); }
        catch (ApiException exception)
        {
            await WriteAsync(context, exception.StatusCode, exception.Code, exception.Message, exception.Details);
        }
        catch (DbUpdateConcurrencyException exception)
        {
            await WriteAsync(context, 409, "CONCURRENCY_CONFLICT", "Yêu cầu khác đã cập nhật dữ liệu trước.", exception.Message);
        }
        catch (Exception exception)
        {
            logger.LogError(exception, "Unhandled request error");
            await WriteAsync(context, 500, "INTERNAL_ERROR", "Hệ thống đang gặp lỗi.", null);
        }
    }

    private static async Task WriteAsync(HttpContext context, int status, string code, string message, object? details)
    {
        context.Response.StatusCode = status;
        context.Response.ContentType = "application/json";
        await context.Response.WriteAsJsonAsync(new { code, message, details });
    }
}
