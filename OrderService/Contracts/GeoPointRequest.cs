using System.ComponentModel.DataAnnotations;

namespace OrderService.Contracts;

public class GeoPointRequest
{
    // vi do -90 den 90, kinh do -180 den 180
    [Range(-90, 90)]
    public decimal Latitude { get; set; }

    [Range(-180, 180)]
    public decimal Longitude { get; set; }
}