namespace DriverService.Contracts
{
    public class NearbyDriverResponse
    {
        public Guid DriverId { get; set; }
        public decimal DistanceKm { get; set; }
        public DateTime LastSeenAt { get; set; }
    }
}
