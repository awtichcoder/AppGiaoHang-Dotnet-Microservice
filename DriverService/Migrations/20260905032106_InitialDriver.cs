using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace DriverService.Migrations
{
    /// <inheritdoc />
    public partial class InitialDriver : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "DriverProfiles",
                columns: table => new
                {
                    DriverId = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    Status = table.Column<string>(type: "nvarchar(20)", maxLength: 20, nullable: false),
                    Latitude = table.Column<decimal>(type: "decimal(9,6)", precision: 9, scale: 6, nullable: true),
                    Longitude = table.Column<decimal>(type: "decimal(9,6)", precision: 9, scale: 6, nullable: true),
                    AccuracyM = table.Column<decimal>(type: "decimal(10,2)", precision: 10, scale: 2, nullable: true),
                    LocationUpdatedAt = table.Column<DateTime>(type: "datetime2", nullable: true),
                    UpdatedAt = table.Column<DateTime>(type: "datetime2", nullable: false),
                    ActiveDeliveryId = table.Column<Guid>(type: "uniqueidentifier", nullable: true),
                    Version = table.Column<int>(type: "int", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_DriverProfiles", x => x.DriverId);
                });

            migrationBuilder.CreateIndex(
                name: "IX_DriverProfiles_ActiveDeliveryId",
                table: "DriverProfiles",
                column: "ActiveDeliveryId");

            migrationBuilder.CreateIndex(
                name: "IX_DriverProfiles_Status_LocationUpdatedAt",
                table: "DriverProfiles",
                columns: new[] { "Status", "LocationUpdatedAt" });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "DriverProfiles");
        }
    }
}
