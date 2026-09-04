using System.Text;
using MapService.Common;
using MapService.Security;
using MapService.Services;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.IdentityModel.Tokens;

var builder = WebApplication.CreateBuilder(args);
var jwtSecret = builder.Configuration["JWT_SECRET"];
if (string.IsNullOrWhiteSpace(jwtSecret) || jwtSecret.Length < 32)
    throw new InvalidOperationException("JWT_SECRET phải có ít nhất 32 ký tự.");

builder.Services.AddControllers();
builder.Services.AddOpenApi();
builder.Services.AddSingleton<IDistanceCalculator, HaversineDistanceCalculator>();
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
});

var app = builder.Build();
if (app.Environment.IsDevelopment()) app.MapOpenApi();
app.UseMiddleware<ExceptionHandlingMiddleware>();
app.UseAuthentication();
app.UseAuthorization();
app.MapControllers();
app.MapGet("/health/live", () => Results.Ok(new { status = "Healthy", service = "MapService" })).AllowAnonymous();
app.MapGet("/health/ready", () => Results.Ok(new { status = "Ready", dependencies = Array.Empty<object>() })).AllowAnonymous();
app.Run();
