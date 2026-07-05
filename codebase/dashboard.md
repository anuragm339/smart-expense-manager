# Dashboard Flow

## Initial load

```text
DashboardFragment
  -> DashboardViewModel
  -> GetDashboardDataUseCase
  -> DashboardRepositoryInterface
  -> ExpenseRepository / TransactionDao
  -> DashboardUIState
  -> DashboardViewBinder and chart helpers
```

The ViewModel starts loading during initialization. If the first query returns no transactions, it retries to allow database migration or SMS import to finish.

## Data assembled

- Total debit spending for the selected date range.
- Transaction count.
- Category spending breakdown.
- Top merchants with category metadata.
- Credit/debit balance and inferred salary information.
- Period comparison and trend series.

Excluded merchants are removed from spending calculations.

## User actions

`DashboardUIEvent` supports refresh, SMS sync, incremental/full rescan, period changes, custom month comparison, and error clearing. Quick-add expense behavior is coordinated by `DashboardActionHandler`.

## Rendering

- `DashboardViewBinder` binds summary and category sections.
- `DashboardTrendBinder` binds trend visuals.
- `DashboardDataOrchestrator` coordinates additional date-range queries.
- MPAndroidChart renders chart data.

## Refresh signals

The screen listens to both the ViewModel's `DATA_CHANGED` receiver and Fragment-level transaction/category broadcasts. These trigger debounced dashboard reloads.

## Key sources

- `ui/dashboard/DashboardFragment.kt`
- `ui/dashboard/DashboardViewModel.kt`
- `ui/dashboard/DashboardViewBinder.kt`
- `ui/dashboard/DashboardTrendBinder.kt`
- `domain/usecase/dashboard/GetDashboardDataUseCase.kt`
