using ComplaintService.Clients;
using ComplaintService.Common;
using ComplaintService.Data;
using ComplaintService.Services;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);
var connectionString = builder.Configuration.GetConnectionString("ComplaintDatabase")
    ?? throw new InvalidOperationException("Không tìm thấy connection string ComplaintDatabase.");
var deliveryServiceUrl = builder.Configuration["ServiceUrls:DeliveryService"]
    ?? throw new InvalidOperationException("Không tìm thấy ServiceUrls:DeliveryService.");

// Ảnh lưu tạm trên đĩa. Chỉ môi trường Development mới được dùng thư mục tạm mặc định.
var uploadDirectory = builder.Configuration["COMPLAINT_UPLOAD_DIR"];
if (string.IsNullOrWhiteSpace(uploadDirectory))
{
    uploadDirectory = builder.Environment.IsDevelopment()
        ? Path.Combine(Path.GetTempPath(), "deliveryapp-complaint-uploads")
        : throw new InvalidOperationException("Chưa cấu hình COMPLAINT_UPLOAD_DIR.");
}

builder.Services.AddDbContext<ComplaintDbContext>(options => options.UseSqlServer(connectionString));
builder.Services.AddJwtAuthentication(builder.Configuration);
builder.Services.AddSingleton(TimeProvider.System);
builder.Services.Configure<ComplaintOptions>(builder.Configuration.GetSection("Complaint"));
builder.Services.PostConfigure<ComplaintOptions>(options => options.UploadDirectory = uploadDirectory);
builder.Services.AddSingleton<IComplaintFileStore, LocalComplaintFileStore>();
builder.Services.AddScoped<IComplaintManager, ComplaintManager>();
builder.Services.AddScoped<ComplaintAttachmentCleaner>();
builder.Services.AddHostedService<ComplaintCleanupWorker>();
builder.Services.AddHttpClient<IDeliveryContextClient, DeliveryContextClient>(client =>
{
    client.BaseAddress = new Uri(deliveryServiceUrl.TrimEnd('/') + "/");
    client.Timeout = TimeSpan.FromSeconds(10);
});
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

app.UseMiddleware<ApiExceptionMiddleware>();
if (app.Environment.IsDevelopment()) app.MapOpenApi();
app.UseStatusCodePages(async context =>
{
    var response = context.HttpContext.Response;
    if (response.StatusCode is 401 or 403)
        await response.WriteAsJsonAsync(new
        {
            code = response.StatusCode == 401 ? "UNAUTHORIZED" : "FORBIDDEN",
            message = response.StatusCode == 401 ? "Cần đăng nhập hoặc thông tin xác thực không hợp lệ." : "Bạn không có quyền thực hiện thao tác này.",
            details = (object?)null
        });
});
app.UseAuthentication();
app.UseAuthorization();
app.MapControllers();
app.MapGet("/health/live", () => Results.Ok(new { status = "Healthy", service = "ComplaintService" }));
app.MapGet("/health/ready", async (ComplaintDbContext db, CancellationToken cancellationToken) =>
    await db.Database.CanConnectAsync(cancellationToken)
        ? Results.Ok(new { status = "Ready", dependencies = new[] { "ComplaintDatabase" } })
        : Results.StatusCode(StatusCodes.Status503ServiceUnavailable));

if (builder.Configuration.GetValue("APPLY_MIGRATIONS", false))
{
    using var scope = app.Services.CreateScope();
    await scope.ServiceProvider.GetRequiredService<ComplaintDbContext>().Database.MigrateAsync();
}

app.Run();
