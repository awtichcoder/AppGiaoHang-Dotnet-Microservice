var builder = WebApplication.CreateBuilder(args);

builder.Services
    .AddReverseProxy()
    .LoadFromConfig(builder.Configuration.GetSection("ReverseProxy"));

var app = builder.Build();

if (builder.Configuration.GetValue("USE_HTTPS_REDIRECTION", true))
{
    app.UseHttpsRedirection();
}

app.MapGet("/health/live", () =>
{
    return Results.Ok(new
    {
        status = "Healthy",
        service = "ApiGateway"
    });
});
app.MapGet("/health/ready", () => Results.Ok(new { status = "Ready", dependencies = new[] { "ReverseProxy" } }));

app.MapReverseProxy();

app.Run();
