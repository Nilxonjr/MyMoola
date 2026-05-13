using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace MyMoola.Infrastructure.Migrations
{
    /// <inheritdoc />
    public partial class AddAdminUserMustChangePasswordAndRowVersion : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<bool>(
                name: "must_change_password",
                table: "admin_users",
                type: "bit",
                nullable: false,
                defaultValue: true);

            migrationBuilder.AddColumn<byte[]>(
                name: "row_version",
                table: "admin_users",
                type: "rowversion",
                rowVersion: true,
                nullable: false,
                defaultValue: new byte[0]);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "must_change_password",
                table: "admin_users");

            migrationBuilder.DropColumn(
                name: "row_version",
                table: "admin_users");
        }
    }
}
