using AspNetCore.Identity.Mongo.Model;

namespace IdentityService.Entities
{
    public class ApplicationUser : MongoUser
    {
        public string? FullName { get; set; }
        public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
        public string? RefreshToken { get; set; }
        public DateTime RefreshTokenExpiryTime { get; set; }
    }
}
