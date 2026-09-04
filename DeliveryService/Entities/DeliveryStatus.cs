namespace DeliveryService.Entities;

public enum DeliveryStatus
{
    SEARCHING,
    ASSIGNED,
    PICKED_UP,
    DELIVERING,
    COMPLETED,
    CANCELLED,
    NO_DRIVER_FOUND
}