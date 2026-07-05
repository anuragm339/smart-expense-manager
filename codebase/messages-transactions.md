# Messages and Transaction Details

## Messages load

```text
MessagesFragment.startInitialLoad()
  -> MessagesViewModel.loadMessages()
  -> paginated TransactionDao query
  -> merchant aliases and categories
  -> filtering and sorting
  -> group by merchant
  -> MessagesUIState
```

The default filter starts at the first day of the current month. Pages are loaded from Room and converted to `MessageItem` models containing display and raw transaction fields.

## Filters and grouping

The feature supports text search, date ranges, merchant groups, debit/credit tabs, sorting, amount criteria, bank criteria, and inclusion state. `TransactionFilterService` merges database exclusions with legacy preference state.

`GroupedMessagesAdapter` renders merchant groups and nested transactions. Loading more advances the database offset.

## Merchant actions

Users can rename a merchant group, change its category, toggle inclusion in expense totals, reset aliases, or soft-delete transactions. Updates broadcast data changes to other screens.

## Transaction details

Selecting a row navigates to `TransactionDetailsFragment`. The ViewModel exposes actions for category changes, merchant changes, deletion, duplicate checks, and navigation.

The current navigation contract passes transaction display data as arguments. Database-backed edits locate records through repository methods when an ID is available.

## Key sources

- `ui/messages/MessagesFragment.kt`
- `ui/messages/MessagesViewModel.kt`
- `ui/messages/MessagesUIState.kt`
- `ui/messages/GroupedMessagesAdapter.kt`
- `ui/transaction/TransactionDetailsFragment.kt`
- `ui/transaction/TransactionDetailsViewModel.kt`
