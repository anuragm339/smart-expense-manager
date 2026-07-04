# Build, Testing, and Known Risks

## Build commands

```bash
./gradlew assembleDebug
./gradlew test
./gradlew connectedAndroidTest
./gradlew lint
```

The app currently compiles with Android Gradle Plugin 8.1.2, Kotlin 1.9.22, compile SDK 35, target SDK 35, and minimum SDK 23.

## Current test state

The previous local tests used the old `com.expensemanager.app` package and undeclared Mockito dependencies, so they were not a reliable suite and were removed during cleanup. New tests should use `com.smartexpenseai.app` and be committed under `app/src/test` or `app/src/androidTest`.

Highest-value coverage:

- Bank rule parser fixtures and multipart SMS handling.
- Duplicate IDs across real-time and historical ingestion.
- Room migrations from every supported schema version.
- Category reassignment across merchants and transactions.
- Exclusion and soft-delete behavior.
- Authentication and permission navigation.

## Known engineering risks

1. Signing passwords are stored in `app/build.gradle`; rotate them and load secrets outside source control.
2. The legacy AI network factory disables certificate and hostname validation.
3. HTTP BODY logging is enabled without a release guard.
4. The manifest globally allows cleartext traffic.
5. SMS multipart PDUs are processed separately and share one `PendingResult`.
6. Parsed transaction dates combine the SMS date with processing time.
7. Hilt and manual singleton graphs coexist.
8. Several Fragments and ViewModels are large and retain legacy duplicate state.
9. The toolchain emits compatibility warnings for compile SDK 35 and Gradle 9 migration.

## Cleanup policy

Generated builds, local databases, logs, IDE state, credentials, screenshots, test utilities, design samples, and release artifacts are ignored. Production source, release automation, logging helpers, and this documentation remain part of the workspace.
