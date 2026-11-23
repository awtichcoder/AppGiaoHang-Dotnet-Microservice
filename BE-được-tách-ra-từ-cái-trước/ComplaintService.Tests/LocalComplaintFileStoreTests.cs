using ComplaintService.Services;
using Microsoft.Extensions.Options;
using Xunit;

namespace ComplaintService.Tests;

public sealed class LocalComplaintFileStoreTests : IDisposable
{
    private readonly string _root = Path.Combine(Path.GetTempPath(), "complaint-store-test-" + Guid.NewGuid().ToString("N"));
    private readonly LocalComplaintFileStore _store;

    public LocalComplaintFileStoreTests()
        => _store = new LocalComplaintFileStore(Options.Create(new ComplaintOptions { UploadDirectory = _root }));

    public void Dispose()
    {
        if (Directory.Exists(_root)) Directory.Delete(_root, recursive: true);
    }

    [Fact]
    public async Task Saves_reads_and_deletes_with_generated_name()
    {
        var name = await _store.SaveAsync(new MemoryStream([1, 2, 3]), ".png", default);
        Assert.Matches("^[0-9a-f]{32}\\.png$", name);
        await using (var stream = _store.OpenRead(name))
        {
            Assert.NotNull(stream);
            Assert.Equal(3, stream.Length);
        }
        _store.Delete(name);
        Assert.Null(_store.OpenRead(name));
        _store.Delete(name); // xóa lại không lỗi, để lần dọn sau chạy tiếp được
    }

    [Theory]
    [InlineData("../secret.jpg")]
    [InlineData("..\\secret.jpg")]
    [InlineData("C:\\secret.jpg")]
    [InlineData("abc.jpg")]
    [InlineData("0123456789abcdef0123456789abcdef.exe")]
    public void Refuses_names_outside_the_generated_pattern(string name)
    {
        Assert.Throws<InvalidOperationException>(() => _store.OpenRead(name));
        Assert.Throws<InvalidOperationException>(() => _store.Delete(name));
    }
}
