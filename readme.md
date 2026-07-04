# Smart Expense AI

Smart Expense AI is an Android expense manager that parses bank SMS messages, stores transactions locally, categorizes merchants, tracks budgets, and generates financial insights.

## Codebase guide

- Open the [book interface](codebase/Index.html) through the local documentation server.
- Start with the [Markdown table of contents](codebase/README.md) when browsing in Git.
- See [build, testing, and known risks](codebase/build-testing-risks.md) before release work.

## Main capabilities

- Real-time and historical bank SMS ingestion.
- Rule-driven transaction parsing and merchant categorization.
- Dashboard totals, trends, categories, and top merchants.
- Searchable and filterable transaction history.
- Category, merchant, exclusion, and soft-delete management.
- Overall and category budget tracking.
- Cached local and remote AI insights.
- CSV, JSON, and report export.

## Technical stack

- Kotlin and XML/ViewBinding
- Android Navigation Component
- Hilt dependency injection
- Room database
- Coroutines, Flow, and StateFlow
- Retrofit and OkHttp
- Firebase and Google Sign-In
- MPAndroidChart

## Build commands

```bash
./gradlew assembleDebug
./gradlew test
./gradlew connectedAndroidTest
./gradlew lint
```

Local credentials, signing files, databases, build outputs, and `google-services.json` are excluded from version control. Do not place passwords or API secrets in source files or documentation.
