namespace MapService.Common;

public record ApiError(string Code, string Message, object? Details);
