using ComplaintService.Contracts;

namespace ComplaintService.Clients;

public interface IDeliveryContextClient
{
    /// <summary>Trả null khi đơn chưa có chuyến giao.</summary>
    Task<DeliveryComplaintContext?> GetAsync(Guid orderId, CancellationToken cancellationToken);
}
