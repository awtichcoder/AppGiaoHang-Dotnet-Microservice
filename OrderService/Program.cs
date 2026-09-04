using OrderService.Clients;
using OrderService.Common;
using OrderService.Data;
using OrderService.Services;
using Microsoft.EntityFrameworkCore;
using OrderServiceImplementation = OrderService.Services.OrderService;

var builder = WebApplication.CreateBuilder(args);
var connectionString =
    builder.Configuration.GetConnectionString("OrderDatabase")
    ?? throw new InvalidOperationException(
        "Không tìm thấy connection string OrderDatabase.");

builder.Services.AddDbContext<OrderDbContext>(options =>
    options.UseSqlServer(connectionString));
builder.Services.AddJwtAuthentication(builder.Configuration);
builder.Services.AddScoped<IOrderService, OrderServiceImplementation>();
builder.Services.AddScoped<IOrderPricingDependencies, OrderPricingDependencies>();
builder.Services.AddHostedService<OrderOutboxWorker>();
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

var deliveryServiceUrl =
    builder.Configuration["ServiceUrls:DeliveryService"]
    ?? throw new InvalidOperationException(
        "Không tìm thấy ServiceUrls:DeliveryService.");

builder.Services.AddHttpClient<
    IDeliveryQuoteClient,
    DeliveryQuoteClient>(client =>
{
    client.BaseAddress = new Uri(
        deliveryServiceUrl.TrimEnd('/') + "/");

    client.Timeout = TimeSpan.FromSeconds(10);
});

builder.Services.AddHttpClient<IDeliveryCommandClient, DeliveryCommandClient>(client =>
{
    client.BaseAddress = new Uri(deliveryServiceUrl.TrimEnd('/') + "/");
    client.Timeout = TimeSpan.FromSeconds(10);
});

var mapServiceUrl = builder.Configuration["ServiceUrls:MapService"]
    ?? throw new InvalidOperationException("Không tìm thấy ServiceUrls:MapService.");
var promotionServiceUrl = builder.Configuration["ServiceUrls:PromotionService"]
    ?? throw new InvalidOperationException("Không tìm thấy ServiceUrls:PromotionService.");
builder.Services.AddHttpClient("MapService", client =>
{
    client.BaseAddress = new Uri(mapServiceUrl.TrimEnd('/') + "/");
    client.Timeout = TimeSpan.FromSeconds(10);
});
builder.Services.AddHttpClient("PromotionService", client =>
{
    client.BaseAddress = new Uri(promotionServiceUrl.TrimEnd('/') + "/");
    client.Timeout = TimeSpan.FromSeconds(10);
});

var app = builder.Build();

app.UseMiddleware<ApiExceptionMiddleware>();
if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
}

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();
app.MapGet("/health/live", () =>
{
    return Results.Ok(new
    {
        status = "Healthy",
        service = "OrderService"
    });
});
app.MapGet("/health/ready", async (OrderDbContext db, CancellationToken cancellationToken) =>
{
    var canConnect = await db.Database.CanConnectAsync(cancellationToken);
    return canConnect
        ? Results.Ok(new { status = "Ready", dependencies = new[] { "OrderDatabase" } })
        : Results.StatusCode(StatusCodes.Status503ServiceUnavailable);
});

if (builder.Configuration.GetValue("APPLY_MIGRATIONS", false))
{
    using var scope = app.Services.CreateScope();
    var db = scope.ServiceProvider.GetRequiredService<OrderDbContext>();
    await db.Database.MigrateAsync();
}

app.Run();
