# Logging Refactoring Progress

This document tracks the progress of migrating from Logback to Timber and standardizing logging practices.

## Phase 1: Migrate to Timber with Basic File Logging

### Files to Process (app/src/main/java/**/*.kt):

- [ ] `com.expensemanager.app.data.repository.ExpenseRepository.kt`
- [ ] `com.expensemanager.app.services.SMSParsingService.kt`
- [ ] `com.expensemanager.app.services.TransactionFilterService.kt`
- [ ] `com.expensemanager.app.services.TransactionParsingService.kt`
- [ ] `com.expensemanager.app.ui.messages.MessagesFragment.kt`
- [ ] `com.expensemanager.app.ui.profile.LoggingSettingsFragment.kt`
- [ ] `com.expensemanager.app.data.repository.AIInsightsRepository.kt`
- [ ] `com.expensemanager.app.data.migration.DataMigrationManager.kt`
- [ ] `com.expensemanager.app.data.storage.TransactionStorage.kt`
- [ ] `com.expensemanager.app.di.NetworkModule.kt`
- [ ] `com.expensemanager.app.domain.usecase.category.GetCategorySpendingUseCase.kt`
- [ ] `com.expensemanager.app.domain.usecase.category.UpdateCategoryUseCase.kt`
- [ ] `com.expensemanager.app.domain.usecase.dashboard.GetBudgetStatusUseCase.kt`
- [ ] `com.expensemanager.app.domain.usecase.dashboard.GetDashboardDataUseCase.kt`
- [ ] `com.expensemanager.app.domain.usecase.dashboard.GetSpendingTrendsUseCase.kt`
- [ ] `com.expensemanager.app.domain.usecase.transaction.AddTransactionUseCase.kt`
- [ ] `com.expensemanager.app.domain.usecase.transaction.DeleteTransactionUseCase.kt`
- [ ] `com.expensemanager.app.domain.usecase.transaction.GetTransactionsUseCase.kt`
- [ ] `com.expensemanager.app.domain.usecase.transaction.UpdateTransactionUseCase.kt`
- [ ] `com.expensemanager.app.ui.categories.CategoriesViewModel.kt`
- [ ] `com.expensemanager.app.ui.categories.CategoryTransactionsViewModel.kt`
- [ ] `com.expensemanager.app.ui.dashboard.DashboardFragment.kt`
- [ ] `com.expensemanager.app.ui.dashboard.DashboardViewModel.kt`
- [ ] `com.expensemanager.app.ui.insights.InsightsFragment.kt`
- [ ] `com.expensemanager.app.ui.insights.InsightsViewModel.kt`
- [ ] `com.expensemanager.app.ui.merchant.MerchantTransactionsViewModel.kt`
- [ ] `com.expensemanager.app.ui.profile.BudgetGoalsViewModel.kt`
- [ ] `com.expensemanager.app.ui.profile.ExportDataFragment.kt`
- [ ] `com.expensemanager.app.ui.profile.ProfileFragment.kt`
- [ ] `com.expensemanager.app.ui.profile.SettingsFragment.kt`
- [ ] `com.expensemanager.app.ui.transaction.TransactionDetailsFragment.kt`
- [ ] `com.expensemanager.app.ui.transaction.TransactionDetailsViewModel.kt`
- [ ] `com.expensemanager.app.utils.BudgetManager.kt`
- [ ] `com.expensemanager.app.utils.CategoryManager.kt`
- [ ] `com.expensemanager.app.utils.DataMigrationHelper.kt`
- [ ] `com.expensemanager.app.utils.ExclusionMigrationHelper.kt`
- [ ] `com.expensemanager.app.utils.SMSHistoryReader.kt`
- [ ] `com.expensemanager.app.utils.SMSReceiver.kt`
- [ ] `com.expensemanager.app.services.TransactionFilterService.kt`
- [ ] `com.expensemanager.app.services.TransactionParsingService.kt`
- [ ] `com.expensemanager.app.services.SMSParsingService.kt`
- [ ] `com.expensemanager.app.services.SmartCategorizationService.kt`

## Progress:

- [x] `AppLogger.kt` (Completed: Migrated to Timber, removed Logback dependencies, implemented file logging)
- [ ] `com.expensemanager.app.data.repository.ExpenseRepository.kt` (Currently processing)

## Notes:

- We will replace `org.slf4j.Logger` and `LoggerFactory` with `appLogger` calls.
- `appLogger` is already injected into many classes. For others, we might need to add it.
- We will ensure `Timber.plant(appLogger)` is called once in `ExpenseManagerApplication.kt`.