namespace DriverService.Common;

public sealed record ApiError(string Code, string Message, object? Details = null);
