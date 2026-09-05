using DeliveryService.Entities;
using Microsoft.EntityFrameworkCore;

namespace DeliveryService.Data;

public sealed class DeliveryDbContext(DbContextOptions<DeliveryDbContext> options) : DbContext(options)
{
    public DbSet<Delivery> Deliveries => Set<Delivery>();
    public DbSet<DeliveryCandidate> DeliveryCandidates => Set<DeliveryCandidate>();
    public DbSet<DeliveryOffer> DeliveryOffers => Set<DeliveryOffer>();
    public DbSet<OutboxMessage> OutboxMessages => Set<OutboxMessage>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<Delivery>(entity =>
        {
            entity.HasKey(x => x.DeliveryId);
            entity.HasIndex(x => x.OrderId).IsUnique();
            entity.Property(x => x.Status).HasConversion<string>().HasMaxLength(30);
            entity.Property(x => x.CurrentRadiusKm).HasPrecision(5, 2);
            entity.Property(x => x.PickupLatitude).HasPrecision(9, 6);
            entity.Property(x => x.PickupLongitude).HasPrecision(9, 6);
            entity.Property(x => x.DropoffLatitude).HasPrecision(9, 6);
            entity.Property(x => x.DropoffLongitude).HasPrecision(9, 6);
            entity.Property(x => x.ReceiverPhone).HasMaxLength(20);
            entity.Property(x => x.PickupAddress).HasMaxLength(500);
            entity.Property(x => x.DropoffAddress).HasMaxLength(500);
            entity.Property(x => x.Version).IsConcurrencyToken();
        });
        modelBuilder.Entity<DeliveryCandidate>(entity =>
        {
            entity.HasKey(x => x.DeliveryCandidateId);
            entity.HasIndex(x => new { x.DeliveryId, x.DriverId }).IsUnique();
            entity.Property(x => x.RadiusKm).HasPrecision(5, 2);
            entity.Property(x => x.DistanceKm).HasPrecision(7, 2);
        });
        modelBuilder.Entity<DeliveryOffer>(entity =>
        {
            entity.HasKey(x => x.OfferId);
            entity.HasIndex(x => new { x.DeliveryId, x.DriverId }).IsUnique();
            entity.Property(x => x.Status).HasConversion<string>().HasMaxLength(30);
            entity.Property(x => x.RadiusKm).HasPrecision(5, 2);
            entity.Property(x => x.DistanceKm).HasPrecision(7, 2);
            entity.Property(x => x.RejectReasonCode).HasMaxLength(100);
        });
        modelBuilder.Entity<OutboxMessage>(entity =>
        {
            entity.HasKey(x => x.Id);
            entity.Property(x => x.Type).HasMaxLength(100);
            entity.Property(x => x.LastError).HasMaxLength(2000);
            entity.HasIndex(x => new { x.ProcessedAt, x.NextAttemptAt });
        });
    }
}
