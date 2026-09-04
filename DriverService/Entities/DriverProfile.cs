using MongoDB.Bson.Serialization.Attributes;
using MongoDB.Driver.GeoJsonObjectModel;
using DriverService.Contracts;

namespace DriverService.Entities
{
    public class DriverProfile
    {
        [BsonId]
        [BsonRepresentation(MongoDB.Bson.BsonType.ObjectId)]
        public string Id { get; set; } = string.Empty;

        public DriverStatus Status { get; set; }

        public GeoJsonPoint<GeoJson2DGeographicCoordinates>? Location { get; set; }

        public DateTime? LastSeenAt { get; set; }
    }
}
