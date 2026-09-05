using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Design;

namespace DeliveryService.Data;

public sealed class DeliveryDbContextFactory : IDesignTimeDbContextFactory<DeliveryDbContext>
{
    public DeliveryDbContext CreateDbContext(string[] args)
    {
        var connection = Environment.GetEnvironmentVariable("DELIVERY_DESIGN_CONNECTION")
            ?? "Server=localhost,1433;Database=DeliveryApp_Delivery;User Id=sa;Password=Your_password123!;TrustServerCertificate=True;Encrypt=False";
        return new DeliveryDbContext(new DbContextOptionsBuilder<DeliveryDbContext>()
            .UseSqlServer(connection).Options);
    }
}
