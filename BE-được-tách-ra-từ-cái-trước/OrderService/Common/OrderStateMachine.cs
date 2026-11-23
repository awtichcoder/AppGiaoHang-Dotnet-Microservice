using OrderService.Entities;

namespace OrderService.Common;

public static class OrderStateMachine
{
    public static bool CanTransition(
        OrderStatus currentStatus,
        OrderStatus newStatus)
    {
        // Gửi lại cùng trạng thái thì xem là không thay đổi
        if (currentStatus == newStatus)
        {
            return true;
        }

        return currentStatus switch
        {
            // trang thái hiện tại là PENDING thì có thể chuyển sang ASSIGNED, CANCELLED hoặc NO_DRIVER_FOUND
            OrderStatus.PENDING =>
                newStatus is OrderStatus.ASSIGNED
                    or OrderStatus.CANCELLED
                    or OrderStatus.NO_DRIVER_FOUND,
            // trạng thái hiện tại là ASSIGNED thì có thể chuyển sang PICKED_UP hoặc CANCELLED

            OrderStatus.ASSIGNED =>
                newStatus is OrderStatus.PICKED_UP
                    or OrderStatus.CANCELLED,

            OrderStatus.PICKED_UP =>
                newStatus == OrderStatus.DELIVERING,

            OrderStatus.DELIVERING =>
                newStatus == OrderStatus.COMPLETED,

            // Các trạng thái kết thúc không được chuyển tiếp
            OrderStatus.COMPLETED => false,
            OrderStatus.CANCELLED => false,
            OrderStatus.NO_DRIVER_FOUND => false,

            _ => false
        };
    }

    public static bool CanCustomerCancel(OrderStatus currentStatus)
    {
        return currentStatus is
            OrderStatus.PENDING or
            OrderStatus.ASSIGNED;
    }

    public static bool IsFinalStatus(OrderStatus status)
    {
        return status is
            OrderStatus.COMPLETED or
            OrderStatus.CANCELLED or
            OrderStatus.NO_DRIVER_FOUND;
    }
}