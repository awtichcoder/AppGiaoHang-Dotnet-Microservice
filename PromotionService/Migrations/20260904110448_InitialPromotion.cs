using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

#pragma warning disable CA1814 // Prefer jagged arrays over multidimensional

namespace PromotionService.Migrations
{
    /// <inheritdoc />
    public partial class InitialPromotion : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "PromotionCustomerUsages",
                columns: table => new
                {
                    PromotionId = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    CustomerId = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    CommittedCount = table.Column<int>(type: "int", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_PromotionCustomerUsages", x => new { x.PromotionId, x.CustomerId });
                });

            migrationBuilder.CreateTable(
                name: "Promotions",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    Code = table.Column<string>(type: "nvarchar(50)", maxLength: 50, nullable: false),
                    Description = table.Column<string>(type: "nvarchar(max)", nullable: true),
                    DiscountType = table.Column<string>(type: "nvarchar(20)", maxLength: 20, nullable: false),
                    DiscountValue = table.Column<decimal>(type: "decimal(18,2)", nullable: false),
                    MaxDiscountAmount = table.Column<int>(type: "int", nullable: true),
                    MinOrderValue = table.Column<int>(type: "int", nullable: false),
                    StartAt = table.Column<DateTime>(type: "datetime2", nullable: false),
                    EndAt = table.Column<DateTime>(type: "datetime2", nullable: false),
                    IsActive = table.Column<bool>(type: "bit", nullable: false),
                    UsageLimitTotal = table.Column<int>(type: "int", nullable: true),
                    UsageCount = table.Column<int>(type: "int", nullable: false),
                    UsageLimitPerCustomer = table.Column<int>(type: "int", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_Promotions", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "PromotionReservations",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    PromotionId = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    OrderId = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    CustomerId = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    DiscountAmount = table.Column<int>(type: "int", nullable: false),
                    Status = table.Column<string>(type: "nvarchar(20)", maxLength: 20, nullable: false),
                    CreatedAt = table.Column<DateTime>(type: "datetime2", nullable: false),
                    ExpiresAt = table.Column<DateTime>(type: "datetime2", nullable: false),
                    CommittedAt = table.Column<DateTime>(type: "datetime2", nullable: true),
                    ReleasedAt = table.Column<DateTime>(type: "datetime2", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_PromotionReservations", x => x.Id);
                    table.ForeignKey(
                        name: "FK_PromotionReservations_Promotions_PromotionId",
                        column: x => x.PromotionId,
                        principalTable: "Promotions",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Restrict);
                });

            migrationBuilder.InsertData(
                table: "Promotions",
                columns: new[] { "Id", "Code", "Description", "DiscountType", "DiscountValue", "EndAt", "IsActive", "MaxDiscountAmount", "MinOrderValue", "StartAt", "UsageCount", "UsageLimitPerCustomer", "UsageLimitTotal" },
                values: new object[,]
                {
                    { new Guid("11111111-1111-1111-1111-111111111111"), "FREESHIP", "Giảm 10.000đ phí ship", "FIXED", 10000m, new DateTime(2026, 12, 31, 23, 59, 59, 0, DateTimeKind.Utc), true, null, 30000, new DateTime(2026, 8, 1, 0, 0, 0, 0, DateTimeKind.Utc), 0, 3, 1000 },
                    { new Guid("22222222-2222-2222-2222-222222222222"), "GIAM10", "Giảm 10%, tối đa 20.000đ", "PERCENT", 10m, new DateTime(2026, 12, 31, 23, 59, 59, 0, DateTimeKind.Utc), true, 20000, 0, new DateTime(2026, 8, 1, 0, 0, 0, 0, DateTimeKind.Utc), 0, 1, null }
                });

            migrationBuilder.CreateIndex(
                name: "IX_PromotionReservations_OrderId",
                table: "PromotionReservations",
                column: "OrderId",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_PromotionReservations_PromotionId",
                table: "PromotionReservations",
                column: "PromotionId");

            migrationBuilder.CreateIndex(
                name: "IX_Promotions_Code",
                table: "Promotions",
                column: "Code",
                unique: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "PromotionCustomerUsages");

            migrationBuilder.DropTable(
                name: "PromotionReservations");

            migrationBuilder.DropTable(
                name: "Promotions");
        }
    }
}
