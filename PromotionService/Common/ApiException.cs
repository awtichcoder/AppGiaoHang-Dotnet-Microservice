namespace PromotionService.Common;

public class ApiException : Exception
{
    public int StatusCode { get; }
    public string Code { get; }
    public object? Details { get; }

    public ApiException(int statusCode, string code, string message, object? details = null)
        : base(message)
    {
        StatusCode = statusCode;
        Code = code;
        Details = details;
    }
}
