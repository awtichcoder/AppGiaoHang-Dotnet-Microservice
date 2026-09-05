using System.Text;
using LoyaltyService.Common;
using LoyaltyService.Data;
using LoyaltyService.Security;
using LoyaltyService.Services;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;

var builder = WebApplication.CreateBuilder(args);
var connectionString = builder.Configuration.GetConnectionString("LoyaltyDatabase")
    ?? throw new InvalidOperationException("Không tìm thấy connection string LoyaltyDatabase.");
var jwtSecret = builder.Configuration["JWT_SECRET"];
if (string.IsNullOrWhiteSpace(jwtSecret) || jwtSecret.Length < 32)
    throw new InvalidOperationException("JWT_SECRET phải có ít nhất 32 ký tự.");

builder.Services.AddDbContext<LoyaltyDbContext>(options => options.UseSqlServer(connectionString));
builder.Services.AddScoped<ILoyaltyManager, LoyaltyManager>();
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
            NameClaimType = "sub",
            RoleClaimType = "role",
            ClockSkew = TimeSpan.FromSeconds(30)
        };
        options.Events = new JwtBearerEvents
        {
            OnChallenge = async context =>
            {
                context.HandleResponse();
                context.Response.StatusCode = 401;
                await context.Response.WriteAsJsonAsync(new ApiError("UNAUTHORIZED", "Thiếu hoặc sai access token."));
            },
            OnForbidden = async context =>
            {
                context.Response.StatusCode = 403;
                await context.Response.WriteAsJsonAsync(new ApiError("FORBIDDEN", "Bạn không có quyền thực hiện thao tác này."));
            }
        };
    })
    .AddScheme<InternalKeyAuthenticationOptions, InternalKeyAuthenticationHandler>(
        InternalKeyAuthenticationHandler.SchemeName,
        options => options.ExpectedKey = builder.Configuration["INTERNAL_API_KEY"] ?? string.Empty);
builder.Services.AddAuthorization(options =>
{
    options.AddPolicy("InternalOnly", policy => policy
        .AddAuthenticationSchemes(InternalKeyAuthenticationHandler.SchemeName)
        .RequireAuthenticatedUser());
});

var app = builder.Build();
app.UseMiddleware<ApiExceptionMiddleware>();
if (app.Environment.IsDevelopment()) app.MapOpenApi();
app.UseAuthentication();
app.UseAuthorization();
app.MapControllers();
app.MapGet("/health/live", () => Results.Ok(new { status = "Healthy", service = "LoyaltyService" })).AllowAnonymous();
app.MapGet("/health/ready", async (LoyaltyDbContext db, CancellationToken cancellationToken) =>
    await db.Database.CanConnectAsync(cancellationToken)
        ? Results.Ok(new { status = "Ready", dependencies = new[] { "LoyaltyDatabase" } })
        : Results.StatusCode(StatusCodes.Status503ServiceUnavailable)).AllowAnonymous();

if (builder.Configuration.GetValue("APPLY_MIGRATIONS", false))
{
    using var scope = app.Services.CreateScope();
    await scope.ServiceProvider.GetRequiredService<LoyaltyDbContext>().Database.MigrateAsync();
}

app.Run();
