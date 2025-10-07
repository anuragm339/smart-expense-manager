# Refactor Tracker

## 2025-10-06
- Established `REFACTOR_README.md` with refactor constraints, workflow, and logging expectations.
- Captured tracker discipline so future sessions log touched packages and follow-ups.
- Completed `data/repository` pass: migrated all repository classes (including `internal/TransactionDataRepository`) from Timber to `StructuredLogger`, validated class lengths (<1K), and standardized debug messaging.
- Extracted merchant/category and maintenance flows from `ExpenseRepository` into `internal/MerchantCategoryOperations.kt` and `internal/DatabaseMaintenanceOperations.kt`; class now 589 lines.
- Introduced `internal/AIInsightsCacheManager.kt` to encapsulate cache/offline logic; trimmed `AIInsightsRepository` to 825 lines while delegating to the manager.
- Converted service-layer logging to `StructuredLogger` (`TransactionFilterService`, `TransactionParsingService`, `SMSParsingService`, `RetryMechanism`); flagged `DateRangeService` (~907 lines) for future extraction if scope expands.
- Confirmed dashboard package separation (fragment orchestrates, binders render, action handler drives flows) and migrated remaining adapter logging away from Timber.
- Created `MessagesViewBinder` and moved RecyclerView/summary wiring out of `MessagesFragment`; converted messages logging to `StructuredLogger` and trimmed redundant UI wiring in the fragment (now delegates to binder + StructuredLogger).

**DateRangeService Extraction TODO**
- 1. Split the 907-line utility into focused components: `DateRangeCalculator` (standard ranges), `TimeAggregationPlanner`, and `CustomRangeResolver`.
- 2. Introduce a lightweight facade (`DateRangeService`) that delegates to the new helpers while preserving the existing public API.
- 3. Add targeted unit tests for critical calculations (month/year boundaries, quarter math) once helpers are in place to prevent regressions.
- 4. Update callers incrementally, prioritizing dashboard/orchestration classes that depend on the heaviest methods.

**Next Focus**
- Continue with `ui/messages`: replace remaining ad-hoc logs with `StructuredLogger`, then carve out a binder/action handler to shrink `MessagesFragment` (currently ~2.7K lines).
- Monitor new delegate classes for reuse opportunities (e.g., move shared cache helpers under `data/repository/internal` if other repositories need them).
- Schedule the DateRangeService extraction work outlined above once current UI refactors settle.
