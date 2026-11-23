using System.Text.Json;
using DeliveryService.Clients;
using DeliveryService.Data;
using DeliveryService.Entities;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

namespace DeliveryService.Services;

public sealed class DeliveryMatchingWorker(
    IServiceScopeFactory scopeFactory,
    IOptions<MatchingOptions> options,
    ILogger<DeliveryMatchingWorker> logger) : BackgroundService
{
    private readonly MatchingOptions _options = options.Value;

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        using var timer = new PeriodicTimer(TimeSpan.FromSeconds(Math.Max(1, _options.WorkerIntervalSeconds)));
        while (!stoppingToken.IsCancellationRequested)
        {
            try { await ProcessAsync(stoppingToken); }
            catch (Exception exception) { logger.LogError(exception, "Matching worker failed"); }
            await timer.WaitForNextTickAsync(stoppingToken);
        }
    }

    private async Task ProcessAsync(CancellationToken cancellationToken)
    {
        using var scope = scopeFactory.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<DeliveryDbContext>();
        var drivers = scope.ServiceProvider.GetRequiredService<IDriverDirectoryClient>();
        var deliveries = await db.Deliveries.Where(x => x.Status == DeliveryStatus.SEARCHING)
            .OrderBy(x => x.SearchStartedAt).Take(20).ToListAsync(cancellationToken);

        foreach (var delivery in deliveries)
        {
            var now = DateTime.UtcNow;
            var expired = await db.DeliveryOffers.Where(x => x.DeliveryId == delivery.DeliveryId
                && x.Status == DeliveryOfferStatus.OFFERED && x.ExpiresAt <= now).ToListAsync(cancellationToken);
            foreach (var offer in expired)
            {
                offer.Status = DeliveryOfferStatus.EXPIRED;
                offer.RespondedAt = now;
            }

            if (await db.DeliveryOffers.AnyAsync(x => x.DeliveryId == delivery.DeliveryId
                && x.Status == DeliveryOfferStatus.OFFERED && x.ExpiresAt > now, cancellationToken))
            {
                await db.SaveChangesAsync(cancellationToken);
                continue;
            }

            while (delivery.SearchRadiusIndex < _options.RadiusStepsKm.Length)
            {
                var radius = _options.RadiusStepsKm[delivery.SearchRadiusIndex];
                delivery.CurrentRadiusKm = radius;
                var candidate = await db.DeliveryCandidates
                    .Where(x => x.DeliveryId == delivery.DeliveryId && x.RadiusKm == radius && !x.Invited)
                    .OrderBy(x => x.Sequence).FirstOrDefaultAsync(cancellationToken);

                if (candidate is null)
                {
                    var snapshotExists = await db.DeliveryCandidates.AnyAsync(
                        x => x.DeliveryId == delivery.DeliveryId && x.RadiusKm == radius, cancellationToken);
                    if (!snapshotExists)
                    {
                        var previousRadius = delivery.SearchRadiusIndex == 0 ? 0 : _options.RadiusStepsKm[delivery.SearchRadiusIndex - 1];
                        var alreadyKnown = await db.DeliveryCandidates.Where(x => x.DeliveryId == delivery.DeliveryId)
                            .Select(x => x.DriverId).ToListAsync(cancellationToken);
                        var nearby = await drivers.GetEligibleDriversAsync(delivery.PickupLatitude, delivery.PickupLongitude, radius, cancellationToken);
                        var shuffled = nearby.Where(x => DeliveryMatchingRules.IsInRadiusStep(
                                x.DistanceKm, previousRadius, radius, delivery.SearchRadiusIndex == 0)
                                && !alreadyKnown.Contains(x.DriverId))
                            .OrderBy(_ => Random.Shared.Next()).ToList();
                        for (var index = 0; index < shuffled.Count; index++)
                        {
                            db.DeliveryCandidates.Add(new DeliveryCandidate
                            {
                                DeliveryId = delivery.DeliveryId,
                                DriverId = shuffled[index].DriverId,
                                DistanceKm = shuffled[index].DistanceKm,
                                RadiusKm = radius,
                                Sequence = index
                            });
                        }
                        await db.SaveChangesAsync(cancellationToken);
                        candidate = await db.DeliveryCandidates.Where(x => x.DeliveryId == delivery.DeliveryId
                            && x.RadiusKm == radius && !x.Invited).OrderBy(x => x.Sequence).FirstOrDefaultAsync(cancellationToken);
                    }
                }

                if (candidate is not null)
                {
                    candidate.Invited = true;
                    db.DeliveryOffers.Add(new DeliveryOffer
                    {
                        DeliveryId = delivery.DeliveryId,
                        DriverId = candidate.DriverId,
                        RadiusKm = candidate.RadiusKm,
                        DistanceKm = candidate.DistanceKm,
                        ExpiresAt = now.AddSeconds(_options.OfferTimeoutSeconds)
                    });
                    delivery.UpdatedAt = now;
                    await db.SaveChangesAsync(cancellationToken);
                    break;
                }

                delivery.SearchRadiusIndex++;
            }

            if (delivery.SearchRadiusIndex >= _options.RadiusStepsKm.Length)
            {
                delivery.Status = DeliveryStatus.NO_DRIVER_FOUND;
                delivery.SearchEndedAt = now;
                delivery.UpdatedAt = now;
                delivery.Version++;
                db.OutboxMessages.Add(new OutboxMessage
                {
                    Type = "OrderStatus",
                    Payload = JsonSerializer.Serialize(new DeliveryWorkflowService.OrderStatusPayload(
                        delivery.OrderId, DeliveryStatus.NO_DRIVER_FOUND.ToString(), delivery.OrderVersion, null))
                });
                delivery.OrderVersion++;
                await db.SaveChangesAsync(cancellationToken);
            }
        }
    }
}
