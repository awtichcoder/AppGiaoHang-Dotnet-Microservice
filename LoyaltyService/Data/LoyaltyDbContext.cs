using LoyaltyService.Entities;
using Microsoft.EntityFrameworkCore;

namespace LoyaltyService.Data;

public sealed class LoyaltyDbContext(DbContextOptions<LoyaltyDbContext> options) : DbContext(options)
{
    public DbSet<LoyaltyAccount> LoyaltyAccounts => Set<LoyaltyAccount>();
    public DbSet<PointReservation> PointReservations => Set<PointReservation>();
    public DbSet<PointTransaction> PointTransactions => Set<PointTransaction>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<LoyaltyAccount>(entity =>
        {
            entity.HasKey(x => x.CustomerId);
            entity.Property(x => x.Tier).HasConversion<string>().HasMaxLength(20);
            entity.Property(x => x.RowVersion).IsRowVersion();
            entity.ToTable(x =>
            {
                x.HasCheckConstraint("CK_LoyaltyAccounts_Available_NonNegative", "[AvailablePoints] >= 0");
                x.HasCheckConstraint("CK_LoyaltyAccounts_Reserved_NonNegative", "[ReservedPoints] >= 0");
                x.HasCheckConstraint("CK_LoyaltyAccounts_Lifetime_NonNegative", "[LifetimeEarnedPoints] >= 0");
            });
        });

        modelBuilder.Entity<PointReservation>(entity =>
        {
            entity.HasKey(x => x.Id);
            entity.Property(x => x.Status).HasConversion<string>().HasMaxLength(20);
            entity.HasIndex(x => x.OrderId).IsUnique();
            entity.HasIndex(x => x.CustomerId);
            entity.ToTable(x =>
            {
                x.HasCheckConstraint("CK_PointReservations_Points_Positive", "[Points] > 0");
                x.HasCheckConstraint("CK_PointReservations_Discount_NonNegative", "[DiscountAmount] >= 0");
            });
            entity.HasOne(x => x.Account)
                .WithMany(x => x.Reservations)
                .HasForeignKey(x => x.CustomerId)
                .OnDelete(DeleteBehavior.Restrict);
        });

        modelBuilder.Entity<PointTransaction>(entity =>
        {
            entity.HasKey(x => x.Id);
            entity.Property(x => x.Type).HasConversion<string>().HasMaxLength(20);
            entity.Property(x => x.Description).HasMaxLength(500);
            entity.HasIndex(x => new { x.CustomerId, x.CreatedAt });
            entity.HasIndex(x => x.ReservationId);
            entity.HasIndex(x => x.OrderId)
                .IsUnique()
                .HasFilter("[OrderId] IS NOT NULL AND [Type] = N'EARN'");
            entity.HasOne(x => x.Account)
                .WithMany(x => x.Transactions)
                .HasForeignKey(x => x.CustomerId)
                .OnDelete(DeleteBehavior.Restrict);
            entity.HasOne(x => x.Reservation)
                .WithMany()
                .HasForeignKey(x => x.ReservationId)
                .OnDelete(DeleteBehavior.Restrict);
        });
    }
}
