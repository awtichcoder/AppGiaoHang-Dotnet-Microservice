using ComplaintService.Common;
using ComplaintService.Contracts;
using ComplaintService.Services;
using Xunit;

namespace ComplaintService.Tests;

public sealed class ComplaintFileValidatorTests
{
    internal static readonly byte[] Jpeg = [0xFF, 0xD8, 0xFF, 0xE0, 1, 2, 3, 4];
    internal static readonly byte[] Png = [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2];

    internal static ComplaintUpload Upload(byte[] bytes, string contentType, string name = "photo.jpg", long? length = null)
        => new(name, contentType, length ?? bytes.Length, () => new MemoryStream(bytes));

    [Fact]
    public async Task Accepts_jpeg_and_png_with_matching_magic_bytes()
    {
        var result = await ComplaintFileValidator.ValidateAsync(
            [Upload(Jpeg, "image/jpeg"), Upload(Png, "IMAGE/PNG", "a.png")], default);
        Assert.Equal([".jpg", ".png"], result.Select(x => x.Extension));
        Assert.Equal(["image/jpeg", "image/png"], result.Select(x => x.ContentType));
    }

    [Theory]
    [InlineData("image/gif")]
    [InlineData("application/pdf")]
    [InlineData("")]
    public async Task Rejects_other_content_types(string contentType)
        => await AssertCode("COMPLAINT_INVALID_FILE", Upload(Jpeg, contentType));

    [Fact]
    public async Task Rejects_content_type_that_does_not_match_the_bytes()
    {
        await AssertCode("COMPLAINT_INVALID_FILE", Upload(Png, "image/jpeg"));
        await AssertCode("COMPLAINT_INVALID_FILE", Upload(Jpeg, "image/png"));
        await AssertCode("COMPLAINT_INVALID_FILE", Upload("<svg onload=alert(1)>"u8.ToArray(), "image/png"));
    }

    [Fact]
    public async Task Rejects_empty_and_truncated_files()
    {
        await AssertCode("COMPLAINT_INVALID_FILE", Upload([], "image/jpeg"));
        await AssertCode("COMPLAINT_INVALID_FILE", Upload([0xFF, 0xD8], "image/jpeg"));
    }

    [Fact]
    public async Task Size_limit_is_inclusive_at_5_mb()
    {
        var atLimit = await ComplaintFileValidator.ValidateAsync(
            [Upload(Jpeg, "image/jpeg", length: ComplaintRules.MaxFileSizeBytes)], default);
        Assert.Single(atLimit);
        await AssertCode("COMPLAINT_FILE_TOO_LARGE", Upload(Jpeg, "image/jpeg", length: ComplaintRules.MaxFileSizeBytes + 1));
    }

    [Fact]
    public async Task Rejects_more_than_three_files()
    {
        var files = Enumerable.Range(0, 4).Select(_ => Upload(Jpeg, "image/jpeg")).ToArray();
        await AssertCode("COMPLAINT_TOO_MANY_FILES", files);
    }

    [Theory]
    [InlineData("../../etc/passwd.jpg", "passwd.jpg")]
    [InlineData("C:\\Users\\me\\photo.png", "photo.png")]
    [InlineData("a\r\nb\".jpg", "ab.jpg")]
    [InlineData("", "anh.jpg")]
    [InlineData(null, "anh.jpg")]
    public void Display_name_never_keeps_path_or_control_characters(string? input, string expected)
        => Assert.Equal(expected, ComplaintFileValidator.SafeDisplayName(input, ".jpg"));

    private static async Task AssertCode(string code, params ComplaintUpload[] files)
    {
        var error = await Assert.ThrowsAsync<ApiException>(() => ComplaintFileValidator.ValidateAsync(files, default));
        Assert.Equal(400, error.StatusCode);
        Assert.Equal(code, error.Code);
    }
}
