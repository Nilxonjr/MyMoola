using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace MyMoola.Infrastructure.Migrations
{
    /// <inheritdoc />
    public partial class AddCryptoDepositFeature : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropIndex(
                name: "IX_deposit_addresses_user_id",
                table: "deposit_addresses");

            migrationBuilder.DropColumn(
                name: "currency",
                table: "deposit_addresses");

            migrationBuilder.RenameIndex(
                name: "IX_deposit_addresses_address",
                table: "deposit_addresses",
                newName: "ix_deposit_addresses_address");

            migrationBuilder.CreateSequence<int>(
                name: "deposit_address_index_seq",
                startValue: 2L);

            migrationBuilder.AddColumn<string>(
                name: "chain",
                table: "deposit_addresses",
                type: "text",
                nullable: false,
                defaultValue: "");

            migrationBuilder.AddColumn<int>(
                name: "derivation_index",
                table: "deposit_addresses",
                type: "integer",
                nullable: false,
                defaultValue: 0);

            migrationBuilder.AddColumn<DateTimeOffset>(
                name: "last_checked_at",
                table: "deposit_addresses",
                type: "timestamp with time zone",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "pending_gas_funding_tx_hash",
                table: "deposit_addresses",
                type: "text",
                nullable: true);

            migrationBuilder.CreateIndex(
                name: "ix_deposit_addresses_user_chain_active",
                table: "deposit_addresses",
                columns: new[] { "user_id", "chain" },
                unique: true,
                filter: "is_active = true");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropIndex(
                name: "ix_deposit_addresses_user_chain_active",
                table: "deposit_addresses");

            migrationBuilder.DropColumn(
                name: "chain",
                table: "deposit_addresses");

            migrationBuilder.DropColumn(
                name: "derivation_index",
                table: "deposit_addresses");

            migrationBuilder.DropColumn(
                name: "last_checked_at",
                table: "deposit_addresses");

            migrationBuilder.DropColumn(
                name: "pending_gas_funding_tx_hash",
                table: "deposit_addresses");

            migrationBuilder.DropSequence(
                name: "deposit_address_index_seq");

            migrationBuilder.RenameIndex(
                name: "ix_deposit_addresses_address",
                table: "deposit_addresses",
                newName: "IX_deposit_addresses_address");

            migrationBuilder.AddColumn<string>(
                name: "currency",
                table: "deposit_addresses",
                type: "character varying(10)",
                maxLength: 10,
                nullable: false,
                defaultValue: "");

            migrationBuilder.CreateIndex(
                name: "IX_deposit_addresses_user_id",
                table: "deposit_addresses",
                column: "user_id");
        }
    }
}
