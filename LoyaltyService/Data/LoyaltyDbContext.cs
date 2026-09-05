using LoyaltyService.Entities;
using Microsoft.EntityFrameworkCore;

namespace LoyaltyService.Data;

public sealed class LoyaltyDbContext(DbContextOptions<LoyaltyDbContext> options) : DbContext(options)
{
    public DbSet<LoyaltyAccount> Accounts => Set<LoyaltyAccount>();
    public DbSet<LoyaltyReservation> Reservations => Set<LoyaltyReservation>();
    public DbSet<LoyaltyTransaction> Transactions => Set<LoyaltyTransaction>();
    public DbSet<LoyaltyEarnRecord> EarnRecords => Set<LoyaltyEarnRecord>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<LoyaltyAccount>(entity =>
        {
            entity.HasKey(x => x.CustomerId);
            entity.Property(x => x.Tier).HasMaxLength(20);
            entity.Property(x => x.Version).IsConcurrencyToken();
        });
        modelBuilder.Entity<LoyaltyReservation>(entity =>
        {
            entity.HasKey(x => x.ReservationId);
            entity.HasIndex(x => x.OrderId).IsUnique();
            entity.HasIndex(x => new { x.CustomerId, x.Status, x.ExpiresAt });
            entity.Property(x => x.Status).HasConversion<string>().HasMaxLength(20);
        });
        modelBuilder.Entity<LoyaltyTransaction>(entity =>
        {
            entity.HasKey(x => x.TransactionId);
            entity.HasIndex(x => new { x.CustomerId, x.CreatedAt });
            entity.Property(x => x.Type).HasMaxLength(20);
        });
        modelBuilder.Entity<LoyaltyEarnRecord>(entity =>
        {
            entity.HasKey(x => x.EarnRecordId);
            entity.HasIndex(x => x.OrderId).IsUnique();
        });

        modelBuilder.Entity<LoyaltyAccount>().HasData(new LoyaltyAccount
        {
            CustomerId = Guid.Parse("20000000-0000-0000-0000-000000000001"),
            Points = 1000,
            Tier = "SILVER",
            Version = 1,
            CreatedAt = new DateTime(2026, 8, 1, 0, 0, 0, DateTimeKind.Utc),
            UpdatedAt = new DateTime(2026, 8, 1, 0, 0, 0, DateTimeKind.Utc)
        });
    }
}
