using System.Text;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using PromotionService.Common;
using PromotionService.Data;
using PromotionService.Security;
using PromotionService.Services;

var builder = WebApplication.CreateBuilder(args);
var connectionString = builder.Configuration.GetConnectionString("PromotionDatabase")
    ?? throw new InvalidOperationException("Không tìm thấy connection string PromotionDatabase.");
var jwtSecret = builder.Configuration["JWT_SECRET"];
if (string.IsNullOrWhiteSpace(jwtSecret) || jwtSecret.Length < 32)
    throw new InvalidOperationException("JWT_SECRET phải có ít nhất 32 ký tự.");

builder.Services.AddDbContext<PromotionDbContext>(options => options.UseSqlServer(connectionString));
builder.Services.AddScoped<IPromotionService, PromotionManager>();
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
            RoleClaimType = "role",
            NameClaimType = "sub",
            ClockSkew = TimeSpan.FromSeconds(30)
        };
    })
    .AddScheme<InternalKeyAuthenticationOptions, InternalKeyAuthenticationHandler>(
        InternalKeyAuthenticationHandler.SchemeName,
        options => options.ExpectedKey = builder.Configuration["INTERNAL_API_KEY"] ?? string.Empty);
builder.Services.AddAuthorization(options =>
{
    options.AddPolicy("ServiceOrUser", policy => policy
        .AddAuthenticationSchemes(JwtBearerDefaults.AuthenticationScheme, InternalKeyAuthenticationHandler.SchemeName)
        .RequireAuthenticatedUser());
    options.AddPolicy("InternalOnly", policy => policy
        .AddAuthenticationSchemes(InternalKeyAuthenticationHandler.SchemeName)
        .RequireAuthenticatedUser());
});

var app = builder.Build();
if (app.Environment.IsDevelopment()) app.MapOpenApi();
app.UseMiddleware<ExceptionHandlingMiddleware>();
app.UseAuthentication();
app.UseAuthorization();
app.MapControllers();
app.MapGet("/health/live", () => Results.Ok(new { status = "Healthy", service = "PromotionService" })).AllowAnonymous();
app.MapGet("/health/ready", async (PromotionDbContext db, CancellationToken cancellationToken) =>
{
    var canConnect = await db.Database.CanConnectAsync(cancellationToken);
    return canConnect
        ? Results.Ok(new { status = "Ready", dependencies = new[] { "PromotionDatabase" } })
        : Results.StatusCode(StatusCodes.Status503ServiceUnavailable);
}).AllowAnonymous();

if (builder.Configuration.GetValue("APPLY_MIGRATIONS", false))
{
    using var scope = app.Services.CreateScope();
    var db = scope.ServiceProvider.GetRequiredService<PromotionDbContext>();
    await db.Database.MigrateAsync();
}

app.Run();
