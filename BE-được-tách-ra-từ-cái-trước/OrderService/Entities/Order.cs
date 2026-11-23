namespace OrderService.Entities
{
    public class Order
    {
        public Guid OrderId { get; set; }
        public Guid CustomerId { get; set; }
        public Guid? AssignedDriverId { get; set; }

        // toa do diem den
        public string PickupAddress { get; set; } = string.Empty;
        public string DropoffAddress { get; set; } = string.Empty;
        // vi do va kinh do diem don va diem den

        public decimal PickupLatitude { get; set; }
        public decimal PickupLongitude { get; set; }
        public decimal DropoffLatitude { get; set; }
        public decimal DropoffLongitude { get; set; }
        // sdt
        public string ReceiverPhone { get; set; } = string.Empty;

        public string? PromotionCode { get; set; }
        public int PointsToUse { get; set; }

        public decimal DistanceKm { get; set; }

        // tinh tien 
        public long BaseFee { get; set; }
        public long Surcharge { get; set; }
        public long PlatformFee { get; set; }
        public long DiscountPromotion { get; set; }
        public long DiscountPoints { get; set; }
        public long TotalFee { get; set; }

        public OrderStatus Status { get; set; } = OrderStatus.PENDING;

        //chong tao trung don khi customer spam
        public Guid ClientRequestId { get; set; }
        public string RequestFingerprint { get; set; } = string.Empty;
        public string MatchingStatus { get; set; } = "NOT_STARTED";
        public Guid? PromotionReservationId { get; set; }
        public Guid? LoyaltyReservationId { get; set; }
        public string? CancelReasonCode { get; set; }

        // chong de 2 nguoi spam cung luc
        public int Version { get; set; } = 1;
        // tg ngay va h 
        public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
        public DateTime? UpdatedAt { get; set; }


    }
}
