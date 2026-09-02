using System.Text;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using PromotionService.Common;
using PromotionService.Data;
using PromotionService.Security;
using PromotionService.Services;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();
builder.Services.AddOpenApi();

builder.Services.AddDbContext<PromotionDbContext>(options =>
    options.UseSqlServer(builder.Configuration.GetConnectionString("PromotionDb")));

builder.Services.AddScoped<IPromotionService, PromotionManager>();

var jwtSecret = builder.Configuration["Jwt:Secret"];
var jwtIssuer = builder.Configuration["Jwt:Issuer"] ?? "deliveryapp-identity";
var internalApiKey = builder.Configuration["InternalApiKey"];

builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = true,
            ValidIssuer = jwtIssuer,
            // Không có claim aud trong hợp đồng JWT của hệ thống -> phải tắt ValidateAudience,
            // nếu không token đúng chữ ký vẫn bị 401 mà không rõ lý do.
            ValidateAudience = false,
            ValidateLifetime = true,
            ValidateIssuerSigningKey = true,
            IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(
                string.IsNullOrEmpty(jwtSecret) ? "dev-only-not-a-real-secret-change-me-please" : jwtSecret)),
            ClockSkew = TimeSpan.FromSeconds(30)
        };
    })
    .AddScheme<InternalKeyAuthenticationOptions, InternalKeyAuthenticationHandler>(
        InternalKeyAuthenticationHandler.SchemeName, options =>
        {
            options.ExpectedKey = internalApiKey ?? string.Empty;
        });

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

if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
}

app.UseMiddleware<ExceptionHandlingMiddleware>();

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

app.MapGet("/health/live", () => Results.Ok(new { status = "ok" }))
    .AllowAnonymous();

app.MapGet("/health/ready", async (PromotionDbContext db) =>
{
    var canConnect = await db.Database.CanConnectAsync();
    return canConnect
        ? Results.Ok(new { status = "ready", dependencies = new { database = "ok" } })
        : Results.Json(new { status = "not_ready", dependencies = new { database = "down" } }, statusCode: 503);
})
    .AllowAnonymous();

app.Run();
