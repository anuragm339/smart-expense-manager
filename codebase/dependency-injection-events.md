# Dependency Injection and Events

## Hilt graph

`ExpenseManagerApplication` is the Hilt application root. Modules installed in `SingletonComponent` provide:

- Room database and all DAOs.
- `ExpenseRepository` bindings for transaction, category, merchant, and dashboard contracts.
- Authentication implementation.
- Retrofit, OkHttp, and AI API services.
- SharedPreferences stores, parser components, managers, and formatting services.

ViewModels use `@HiltViewModel`; Activities and Fragments use `@AndroidEntryPoint`.

## Manual graph still present

Some receivers and Fragments call `ExpenseRepository.getInstance()`, construct parser dependencies, or use `AIInsightsRepository.getInstance()`. This creates a second object graph beside Hilt and can produce different optional dependencies and caches.

## Event propagation

The app currently uses package broadcasts such as:

- `com.expensemanager.NEW_TRANSACTION_ADDED`
- `com.expensemanager.CATEGORY_UPDATED`
- `com.expensemanager.MERCHANT_CATEGORY_CHANGED`
- `com.smartexpenseai.app.DATA_CHANGED`

Fragments and one ViewModel dynamically register receivers and refresh their state when these actions arrive.

## Recommended boundary

Use one injected repository graph and expose database-backed Flow streams or a typed in-process event coordinator. Room invalidation and StateFlow can then replace most string-based broadcasts and manual refresh calls.

## Key sources

- `ExpenseManagerApplication.kt`
- `di/AppModule.kt`
- `di/AuthModule.kt`
- `di/DatabaseModule.kt`
- `di/NetworkModule.kt`
- `di/RepositoryModule.kt`
