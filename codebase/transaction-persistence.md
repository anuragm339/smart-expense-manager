# Transaction Persistence

## Transaction record

`TransactionEntity` stores SMS identity, amount, raw and normalized merchant names, category, bank, transaction date, raw SMS, confidence, debit/credit direction, reference number, timestamps, and an `is_active` soft-delete flag.

## Insert flow

```text
Parsed transaction
  -> normalize merchant
  -> ensure merchant and category exist
  -> insert transaction with IGNORE conflict strategy
  -> update transaction category from merchant
```

The unique `sms_id` index makes repeated ingestion idempotent when IDs are generated consistently.

## Read flow

`TransactionDao` provides:

- Reactive and synchronous all-transaction reads.
- Date-range and paginated date-range reads.
- Merchant, bank, and text search.
- Debit, credit, salary, category, and top-merchant aggregates.
- Similar-transaction lookup for duplicate detection.

Normal reads filter on `is_active = 1`.

## Delete behavior

Single and merchant-level deletes normally set `is_active = 0`. Merchant deletion also records `is_deleted`, allowing future messages from that merchant to remain hidden. Restore queries can reactivate records.

## Database lifecycle

`ExpenseDatabase` is a Room singleton named `expense_database`. Schema version 14 includes migrations for exclusions, AI tracking, users, subscriptions, budgets, references, direct transaction categories, soft deletion, and deleted merchants.

Default categories and initial sync state are inserted when the database is created.

## Key sources

- `data/entities/TransactionEntity.kt`
- `data/dao/TransactionDao.kt`
- `data/repository/ExpenseRepository.kt`
- `data/repository/internal/TransactionDataRepository.kt`
- `data/database/ExpenseDatabase.kt`
