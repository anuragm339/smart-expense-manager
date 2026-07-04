# Architecture

## System shape

```text
Android framework events and user actions
                 |
Activities / Fragments / BroadcastReceivers
                 |
ViewModels and use cases
                 |
ExpenseRepository and feature services
                 |
Room DAOs / SharedPreferences / Retrofit
```

The project aims for MVVM with clean-domain boundaries, but it is currently hybrid. Hilt injects most ViewModels and services while some Fragments and receivers construct repositories through `getInstance()` factories.

## Main layers

- **Application shell:** `ExpenseManagerApplication`, `SplashActivity`, `LoginActivity`, and `MainActivity`.
- **UI:** feature packages under `ui/` for dashboard, messages, categories, insights, profile, merchant, and transaction details.
- **Domain:** repository contracts and use cases under `domain/`.
- **Data:** Room entities, DAOs, database migrations, API models, and repository implementations under `data/`.
- **Services:** parsing, analytics, date ranges, filtering, exporting, retries, and insight preparation.
- **Cross-cutting:** Hilt modules, structured logging, notifications, and Android broadcasts.

## Core data path

```text
Bank SMS
  -> SMSReceiver or historical scan
  -> UnifiedSMSParser + bank_rules.json
  -> TransactionEntity
  -> TransactionDao
  -> merchant categorization
  -> dashboard/messages/categories/insights
```

## Source map

- App manifest: `app/src/main/AndroidManifest.xml`
- Navigation graph: `app/src/main/res/navigation/nav_graph.xml`
- Main repository: `app/src/main/java/com/smartexpenseai/app/data/repository/ExpenseRepository.kt`
- Database: `app/src/main/java/com/smartexpenseai/app/data/database/ExpenseDatabase.kt`
- DI modules: `app/src/main/java/com/smartexpenseai/app/di/`
- UI features: `app/src/main/java/com/smartexpenseai/app/ui/`

## Architectural constraint

Transactions store `category_id` directly while merchants also own a category. Every merchant category change must therefore update both records atomically to prevent reporting drift.
