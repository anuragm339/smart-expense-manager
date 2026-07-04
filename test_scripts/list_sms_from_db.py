#!/usr/bin/env python3
"""
Script to list SMS from database transactions for manual testing
"""

import sqlite3
import sys

import os

# Get the directory where this script is located
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.path.join(SCRIPT_DIR, "expense_database_live.db")

def main():
    # Parse arguments
    limit = int(sys.argv[1]) if len(sys.argv) > 1 else 10
    merchant_filter = sys.argv[2] if len(sys.argv) > 2 else None

    print("=" * 80)
    print("SMS Messages from Database")
    print("=" * 80)
    print(f"Database: {DB_PATH}")
    print(f"Showing: {limit} transactions")
    print(f"Filter: {merchant_filter or 'All merchants'}")
    print("=" * 80)
    print()

    # Connect to database
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    # Query
    query = """
        SELECT
            t.id,
            t.raw_sms_body,
            t.raw_merchant,
            t.amount,
            c.name as category_name,
            t.bank_name,
            datetime(t.transaction_date/1000, 'unixepoch', 'localtime') as trans_date
        FROM transactions t
        LEFT JOIN categories c ON t.category_id = c.id
        WHERE t.raw_merchant LIKE ?
        ORDER BY t.transaction_date DESC
        LIMIT ?
    """

    filter_pattern = f"%{merchant_filter}%" if merchant_filter else "%"
    cursor.execute(query, (filter_pattern, limit))

    transactions = cursor.fetchall()
    conn.close()

    if not transactions:
        print("❌ No transactions found!")
        return

    print(f"✅ Found {len(transactions)} transactions\n")

    for idx, (tid, sms_body, merchant, amount, category, bank, date) in enumerate(transactions, 1):
        print("=" * 80)
        print(f"#{idx}: {merchant} - ₹{amount}")
        print("=" * 80)
        print(f"Transaction ID: {tid}")
        print(f"Merchant: {merchant}")
        print(f"Amount: ₹{amount}")
        print(f"Category: {category}")
        print(f"Bank: {bank}")
        print(f"Date: {date}")
        print("-" * 80)
        print("SMS Body:")
        print(sms_body)
        print("=" * 80)
        print()

    print("\n💡 To test with these SMS:")
    print("1. Manually copy an SMS above")
    print("2. Use a tool like 'SMS Simulator' app on your device")
    print("3. Or send via: adb shell input text 'SMS_BODY'")
    print()
    print("📊 Monitor with: adb logcat | grep -E 'SMS|CATEGORY|Transaction'")

if __name__ == "__main__":
    main()
