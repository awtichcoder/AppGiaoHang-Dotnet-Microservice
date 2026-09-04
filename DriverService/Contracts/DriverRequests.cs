using DriverService.Entities;

namespace DriverService.Contracts
{
    public class UpdateAvailabilityRequest
    {
        public DriverStatus AvailabilityStatus { get; set; }
    }

    public class UpdateLocationRequest
    {
        public decimal Latitude { get; set; }
        public decimal Longitude { get; set; }
        public decimal AccuracyM { get; set; }
        public DateTime RecordedAt { get; set; }
    }
}
