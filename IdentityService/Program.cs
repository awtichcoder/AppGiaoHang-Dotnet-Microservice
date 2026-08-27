var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();
builder.Services.AddOpenApi();

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
}

app.UseAuthorization();

app.MapControllers();

app.MapGet("/health/live", () =>
{
    return Results.Ok(new
    {
        status = "Healthy"
    });
});

app.MapGet("/health/ready", () =>
{
    return Results.Ok(new
    {
        status = "Ready",
        dependencies = Array.Empty<object>()
    });
});

app.Run();