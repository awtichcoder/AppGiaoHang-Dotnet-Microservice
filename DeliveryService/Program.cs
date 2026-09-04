using DeliveryService.Clients;
using DeliveryService.Common;
using DeliveryService.Data;
using DeliveryService.Services;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);
var connectionString = builder.Configuration.GetConnectionString("DeliveryDatabase")
    ?? throw new InvalidOperationException("Không tìm thấy connection string DeliveryDatabase.");

builder.Services.AddDbContext<DeliveryDbContext>(options => options.UseSqlServer(connectionString));
builder.Services.AddJwtAuthentication(builder.Configuration);
builder.Services.Configure<DeliveryFeeOptions>(builder.Configuration.GetSection("DeliveryFee"));
builder.Services.Configure<MatchingOptions>(builder.Configuration.GetSection("Matching"));
builder.Services.AddScoped<IDeliveryQuoteService, DeliveryQuoteService>();
builder.Services.AddScoped<IDeliveryWorkflowService, DeliveryWorkflowService>();
builder.Services.AddScoped<IDriverDirectoryClient, ConfiguredDriverDirectoryClient>();
builder.Services.AddHostedService<DeliveryMatchingWorker>();
builder.Services.AddHostedService<DeliveryOutboxWorker>();
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

var orderServiceUrl = builder.Configuration["ServiceUrls:OrderService"] ?? "http://orderservice:8080";
builder.Services.AddHttpClient<IOrderStatusClient, OrderStatusClient>(client =>
{
    client.BaseAddress = new Uri(orderServiceUrl.TrimEnd('/') + "/");
    client.Timeout = TimeSpan.FromSeconds(10);
});

var app = builder.Build();
app.UseMiddleware<ApiExceptionMiddleware>();
if (app.Environment.IsDevelopment()) app.MapOpenApi();
app.UseAuthentication();
app.UseAuthorization();
app.MapControllers();
app.MapGet("/health/live", () => Results.Ok(new { status = "Healthy", service = "DeliveryService" }));
app.MapGet("/health/ready", async (DeliveryDbContext db, CancellationToken cancellationToken) =>
    await db.Database.CanConnectAsync(cancellationToken)
        ? Results.Ok(new { status = "Ready", dependencies = new[] { "DeliveryDatabase" } })
        : Results.StatusCode(StatusCodes.Status503ServiceUnavailable));

if (builder.Configuration.GetValue("APPLY_MIGRATIONS", false))
{
    using var scope = app.Services.CreateScope();
    await scope.ServiceProvider.GetRequiredService<DeliveryDbContext>().Database.MigrateAsync();
}

app.Run();
