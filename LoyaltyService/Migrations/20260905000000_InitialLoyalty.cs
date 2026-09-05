using LoyaltyService.Data;
using Microsoft.EntityFrameworkCore.Infrastructure;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace LoyaltyService.Migrations;

[DbContext(typeof(LoyaltyDbContext))]
[Migration("20260905000000_InitialLoyalty")]
public partial class InitialLoyalty : Migration
{
    protected override void Up(MigrationBuilder migrationBuilder)
    {
        migrationBuilder.CreateTable(
            name: "LoyaltyAccounts",
            columns: table => new
            {
                CustomerId = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                AvailablePoints = table.Column<int>(type: "int", nullable: false),
                ReservedPoints = table.Column<int>(type: "int", nullable: false),
                LifetimeEarnedPoints = table.Column<int>(type: "int", nullable: false),
                Tier = table.Column<string>(type: "nvarchar(20)", maxLength: 20, nullable: false),
                CreatedAt = table.Column<DateTime>(type: "datetime2", nullable: false),
                UpdatedAt = table.Column<DateTime>(type: "datetime2", nullable: false),
                RowVersion = table.Column<byte[]>(type: "rowversion", rowVersion: true, nullable: false)
            },
            constraints: table =>
            {
                table.PrimaryKey("PK_LoyaltyAccounts", x => x.CustomerId);
                table.CheckConstraint("CK_LoyaltyAccounts_Available_NonNegative", "[AvailablePoints] >= 0");
                table.CheckConstraint("CK_LoyaltyAccounts_Lifetime_NonNegative", "[LifetimeEarnedPoints] >= 0");
                table.CheckConstraint("CK_LoyaltyAccounts_Reserved_NonNegative", "[ReservedPoints] >= 0");
            });

        migrationBuilder.CreateTable(
            name: "PointReservations",
            columns: table => new
            {
                Id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                CustomerId = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                OrderId = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                Points = table.Column<int>(type: "int", nullable: false),
                DiscountAmount = table.Column<int>(type: "int", nullable: false),
                Status = table.Column<string>(type: "nvarchar(20)", maxLength: 20, nullable: false),
                CreatedAt = table.Column<DateTime>(type: "datetime2", nullable: false),
                ExpiresAt = table.Column<DateTime>(type: "datetime2", nullable: false),
                CommittedAt = table.Column<DateTime>(type: "datetime2", nullable: true),
                ReleasedAt = table.Column<DateTime>(type: "datetime2", nullable: true)
            },
            constraints: table =>
            {
                table.PrimaryKey("PK_PointReservations", x => x.Id);
                table.CheckConstraint("CK_PointReservations_Discount_NonNegative", "[DiscountAmount] >= 0");
                table.CheckConstraint("CK_PointReservations_Points_Positive", "[Points] > 0");
                table.ForeignKey(
                    name: "FK_PointReservations_LoyaltyAccounts_CustomerId",
                    column: x => x.CustomerId,
                    principalTable: "LoyaltyAccounts",
                    principalColumn: "CustomerId",
                    onDelete: ReferentialAction.Restrict);
            });

        migrationBuilder.CreateTable(
            name: "PointTransactions",
            columns: table => new
            {
                Id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                CustomerId = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                OrderId = table.Column<Guid>(type: "uniqueidentifier", nullable: true),
                ReservationId = table.Column<Guid>(type: "uniqueidentifier", nullable: true),
                Type = table.Column<string>(type: "nvarchar(20)", maxLength: 20, nullable: false),
                Points = table.Column<int>(type: "int", nullable: false),
                DiscountAmount = table.Column<int>(type: "int", nullable: false),
                AvailablePointsAfter = table.Column<int>(type: "int", nullable: false),
                ReservedPointsAfter = table.Column<int>(type: "int", nullable: false),
                CreatedAt = table.Column<DateTime>(type: "datetime2", nullable: false),
                Description = table.Column<string>(type: "nvarchar(500)", maxLength: 500, nullable: true)
            },
            constraints: table =>
            {
                table.PrimaryKey("PK_PointTransactions", x => x.Id);
                table.ForeignKey(
                    name: "FK_PointTransactions_LoyaltyAccounts_CustomerId",
                    column: x => x.CustomerId,
                    principalTable: "LoyaltyAccounts",
                    principalColumn: "CustomerId",
                    onDelete: ReferentialAction.Restrict);
                table.ForeignKey(
                    name: "FK_PointTransactions_PointReservations_ReservationId",
                    column: x => x.ReservationId,
                    principalTable: "PointReservations",
                    principalColumn: "Id",
                    onDelete: ReferentialAction.Restrict);
            });

        migrationBuilder.CreateIndex(
            name: "IX_PointReservations_CustomerId",
            table: "PointReservations",
            column: "CustomerId");

        migrationBuilder.CreateIndex(
            name: "IX_PointReservations_OrderId",
            table: "PointReservations",
            column: "OrderId",
            unique: true);

        migrationBuilder.CreateIndex(
            name: "IX_PointTransactions_CustomerId_CreatedAt",
            table: "PointTransactions",
            columns: new[] { "CustomerId", "CreatedAt" });

        migrationBuilder.CreateIndex(
            name: "IX_PointTransactions_OrderId",
            table: "PointTransactions",
            column: "OrderId",
            unique: true,
            filter: "[OrderId] IS NOT NULL AND [Type] = N'EARN'");

        migrationBuilder.CreateIndex(
            name: "IX_PointTransactions_ReservationId",
            table: "PointTransactions",
            column: "ReservationId");
    }

    protected override void Down(MigrationBuilder migrationBuilder)
    {
        migrationBuilder.DropTable(name: "PointTransactions");
        migrationBuilder.DropTable(name: "PointReservations");
        migrationBuilder.DropTable(name: "LoyaltyAccounts");
    }
}
