# Smart Expense AI Codebase Book

This book describes the Android application as it exists in the repository. Each chapter follows a user or system flow from its entry point to persistence, side effects, and UI state.

## Reading order

1. [Architecture](architecture.md)
2. [Startup and authentication](startup-auth.md)
3. [Permissions and navigation](permissions-navigation.md)
4. [SMS ingestion](sms-ingestion.md)
5. [Transaction persistence](transaction-persistence.md)
6. [Dashboard](dashboard.md)
7. [Messages and transaction details](messages-transactions.md)
8. [Categories and merchants](categories-merchants.md)
9. [Budgets](budgets.md)
10. [AI insights](ai-insights.md)
11. [Notifications](notifications.md)
12. [Export, profile, and settings](export-profile-settings.md)
13. [Dependency injection and events](dependency-injection-events.md)
14. [Build, testing, and known risks](build-testing-risks.md)

## Scope

- Production package: `com.smartexpenseai.app`
- Android module: `app`
- UI: XML layouts, Fragments, ViewBinding, Navigation Component
- State: ViewModels with StateFlow, plus several legacy broadcasts and direct helpers
- Storage: Room database `expense_database`, schema version 14
- External services: Google Sign-In and the AI insights backend

Open [Index.html](Index.html) through the local documentation server for the book interface.
