namespace OrderService.Contracts;

public sealed class PagedResponse<T>
{
    public int Page { get; set; }
    public int PageSize { get; set; }
    public int TotalItems { get; set; }
    public IReadOnlyList<T> Items { get; set; } = [];
}
