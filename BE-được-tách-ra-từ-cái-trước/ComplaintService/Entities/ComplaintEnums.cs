namespace ComplaintService.Entities;

public enum ComplaintReporterRole
{
    CUSTOMER,
    DRIVER
}

public enum ComplaintCategory
{
    LATE_DELIVERY,
    WRONG_ITEM,
    DAMAGED_ITEM,
    DRIVER_BEHAVIOR,
    OTHER
}

public enum ComplaintStatus
{
    OPEN,
    IN_REVIEW,
    RESOLVED,
    REJECTED
}
