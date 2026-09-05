using LoyaltyService.Common;
using LoyaltyService.Data;
using LoyaltyService.Services;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);
var connectionString = builder.Configuration.GetConnectionString("LoyaltyDatabase")
    ?? throw new InvalidOperationException("Không tìm thấy connection string LoyaltyDatabase.");

builder.Services.AddDbContext<LoyaltyDbContext>(options => options.UseSqlServer(connectionString));
builder.Services.Configure<LoyaltyOptions>(builder.Configuration.GetSection("Loyalty"));
builder.Services.AddScoped<ILoyaltyService, LoyaltyManager>();
builder.Services.AddJwtAndInternalAuthentication(builder.Configuration);
builder.Services.AddControllers().ConfigureApiBehaviorOptions(options =>
{
    options.InvalidModelStateResponseFactory = context => new Microsoft.AspNetCore.Mvc.BadRequestObjectResult(new
    {
        code = "VALIDATION_ERROR",
        message = "Dữ liệu đầu vào không hợp lệ.",
        details = context.ModelState.Where(x => x.Value?.Errors.Count > 0)
            .ToDictionary(x => x.Key, x => x.Value!.Errors.Select(error => error.ErrorMessage))
    });
});
builder.Services.AddOpenApi();

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
}

app.UseMiddleware<ApiExceptionMiddleware>();
app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();
app.MapGet("/health/live", () => Results.Ok(new { status = "Healthy", service = "LoyaltyService" }));
app.MapGet("/health/ready", async (LoyaltyDbContext db, CancellationToken cancellationToken) =>
    await db.Database.CanConnectAsync(cancellationToken)
        ? Results.Ok(new { status = "Ready", dependencies = new[] { "LoyaltyDatabase" } })
        : Results.StatusCode(StatusCodes.Status503ServiceUnavailable));

if (builder.Configuration.GetValue("APPLY_MIGRATIONS", false))
{
    using var scope = app.Services.CreateScope();
    await scope.ServiceProvider.GetRequiredService<LoyaltyDbContext>().Database.MigrateAsync();
}

app.Run();