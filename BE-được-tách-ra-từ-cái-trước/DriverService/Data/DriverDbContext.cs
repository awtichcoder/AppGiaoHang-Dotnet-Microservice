using DriverService.Entities;
using Microsoft.EntityFrameworkCore;

namespace DriverService.Data;

public sealed class DriverDbContext(DbContextOptions<DriverDbContext> options) : DbContext(options)
{
    public DbSet<DriverProfile> Drivers => Set<DriverProfile>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        var driver = modelBuilder.Entity<DriverProfile>();
        driver.ToTable("DriverProfiles");
        driver.HasKey(x => x.DriverId);
        driver.Property(x => x.Status).HasConversion<string>().HasMaxLength(20);
        driver.Property(x => x.Latitude).HasPrecision(9, 6);
        driver.Property(x => x.Longitude).HasPrecision(9, 6);
        driver.Property(x => x.AccuracyM).HasPrecision(10, 2);
        driver.Property(x => x.Version).IsConcurrencyToken();
        driver.HasIndex(x => new { x.Status, x.LocationUpdatedAt });
        driver.HasIndex(x => x.ActiveDeliveryId);
    }
}
