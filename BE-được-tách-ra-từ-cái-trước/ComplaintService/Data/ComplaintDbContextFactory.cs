using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Design;

namespace ComplaintService.Data;

public sealed class ComplaintDbContextFactory : IDesignTimeDbContextFactory<ComplaintDbContext>
{
    public ComplaintDbContext CreateDbContext(string[] args)
    {
        var connection = Environment.GetEnvironmentVariable("COMPLAINT_DESIGN_CONNECTION")
            ?? "Server=localhost,1433;Database=DeliveryApp_Complaint;User Id=sa;Password=Your_password123!;TrustServerCertificate=True;Encrypt=False";
        return new ComplaintDbContext(new DbContextOptionsBuilder<ComplaintDbContext>()
            .UseSqlServer(connection).Options);
    }
}
