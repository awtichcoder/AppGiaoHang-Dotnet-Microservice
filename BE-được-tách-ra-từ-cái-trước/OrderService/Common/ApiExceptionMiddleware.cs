using Microsoft.EntityFrameworkCore;

namespace OrderService.Common;

public sealed class ApiExceptionMiddleware(RequestDelegate next, ILogger<ApiExceptionMiddleware> logger)
{
    public async Task InvokeAsync(HttpContext context)
    {
        try
        {
            await next(context);
        }
        catch (ApiException exception)
        {
            await WriteErrorAsync(context, exception.StatusCode, exception.Code, exception.Message, exception.Details);
        }
        catch (DbUpdateConcurrencyException exception)
        {
            await WriteErrorAsync(context, 409, "CONCURRENCY_CONFLICT", "Dữ liệu đã được cập nhật bởi yêu cầu khác.", exception.Message);
        }
        catch (Exception exception)
        {
            logger.LogError(exception, "Unhandled request error");
            await WriteErrorAsync(context, 500, "INTERNAL_ERROR", "Hệ thống đang gặp lỗi.", null);
        }
    }

    private static async Task WriteErrorAsync(HttpContext context, int status, string code, string message, object? details)
    {
        context.Response.StatusCode = status;
        context.Response.ContentType = "application/json";
        await context.Response.WriteAsJsonAsync(new { code, message, details });
    }
}
