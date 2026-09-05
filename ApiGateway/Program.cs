using Microsoft.Extensions.Primitives;
using Yarp.ReverseProxy.Configuration;

const string CorrelationIdHeader = "X-Correlation-ID";
const string InternalApiKeyHeader = "X-Internal-Key";

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddHttpClient("GatewayHealth", client =>
{
    client.Timeout = TimeSpan.FromSeconds(builder.Configuration.GetValue("Gateway:HealthTimeoutSeconds", 5));
});

builder.Services
    .AddReverseProxy()
    .LoadFromConfig(builder.Configuration.GetSection("ReverseProxy"));

var app = builder.Build();

app.Use(async (context, next) =>
{
    var correlationId = GetOrCreateCorrelationId(context.Request);
    context.TraceIdentifier = correlationId;
    context.Request.Headers[CorrelationIdHeader] = correlationId;
    context.Request.Headers.Remove(InternalApiKeyHeader);

    context.Response.OnStarting(() =>
    {
        context.Response.Headers[CorrelationIdHeader] = correlationId;
        return Task.CompletedTask;
    });

    await next();
});

app.Use(async (context, next) =>
{
    if (!ShouldWrapEmptyErrorResponse(context.Request))
    {
        await next();
        return;
    }

    var originalBody = context.Response.Body;
    await using var buffer = new MemoryStream();
    context.Response.Body = buffer;

    try
    {
        await next();
    }
    finally
    {
        context.Response.Body = originalBody;
    }

    if (context.Response.StatusCode >= StatusCodes.Status400BadRequest && buffer.Length == 0)
    {
        context.Response.Headers.Remove("Content-Length");
        await WriteGatewayErrorAsync(
            context,
            context.Response.StatusCode,
            GetGatewayErrorCode(context.Response.StatusCode),
            GetGatewayErrorMessage(context.Response.StatusCode));
        return;
    }

    buffer.Position = 0;
    await buffer.CopyToAsync(originalBody, context.RequestAborted);
});
app.UseStatusCodePages(async statusCodeContext =>
{
    var context = statusCodeContext.HttpContext;
    if (context.Response.HasStarted) return;

    await WriteGatewayErrorAsync(
        context,
        context.Response.StatusCode,
        GetGatewayErrorCode(context.Response.StatusCode),
        GetGatewayErrorMessage(context.Response.StatusCode));
});

if (builder.Configuration.GetValue("USE_HTTPS_REDIRECTION", true))
{
    app.UseHttpsRedirection();
}

app.Use(async (context, next) =>
{
    if (IsInternalRoute(context.Request))
    {
        await WriteGatewayErrorAsync(
            context,
            StatusCodes.Status404NotFound,
            "INTERNAL_ENDPOINT_NOT_EXPOSED",
            "Endpoint này là API nội bộ và không được public qua ApiGateway.");
        return;
    }

    await next();
});

app.MapGet("/health/live", () => Results.Ok(new
{
    status = "Healthy",
    service = "ApiGateway"
}));

app.MapGet("/health/ready", CheckReadinessAsync);

app.MapReverseProxy();

app.MapFallback(async context =>
{
    await WriteGatewayErrorAsync(
        context,
        StatusCodes.Status404NotFound,
        "GATEWAY_ROUTE_NOT_FOUND",
        "Endpoint không tồn tại hoặc không được public qua ApiGateway.");
});

app.Run();

static string GetOrCreateCorrelationId(HttpRequest request)
{
    if (request.Headers.TryGetValue(CorrelationIdHeader, out StringValues values)
        && values.Count > 0
        && !string.IsNullOrWhiteSpace(values[0]))
    {
        return values[0]!;
    }

    return Guid.NewGuid().ToString("N");
}

static bool ShouldWrapEmptyErrorResponse(HttpRequest request)
{
    return request.Path.StartsWithSegments("/api") || request.Path.StartsWithSegments("/health");
}

static string GetGatewayErrorCode(int statusCode)
{
    return statusCode switch
    {
        StatusCodes.Status404NotFound => "GATEWAY_ROUTE_NOT_FOUND",
        StatusCodes.Status502BadGateway => "UPSTREAM_BAD_GATEWAY",
        StatusCodes.Status503ServiceUnavailable => "UPSTREAM_UNAVAILABLE",
        StatusCodes.Status504GatewayTimeout => "UPSTREAM_TIMEOUT",
        _ => "GATEWAY_ERROR"
    };
}

static string GetGatewayErrorMessage(int statusCode)
{
    return statusCode switch
    {
        StatusCodes.Status404NotFound => "Endpoint không tồn tại hoặc không được public qua ApiGateway.",
        StatusCodes.Status502BadGateway => "Service phía sau trả lỗi hoặc không phản hồi hợp lệ.",
        StatusCodes.Status503ServiceUnavailable => "Service phía sau hiện không sẵn sàng.",
        StatusCodes.Status504GatewayTimeout => "Service phía sau phản hồi quá thời gian cho phép.",
        _ => "ApiGateway không xử lý được request."
    };
}

static bool IsInternalRoute(HttpRequest request)
{
    var path = request.Path.Value?.TrimEnd('/').ToLowerInvariant() ?? string.Empty;
    var method = request.Method.ToUpperInvariant();

    if (path.StartsWith("/api/driver/nearby", StringComparison.Ordinal)) return true;
    if (path == "/api/map/route/quote") return true;
    if (path == "/api/delivery/quote") return true;
    if (path == "/api/delivery/assign") return true;
    if (method == HttpMethods.Post && path.StartsWith("/api/delivery/", StringComparison.Ordinal) && path.EndsWith("/cancel", StringComparison.Ordinal)) return true;
    if (method == HttpMethods.Patch && path.StartsWith("/api/order/", StringComparison.Ordinal) && path.EndsWith("/status", StringComparison.Ordinal)) return true;

    if (path == "/api/promotion/validate") return true;
    if (path == "/api/promotion/reserve") return true;
    if (path.StartsWith("/api/promotion/reservations/", StringComparison.Ordinal)
        && (path.EndsWith("/commit", StringComparison.Ordinal) || path.EndsWith("/release", StringComparison.Ordinal))) return true;

    if (path == "/api/loyalty/reserve") return true;
    if (path == "/api/loyalty/earn") return true;
    if (path.StartsWith("/api/loyalty/reservations/", StringComparison.Ordinal)
        && (path.EndsWith("/commit", StringComparison.Ordinal) || path.EndsWith("/release", StringComparison.Ordinal))) return true;

    return false;
}

static async Task<IResult> CheckReadinessAsync(
    IProxyConfigProvider proxyConfigProvider,
    IHttpClientFactory httpClientFactory,
    CancellationToken cancellationToken)
{
    var requiredClusters = new[] { "identity", "delivery", "driver", "loyalty", "map", "order", "promotion" };
    var proxyConfig = proxyConfigProvider.GetConfig();
    var client = httpClientFactory.CreateClient("GatewayHealth");
    var dependencies = new Dictionary<string, string>();
    var allReady = true;

    foreach (var clusterId in requiredClusters)
    {
        var cluster = proxyConfig.Clusters.FirstOrDefault(x =>
            string.Equals(x.ClusterId, clusterId, StringComparison.OrdinalIgnoreCase));
        var address = cluster?.Destinations?.Values.FirstOrDefault()?.Address;
        if (string.IsNullOrWhiteSpace(address))
        {
            dependencies[clusterId] = "MissingDestination";
            allReady = false;
            continue;
        }

        try
        {
            var healthUri = new Uri(new Uri(address), "health/ready");
            using var response = await client.GetAsync(healthUri, cancellationToken);
            dependencies[clusterId] = response.IsSuccessStatusCode ? "Ready" : $"HTTP {(int)response.StatusCode}";
            allReady &= response.IsSuccessStatusCode;
        }
        catch (Exception exception) when (exception is HttpRequestException or TaskCanceledException)
        {
            dependencies[clusterId] = "Unavailable";
            allReady = false;
        }
    }

    return Results.Json(new
    {
        status = allReady ? "Ready" : "Degraded",
        dependencies
    }, statusCode: allReady ? StatusCodes.Status200OK : StatusCodes.Status503ServiceUnavailable);
}

static Task WriteGatewayErrorAsync(HttpContext context, int statusCode, string code, string message, object? details = null)
{
    context.Response.StatusCode = statusCode;
    context.Response.ContentType = "application/json";
    return context.Response.WriteAsJsonAsync(new
    {
        code,
        message,
        details
    });
}
