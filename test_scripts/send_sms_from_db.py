#!/usr/bin/env python3
"""
Auto-send SMS to Android Emulator from DB using:
adb -s <serial> emu sms send <sender> "<body>"
"""

import os
import re
import sqlite3
import subprocess
import sys
import time

# --- Paths ---
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.path.join(SCRIPT_DIR, "expense_database_live.db")

ADB = "adb"

# --- DB ---
def get_transactions(limit=5, merchant_filter=None):
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()
    query = """
        SELECT
            id,
            raw_sms_body,
            raw_merchant,
            amount,
            category_id,
            bank_name
        FROM transactions
        WHERE raw_merchant LIKE ?
        ORDER BY transaction_date DESC
        LIMIT ?
    """
    filt = f"%{merchant_filter}%" if merchant_filter else "%"
    cur.execute(query, (filt, limit))
    rows = cur.fetchall()
    conn.close()
    return rows

# --- ADB helpers ---
def list_devices():
    out = subprocess.run([ADB, "devices"], capture_output=True, text=True, check=True).stdout
    devs = []
    for line in out.splitlines()[1:]:
        line = line.strip()
        if not line:
            continue
        if "\t" in line:
            serial, state = line.split("\t", 1)
            devs.append((serial, state))
    return devs

def pick_emulator(preferred_serial=None):
    devs = list_devices()
    if preferred_serial:
        for s, st in devs:
            if s == preferred_serial and st == "device":
                return s
        raise RuntimeError(f"Preferred emulator {preferred_serial} not found or not in 'device' state")
    # pick first emulator-XXXX that’s ready
    for s, st in devs:
        if s.startswith("emulator-") and st == "device":
            return s
    raise RuntimeError("No running emulator found in 'device' state. Start one and try again.")

def sanitize_body(body: str) -> str:
    # emu console is fine with most chars; just collapse newlines/tabs
    return re.sub(r"[\r\n\t]", " ", body).strip()

def send_emulator_sms(serial: str, sender: str, body: str) -> bool:
    body = sanitize_body(body)
    cmd = [ADB, "-s", serial, "emu", "sms", "send", sender, body]
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode == 0:
        print(f"✅ [{serial}] Injected from {sender}")
        return True
    print(f"❌ [{serial}] Failed to inject: {res.stderr or res.stdout}")
    return False

# --- Main ---
def main():
    # Usage: python3 send_from_db.py <limit?> <merchant_filter?> <sender?> <emulator_serial?>
    limit          = int(sys.argv[1]) if len(sys.argv) > 1 else 10
    merchant_filter= sys.argv[2] if len(sys.argv) > 2 else None
    sender         = sys.argv[3] if len(sys.argv) > 3 else "JM-HDFCBK-S"
    emulator_serial= sys.argv[4] if len(sys.argv) > 4 else None

    print("="*60)
    print("Auto SMS → Android Emulator")
    print("="*60)
    print(f"DB: {DB_PATH}")
    print(f"Limit: {limit} | Filter: {merchant_filter or 'All'} | Sender: {sender}")
    try:
        serial = pick_emulator(emulator_serial)
        print(f"Target emulator: {serial}")
    except Exception as e:
        print(f"❌ {e}")
        return

    txns = get_transactions(limit, merchant_filter)
    if not txns:
        print("❌ No transactions found!")
        return

    print(f"✅ Found {len(txns)} transactions. Sending...\n")
    sent = 0
    for idx, (tid, sms_body, merchant, amount, category_id, bank) in enumerate(txns, 1):
        print("-"*60)
        print(f"#{idx} ID={tid}  Merchant={merchant}  Amount=₹{amount}  Bank={bank}")
        ok = send_emulator_sms(serial, sender, sms_body)
        if ok:
            sent += 1
        # small pacing so your BroadcastReceiver/SMS parser can keep up
        time.sleep(0.3)

    print("\n" + "="*60)
    print(f"Done. Injected {sent}/{len(txns)} messages.")
    print("="*60)
    print("\nTip: tail logs with →  adb logcat | grep -E 'SMS|Transaction|CATEGORY'")

if __name__ == "__main__":
    main()
