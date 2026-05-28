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
}