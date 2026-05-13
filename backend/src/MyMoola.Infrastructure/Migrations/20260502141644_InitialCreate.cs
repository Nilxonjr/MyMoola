using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace MyMoola.Infrastructure.Migrations
{
    /// <inheritdoc />
    public partial class InitialCreate : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "admin_users",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    name = table.Column<string>(type: "nvarchar(100)", maxLength: 100, nullable: false),
                    email = table.Column<string>(type: "nvarchar(200)", maxLength: 200, nullable: false),
                    password_hash = table.Column<string>(type: "nvarchar(100)", maxLength: 100, nullable: false),
                    role = table.Column<string>(type: "nvarchar(30)", maxLength: 30, nullable: false),
                    is_active = table.Column<bool>(type: "bit", nullable: false, defaultValue: true),
                    last_login_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: true),
                    created_by = table.Column<Guid>(type: "uniqueidentifier", nullable: true),
                    created_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false),
                    updated_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_admin_users", x => x.id);
                });

            migrationBuilder.CreateTable(
                name: "audit_log",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    actor_id = table.Column<Guid>(type: "uniqueidentifier", nullable: true),
                    actor_type = table.Column<string>(type: "nvarchar(20)", maxLength: 20, nullable: false),
                    action = table.Column<string>(type: "nvarchar(100)", maxLength: 100, nullable: false),
                    target_entity = table.Column<string>(type: "nvarchar(50)", maxLength: 50, nullable: false),
                    target_id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    ip_address = table.Column<string>(type: "nvarchar(50)", maxLength: 50, nullable: true),
                    before_state = table.Column<string>(type: "nvarchar(max)", nullable: true),
                    after_state = table.Column<string>(type: "nvarchar(max)", nullable: true),
                    created_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_audit_log", x => x.id);
                });

            migrationBuilder.CreateTable(
                name: "exchange_rates",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    currency = table.Column<string>(type: "nvarchar(10)", maxLength: 10, nullable: false),
                    rate_kes = table.Column<decimal>(type: "decimal(18,4)", nullable: false),
                    rate_usd = table.Column<decimal>(type: "decimal(18,4)", nullable: false),
                    buy_rate_kes = table.Column<decimal>(type: "decimal(18,4)", nullable: false),
                    sell_rate_kes = table.Column<decimal>(type: "decimal(18,4)", nullable: false),
                    spread_percent = table.Column<decimal>(type: "decimal(6,4)", nullable: false),
                    source = table.Column<string>(type: "nvarchar(30)", maxLength: 30, nullable: false),
                    fetched_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_exchange_rates", x => x.id);
                });

            migrationBuilder.CreateTable(
                name: "idempotency_keys",
                columns: table => new
                {
                    key = table.Column<string>(type: "nvarchar(100)", maxLength: 100, nullable: false),
                    response_body = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    status_code = table.Column<int>(type: "int", nullable: false),
                    created_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false),
                    expires_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_idempotency_keys", x => x.key);
                });

            migrationBuilder.CreateTable(
                name: "system_controls",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    control_key = table.Column<string>(type: "nvarchar(50)", maxLength: 50, nullable: false),
                    is_enabled = table.Column<bool>(type: "bit", nullable: false, defaultValue: true),
                    reason = table.Column<string>(type: "nvarchar(max)", nullable: true),
                    disabled_by = table.Column<Guid>(type: "uniqueidentifier", nullable: true),
                    disabled_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: true),
                    updated_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_system_controls", x => x.id);
                });

            migrationBuilder.CreateTable(
                name: "transactions",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    reference_code = table.Column<string>(type: "nvarchar(30)", maxLength: 30, nullable: false),
                    type = table.Column<string>(type: "nvarchar(20)", maxLength: 20, nullable: false),
                    status = table.Column<string>(type: "nvarchar(20)", maxLength: 20, nullable: false),
                    initiator_user_id = table.Column<Guid>(type: "uniqueidentifier", nullable: true),
                    counterparty_user_id = table.Column<Guid>(type: "uniqueidentifier", nullable: true),
                    currency = table.Column<string>(type: "nvarchar(10)", maxLength: 10, nullable: false),
                    amount = table.Column<decimal>(type: "decimal(28,18)", nullable: false),
                    fee_amount = table.Column<decimal>(type: "decimal(28,18)", nullable: false, defaultValue: 0m),
                    kes_amount = table.Column<decimal>(type: "decimal(18,2)", nullable: true),
                    exchange_rate_snapshot = table.Column<decimal>(type: "decimal(28,8)", nullable: true),
                    market_rate_snapshot = table.Column<decimal>(type: "decimal(28,8)", nullable: true),
                    mpesa_reference = table.Column<string>(type: "nvarchar(50)", maxLength: 50, nullable: true),
                    on_chain_tx_hash = table.Column<string>(type: "nvarchar(100)", maxLength: 100, nullable: true),
                    on_chain_confirmations = table.Column<int>(type: "int", nullable: false, defaultValue: 0),
                    idempotency_key = table.Column<string>(type: "nvarchar(100)", maxLength: 100, nullable: false),
                    metadata = table.Column<string>(type: "nvarchar(max)", nullable: true),
                    admin_note = table.Column<string>(type: "nvarchar(max)", nullable: true),
                    completed_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: true),
                    created_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false),
                    updated_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_transactions", x => x.id);
                });

            migrationBuilder.CreateTable(
                name: "treasury_positions",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    currency = table.Column<string>(type: "nvarchar(10)", maxLength: 10, nullable: false),
                    balance = table.Column<decimal>(type: "decimal(28,18)", nullable: false, defaultValue: 0m),
                    kes_reserve = table.Column<decimal>(type: "decimal(18,2)", nullable: false, defaultValue: 0m),
                    coverage_ratio = table.Column<decimal>(type: "decimal(8,4)", nullable: false, defaultValue: 0m),
                    buy_halted = table.Column<bool>(type: "bit", nullable: false, defaultValue: false),
                    last_rebalanced_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: true),
                    last_synced_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: true),
                    updated_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_treasury_positions", x => x.id);
                });

            migrationBuilder.CreateTable(
                name: "users",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    phone_number = table.Column<string>(type: "nvarchar(20)", maxLength: 20, nullable: false),
                    phone_verified_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: true),
                    email = table.Column<string>(type: "nvarchar(200)", maxLength: 200, nullable: true),
                    email_verified_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: true),
                    pin_hash = table.Column<string>(type: "nvarchar(100)", maxLength: 100, nullable: false),
                    failed_pin_attempts = table.Column<int>(type: "int", nullable: false, defaultValue: 0),
                    pin_locked_until = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: true),
                    account_status = table.Column<string>(type: "nvarchar(20)", maxLength: 20, nullable: false),
                    freeze_reason = table.Column<string>(type: "nvarchar(max)", nullable: true),
                    full_name = table.Column<string>(type: "nvarchar(100)", maxLength: 100, nullable: false),
                    national_id = table.Column<string>(type: "nvarchar(20)", maxLength: 20, nullable: true),
                    kyc_status = table.Column<string>(type: "nvarchar(20)", maxLength: 20, nullable: false),
                    kyc_verified_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: true),
                    last_login_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: true),
                    row_version = table.Column<byte[]>(type: "rowversion", rowVersion: true, nullable: true),
                    created_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false),
                    updated_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_users", x => x.id);
                });

            migrationBuilder.CreateTable(
                name: "mpesa_transactions",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    transaction_id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    checkout_request_id = table.Column<string>(type: "nvarchar(100)", maxLength: 100, nullable: true),
                    merchant_request_id = table.Column<string>(type: "nvarchar(100)", maxLength: 100, nullable: true),
                    mpesa_receipt_number = table.Column<string>(type: "nvarchar(50)", maxLength: 50, nullable: true),
                    phone_number = table.Column<string>(type: "nvarchar(500)", maxLength: 500, nullable: false),
                    amount_kes = table.Column<decimal>(type: "decimal(18,2)", nullable: false),
                    direction = table.Column<string>(type: "nvarchar(10)", maxLength: 10, nullable: false),
                    status = table.Column<string>(type: "nvarchar(20)", maxLength: 20, nullable: false),
                    raw_callback_payload = table.Column<string>(type: "nvarchar(max)", nullable: true),
                    created_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false),
                    updated_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_mpesa_transactions", x => x.id);
                    table.ForeignKey(
                        name: "FK_mpesa_transactions_transactions_transaction_id",
                        column: x => x.transaction_id,
                        principalTable: "transactions",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                });

            migrationBuilder.CreateTable(
                name: "deposit_addresses",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    user_id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    currency = table.Column<string>(type: "nvarchar(10)", maxLength: 10, nullable: false),
                    address = table.Column<string>(type: "nvarchar(100)", maxLength: 100, nullable: false),
                    derivation_path = table.Column<string>(type: "nvarchar(50)", maxLength: 50, nullable: false),
                    is_active = table.Column<bool>(type: "bit", nullable: false, defaultValue: true),
                    last_used_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: true),
                    created_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false),
                    updated_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_deposit_addresses", x => x.id);
                    table.ForeignKey(
                        name: "FK_deposit_addresses_users_user_id",
                        column: x => x.user_id,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                });

            migrationBuilder.CreateTable(
                name: "wallets",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    user_id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    currency = table.Column<string>(type: "nvarchar(10)", maxLength: 10, nullable: false),
                    balance = table.Column<decimal>(type: "decimal(28,18)", nullable: false, defaultValue: 0m),
                    locked_balance = table.Column<decimal>(type: "decimal(28,18)", nullable: false, defaultValue: 0m),
                    row_version = table.Column<byte[]>(type: "rowversion", rowVersion: true, nullable: true),
                    created_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false),
                    updated_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_wallets", x => x.id);
                    table.CheckConstraint("CK_wallets_balance", "[balance] >= 0");
                    table.CheckConstraint("CK_wallets_locked_balance", "[locked_balance] >= 0");
                    table.ForeignKey(
                        name: "FK_wallets_users_user_id",
                        column: x => x.user_id,
                        principalTable: "users",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                });

            migrationBuilder.CreateTable(
                name: "ledger_entries",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    transaction_id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    wallet_id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    entry_type = table.Column<string>(type: "nvarchar(10)", maxLength: 10, nullable: false),
                    amount = table.Column<decimal>(type: "decimal(28,18)", nullable: false),
                    balance_before = table.Column<decimal>(type: "decimal(28,18)", nullable: false),
                    balance_after = table.Column<decimal>(type: "decimal(28,18)", nullable: false),
                    created_at = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_ledger_entries", x => x.id);
                    table.CheckConstraint("CK_ledger_entries_amount", "[amount] > 0");
                    table.ForeignKey(
                        name: "FK_ledger_entries_transactions_transaction_id",
                        column: x => x.transaction_id,
                        principalTable: "transactions",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                    table.ForeignKey(
                        name: "FK_ledger_entries_wallets_wallet_id",
                        column: x => x.wallet_id,
                        principalTable: "wallets",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                });

            migrationBuilder.CreateIndex(
                name: "IX_admin_users_email",
                table: "admin_users",
                column: "email",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_audit_log_action",
                table: "audit_log",
                column: "action");

            migrationBuilder.CreateIndex(
                name: "IX_audit_log_created_at",
                table: "audit_log",
                column: "created_at");

            migrationBuilder.CreateIndex(
                name: "IX_audit_log_target_id",
                table: "audit_log",
                column: "target_id");

            migrationBuilder.CreateIndex(
                name: "IX_deposit_addresses_address",
                table: "deposit_addresses",
                column: "address",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_deposit_addresses_user_id",
                table: "deposit_addresses",
                column: "user_id");

            migrationBuilder.CreateIndex(
                name: "IX_exchange_rates_currency",
                table: "exchange_rates",
                column: "currency");

            migrationBuilder.CreateIndex(
                name: "IX_exchange_rates_fetched_at",
                table: "exchange_rates",
                column: "fetched_at");

            migrationBuilder.CreateIndex(
                name: "IX_idempotency_keys_expires_at",
                table: "idempotency_keys",
                column: "expires_at");

            migrationBuilder.CreateIndex(
                name: "IX_ledger_entries_created_at",
                table: "ledger_entries",
                column: "created_at");

            migrationBuilder.CreateIndex(
                name: "IX_ledger_entries_transaction_id",
                table: "ledger_entries",
                column: "transaction_id");

            migrationBuilder.CreateIndex(
                name: "IX_ledger_entries_wallet_id",
                table: "ledger_entries",
                column: "wallet_id");

            migrationBuilder.CreateIndex(
                name: "IX_mpesa_transactions_checkout_request_id",
                table: "mpesa_transactions",
                column: "checkout_request_id",
                filter: "[checkout_request_id] IS NOT NULL");

            migrationBuilder.CreateIndex(
                name: "IX_mpesa_transactions_mpesa_receipt_number",
                table: "mpesa_transactions",
                column: "mpesa_receipt_number",
                unique: true,
                filter: "[mpesa_receipt_number] IS NOT NULL");

            migrationBuilder.CreateIndex(
                name: "IX_mpesa_transactions_transaction_id",
                table: "mpesa_transactions",
                column: "transaction_id");

            migrationBuilder.CreateIndex(
                name: "IX_system_controls_control_key",
                table: "system_controls",
                column: "control_key",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_transactions_created_at",
                table: "transactions",
                column: "created_at");

            migrationBuilder.CreateIndex(
                name: "IX_transactions_idempotency_key",
                table: "transactions",
                column: "idempotency_key",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_transactions_mpesa_reference",
                table: "transactions",
                column: "mpesa_reference",
                filter: "[mpesa_reference] IS NOT NULL");

            migrationBuilder.CreateIndex(
                name: "IX_transactions_on_chain_tx_hash",
                table: "transactions",
                column: "on_chain_tx_hash",
                filter: "[on_chain_tx_hash] IS NOT NULL");

            migrationBuilder.CreateIndex(
                name: "IX_transactions_reference_code",
                table: "transactions",
                column: "reference_code",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_treasury_positions_currency",
                table: "treasury_positions",
                column: "currency",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_users_email",
                table: "users",
                column: "email",
                unique: true,
                filter: "[email] IS NOT NULL");

            migrationBuilder.CreateIndex(
                name: "IX_users_national_id",
                table: "users",
                column: "national_id",
                unique: true,
                filter: "[national_id] IS NOT NULL");

            migrationBuilder.CreateIndex(
                name: "IX_users_phone_number",
                table: "users",
                column: "phone_number",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_wallets_user_id_currency",
                table: "wallets",
                columns: new[] { "user_id", "currency" },
                unique: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "admin_users");

            migrationBuilder.DropTable(
                name: "audit_log");

            migrationBuilder.DropTable(
                name: "deposit_addresses");

            migrationBuilder.DropTable(
                name: "exchange_rates");

            migrationBuilder.DropTable(
                name: "idempotency_keys");

            migrationBuilder.DropTable(
                name: "ledger_entries");

            migrationBuilder.DropTable(
                name: "mpesa_transactions");

            migrationBuilder.DropTable(
                name: "system_controls");

            migrationBuilder.DropTable(
                name: "treasury_positions");

            migrationBuilder.DropTable(
                name: "wallets");

            migrationBuilder.DropTable(
                name: "transactions");

            migrationBuilder.DropTable(
                name: "users");
        }
    }
}
