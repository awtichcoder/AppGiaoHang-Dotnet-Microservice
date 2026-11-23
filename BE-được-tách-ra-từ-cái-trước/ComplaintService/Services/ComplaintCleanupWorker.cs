using Microsoft.Extensions.Options;

namespace ComplaintService.Services;

public sealed class ComplaintCleanupWorker(
    IServiceScopeFactory scopes,
    IOptions<ComplaintOptions> options,
    ILogger<ComplaintCleanupWorker> logger) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        var interval = TimeSpan.FromMinutes(Math.Max(1, options.Value.CleanupIntervalMinutes));
        using var timer = new PeriodicTimer(interval);
        do
        {
            try
            {
                using var scope = scopes.CreateScope();
                var removed = await scope.ServiceProvider.GetRequiredService<ComplaintAttachmentCleaner>().CleanAsync(stoppingToken);
                if (removed > 0) logger.LogInformation("Đã dọn {Count} ảnh khiếu nại quá hạn", removed);
            }
            catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
            {
                return;
            }
            catch (Exception exception)
            {
                // Lỗi tạm thời (ví dụ DB chưa sẵn sàng) không được làm dừng service.
                logger.LogError(exception, "Dọn ảnh khiếu nại thất bại");
            }
        }
        while (await WaitAsync(timer, stoppingToken));
    }

    private static async Task<bool> WaitAsync(PeriodicTimer timer, CancellationToken stoppingToken)
    {
        try { return await timer.WaitForNextTickAsync(stoppingToken); }
        catch (OperationCanceledException) { return false; }
    }
}
