using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace MyMoola.Infrastructure.Migrations
{
    /// <inheritdoc />
    public partial class RewriteWalletsAndLedgerEntries : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.RenameColumn(
                name: "balance_before",
                table: "ledger_entries",
                newName: "locked_balance_before");

            migrationBuilder.RenameColumn(
                name: "balance_after",
                table: "ledger_entries",
                newName: "locked_balance_after");


            migrationBuilder.AlterColumn<string>(
                name: "entry_type",
                table: "ledger_entries",
                type: "nvarchar(max)",
                nullable: false,
                oldClrType: typeof(string),
                oldType: "nvarchar(10)",
                oldMaxLength: 10);

            migrationBuilder.AddColumn<decimal>(
                name: "available_balance_after",
                table: "ledger_entries",
                type: "decimal(28,18)",
                nullable: false,
                defaultValue: 0m);

            migrationBuilder.AddColumn<decimal>(
                name: "available_balance_before",
                table: "ledger_entries",
                type: "decimal(28,18)",
                nullable: false,
                defaultValue: 0m);

            migrationBuilder.AddColumn<string>(
                name: "currency",
                table: "ledger_entries",
                type: "nvarchar(max)",
                nullable: false,
                defaultValue: "");

            migrationBuilder.Sql(@"
                CREATE TRIGGER prevent_ledger_modification
                ON ledger_entries
                AFTER UPDATE, DELETE
                AS
                BEGIN
                    RAISERROR('Ledger entries are immutable.', 16, 1);
                    ROLLBACK TRANSACTION;
                END;
            ");

            migrationBuilder.InsertData(
            table: "system_controls",
            columns: new[] { "id", "control_key", "is_enabled", "updated_at" },
            columnTypes: new[] { "uniqueidentifier", "nvarchar(100)", "bit", "datetimeoffset" },
            values: new object[]
            {
                new Guid("00000000-0000-0000-0000-000000000001"),
                "GLOBAL_MAINTENANCE",
                true,
                DateTimeOffset.UtcNow
            });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DeleteData(
                table: "system_controls",
                keyColumn: "id",
                keyValue: new Guid("00000000-0000-0000-0000-000000000001"));

                    migrationBuilder.Sql(@"
                IF EXISTS (
                    SELECT 1 FROM sys.triggers 
                    WHERE name = 'prevent_ledger_modification'
                )
                DROP TRIGGER prevent_ledger_modification;
            ");


            migrationBuilder.DropColumn(
                name: "available_balance_after",
                table: "ledger_entries");

            migrationBuilder.DropColumn(
                name: "available_balance_before",
                table: "ledger_entries");

            migrationBuilder.DropColumn(
                name: "currency",
                table: "ledger_entries");

            migrationBuilder.RenameColumn(
                name: "locked_balance_before",
                table: "ledger_entries",
                newName: "balance_before");

            migrationBuilder.RenameColumn(
                name: "locked_balance_after",
                table: "ledger_entries",
                newName: "balance_after");


            migrationBuilder.AlterColumn<string>(
                name: "entry_type",
                table: "ledger_entries",
                type: "nvarchar(10)",
                maxLength: 10,
                nullable: false,
                oldClrType: typeof(string),
                oldType: "nvarchar(max)");
        }
    }
}
