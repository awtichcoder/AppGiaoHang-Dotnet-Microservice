namespace PromotionService.Common;

public record ApiError(string Code, string Message, object? Details);
