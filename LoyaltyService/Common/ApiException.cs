namespace LoyaltyService.Common;

public sealed class ApiException(int statusCode, string code, string message, object? details = null)
    : Exception(message)
{
    public int StatusCode { get; } = statusCode;
    public string Code { get; } = code;
    public object? Details { get; } = details;
}
