using Microsoft.EntityFrameworkCore;
using PromotionService.Entities;

namespace PromotionService.Data;

public class PromotionDbContext : DbContext
{
    public PromotionDbContext(DbContextOptions<PromotionDbContext> options) : base(options)
    {
    }

    public DbSet<Promotion> Promotions => Set<Promotion>();
    public DbSet<PromotionReservation> PromotionReservations => Set<PromotionReservation>();
    public DbSet<PromotionCustomerUsage> PromotionCustomerUsages => Set<PromotionCustomerUsage>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<Promotion>(entity =>
        {
            entity.HasIndex(p => p.Code).IsUnique();
            entity.Property(p => p.DiscountType).HasConversion<string>().HasMaxLength(20);
            entity.Property(p => p.DiscountValue).HasColumnType("decimal(18,2)");
        });

        modelBuilder.Entity<PromotionReservation>(entity =>
        {
            entity.HasIndex(r => r.OrderId).IsUnique();
            entity.Property(r => r.Status).HasConversion<string>().HasMaxLength(20);
            entity.HasOne(r => r.Promotion)
                .WithMany(p => p.Reservations)
                .HasForeignKey(r => r.PromotionId)
                .OnDelete(DeleteBehavior.Restrict);
        });

        modelBuilder.Entity<PromotionCustomerUsage>(entity =>
        {
            entity.HasKey(u => new { u.PromotionId, u.CustomerId });
        });

        // Dữ liệu mẫu để test validate/reserve ngay sau khi migrate, theo đúng 2 mã ví dụ
        // trong đặc tả (FREESHIP, GIAM10).
        var freeshipId = Guid.Parse("11111111-1111-1111-1111-111111111111");
        var giam10Id = Guid.Parse("22222222-2222-2222-2222-222222222222");
        var seedStart = new DateTime(2026, 8, 1, 0, 0, 0, DateTimeKind.Utc);
        var seedEnd = new DateTime(2026, 12, 31, 23, 59, 59, DateTimeKind.Utc);

        modelBuilder.Entity<Promotion>().HasData(
            new Promotion
            {
                Id = freeshipId,
                Code = "FREESHIP",
                Description = "Giảm 10.000đ phí ship",
                DiscountType = PromotionDiscountType.FIXED,
                DiscountValue = 10000,
                MaxDiscountAmount = null,
                MinOrderValue = 30000,
                StartAt = seedStart,
                EndAt = seedEnd,
                IsActive = true,
                UsageLimitTotal = 1000,
                UsageCount = 0,
                UsageLimitPerCustomer = 3
            },
            new Promotion
            {
                Id = giam10Id,
                Code = "GIAM10",
                Description = "Giảm 10%, tối đa 20.000đ",
                DiscountType = PromotionDiscountType.PERCENT,
                DiscountValue = 10,
                MaxDiscountAmount = 20000,
                MinOrderValue = 0,
                StartAt = seedStart,
                EndAt = seedEnd,
                IsActive = true,
                UsageLimitTotal = null,
                UsageCount = 0,
                UsageLimitPerCustomer = 1
            }
        );
    }
}
