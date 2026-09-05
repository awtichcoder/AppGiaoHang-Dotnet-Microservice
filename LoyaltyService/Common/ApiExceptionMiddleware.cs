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
            context.Response.StatusCode = exception.StatusCode;
            await context.Response.WriteAsJsonAsync(new ApiError(exception.Code, exception.Message, exception.Details));
        }
        catch (Exception exception)
        {
            logger.LogError(exception, "Unhandled LoyaltyService error");
            context.Response.StatusCode = 500;
            await context.Response.WriteAsJsonAsync(new ApiError("INTERNAL_ERROR", "Hệ thống đang gặp lỗi."));
        }
    }
}
