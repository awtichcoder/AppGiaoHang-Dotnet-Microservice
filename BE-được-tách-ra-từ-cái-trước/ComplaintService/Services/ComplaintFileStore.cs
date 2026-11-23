using System.Text.RegularExpressions;
using Microsoft.Extensions.Options;

namespace ComplaintService.Services;

public interface IComplaintFileStore
{
    /// <summary>Lưu file với tên do server sinh và trả về tên đó.</summary>
    Task<string> SaveAsync(Stream content, string extension, CancellationToken cancellationToken);

    /// <summary>Trả null nếu file không còn trên đĩa.</summary>
    Stream? OpenRead(string storedFileName);

    void Delete(string storedFileName);
}

/// <summary>Lưu TẠM trên đĩa cục bộ (COMPLAINT_UPLOAD_DIR), chưa phải lưu trữ lâu dài.</summary>
public sealed partial class LocalComplaintFileStore : IComplaintFileStore
{
    private readonly string _root;

    public LocalComplaintFileStore(IOptions<ComplaintOptions> options)
    {
        if (string.IsNullOrWhiteSpace(options.Value.UploadDirectory))
            throw new InvalidOperationException("Chưa cấu hình COMPLAINT_UPLOAD_DIR.");
        _root = Path.GetFullPath(options.Value.UploadDirectory);
        Directory.CreateDirectory(_root);
    }

    public async Task<string> SaveAsync(Stream content, string extension, CancellationToken cancellationToken)
    {
        var storedFileName = Guid.NewGuid().ToString("N") + extension;
        var path = Resolve(storedFileName);
        try
        {
            await using var output = new FileStream(path, FileMode.CreateNew, FileAccess.Write, FileShare.None, 81920, useAsync: true);
            await content.CopyToAsync(output, cancellationToken);
        }
        catch
        {
            File.Delete(path);
            throw;
        }
        return storedFileName;
    }

    public Stream? OpenRead(string storedFileName)
    {
        try
        {
            return new FileStream(Resolve(storedFileName), FileMode.Open, FileAccess.Read, FileShare.Read, 81920, useAsync: true);
        }
        catch (Exception exception) when (exception is FileNotFoundException or DirectoryNotFoundException)
        {
            return null;
        }
    }

    public void Delete(string storedFileName) => File.Delete(Resolve(storedFileName));

    // Tên lưu trong DB chỉ có dạng GUID + đuôi ảnh, nên không thể trỏ ra ngoài thư mục upload.
    private string Resolve(string storedFileName)
    {
        if (!StoredNamePattern().IsMatch(storedFileName))
            throw new InvalidOperationException("Tên file lưu trữ không hợp lệ.");
        return Path.Combine(_root, storedFileName);
    }

    [GeneratedRegex("^[0-9a-f]{32}\\.(jpg|png)$")]
    private static partial Regex StoredNamePattern();
}
