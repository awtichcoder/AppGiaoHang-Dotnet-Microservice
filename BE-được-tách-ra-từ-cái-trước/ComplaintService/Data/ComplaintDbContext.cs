using ComplaintService.Entities;
using Microsoft.EntityFrameworkCore;

namespace ComplaintService.Data;

public class ComplaintDbContext(DbContextOptions<ComplaintDbContext> options) : DbContext(options)
{
    public DbSet<Complaint> Complaints => Set<Complaint>();
    public DbSet<ComplaintAttachment> ComplaintAttachments => Set<ComplaintAttachment>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<Complaint>(entity =>
        {
            entity.HasKey(x => x.Id);
            // Mỗi đơn tối đa một khiếu nại từ khách và một từ tài xế, kể cả khi gửi đồng thời.
            entity.HasIndex(x => new { x.OrderId, x.ReporterRole }).IsUnique();
            entity.HasIndex(x => new { x.ReporterId, x.CreatedAt });
            entity.HasIndex(x => new { x.Status, x.CreatedAt });
            entity.Property(x => x.ReporterRole).HasConversion<string>().HasMaxLength(20);
            entity.Property(x => x.Category).HasConversion<string>().HasMaxLength(30);
            // Status là concurrency token để hai admin không ghi đè quyết định của nhau.
            entity.Property(x => x.Status).HasConversion<string>().HasMaxLength(20).IsConcurrencyToken();
            entity.Property(x => x.Description).IsRequired().HasMaxLength(1000);
            entity.Property(x => x.AdminNote).HasMaxLength(1000);
            entity.HasMany(x => x.Attachments).WithOne(x => x.Complaint)
                .HasForeignKey(x => x.ComplaintId).OnDelete(DeleteBehavior.Cascade);
        });

        modelBuilder.Entity<ComplaintAttachment>(entity =>
        {
            entity.HasKey(x => x.Id);
            entity.HasIndex(x => x.StoredFileName).IsUnique();
            entity.Property(x => x.StoredFileName).IsRequired().HasMaxLength(64);
            entity.Property(x => x.OriginalFileName).IsRequired().HasMaxLength(255);
            entity.Property(x => x.ContentType).IsRequired().HasMaxLength(50);
        });
    }
}
