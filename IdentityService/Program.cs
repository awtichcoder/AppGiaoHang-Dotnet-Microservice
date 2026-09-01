using AspNetCore.Identity.Mongo;
using AspNetCore.Identity.Mongo.Model;
using IdentityService.Entities;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.IdentityModel.Tokens;
using System.Text;

// Tải biến môi trường từ file .env
DotNetEnv.Env.Load();

var builder = WebApplication.CreateBuilder(args);
builder.Configuration.AddEnvironmentVariables();

builder.Services.AddControllers();
builder.Services.AddOpenApi();

// Cấu hình MongoDB Identity
builder.Services.AddIdentityMongoDbProvider<ApplicationUser, MongoRole>(identity =>
{
    identity.Password.RequireDigit = false;
    identity.Password.RequiredLength = 6;
    identity.Password.RequireLowercase = false;
    identity.Password.RequireNonAlphanumeric = false;
    identity.Password.RequireUppercase = false;
    identity.User.RequireUniqueEmail = true;
},
mongo =>
{
    // Đọc từ appsettings (ConnectionStrings) hoặc đọc trực tiếp từ biến môi trường (IdentityDB) trong file .env
    mongo.ConnectionString = builder.Configuration.GetConnectionString("IdentityDB") ?? builder.Configuration["IdentityDB"];
});

// Cấu hình kiểm tra JWT Token
builder.Services.AddAuthentication(options =>
{
    options.DefaultAuthenticateScheme = JwtBearerDefaults.AuthenticationScheme;
    options.DefaultChallengeScheme = JwtBearerDefaults.AuthenticationScheme;
})
.AddJwtBearer(options =>
{
    options.SaveToken = true;
    options.RequireHttpsMetadata = false;
    options.TokenValidationParameters = new TokenValidationParameters()
    {
        ValidateIssuer = true,
        ValidateAudience = true,
        ValidAudience = builder.Configuration["JWT_AUDIENCE"] ?? "DeliveryAppClient",
        ValidIssuer = builder.Configuration["JWT_ISSUER"] ?? "DeliveryApp",
        IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(builder.Configuration["JWT_SECRET"] ?? "SuperSecretKey1234567890_PleaseChangeMe"))
    };
});

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
}

app.UseAuthentication(); 
app.UseAuthorization();

app.MapControllers();

app.MapGet("/health/live", () =>
{
    return Results.Ok(new { status = "Healthy" });
});

app.MapGet("/health/ready", () =>
{
    return Results.Ok(new { status = "Ready", dependencies = Array.Empty<object>() });
});

// kiểm tra connection MongoDB
app.MapGet("/test-db", (IConfiguration config) => {
    try {
        var client = new MongoDB.Driver.MongoClient(config.GetConnectionString("IdentityDb"));
        var db = client.GetDatabase("DeliveryApp_Identity");
        var ping = new MongoDB.Bson.BsonDocument("ping", 1);
        db.RunCommand<MongoDB.Bson.BsonDocument>(ping);
        
        return Results.Ok(new { message = "✅ Kết nối MongoDB Thành Công" });
    } catch(Exception ex) {
        return Results.Problem(detail: ex.Message, title: "❌ Kết nối MongoDB THẤT BẠI");
    }
});

app.Run();