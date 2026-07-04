# SMS Ingestion

Two paths ingest bank SMS: real-time broadcast receipt and an explicit historical scan.

## Real-time path

```text
SMS_RECEIVED broadcast
  -> SMSReceiver.goAsync()
  -> UnifiedSMSParser.parseSMS()
  -> duplicate lookup by sms_id
  -> TransactionDao.insertTransaction()
  -> auto-categorize merchant
  -> transaction notification
  -> NEW_TRANSACTION_ADDED broadcast
```

`SMSReceiver` is manifest-registered and exported because Android delivers the system SMS broadcast. Processing runs on an IO coroutine while the receiver holds a `PendingResult`.

## Historical path

1. `SMSParsingService` queries the inbox through `Telephony.Sms.CONTENT_URI`.
2. It scans at most 5,000 inbox messages from the last six months.
3. Each message is delegated to `UnifiedSMSParser`.
4. Accepted results become `ParsedTransaction` objects.
5. `TransactionDataRepository.syncNewSms()` compares the sync timestamp, applies duplicate checks, inserts rows, and updates `SyncStateEntity`.

`SMSHistoryReader` provides an older overlapping path still used by parts of the Messages UI.

## Parser behavior

- `RuleLoader` reads and validates `assets/bank_rules.json`.
- Sender, amount, merchant, date, transaction type, and reference regexes are cached.
- A reference number is mandatory.
- `ConfidenceCalculator` scores extracted fields.
- `merchant_rules.json` drives automatic merchant categorization.

## Duplicate prevention

The unique `sms_id` index is the first guard. Reference number plus sender is preferred; otherwise sender, body hash, and timestamp are used. Historical sync also performs a merchant, amount, bank, and time-window similarity lookup.

## Key sources

- `utils/SMSReceiver.kt`
- `services/SMSParsingService.kt`
- `utils/SMSHistoryReader.kt`
- `parsing/engine/UnifiedSMSParser.kt`
- `parsing/engine/RuleLoader.kt`
- `data/repository/internal/TransactionDataRepository.kt`
