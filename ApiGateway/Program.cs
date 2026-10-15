var builder = WebApplication.CreateBuilder(args);
builder.Services.AddHttpClient("Readiness", client => client.Timeout = TimeSpan.FromSeconds(3));

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
app.MapGet("/health/ready", async (IHttpClientFactory clients, IConfiguration configuration, CancellationToken cancellationToken) =>
{
    var checks = configuration.GetSection("ReverseProxy:Clusters").GetChildren().Select(async cluster =>
    {
        var destinations = cluster.GetSection("Destinations").GetChildren().ToArray();
        var ready = false;
        foreach (var destination in destinations)
        {
            try
            {
                var url = new Uri(new Uri(destination["Address"]!), "health/ready");
                using var response = await clients.CreateClient("Readiness").GetAsync(url, cancellationToken);
                ready |= response.IsSuccessStatusCode;
            }
            catch (Exception e) when (e is HttpRequestException or OperationCanceledException) { }
        }
        return new { service = cluster.Key, ready };
    });
    var dependencies = await Task.WhenAll(checks);
    var ready = dependencies.Length > 0 && dependencies.All(d => d.ready);
    return Results.Json(new { status = ready ? "Ready" : "NotReady", dependencies }, statusCode: ready ? 200 : 503);
});

app.MapReverseProxy();

app.Run();
