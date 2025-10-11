# Repository Guidelines

## Project Structure & Module Organization
- `app/` is the primary Android module; Kotlin sources live under `app/src/main/java/com/expensemanager/app`, resources in `app/src/main/res`, and dependency injection wiring under `app/src/main/java/.../di`.
- Unit tests sit in `app/src/test`, instrumentation tests in `app/src/androidTest`, and shared fixtures like `test_sms_samples.kt` aid SMS parsing scenarios.
- `ui-sample/` hosts static HTML mockups for design reference, while helper scripts such as `convert_timber_to_log.sh` and `convert_to_structured_logger.py` support ongoing logging refactors.

## Build, Test, and Development Commands
- `./gradlew assembleDebug` builds the debug APK with current sources and resources.
- `./gradlew test` runs the JVM unit suite (MockK, coroutines, Room, Hilt).
- `./gradlew connectedAndroidTest` executes instrumentation tests on an attached device or emulator using the custom `HiltTestRunner`.
- `./gradlew lint` performs Android Lint checks; resolve warnings before merging.

## Coding Style & Naming Conventions
- Follow idiomatic Kotlin: 4-space indentation, trailing commas for multiline lists, `UpperCamelCase` for classes, `lowerCamelCase` for functions and properties, and `SCREAMING_SNAKE_CASE` for constants.
- Prefer expression-bodied functions when concise, but favor clarity for asynchronous flows and Hilt modules.
- Use `StructuredLogger` and the logging utilities under `app/src/main/java/.../utils/logging` instead of raw `Log` or `Timber` while the migration completes.

## Testing Guidelines
- Mirror production package structure in tests; name test classes `<Feature>Test` and methods with `should...` descriptions for readability.
- Mock network and repository layers with MockK, and use coroutines test dispatchers to control threading.
- Add instrumentation tests for navigation flows touching `NavHostFragment` and permissions logic in `MainActivity`; capture new regression risks with UI screenshots when practical.

## Commit & Pull Request Guidelines
- Base commit messages on present-tense summaries similar to the history (e.g., `Fix sign-in flow`); when scope is wider, add a short body detailing rationale and testing.
- PRs must state the user impact, list verification steps (`./gradlew test`, lint, device runs), link related issues, and attach UI captures for visual changes.
- Request review from core maintainers before merge and ensure CI (if enabled) is green.

## Configuration Tips
- Keep `google-services.json` and other secrets out of version control; rely on the provided stub for local testing.
- Update build config flags in `app/src/main/java/com/expensemanager/app/core` in tandem with environment changes to avoid runtime mismatches.
