namespace PromotionService.Common;

public sealed class ExceptionHandlingMiddleware(RequestDelegate next, ILogger<ExceptionHandlingMiddleware> logger)
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
            logger.LogError(exception, "Unhandled PromotionService error");
            context.Response.StatusCode = 500;
            await context.Response.WriteAsJsonAsync(new ApiError("INTERNAL_ERROR", "Hệ thống đang gặp lỗi."));
        }
    }
}
