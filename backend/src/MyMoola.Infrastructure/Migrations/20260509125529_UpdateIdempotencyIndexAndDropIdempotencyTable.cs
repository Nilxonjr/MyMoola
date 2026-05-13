using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace MyMoola.Infrastructure.Migrations
{
    /// <inheritdoc />
    public partial class UpdateIdempotencyIndexAndDropIdempotencyTable : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            // Moving idempotency storage to Redis — table no longer needed
            migrationBuilder.DropTable("idempotency_keys");

            migrationBuilder.DropIndex(
                name: "IX_transactions_idempotency_key",
                table: "transactions");

            migrationBuilder.RenameIndex(
                name: "IX_wallets_user_id_currency",
                table: "wallets",
                newName: "IX_wallets_user_currency");

            migrationBuilder.CreateIndex(
                name: "IX_transactions_initiator_idempotency_key",
                table: "transactions",
                columns: new[] { "initiator_user_id", "idempotency_key" },
                unique: true,
                filter: "[initiator_user_id] IS NOT NULL");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
           
            migrationBuilder.DropIndex(
                name: "IX_transactions_initiator_idempotency_key",
                table: "transactions");

            migrationBuilder.RenameIndex(
                name: "IX_wallets_user_currency",
                table: "wallets",
                newName: "IX_wallets_user_id_currency");

            migrationBuilder.CreateIndex(
                name: "IX_transactions_idempotency_key",
                table: "transactions",
                column: "idempotency_key",
                unique: true);
        }
    }
}
