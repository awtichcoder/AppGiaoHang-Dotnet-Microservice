namespace DriverService.Contracts
{
    public class NearbyDriverResponse
    {
        public string DriverId { get; set; }
        public decimal DistanceKm { get; set; }
        public DateTime LastSeenAt { get; set; }
    }
}
