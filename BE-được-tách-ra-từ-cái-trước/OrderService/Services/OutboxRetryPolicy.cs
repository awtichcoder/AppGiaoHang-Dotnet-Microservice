using OrderService.Common;
using OrderService.Entities;

namespace OrderService.Services;

/// <summary>
/// Decides what happens to an outbox message after a failed attempt.
/// Network errors and 5xx keep retrying (capped at 60 s between attempts), because the dependency
/// is expected to come back. A business rejection (400/404/409/422) will not change on its own, so
/// after <see cref="MaxBusinessFailures"/> attempts the message is parked: it stays unprocessed with
/// "PARKED:" in LastError and is no longer picked up. Re-queue it manually by setting NextAttemptAt
/// to the current time.
/// </summary>
public static class OutboxRetryPolicy
{
    public const int MaxBusinessFailures = 5;
    public const string ParkedPrefix = "PARKED: ";
    public static readonly DateTime ParkedUntil = new(9999, 12, 31, 0, 0, 0, DateTimeKind.Utc);

    public static bool IsBusinessRejection(Exception exception) => StatusOf(exception) is 400 or 404 or 409 or 422;

    /// <returns>true when the message was parked.</returns>
    public static bool RecordFailure(OutboxMessage message, Exception exception, DateTime now)
    {
        message.RetryCount++;
        var parked = IsBusinessRejection(exception) && message.RetryCount >= MaxBusinessFailures;
        var error = (parked ? ParkedPrefix : string.Empty) + exception.Message;
        message.LastError = error[..Math.Min(error.Length, 2000)];
        message.NextAttemptAt = parked ? ParkedUntil : now.AddSeconds(Math.Min(60, Math.Pow(2, message.RetryCount)));
        return parked;
    }

    private static int? StatusOf(Exception exception) => exception switch
    {
        ApiException api => api.StatusCode,
        HttpRequestException { StatusCode: not null } http => (int)http.StatusCode,
        _ => null
    };
}
