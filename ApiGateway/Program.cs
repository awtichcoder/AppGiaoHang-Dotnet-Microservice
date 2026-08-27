var builder = WebApplication.CreateBuilder(args);

builder.Services
    .AddReverseProxy()
    .LoadFromConfig(builder.Configuration.GetSection("ReverseProxy"));

var app = builder.Build();

app.UseHttpsRedirection();

app.MapGet("/health/live", () =>
{
    return Results.Ok(new
    {
        status = "Healthy",
        service = "ApiGateway"
    });
});

app.MapReverseProxy();

app.Run();