#!/usr/bin/env python3
"""
MyMoola — Safaricom Callback Simulator

Simulates B2C and B2B result/timeout callbacks from Safaricom
for local/sandbox testing when Daraja sandbox fails to deliver
real callbacks.

Usage:
    python simulate_callback.py b2c-success --conversation-id AG_xxx --receipt UET030D8CE --amount 251
    python simulate_callback.py b2c-failure --conversation-id AG_xxx --result-code 2001
    python simulate_callback.py b2b-success --conversation-id AG_xxx --receipt PAYBILL001 --amount 100
    python simulate_callback.py pochi-success --conversation-id AG_xxx --amount 31
    python simulate_callback.py b2c-timeout --conversation-id AG_xxx
    python simulate_callback.py b2b-timeout --conversation-id AG_xxx
"""

import argparse
import json
import sys
import uuid

import requests

BASE_URL = "https://mymoola-production-fbca.up.railway.app"

B2C_CALLBACK_URL = f"{BASE_URL}/api/mpesa/callbacks/b2c"
B2C_TIMEOUT_URL = f"{BASE_URL}/api/mpesa/callbacks/b2c-timeout"
B2B_CALLBACK_URL = f"{BASE_URL}/api/mpesa/callbacks/b2b"
B2B_TIMEOUT_URL = f"{BASE_URL}/api/mpesa/callbacks/b2b-timeout"


def _originator_id() -> str:
    return uuid.uuid4().hex


def build_result_parameters(pairs: list[tuple[str, object]]) -> dict:
    return {
        "ResultParameters": {
            "ResultParameter": [
                {"Key": key, "Value": value} for key, value in pairs
            ]
        }
    }


def b2c_success(conversation_id, originator_id, receipt, amount, receiver_name):
    payload = {
        "result": {
            "ConversationID": conversation_id,
            "OriginatorConversationID": originator_id,
            "ResultCode": 0,
            "ResultDesc": "The service request is processed successfully.",
            **build_result_parameters([
                ("TransactionAmount", amount),
                ("TransactionReceipt", receipt),
                ("ReceiverPartyPublicName", receiver_name),
                ("TransactionCompletedDateTime", "03.06.2026 12:00:00"),
                ("B2CUtilityAccountAvailableFunds", 9500000.00),
                ("B2CWorkingAccountAvailableFunds", 199383.00),
                ("B2CRecipientIsRegisteredCustomer", "Y"),
                ("B2CChargesPaidAccountAvailableFunds", -880.00),
            ]),
        }
    }
    return B2C_CALLBACK_URL, payload


def b2c_failure(conversation_id, originator_id, result_code, result_desc):
    payload = {
        "result": {
            "ConversationID": conversation_id,
            "OriginatorConversationID": originator_id,
            "ResultCode": result_code,
            "ResultDesc": result_desc,
            "ResultParameters": None,
        }
    }
    return B2C_CALLBACK_URL, payload


def b2c_timeout(conversation_id, originator_id):
    payload = {
        "result": {
            "ConversationID": conversation_id,
            "OriginatorConversationID": originator_id,
            "ResultCode": 1,
            "ResultDesc": "The service request has timed out.",
            "ResultParameters": None,
        }
    }
    return B2C_TIMEOUT_URL, payload


def b2b_success(conversation_id, originator_id, receipt, amount, receiver_name):
    payload = {
        "result": {
            "ConversationID": conversation_id,
            "OriginatorConversationID": originator_id,
            "ResultCode": 0,
            "ResultDesc": "The service request is processed successfully.",
            **build_result_parameters([
                ("TransactionReceipt", receipt),
                ("TransactionAmount", amount),
                ("ReceiverPartyPublicName", receiver_name),
                ("TransactionCompletedDateTime", "03.06.2026 12:00:00"),
            ]),
        }
    }
    return B2B_CALLBACK_URL, payload


def b2b_failure(conversation_id, originator_id, result_code, result_desc):
    payload = {
        "result": {
            "ConversationID": conversation_id,
            "OriginatorConversationID": originator_id,
            "ResultCode": result_code,
            "ResultDesc": result_desc,
            "ResultParameters": None,
        }
    }
    return B2B_CALLBACK_URL, payload


def b2b_timeout(conversation_id, originator_id):
    payload = {
        "result": {
            "ConversationID": conversation_id,
            "OriginatorConversationID": originator_id,
            "ResultCode": 1,
            "ResultDesc": "The service request has timed out.",
            "ResultParameters": None,
        }
    }
    return B2B_TIMEOUT_URL, payload


def pochi_success(conversation_id, originator_id, amount, receiver_name):
    """
    Pochi callbacks arrive at /api/mpesa/callbacks/b2b and do NOT
    include TransactionReceipt — only Amount, DebitAccountCurrentBalance,
    and CreditPartyName.
    """
    payload = {
        "result": {
            "ConversationID": conversation_id,
            "OriginatorConversationID": originator_id,
            "ResultCode": 0,
            "ResultDesc": "The service request is processed successfully.",
            **build_result_parameters([
                ("Amount", amount),
                ("DebitAccountCurrentBalance",
                 f"{{Amount={{CurrencyCode=KES, MinimumAmount=19903000, BasicAmount=199030.00}}}}"),
                ("CreditPartyName", receiver_name),
            ]),
        }
    }
    return B2B_CALLBACK_URL, payload


def main():
    parser = argparse.ArgumentParser(
        description="Simulate Safaricom B2C/B2B callbacks for MyMoola")
    subparsers = parser.add_subparsers(dest="command", required=True)

    # --- B2C success ---
    p = subparsers.add_parser("b2c-success", help="B2C success callback (sell flow)")
    p.add_argument("--conversation-id", required=True)
    p.add_argument("--originator-id", default=None)
    p.add_argument("--receipt", required=True, help="e.g. UET030D8CE")
    p.add_argument("--amount", type=int, required=True)
    p.add_argument("--receiver-name", default="254708374149 - John Doe")

    # --- B2C failure ---
    p = subparsers.add_parser("b2c-failure", help="B2C failure callback")
    p.add_argument("--conversation-id", required=True)
    p.add_argument("--originator-id", default=None)
    p.add_argument("--result-code", type=int, default=2001)
    p.add_argument("--result-desc", default="The initiator information is invalid.")

    # --- B2C timeout ---
    p = subparsers.add_parser("b2c-timeout", help="B2C queue timeout callback")
    p.add_argument("--conversation-id", required=True)
    p.add_argument("--originator-id", default=None)

    # --- B2B success ---
    p = subparsers.add_parser("b2b-success", help="B2B success callback (Paybill/Till)")
    p.add_argument("--conversation-id", required=True)
    p.add_argument("--originator-id", default=None)
    p.add_argument("--receipt", required=True, help="e.g. PAYBILL001")
    p.add_argument("--amount", type=int, required=True)
    p.add_argument("--receiver-name", default="600000 - Java House")

    # --- B2B failure ---
    p = subparsers.add_parser("b2b-failure", help="B2B failure callback")
    p.add_argument("--conversation-id", required=True)
    p.add_argument("--originator-id", default=None)
    p.add_argument("--result-code", type=int, default=2001)
    p.add_argument("--result-desc", default="The initiator information is invalid.")

    # --- B2B timeout ---
    p = subparsers.add_parser("b2b-timeout", help="B2B queue timeout callback")
    p.add_argument("--conversation-id", required=True)
    p.add_argument("--originator-id", default=None)

    # --- Pochi success ---
    p = subparsers.add_parser("pochi-success", help="Pochi/SendMoney success callback (no receipt)")
    p.add_argument("--conversation-id", required=True)
    p.add_argument("--originator-id", default=None)
    p.add_argument("--amount", type=int, required=True)
    p.add_argument("--receiver-name", default="254708374149 - John Doe")

    args = parser.parse_args()
    originator_id = args.originator_id or _originator_id()

    if args.command == "b2c-success":
        url, payload = b2c_success(
            args.conversation_id, originator_id,
            args.receipt, args.amount, args.receiver_name)

    elif args.command == "b2c-failure":
        url, payload = b2c_failure(
            args.conversation_id, originator_id,
            args.result_code, args.result_desc)

    elif args.command == "b2c-timeout":
        url, payload = b2c_timeout(args.conversation_id, originator_id)

    elif args.command == "b2b-success":
        url, payload = b2b_success(
            args.conversation_id, originator_id,
            args.receipt, args.amount, args.receiver_name)

    elif args.command == "b2b-failure":
        url, payload = b2b_failure(
            args.conversation_id, originator_id,
            args.result_code, args.result_desc)

    elif args.command == "b2b-timeout":
        url, payload = b2b_timeout(args.conversation_id, originator_id)

    elif args.command == "pochi-success":
        url, payload = pochi_success(
            args.conversation_id, originator_id,
            args.amount, args.receiver_name)

    else:
        print(f"Unknown command: {args.command}", file=sys.stderr)
        sys.exit(1)

    print(f"POST {url}")
    print(json.dumps(payload, indent=2))
    print()

    response = requests.post(url, json=payload, timeout=30)

    print(f"Response: {response.status_code}")
    print(response.text)


if __name__ == "__main__":
    main()