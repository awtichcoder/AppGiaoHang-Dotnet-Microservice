using System.Text.Json;
using DeliveryService.Clients;
using DeliveryService.Data;
using Microsoft.EntityFrameworkCore;

namespace DeliveryService.Services;

public sealed class DeliveryOutboxWorker(IServiceScopeFactory scopeFactory, ILogger<DeliveryOutboxWorker> logger)
    : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        using var timer = new PeriodicTimer(TimeSpan.FromSeconds(2));
        while (!stoppingToken.IsCancellationRequested)
        {
            try { await ProcessAsync(stoppingToken); }
            catch (Exception exception) { logger.LogError(exception, "Delivery outbox worker failed"); }
            await timer.WaitForNextTickAsync(stoppingToken);
        }
    }

    private async Task ProcessAsync(CancellationToken cancellationToken)
    {
        using var scope = scopeFactory.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<DeliveryDbContext>();
        var orders = scope.ServiceProvider.GetRequiredService<IOrderStatusClient>();
        var drivers = scope.ServiceProvider.GetRequiredService<IDriverDirectoryClient>();
        var loyalty = scope.ServiceProvider.GetRequiredService<ILoyaltyClient>();
        var messages = await db.OutboxMessages.Where(x => x.ProcessedAt == null && x.NextAttemptAt <= DateTime.UtcNow)
            .OrderBy(x => x.CreatedAt).Take(20).ToListAsync(cancellationToken);
        foreach (var message in messages)
        {
            try
            {
                if (message.Type == "OrderStatus")
                {
                    var payload = JsonSerializer.Deserialize<DeliveryWorkflowService.OrderStatusPayload>(message.Payload)!;
                    await orders.UpdateAsync(payload.OrderId, payload.Status, payload.Version, payload.DriverId, cancellationToken);
                }
                else if (message.Type == "LoyaltyEarn")
                {
                    var payload = JsonSerializer.Deserialize<LoyaltyEarnPayload>(message.Payload)!;
                    await loyalty.EarnAsync(payload.OrderId, payload.CustomerId, payload.Amount, cancellationToken);
                }
                else if (message.Type == "DriverBusy")
                {
                    var payload = JsonSerializer.Deserialize<DriverStatusPayload>(message.Payload)!;
                    if (payload.DriverId is null) throw new InvalidOperationException("DriverBusy thiếu DriverId.");
                    await drivers.MarkBusyAsync(payload.DriverId.Value, payload.DeliveryId, payload.OrderId, cancellationToken);
                }
                else if (message.Type == "DriverAvailable")
                {
                    var payload = JsonSerializer.Deserialize<DriverStatusPayload>(message.Payload)!;
                    if (payload.DriverId is null) throw new InvalidOperationException("DriverAvailable thiếu DriverId.");
                    await drivers.MarkAvailableAsync(payload.DriverId.Value, payload.DeliveryId, payload.OrderId, cancellationToken);
                }
                message.ProcessedAt = DateTime.UtcNow;
                message.LastError = null;
            }
            catch (Exception exception)
            {
                message.RetryCount++;
                message.LastError = exception.Message[..Math.Min(2000, exception.Message.Length)];
                message.NextAttemptAt = DateTime.UtcNow.AddSeconds(Math.Min(60, Math.Pow(2, message.RetryCount)));
                logger.LogWarning(exception, "Outbox {OutboxId} sẽ được thử lại", message.Id);
            }
        }
        if (messages.Count > 0) await db.SaveChangesAsync(cancellationToken);
    }

    private sealed record DriverStatusPayload(Guid? DriverId, Guid DeliveryId, Guid OrderId);
    private sealed record LoyaltyEarnPayload(Guid OrderId, Guid CustomerId, long Amount);
}
