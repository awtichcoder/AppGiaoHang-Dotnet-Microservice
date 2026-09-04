using Microsoft.EntityFrameworkCore;
using PromotionService.Entities;

namespace PromotionService.Data;

public sealed class PromotionDbContext(DbContextOptions<PromotionDbContext> options) : DbContext(options)
{
    public DbSet<Promotion> Promotions => Set<Promotion>();
    public DbSet<PromotionReservation> PromotionReservations => Set<PromotionReservation>();
    public DbSet<PromotionCustomerUsage> PromotionCustomerUsages => Set<PromotionCustomerUsage>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<Promotion>(entity =>
        {
            entity.HasIndex(x => x.Code).IsUnique();
            entity.Property(x => x.Code).IsRequired().HasMaxLength(50);
            entity.Property(x => x.DiscountType).HasConversion<string>().HasMaxLength(20);
            entity.Property(x => x.DiscountValue).HasColumnType("decimal(18,2)");
        });
        modelBuilder.Entity<PromotionReservation>(entity =>
        {
            entity.HasIndex(x => x.OrderId).IsUnique();
            entity.Property(x => x.Status).HasConversion<string>().HasMaxLength(20);
            entity.HasOne(x => x.Promotion).WithMany(x => x.Reservations)
                .HasForeignKey(x => x.PromotionId).OnDelete(DeleteBehavior.Restrict);
        });
        modelBuilder.Entity<PromotionCustomerUsage>(entity =>
            entity.HasKey(x => new { x.PromotionId, x.CustomerId }));

        var start = new DateTime(2026, 8, 1, 0, 0, 0, DateTimeKind.Utc);
        var end = new DateTime(2026, 12, 31, 23, 59, 59, DateTimeKind.Utc);
        modelBuilder.Entity<Promotion>().HasData(
            new Promotion
            {
                Id = Guid.Parse("11111111-1111-1111-1111-111111111111"),
                Code = "FREESHIP",
                Description = "Giảm 10.000đ phí ship",
                DiscountType = PromotionDiscountType.FIXED,
                DiscountValue = 10000,
                MinOrderValue = 30000,
                StartAt = start,
                EndAt = end,
                IsActive = true,
                UsageLimitTotal = 1000,
                UsageLimitPerCustomer = 3
            },
            new Promotion
            {
                Id = Guid.Parse("22222222-2222-2222-2222-222222222222"),
                Code = "GIAM10",
                Description = "Giảm 10%, tối đa 20.000đ",
                DiscountType = PromotionDiscountType.PERCENT,
                DiscountValue = 10,
                MaxDiscountAmount = 20000,
                StartAt = start,
                EndAt = end,
                IsActive = true,
                UsageLimitPerCustomer = 1
            });
    }
}
