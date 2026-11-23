using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Design;

namespace DriverService.Data;

public sealed class DriverDbContextFactory : IDesignTimeDbContextFactory<DriverDbContext>
{
    public DriverDbContext CreateDbContext(string[] args)
    {
        var options = new DbContextOptionsBuilder<DriverDbContext>()
            .UseSqlServer("Server=localhost;Database=DeliveryApp_Driver;Trusted_Connection=True;TrustServerCertificate=True")
            .Options;
        return new DriverDbContext(options);
    }
}
