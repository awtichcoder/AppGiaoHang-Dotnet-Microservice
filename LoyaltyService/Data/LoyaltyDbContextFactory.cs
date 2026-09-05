using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Design;

namespace LoyaltyService.Data;

public sealed class LoyaltyDbContextFactory : IDesignTimeDbContextFactory<LoyaltyDbContext>
{
    public LoyaltyDbContext CreateDbContext(string[] args)
    {
        var connection = Environment.GetEnvironmentVariable("LOYALTY_DESIGN_CONNECTION")
            ?? "Server=localhost,1433;Database=DeliveryApp_Loyalty;User Id=sa;Password=Your_password123!;TrustServerCertificate=True;Encrypt=False";
        return new LoyaltyDbContext(new DbContextOptionsBuilder<LoyaltyDbContext>()
            .UseSqlServer(connection).Options);
    }
}
