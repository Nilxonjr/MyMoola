using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace MyMoola.Infrastructure.Migrations
{
    /// <inheritdoc />
    public partial class AddMpesaAndOutboxFeatures : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "ConversationId",
                table: "mpesa_transactions",
                type: "text",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "OriginatorConversationId",
                table: "mpesa_transactions",
                type: "text",
                nullable: true);

            migrationBuilder.CreateTable(
                name: "outbox_messages",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    type = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: false),
                    payload = table.Column<string>(type: "text", nullable: false),
                    status = table.Column<string>(type: "character varying(20)", maxLength: 20, nullable: false),
                    retry_count = table.Column<int>(type: "integer", nullable: false, defaultValue: 0),
                    error = table.Column<string>(type: "character varying(2000)", maxLength: 2000, nullable: true),
                    processed_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: true),
                    locked_until = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: true),
                    last_attempted_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: true),
                    created_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false),
                    updated_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_outbox_messages", x => x.id);
                    table.CheckConstraint("CK_outbox_messages_retry_count", "retry_count >= 0");
                });

            migrationBuilder.CreateIndex(
                name: "IX_outbox_messages_status_locked_until_created_at",
                table: "outbox_messages",
                columns: new[] { "status", "locked_until", "created_at" });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "outbox_messages");

            migrationBuilder.DropColumn(
                name: "ConversationId",
                table: "mpesa_transactions");

            migrationBuilder.DropColumn(
                name: "OriginatorConversationId",
                table: "mpesa_transactions");
        }
    }
}
