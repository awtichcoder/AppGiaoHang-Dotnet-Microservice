using System.Text;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.IdentityModel.Tokens;

namespace LoyaltyService.Common;

public static class AuthenticationExtensions
{
    public static IServiceCollection AddJwtAndInternalAuthentication(this IServiceCollection services, IConfiguration configuration)
    {
        var jwtSecret = configuration["JWT_SECRET"];
        if (string.IsNullOrWhiteSpace(jwtSecret) || jwtSecret.Length < 32)
            throw new InvalidOperationException("JWT_SECRET ph?i c? ?t nh?t 32 k? t?.");

        services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
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
            .AddScheme<InternalApiKeyOptions, InternalApiKeyHandler>(
                InternalApiKeyHandler.SchemeName,
                options => options.ExpectedKey = configuration["INTERNAL_API_KEY"] ?? string.Empty);

        services.AddAuthorization(options =>
        {
            options.AddPolicy("CustomerOnly", policy => policy
                .AddAuthenticationSchemes(JwtBearerDefaults.AuthenticationScheme)
                .RequireRole("CUSTOMER"));

            options.AddPolicy("InternalOnly", policy => policy
                .AddAuthenticationSchemes(InternalApiKeyHandler.SchemeName)
                .RequireAuthenticatedUser());
        });

        return services;
    }
}
