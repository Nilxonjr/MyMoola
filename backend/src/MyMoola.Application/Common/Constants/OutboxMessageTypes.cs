// MyMoola.Application/Common/Constants/OutboxMessageTypes.cs
namespace MyMoola.Application.Common.Constants;

public static class OutboxMessageTypes
{
    public const string StkPush = "mpesa.stk_push.v1";
    public const string StkCallback = "mpesa.stk_callback.v1";
    public const string B2C = "mpesa.b2c.v1";
    public const string B2CCallback = "mpesa.b2c_callback.v1";
    public const string B2CTimeout = "mpesa.b2c_timeout.v1";
    public const string Sms = "sms.v1";
    public const string B2BPayment = "mpesa.b2b_payment.v1";
    public const string B2BCallback = "mpesa.b2b_callback.v1";
    public const string B2BTimeout = "mpesa.b2b_timeout.v1";
    public const string PochiPayment = "mpesa.pochi_payment.v1";
    public const string PochiCallbackPayment = "mpesa.pochi_callback.v1";
    public const string PochiTimeout = "mpesa.pochi_timeout.v1";
    public const string DepositDetected = "crypto.deposit_detected.v1";
    public const string DepositConfirmed = "crypto.deposit_confirmed.v1";
    public const string WithdrawalBroadcast = "crypto.withdrawal_broadcast.v1";
    public const string WithdrawalConfirmed = "crypto.withdrawal_confirmed.v1";
    public const string WithdrawalFailed = "crypto.withdrawal_failed.v1";
    public const string AddressSweep = "crypto.address_sweep.v1";
    public const string WalletCredited = "notification.wallet_credited.v1";
}