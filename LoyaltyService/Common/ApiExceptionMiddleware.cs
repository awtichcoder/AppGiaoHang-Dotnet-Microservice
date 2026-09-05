using Microsoft.EntityFrameworkCore;

namespace LoyaltyService.Common;

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
            await WriteErrorAsync(context, 409, "CONCURRENCY_CONFLICT", "D? li?u ?? ???c c?p nh?t b?i y?u c?u kh?c.", exception.Message);
        }
        catch (Exception exception)
        {
            logger.LogError(exception, "Unhandled LoyaltyService error");
            await WriteErrorAsync(context, 500, "INTERNAL_ERROR", "H? th?ng ?ang g?p l?i.", null);
        }
    }

    private static async Task WriteErrorAsync(HttpContext context, int status, string code, string message, object? details)
    {
        context.Response.StatusCode = status;
        context.Response.ContentType = "application/json";
        await context.Response.WriteAsJsonAsync(new { code, message, details });
    }
}
