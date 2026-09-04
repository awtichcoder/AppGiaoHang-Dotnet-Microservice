namespace OrderService.Entities
{
    // enum de goi y khi code ve trang thai order de ko bi sai giua cac version
    public enum OrderStatus
    {
        PENDING,
        ASSIGNED,
        PICKED_UP,
        DELIVERING,
        COMPLETED,
        CANCELLED,
        NO_DRIVER_FOUND

    }
}
