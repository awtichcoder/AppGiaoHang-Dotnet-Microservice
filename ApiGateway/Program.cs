var builder = WebApplication.CreateBuilder(args);

builder.Services
    .AddReverseProxy()
    .LoadFromConfig(builder.Configuration.GetSection("ReverseProxy"));

var app = builder.Build();

if (builder.Configuration.GetValue("USE_HTTPS_REDIRECTION", true))
{
    app.UseHttpsRedirection();
}

app.Use(async (context, next) =>
{
    const string header = "X-Correlation-ID";
    var correlationId = context.Request.Headers[header].FirstOrDefault();
    if (string.IsNullOrWhiteSpace(correlationId)) correlationId = Guid.NewGuid().ToString("N");
    context.Request.Headers[header] = correlationId;
    context.Response.OnStarting(() =>
    {
        context.Response.Headers[header] = correlationId;
        return Task.CompletedTask;
    });
    await next();
});

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
