using ComplaintService.Common;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Http.Features;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.Abstractions;
using Microsoft.AspNetCore.Mvc.Filters;
using Microsoft.AspNetCore.Routing;
using Xunit;

namespace ComplaintService.Tests;

public sealed class ComplaintFormGuardTests
{
    [Fact]
    public async Task Oversized_body_returns_413_with_a_clear_code()
    {
        var (context, nextCalled) = await Run(new BadHttpRequestException("too large", StatusCodes.Status413PayloadTooLarge));
        var result = Assert.IsType<ObjectResult>(context.Result);
        Assert.Equal(413, result.StatusCode);
        Assert.Contains("COMPLAINT_FILE_TOO_LARGE", result.Value!.ToString());
        Assert.False(nextCalled);
    }

    [Fact]
    public async Task Malformed_multipart_returns_400()
    {
        var (context, nextCalled) = await Run(new InvalidDataException("bad multipart"));
        var result = Assert.IsType<ObjectResult>(context.Result);
        Assert.Equal(400, result.StatusCode);
        Assert.Contains("VALIDATION_ERROR", result.Value!.ToString());
        Assert.False(nextCalled);
    }

    [Fact]
    public async Task Valid_form_continues_to_the_action()
    {
        var (context, nextCalled) = await Run(null);
        Assert.Null(context.Result);
        Assert.True(nextCalled);
    }

    private static async Task<(ResourceExecutingContext Context, bool NextCalled)> Run(Exception? readError)
    {
        var http = new DefaultHttpContext();
        http.Features.Set<IFormFeature>(new StubFormFeature(readError));
        var action = new ActionContext(http, new RouteData(), new ActionDescriptor());
        var context = new ResourceExecutingContext(action, [], []);
        var nextCalled = false;
        await new ComplaintFormGuardAttribute().OnResourceExecutionAsync(context, () =>
        {
            nextCalled = true;
            return Task.FromResult(new ResourceExecutedContext(action, []));
        });
        return (context, nextCalled);
    }

    private sealed class StubFormFeature(Exception? readError) : IFormFeature
    {
        public bool HasFormContentType => true;
        public IFormCollection? Form { get; set; }
        public IFormCollection ReadForm() => throw new NotSupportedException();
        public Task<IFormCollection> ReadFormAsync(CancellationToken cancellationToken)
            => readError is null ? Task.FromResult<IFormCollection>(FormCollection.Empty) : Task.FromException<IFormCollection>(readError);
    }
}
