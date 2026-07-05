# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Smart Expense AI** is a shipping Android expense manager (package `com.smartexpenseai.app`, currently on the 3.x release line). It parses bank SMS messages, stores transactions locally in Room, categorizes merchants via a rule engine, tracks budgets, and generates local and remote AI-powered financial insights.

This is a mature, production codebase — not a planning-stage project. It has ~169 Kotlin sources, 58 layouts, and a Room database on schema version 14 with a full migration chain.

## Source of Truth

The maintained architecture book lives in [`codebase/`](codebase/README.md). Read it before non-trivial work:

- [`codebase/README.md`](codebase/README.md) — table of contents / reading order
- [`codebase/architecture.md`](codebase/architecture.md) — layers, data path, source map
- [`codebase/build-testing-risks.md`](codebase/build-testing-risks.md) — build config, test state, known risks
- Feature chapters: sms-ingestion, transaction-persistence, dashboard, categories-merchants, budgets, ai-insights, notifications, export-profile-settings, dependency-injection-events

See also [`AGENTS.md`](AGENTS.md) for contributor conventions.

## Development Commands

```bash
./gradlew assembleDebug        # Build debug APK
./gradlew installDebug         # Install on device
./gradlew test                 # JVM unit tests (see caveat below)
./gradlew connectedAndroidTest # Instrumentation tests on device/emulator
./gradlew lint                 # Android Lint
./gradlew clean                # Clean build
```

Versioning is automated: `versionCode`/`versionName` derive from the git commit count in `app/build.gradle`. Only bump `VERSION_BASE` for a major/minor release.

## Tech Stack (verified from app/build.gradle)

- **Language/UI**: Kotlin 1.9, XML layouts + ViewBinding, Material Components
- **SDK**: compileSdk/targetSdk 35, minSdk 23; AGP 8.1.2
- **Architecture**: MVVM with domain use-cases; hybrid — Hilt injects most ViewModels/services, but some Fragments and receivers still build repositories via `getInstance()` factories
- **DI**: Hilt 2.48 (plus some legacy manual singletons)
- **Database**: Room 2.5.0 (`expense_database`, schema v14) via KSP
- **Async**: Coroutines, Flow, StateFlow
- **Networking**: Retrofit 2.9 + OkHttp + Gson (AI insights backend)
- **Auth**: Firebase Auth 22.3.1 + Google Sign-In
- **Charts**: MPAndroidChart 3.1.0

## Code Layout (`app/src/main/java/com/smartexpenseai/app/`)

- `ui/` — feature packages: `dashboard`, `messages`, `categories`, `insights`, `profile`, `merchant`, `transaction`, `auth`, `base`, `settings`
- `domain/` — repository interfaces + use cases (category, dashboard, merchant, sms, transaction, insights)
- `data/` — `entities/`, `dao/`, `database/`, `converters/`, `repository/` (+ `internal/`), `api/insights/`, `models/`, `storage/`
- `parsing/` — `engine/` (`UnifiedSMSParser`, `MerchantRuleEngine`, `RuleLoader`, `ConfidenceCalculator`) and `models/`; rules are data-driven from `assets/bank_rules.json` and `assets/merchant_rules.json`
- `services/` — parsing, analytics, date ranges, filtering, export, retry, insight prep
- `di/` — Hilt modules (`App`, `Auth`, `Database`, `Network`, `Repository`)
- `auth/`, `notifications/`, `utils/` (incl. `SMSReceiver.kt`, `logging/`)

Key files: `MainActivity.kt`, `ExpenseManagerApplication.kt`, `data/database/ExpenseDatabase.kt`, `data/repository/ExpenseRepository.kt`, `res/navigation/nav_graph.xml`.

## Core Data Path

```
Bank SMS → SMSReceiver (or historical scan) → UnifiedSMSParser + bank_rules.json
        → TransactionEntity → TransactionDao → merchant categorization
        → dashboard / messages / categories / insights
```

## Important Constraints & Conventions

- **Category duality**: transactions store `category_id` directly *and* merchants own a category. Any merchant category change must update both atomically to avoid reporting drift.
- **Logging**: use `StructuredLogger` / the helpers under `utils/logging/` rather than raw `Log`/`Timber`.
- **Required permissions**: `READ_SMS`, `RECEIVE_SMS`, `INTERNET` (see `AndroidManifest.xml`).
- **Secrets**: keep `google-services.json`, signing/keystore passwords, and API secrets out of source. (Note: some are currently committed — see risks below — treat as tech debt, don't add more.)

## Testing Status

There is currently **no test suite on disk** — `app/src/test` and `app/src/androidTest` do not exist. Test dependencies (Hilt testing, Room testing, coroutines-test) are declared in `build.gradle` and ready to use. Old tests were removed during cleanup (they used the obsolete `com.expensemanager.app` package). New tests should use `com.smartexpenseai.app`. Highest-value coverage: bank-rule parser fixtures, duplicate IDs across real-time vs. historical ingestion, Room migrations, category reassignment, exclusion/soft-delete, auth/permission navigation.

## Known Risks (from codebase/build-testing-risks.md)

Signing passwords live in `app/build.gradle` and the keystore is committed; the AI network factory disables cert/hostname validation; HTTP BODY logging has no release guard; the manifest globally allows cleartext traffic; Hilt and manual singleton graphs coexist; several Fragments/ViewModels are large with legacy duplicate state.
