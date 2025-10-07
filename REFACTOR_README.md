# Refactor Playbook

This document defines the ground rules for the ongoing refactor of Smart Expense Manager. It consolidates the constraints, priorities, and workflow so we can resume quickly after any pause.

## Core Goals
- Keep every Kotlin/Java class under **1,000 lines**; split or extract helpers when classes grow larger.
- Remove duplicate logic and dead code as we encounter it; prefer shared utilities over copy/paste.
- Standardize on `StructuredLogger` for all logging; it wraps Timber internally so we retain existing filters.
- Preserve user-facing layouts exactly as they are; do not edit XML resources unless explicitly approved.
- Work package-by-package so changes remain focused and reviewable.

## Day-to-Day Workflow
1. Pick a single package (e.g., `data/repository`, `ui/dashboard`).
2. Inventory the classes in that package. Note any that exceed 1K lines or contain obvious duplication.
3. Plan refactors for that package only: extract helpers, move shared logic, and migrate logging calls.
4. Replace Timber usage with `StructuredLogger`. Use `ExpenseRepository.kt` as the reference style until all call sites conform.
5. For UI code, push presentation wiring into a binder/adapter layer while keeping data orchestration inside the ViewModel.
6. Run targeted checks (`./gradlew test`, `lint`) when practical for the touched scope.
7. Record what changed, what is pending, and any follow-up actions in `REFACTOR_TRACKER.md`.

## Structured Logger Guidelines
- Instantiate a single `StructuredLogger` per class with the relevant `LogConfig.FeatureTags` value.
- Log method names in the `where` argument and supply concise context via `what`; include `why` only when it clarifies the decision path.
- When migrating, search for formatted Timber calls (`Timber.tag(...)`) and translate them to structured messages rather than copying strings verbatim.
- Refer to `app/src/main/java/com/expensemanager/app/data/repository/ExpenseRepository.kt` for the current formatting and tag usage pattern.
- Legacy helpers like `convert_timber_to_log.sh` and `convert_to_structured_logger.py` can speed up rote replacements, but always review the output.

## UI Architecture Expectations
- ViewModels remain the orchestration layer: they gather data, issue commands, and expose state.
- Introduce or update binder/adapters to own UI-specific logic (formatting, click handling, view binding). Keep binders free of data fetching responsibilities.
- Fragment/Activity classes should delegate to the binder for rendering and to the ViewModel for state access.
- Avoid modifying XML layout resources; achieve UI changes through binders, adapters, or theming attributes already present.

## Duplication & Cleanup Checklist
- Consolidate repeated parsing, formatting, or Room queries into shared utilities under the appropriate module.
- Remove unused methods, stale feature flags, and commented-out code once confirmed safe.
- When extracting shared logic, ensure naming mirrors the production package structure so tests can follow the same path later.

## Testing Approach
- Unit and view tests are lower priority during this refactor, but track any gaps you introduce.
- When time allows, add tests that cover newly extracted logic or critical regressions (repository queries, navigation guards, etc.).
- Note deferred test work in the tracker so it can be revisited in a focused pass.

## Tracker Discipline
- Log every meaningful action in `REFACTOR_TRACKER.md`: package touched, summary of work, date, and pending items.
- Include quick links to PRs or commits if relevant.
- If you pause mid-package, add clear TODO markers so the next session can resume without re-analysis.

## Reference Material
- `LOGGING_REFACTOR_PLAN.md` – deep dive into the StructuredLogger strategy.
- `AGENTS.md` – repository-wide guidelines (build commands, testing, contribution standards).
- `ui-sample/` – static mockups for verifying UI intent without touching layout XML.
