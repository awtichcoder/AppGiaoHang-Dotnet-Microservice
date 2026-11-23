using Microsoft.EntityFrameworkCore;
using OrderService.Entities;

namespace OrderService.Data;

public class OrderDbContext : DbContext
{
    public OrderDbContext(DbContextOptions<OrderDbContext> options)
        : base(options)
    {
    }

    public DbSet<Order> Orders => Set<Order>();
    public DbSet<OutboxMessage> OutboxMessages => Set<OutboxMessage>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        modelBuilder.Entity<Order>(entity =>
        {
            entity.ToTable("Orders");

            // Khóa chính
            entity.HasKey(x => x.OrderId);

            entity.Property(x => x.PickupAddress)
                .IsRequired()
                .HasMaxLength(500);

            entity.Property(x => x.DropoffAddress)
                .IsRequired()
                .HasMaxLength(500);

            entity.Property(x => x.ReceiverPhone)
                .IsRequired()
                .HasMaxLength(20);

            entity.Property(x => x.PromotionCode)
                .HasMaxLength(50);
            entity.Property(x => x.RequestFingerprint).IsRequired().HasMaxLength(64);
            entity.Property(x => x.MatchingStatus).IsRequired().HasMaxLength(30);
            entity.Property(x => x.CancelReasonCode).HasMaxLength(100);

            // Tọa độ
            entity.Property(x => x.PickupLatitude)
                .HasPrecision(9, 6);

            entity.Property(x => x.PickupLongitude)
                .HasPrecision(9, 6);

            entity.Property(x => x.DropoffLatitude)
                .HasPrecision(9, 6);

            entity.Property(x => x.DropoffLongitude)
                .HasPrecision(9, 6);

            entity.Property(x => x.DistanceKm)
                .HasPrecision(10, 2);

            // Lưu trạng thái bằng chữ trong SQL Server
            entity.Property(x => x.Status)
                .HasConversion<string>()
                .HasMaxLength(30);

            // Chống hai tiến trình cập nhật một đơn cùng lúc
            entity.Property(x => x.Version)
                .IsConcurrencyToken();

            // Chống customer gửi yêu cầu tạo trùng đơn
            entity.HasIndex(x => new
            {
                x.CustomerId,
                x.ClientRequestId
            }).IsUnique();
        });

        modelBuilder.Entity<OutboxMessage>(entity =>
        {
            entity.ToTable("OutboxMessages");
            entity.HasKey(x => x.Id);
            entity.Property(x => x.Type).IsRequired().HasMaxLength(100);
            entity.Property(x => x.Payload).IsRequired();
            entity.Property(x => x.LastError).HasMaxLength(2000);
            entity.HasIndex(x => new { x.ProcessedAt, x.NextAttemptAt });
        });
    }
}
