# AI Insights Flow

## User flow

```text
InsightsFragment
  -> InsightsViewModel / GetAIInsightsUseCase
  -> AIInsightsRepository
  -> cached insights first
  -> threshold and network checks
  -> Retrofit backend request when eligible
  -> Room/SharedPreferences tracking and cache
  -> InsightsUIState and charts
```

The screen also queries local transactions for category, monthly, and trend charts. `DateRangeService`, `TimeSeriesAggregationService`, and `ChartConfigurationService` support those views.

## Request preparation

`FinancialDataProcessor` converts transactions into summaries. `TransactionCSVGenerator` and `InsightsPromptBuilder` prepare compact financial context for the backend.

## Cost and retry controls

- `AIThresholdService` decides when another call is eligible.
- `AICallDao` tracks daily/monthly usage, errors, frequency, and next eligible time.
- `RetryMechanism` and `NetworkErrorHandler` classify and retry transient failures.
- A mutex prevents concurrent calls in the legacy repository.

## Offline behavior

Cached insights are returned before network work. `EnhancedOfflineInsightsGenerator` can derive spending, merchant, category, trend, and budget observations locally when remote data is unavailable.

## Current dual path

There are two repository/network paths: Hilt-provided enhanced services and a legacy `AIInsightsRepository.getInstance()` factory used by `InsightsFragment`. Consolidating these is important because they have different timeout, TLS, and logging behavior.

## Key sources

- `ui/insights/`
- `data/repository/AIInsightsRepository.kt`
- `data/repository/EnhancedAIInsightsRepository.kt`
- `di/NetworkModule.kt`
- `data/api/insights/NetworkConfig.kt`
- `services/AIThresholdService.kt`
