using ComplaintService.Common;
using ComplaintService.Contracts;

namespace ComplaintService.Services;

public sealed record ValidatedComplaintFile(ComplaintUpload Upload, string ContentType, string Extension, string DisplayName);

public static class ComplaintFileValidator
{
    private static readonly byte[] JpegSignature = [0xFF, 0xD8, 0xFF];
    private static readonly byte[] PngSignature = [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A];

    public static async Task<IReadOnlyList<ValidatedComplaintFile>> ValidateAsync(
        IReadOnlyList<ComplaintUpload> files, CancellationToken cancellationToken)
    {
        if (files.Count > ComplaintRules.MaxFiles)
            throw new ApiException(400, "COMPLAINT_TOO_MANY_FILES", $"Chỉ được đính kèm tối đa {ComplaintRules.MaxFiles} ảnh.");

        var validated = new List<ValidatedComplaintFile>(files.Count);
        foreach (var file in files)
        {
            if (file.Length > ComplaintRules.MaxFileSizeBytes)
                throw new ApiException(400, "COMPLAINT_FILE_TOO_LARGE", "Mỗi ảnh tối đa 5 MB.");
            if (file.Length <= 0) throw InvalidFile();

            var contentType = NormalizeContentType(file.ContentType);
            var (signature, extension) = contentType switch
            {
                "image/jpeg" => (JpegSignature, ".jpg"),
                "image/png" => (PngSignature, ".png"),
                _ => throw InvalidFile()
            };

            await using var stream = file.OpenReadStream();
            if (!await StartsWithAsync(stream, signature, cancellationToken)) throw InvalidFile();
            validated.Add(new ValidatedComplaintFile(file, contentType, extension, SafeDisplayName(file.FileName, extension)));
        }
        return validated;
    }

    /// <summary>Tên gốc chỉ để hiển thị: bỏ đường dẫn, ký tự điều khiển và giới hạn độ dài.</summary>
    public static string SafeDisplayName(string? fileName, string extension)
    {
        var name = (fileName ?? string.Empty).Replace('\\', '/');
        name = name[(name.LastIndexOf('/') + 1)..];
        name = new string(name.Where(c => !char.IsControl(c) && c is not ('"' or '<' or '>')).ToArray()).Trim();
        if (name.Length == 0) return "anh" + extension;
        return name.Length <= 255 ? name : name[^255..];
    }

    private static string NormalizeContentType(string? value)
        => (value ?? string.Empty).Split(';')[0].Trim().ToLowerInvariant();

    private static async Task<bool> StartsWithAsync(Stream stream, byte[] signature, CancellationToken cancellationToken)
    {
        var buffer = new byte[signature.Length];
        var read = await stream.ReadAtLeastAsync(buffer, buffer.Length, throwOnEndOfStream: false, cancellationToken);
        return read == signature.Length && buffer.AsSpan().SequenceEqual(signature);
    }

    private static ApiException InvalidFile() =>
        new(400, "COMPLAINT_INVALID_FILE", "Chỉ nhận ảnh JPEG hoặc PNG hợp lệ.");
}
