using ComplaintService.Clients;
using ComplaintService.Common;
using ComplaintService.Contracts;
using ComplaintService.Data;
using ComplaintService.Entities;
using ComplaintService.Services;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging.Abstractions;
using Microsoft.Extensions.Options;
using Xunit;

namespace ComplaintService.Tests;

public sealed class ComplaintManagerTests : IAsyncLifetime
{
    private static readonly DateTime CompletedAt = new(2026, 11, 20, 8, 0, 0, DateTimeKind.Utc);
    private readonly Guid _orderId = Guid.NewGuid();
    private readonly Guid _customerId = Guid.NewGuid();
    private readonly Guid _driverId = Guid.NewGuid();
    private readonly Guid _adminId = Guid.NewGuid();
    private readonly SqliteConnection _connection = new("Data Source=:memory:");
    private readonly FakeClock _clock = new(CompletedAt.AddHours(1));
    private readonly FakeDeliveries _deliveries = new();
    private readonly MemoryFileStore _files = new();
    private ComplaintDbContext _db = null!;
    private ComplaintManager _manager = null!;

    public async Task InitializeAsync()
    {
        await _connection.OpenAsync();
        _db = NewDb();
        await _db.Database.EnsureCreatedAsync();
        _manager = NewManager(_db);
        _deliveries.Context = new DeliveryComplaintContext
        {
            OrderId = _orderId, CustomerId = _customerId, DriverId = _driverId, Status = "COMPLETED",
            // Giống giá trị đọc từ SQL Server: không có Kind.
            CompletedAt = DateTime.SpecifyKind(CompletedAt, DateTimeKind.Unspecified)
        };
    }

    public async Task DisposeAsync()
    {
        await _db.DisposeAsync();
        await _connection.DisposeAsync();
    }

    private ComplaintDbContext NewDb() =>
        new(new DbContextOptionsBuilder<ComplaintDbContext>().UseSqlite(_connection).Options);

    private ComplaintManager NewManager(ComplaintDbContext db) =>
        new(db, _deliveries, _files, _clock, NullLogger<ComplaintManager>.Instance);

    private CreateComplaintCommand Command(params ComplaintUpload[] files) =>
        new(_orderId, "DAMAGED_ITEM", "  Hàng bị móp  ", files);

    [Fact]
    public async Task Customer_creates_complaint_with_trimmed_text_and_server_generated_file_name()
    {
        var created = await _manager.CreateAsync(
            Command(ComplaintFileValidatorTests.Upload(ComplaintFileValidatorTests.Jpeg, "image/jpeg", "../evil.jpg")),
            _customerId, "CUSTOMER", default);

        Assert.Equal("OPEN", created.Status);
        Assert.Equal("CUSTOMER", created.ReporterRole);
        Assert.Equal("Hàng bị móp", created.Description);
        Assert.Equal("evil.jpg", Assert.Single(created.Attachments).FileName);
        var stored = await _db.ComplaintAttachments.SingleAsync();
        Assert.Matches("^[0-9a-f]{32}\\.jpg$", stored.StoredFileName);
        Assert.True(_files.Contains(stored.StoredFileName));
    }

    [Fact]
    public async Task Exactly_24_hours_is_accepted()
    {
        _clock.Now = CompletedAt.AddHours(24);
        var created = await _manager.CreateAsync(Command(), _customerId, "CUSTOMER", default);
        Assert.Equal("OPEN", created.Status);
    }

    [Fact]
    public async Task Just_past_24_hours_is_rejected_and_stores_nothing()
    {
        _clock.Now = CompletedAt.AddHours(24).AddMilliseconds(1);
        var error = await Assert.ThrowsAsync<ApiException>(() => _manager.CreateAsync(
            Command(ComplaintFileValidatorTests.Upload(ComplaintFileValidatorTests.Png, "image/png")),
            _customerId, "CUSTOMER", default));
        AssertError(error, 409, "COMPLAINT_WINDOW_CLOSED");
        Assert.Equal(0, _files.Count);
        Assert.Empty(await _db.Complaints.ToListAsync());
    }

    [Fact]
    public async Task One_complaint_per_side_but_customer_and_driver_can_each_report()
    {
        await _manager.CreateAsync(Command(), _customerId, "CUSTOMER", default);
        var second = await Assert.ThrowsAsync<ApiException>(() => _manager.CreateAsync(Command(), _customerId, "CUSTOMER", default));
        AssertError(second, 409, "COMPLAINT_ALREADY_EXISTS");

        var driver = await _manager.CreateAsync(Command(), _driverId, "DRIVER", default);
        Assert.Equal("DRIVER", driver.ReporterRole);
        Assert.Equal(2, await _db.Complaints.CountAsync());
    }

    [Fact]
    public async Task Concurrent_duplicate_is_blocked_by_unique_index_and_removes_uploaded_files()
    {
        // Yêu cầu thứ hai đã qua bước kiểm tra AnyAsync, rồi yêu cầu khác chèn trước khi nó lưu.
        _files.BeforeSave = () =>
        {
            _files.BeforeSave = null;
            using var other = NewDb();
            other.Complaints.Add(new Complaint
            {
                Id = Guid.NewGuid(), OrderId = _orderId, ReporterRole = ComplaintReporterRole.CUSTOMER, ReporterId = _customerId,
                Category = ComplaintCategory.OTHER, Description = "first", CreatedAt = _clock.Now, UpdatedAt = _clock.Now
            });
            other.SaveChanges();
        };

        var error = await Assert.ThrowsAsync<ApiException>(() => _manager.CreateAsync(
            Command(ComplaintFileValidatorTests.Upload(ComplaintFileValidatorTests.Jpeg, "image/jpeg")),
            _customerId, "CUSTOMER", default));

        AssertError(error, 409, "COMPLAINT_ALREADY_EXISTS");
        Assert.Equal(0, _files.Count);
    }

    [Fact]
    public async Task Only_the_order_owner_or_assigned_driver_can_report()
    {
        var stranger = await Assert.ThrowsAsync<ApiException>(() => _manager.CreateAsync(Command(), Guid.NewGuid(), "CUSTOMER", default));
        AssertError(stranger, 403, "COMPLAINT_FORBIDDEN");
        var otherDriver = await Assert.ThrowsAsync<ApiException>(() => _manager.CreateAsync(Command(), Guid.NewGuid(), "DRIVER", default));
        AssertError(otherDriver, 403, "COMPLAINT_FORBIDDEN");
        // Id của khách không được dùng dưới vai trò tài xế.
        var wrongRole = await Assert.ThrowsAsync<ApiException>(() => _manager.CreateAsync(Command(), _customerId, "DRIVER", default));
        AssertError(wrongRole, 403, "COMPLAINT_FORBIDDEN");
    }

    [Theory]
    [InlineData("DELIVERING")]
    [InlineData("CANCELLED")]
    public async Task Order_must_be_completed(string status)
    {
        _deliveries.Context!.Status = status;
        _deliveries.Context.CompletedAt = null;
        var error = await Assert.ThrowsAsync<ApiException>(() => _manager.CreateAsync(Command(), _customerId, "CUSTOMER", default));
        AssertError(error, 409, "COMPLAINT_ORDER_NOT_COMPLETED");
    }

    [Fact]
    public async Task Unknown_order_returns_404()
    {
        _deliveries.Context = null;
        var error = await Assert.ThrowsAsync<ApiException>(() => _manager.CreateAsync(Command(), _customerId, "CUSTOMER", default));
        AssertError(error, 404, "ORDER_NOT_FOUND");
    }

    [Theory]
    [InlineData("BAD", "ok", "COMPLAINT_INVALID_CATEGORY")]
    [InlineData("OTHER", "   ", "COMPLAINT_INVALID_DESCRIPTION")]
    [InlineData("OTHER", null, "COMPLAINT_INVALID_DESCRIPTION")]
    public async Task Invalid_input_is_rejected_before_calling_delivery(string category, string? description, string code)
    {
        var error = await Assert.ThrowsAsync<ApiException>(() => _manager.CreateAsync(
            new CreateComplaintCommand(_orderId, category, description, []), _customerId, "CUSTOMER", default));
        AssertError(error, 400, code);
        Assert.Equal(0, _deliveries.Calls);
    }

    [Fact]
    public async Task Description_limit_is_1000_characters()
    {
        var atLimit = await _manager.CreateAsync(
            new CreateComplaintCommand(_orderId, "OTHER", new string('a', 1000), []), _customerId, "CUSTOMER", default);
        Assert.Equal(1000, atLimit.Description.Length);
        var tooLong = await Assert.ThrowsAsync<ApiException>(() => _manager.CreateAsync(
            new CreateComplaintCommand(_orderId, "OTHER", new string('a', 1001), []), _driverId, "DRIVER", default));
        AssertError(tooLong, 400, "COMPLAINT_INVALID_DESCRIPTION");
    }

    [Fact]
    public async Task Only_owner_or_admin_can_read_and_admin_id_stays_private()
    {
        var created = await _manager.CreateAsync(Command(), _customerId, "CUSTOMER", default);
        await _manager.UpdateStatusAsync(created.Id, new UpdateComplaintStatusRequest { Status = "IN_REVIEW" }, _adminId, default);

        Assert.Null((await _manager.GetByIdAsync(created.Id, _customerId, "CUSTOMER", default)).HandledByAdminId);
        Assert.Equal(_adminId, (await _manager.GetByIdAsync(created.Id, _adminId, "ADMIN", default)).HandledByAdminId);
        // Tài xế cùng đơn không được xem khiếu nại của khách.
        var error = await Assert.ThrowsAsync<ApiException>(() => _manager.GetByIdAsync(created.Id, _driverId, "DRIVER", default));
        AssertError(error, 403, "COMPLAINT_FORBIDDEN");
    }

    [Fact]
    public async Task Attachment_must_belong_to_the_complaint_and_reader()
    {
        var first = await _manager.CreateAsync(
            Command(ComplaintFileValidatorTests.Upload(ComplaintFileValidatorTests.Jpeg, "image/jpeg")), _customerId, "CUSTOMER", default);
        var second = await _manager.CreateAsync(
            Command(ComplaintFileValidatorTests.Upload(ComplaintFileValidatorTests.Png, "image/png", "b.png")), _driverId, "DRIVER", default);
        var attachmentId = first.Attachments[0].Id;

        await using (var content = (await _manager.OpenAttachmentAsync(first.Id, attachmentId, _customerId, "CUSTOMER", default)).Content) { }
        var forbidden = await Assert.ThrowsAsync<ApiException>(() => _manager.OpenAttachmentAsync(first.Id, attachmentId, _driverId, "DRIVER", default));
        AssertError(forbidden, 403, "COMPLAINT_FORBIDDEN");
        // Lấy ảnh của khiếu nại khác thông qua khiếu nại của mình.
        var crossed = await Assert.ThrowsAsync<ApiException>(() => _manager.OpenAttachmentAsync(second.Id, attachmentId, _driverId, "DRIVER", default));
        AssertError(crossed, 404, "COMPLAINT_ATTACHMENT_NOT_FOUND");
    }

    [Fact]
    public async Task Mine_lists_only_own_complaints_and_can_filter_by_order()
    {
        await _manager.CreateAsync(Command(), _customerId, "CUSTOMER", default);
        await _manager.CreateAsync(Command(), _driverId, "DRIVER", default);

        var mine = await _manager.GetMineAsync(_customerId, null, 1, 20, default);
        Assert.Equal(1, mine.TotalItems);
        Assert.Equal(_customerId, mine.Items[0].ReporterId);
        Assert.Empty((await _manager.GetMineAsync(_customerId, Guid.NewGuid(), 1, 20, default)).Items);
        Assert.Single((await _manager.GetMineAsync(_customerId, _orderId, 1, 20, default)).Items);
        var invalid = await Assert.ThrowsAsync<ApiException>(() => _manager.GetMineAsync(_customerId, null, 0, 20, default));
        AssertError(invalid, 400, "INVALID_PAGINATION");
    }

    [Fact]
    public async Task Admin_flow_sets_handler_and_resolved_time()
    {
        var created = await _manager.CreateAsync(Command(), _customerId, "CUSTOMER", default);
        var review = await _manager.UpdateStatusAsync(created.Id, new UpdateComplaintStatusRequest { Status = "IN_REVIEW", AdminNote = "Đang xem" }, _adminId, default);
        Assert.Null(review.ResolvedAt);
        Assert.Equal(_adminId, review.HandledByAdminId);

        _clock.Now = _clock.Now.AddHours(2);
        var resolved = await _manager.UpdateStatusAsync(created.Id, new UpdateComplaintStatusRequest { Status = "RESOLVED" }, _adminId, default);
        Assert.Equal(_clock.Now, resolved.ResolvedAt);
        Assert.Equal("Đang xem", resolved.AdminNote); // không gửi ghi chú mới thì giữ ghi chú cũ

        var back = await Assert.ThrowsAsync<ApiException>(() => _manager.UpdateStatusAsync(
            created.Id, new UpdateComplaintStatusRequest { Status = "IN_REVIEW" }, _adminId, default));
        AssertError(back, 409, "COMPLAINT_INVALID_TRANSITION");
    }

    [Fact]
    public async Task Skipping_review_is_rejected()
    {
        var created = await _manager.CreateAsync(Command(), _customerId, "CUSTOMER", default);
        var error = await Assert.ThrowsAsync<ApiException>(() => _manager.UpdateStatusAsync(
            created.Id, new UpdateComplaintStatusRequest { Status = "RESOLVED" }, _adminId, default));
        AssertError(error, 409, "COMPLAINT_INVALID_TRANSITION");
    }

    [Theory]
    [InlineData(null)]
    [InlineData("   ")]
    public async Task Reject_requires_admin_note(string? note)
    {
        var created = await _manager.CreateAsync(Command(), _customerId, "CUSTOMER", default);
        await _manager.UpdateStatusAsync(created.Id, new UpdateComplaintStatusRequest { Status = "IN_REVIEW" }, _adminId, default);
        var error = await Assert.ThrowsAsync<ApiException>(() => _manager.UpdateStatusAsync(
            created.Id, new UpdateComplaintStatusRequest { Status = "REJECTED", AdminNote = note }, _adminId, default));
        AssertError(error, 400, "COMPLAINT_ADMIN_NOTE_REQUIRED");
        Assert.Equal(ComplaintStatus.IN_REVIEW, (await _db.Complaints.AsNoTracking().SingleAsync()).Status);
    }

    [Fact]
    public async Task Two_admins_cannot_overwrite_each_other()
    {
        var created = await _manager.CreateAsync(Command(), _customerId, "CUSTOMER", default);
        await _manager.UpdateStatusAsync(created.Id, new UpdateComplaintStatusRequest { Status = "IN_REVIEW" }, _adminId, default);

        await using var staleDb = NewDb();
        var stale = await staleDb.Complaints.SingleAsync(x => x.Id == created.Id);
        await _manager.UpdateStatusAsync(created.Id, new UpdateComplaintStatusRequest { Status = "RESOLVED" }, _adminId, default);

        stale.Status = ComplaintStatus.REJECTED;
        await Assert.ThrowsAsync<DbUpdateConcurrencyException>(() => staleDb.SaveChangesAsync());
    }

    [Fact]
    public async Task Admin_list_filters_by_status_and_rejects_unknown_status()
    {
        var created = await _manager.CreateAsync(Command(), _customerId, "CUSTOMER", default);
        await _manager.CreateAsync(Command(), _driverId, "DRIVER", default);
        await _manager.UpdateStatusAsync(created.Id, new UpdateComplaintStatusRequest { Status = "IN_REVIEW" }, _adminId, default);

        Assert.Equal(2, (await _manager.GetForAdminAsync(null, 1, 20, default)).TotalItems);
        Assert.Equal(created.Id, Assert.Single((await _manager.GetForAdminAsync("IN_REVIEW", 1, 20, default)).Items).Id);
        var error = await Assert.ThrowsAsync<ApiException>(() => _manager.GetForAdminAsync("NOPE", 1, 20, default));
        AssertError(error, 400, "COMPLAINT_INVALID_STATUS");
    }

    [Fact]
    public async Task Cleanup_removes_only_final_complaints_older_than_30_days()
    {
        var old = await Seed(ComplaintStatus.RESOLVED, _clock.Now.AddDays(-30).AddSeconds(-1));
        var oldRejected = await Seed(ComplaintStatus.REJECTED, _clock.Now.AddDays(-31));
        var recent = await Seed(ComplaintStatus.RESOLVED, _clock.Now.AddDays(-29));
        var atBoundary = await Seed(ComplaintStatus.REJECTED, _clock.Now.AddDays(-30));
        var open = await Seed(ComplaintStatus.IN_REVIEW, null);

        var cleaner = new ComplaintAttachmentCleaner(_db, _files, Options.Create(new ComplaintOptions()), _clock,
            NullLogger<ComplaintAttachmentCleaner>.Instance);
        Assert.Equal(2, await cleaner.CleanAsync(default));

        Assert.False(_files.Contains(old));
        Assert.False(_files.Contains(oldRejected));
        Assert.True(_files.Contains(recent));
        Assert.True(_files.Contains(atBoundary));
        Assert.True(_files.Contains(open));
        Assert.Equal(3, await _db.ComplaintAttachments.CountAsync());
        Assert.Equal(5, await _db.Complaints.CountAsync());
    }

    [Fact]
    public async Task Cleanup_keeps_the_row_when_the_file_cannot_be_deleted()
    {
        var name = await Seed(ComplaintStatus.RESOLVED, _clock.Now.AddDays(-40));
        _files.FailDelete = true;
        var cleaner = new ComplaintAttachmentCleaner(_db, _files, Options.Create(new ComplaintOptions()), _clock,
            NullLogger<ComplaintAttachmentCleaner>.Instance);

        Assert.Equal(0, await cleaner.CleanAsync(default));
        Assert.Equal(1, await _db.ComplaintAttachments.CountAsync());

        _files.FailDelete = false;
        Assert.Equal(1, await cleaner.CleanAsync(default));
        Assert.False(_files.Contains(name));
    }

    private async Task<string> Seed(ComplaintStatus status, DateTime? resolvedAt)
    {
        var stored = await _files.SaveAsync(new MemoryStream(ComplaintFileValidatorTests.Jpeg), ".jpg", default);
        var id = Guid.NewGuid();
        _db.Complaints.Add(new Complaint
        {
            Id = id, OrderId = Guid.NewGuid(), ReporterRole = ComplaintReporterRole.CUSTOMER, ReporterId = _customerId,
            Category = ComplaintCategory.OTHER, Description = "seed", Status = status, ResolvedAt = resolvedAt,
            CreatedAt = _clock.Now, UpdatedAt = _clock.Now,
            Attachments =
            [
                new ComplaintAttachment
                {
                    Id = Guid.NewGuid(), ComplaintId = id, StoredFileName = stored, OriginalFileName = "a.jpg",
                    ContentType = "image/jpeg", SizeBytes = 8, CreatedAt = _clock.Now
                }
            ]
        });
        await _db.SaveChangesAsync();
        _db.ChangeTracker.Clear();
        return stored;
    }

    private static void AssertError(ApiException error, int status, string code)
    {
        Assert.Equal(status, error.StatusCode);
        Assert.Equal(code, error.Code);
    }

    private sealed class FakeClock(DateTime now) : TimeProvider
    {
        public DateTime Now { get; set; } = now;
        public override DateTimeOffset GetUtcNow() => new(Now, TimeSpan.Zero);
    }

    private sealed class FakeDeliveries : IDeliveryContextClient
    {
        public DeliveryComplaintContext? Context { get; set; }
        public int Calls { get; private set; }
        public Task<DeliveryComplaintContext?> GetAsync(Guid orderId, CancellationToken cancellationToken)
        {
            Calls++;
            return Task.FromResult(Context);
        }
    }

    private sealed class MemoryFileStore : IComplaintFileStore
    {
        private readonly Dictionary<string, byte[]> _files = [];
        public Action? BeforeSave { get; set; }
        public bool FailDelete { get; set; }
        public int Count => _files.Count;
        public bool Contains(string name) => _files.ContainsKey(name);

        public async Task<string> SaveAsync(Stream content, string extension, CancellationToken cancellationToken)
        {
            BeforeSave?.Invoke();
            using var buffer = new MemoryStream();
            await content.CopyToAsync(buffer, cancellationToken);
            var name = Guid.NewGuid().ToString("N") + extension;
            _files[name] = buffer.ToArray();
            return name;
        }

        public Stream? OpenRead(string storedFileName)
            => _files.TryGetValue(storedFileName, out var bytes) ? new MemoryStream(bytes) : null;

        public void Delete(string storedFileName)
        {
            if (FailDelete) throw new IOException("locked");
            _files.Remove(storedFileName);
        }
    }
}
