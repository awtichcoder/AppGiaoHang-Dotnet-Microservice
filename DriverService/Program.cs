using Microsoft.AspNetCore.Authentication.JwtBearer;
using DriverService.Common;
using DriverService.Data;
using DriverService.Security;
using DriverService.Services;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using System.Text;

var builder = WebApplication.CreateBuilder(args);
var connectionString = builder.Configuration.GetConnectionString("DriverDatabase")
    ?? throw new InvalidOperationException("Không tìm thấy connection string DriverDatabase.");
var jwtSecret = builder.Configuration["JWT_SECRET"];
if (string.IsNullOrWhiteSpace(jwtSecret) || jwtSecret.Length < 32)
    throw new InvalidOperationException("JWT_SECRET phải có ít nhất 32 ký tự.");

builder.Services.AddDbContext<DriverDbContext>(options => options.UseSqlServer(connectionString));
builder.Services.AddScoped<IDriverManager, DriverManager>();
builder.Services.AddControllers()
    .AddJsonOptions(options => options.JsonSerializerOptions.Converters.Add(new System.Text.Json.Serialization.JsonStringEnumConverter()))
    .ConfigureApiBehaviorOptions(options =>
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
builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
.AddJwtBearer(options =>
{
    options.MapInboundClaims = false;
    options.TokenValidationParameters = new TokenValidationParameters
    {
        ValidateIssuer = true,
        ValidIssuer = "deliveryapp-identity",
        ValidateAudience = false,
        ValidateLifetime = true,
        ValidateIssuerSigningKey = true,
        IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwtSecret)),
        RoleClaimType = "role",
        NameClaimType = "sub",
        ClockSkew = TimeSpan.FromSeconds(30)
    };
})
.AddScheme<InternalKeyAuthenticationOptions, InternalKeyAuthenticationHandler>(
    InternalKeyAuthenticationHandler.SchemeName,
    options => options.ExpectedKey = builder.Configuration["INTERNAL_API_KEY"] ?? string.Empty);
builder.Services.AddAuthorization(options => options.AddPolicy("InternalOnly", policy => policy
    .AddAuthenticationSchemes(InternalKeyAuthenticationHandler.SchemeName)
    .RequireAuthenticatedUser()));

var app = builder.Build();
app.UseMiddleware<ApiExceptionMiddleware>();
if (app.Environment.IsDevelopment()) app.MapOpenApi();
app.UseAuthentication();
app.UseAuthorization();
app.MapControllers();
app.MapGet("/health/live", () => Results.Ok(new { status = "Healthy", service = "DriverService" })).AllowAnonymous();
app.MapGet("/health/ready", async (DriverDbContext db, CancellationToken cancellationToken) =>
    await db.Database.CanConnectAsync(cancellationToken)
        ? Results.Ok(new { status = "Ready", dependencies = new[] { "DriverDatabase" } })
        : Results.StatusCode(StatusCodes.Status503ServiceUnavailable)).AllowAnonymous();

if (builder.Configuration.GetValue("APPLY_MIGRATIONS", false))
{
    using var scope = app.Services.CreateScope();
    await scope.ServiceProvider.GetRequiredService<DriverDbContext>().Database.MigrateAsync();
}

app.Run();
