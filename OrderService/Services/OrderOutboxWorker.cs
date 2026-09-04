using System.Text.Json;
using Microsoft.EntityFrameworkCore;
using OrderService.Clients;
using OrderService.Contracts;
using OrderService.Data;

namespace OrderService.Services;

public sealed class OrderOutboxWorker(IServiceScopeFactory scopeFactory, ILogger<OrderOutboxWorker> logger)
    : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        using var timer = new PeriodicTimer(TimeSpan.FromSeconds(2));
        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                await ProcessBatchAsync(stoppingToken);
            }
            catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
            {
                break;
            }
            catch (Exception exception)
            {
                logger.LogError(exception, "Order outbox worker failed");
            }

            await timer.WaitForNextTickAsync(stoppingToken);
        }
    }

    private async Task ProcessBatchAsync(CancellationToken cancellationToken)
    {
        using var scope = scopeFactory.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<OrderDbContext>();
        var delivery = scope.ServiceProvider.GetRequiredService<IDeliveryCommandClient>();
        var pricingDependencies = scope.ServiceProvider.GetRequiredService<IOrderPricingDependencies>();
        var messages = await db.OutboxMessages
            .Where(x => x.ProcessedAt == null && x.NextAttemptAt <= DateTime.UtcNow)
            .OrderBy(x => x.CreatedAt)
            .Take(20)
            .ToListAsync(cancellationToken);

        foreach (var message in messages)
        {
            try
            {
                if (message.Type == "DeliveryAssign")
                {
                    var payload = JsonSerializer.Deserialize<DeliveryAssignRequest>(message.Payload)!;
                    await delivery.AssignAsync(payload, cancellationToken);
                    var order = await db.Orders.FindAsync([payload.OrderId], cancellationToken);
                    if (order is not null && order.Status == Entities.OrderStatus.PENDING)
                    {
                        order.MatchingStatus = "SEARCHING";
                        order.UpdatedAt = DateTime.UtcNow;
                    }
                }
                else if (message.Type == "DeliveryCancel")
                {
                    var payload = JsonSerializer.Deserialize<CancelPayload>(message.Payload)!;
                    await delivery.CancelAsync(payload.OrderId, payload.ReasonCode, cancellationToken);
                }
                else if (message.Type is "CommitReservations" or "ReleaseReservations")
                {
                    var payload = JsonSerializer.Deserialize<ReservationPayload>(message.Payload)!;
                    if (payload.PromotionReservationId is Guid promotionReservationId)
                    {
                        if (message.Type == "CommitReservations")
                            await pricingDependencies.CommitPromotionAsync(promotionReservationId, cancellationToken);
                        else
                            await pricingDependencies.ReleasePromotionAsync(promotionReservationId, cancellationToken);
                    }

                    // Loyalty vẫn dùng mock cho đến khi nhóm phụ trách cung cấp API commit/release.
                    if (payload.LoyaltyReservationId is not null)
                        logger.LogInformation(
                            "{Action} loyalty reservation {ReservationId} bằng mock adapter",
                            message.Type,
                            payload.LoyaltyReservationId);
                }

                message.ProcessedAt = DateTime.UtcNow;
                message.LastError = null;
            }
            catch (Exception exception)
            {
                message.RetryCount++;
                message.LastError = exception.Message[..Math.Min(exception.Message.Length, 2000)];
                message.NextAttemptAt = DateTime.UtcNow.AddSeconds(Math.Min(60, Math.Pow(2, message.RetryCount)));
                logger.LogWarning(exception, "Outbox {OutboxId} sẽ được thử lại", message.Id);
            }
        }

        if (messages.Count > 0) await db.SaveChangesAsync(cancellationToken);
    }

    public sealed record CancelPayload(Guid OrderId, string ReasonCode);
    public sealed record ReservationPayload(
        Guid OrderId,
        Guid CustomerId,
        Guid? PromotionReservationId,
        Guid? LoyaltyReservationId);
}
