# Structured Logging Implementation Plan

## 1. Objective

To refactor the application's logging to use a more structured, consistent, and readable format. This will improve debuggability and maintainability by standardizing how log messages are created.

## 2. Current System Analysis

The application currently uses a powerful and flexible logging system based on **Timber**. The key components are:
-   **`Timber`**: The core logging library.
-   **`LogConfig.kt`**: A class that enables/disables logging for specific application features (e.g., `DASHBOARD`, `SMS`).
-   **`TimberFileTree.kt`**: A custom Timber `Tree` that writes logs to files and handles log rotation and filtering based on `LogConfig`.

While powerful, the current logging calls are free-form, leading to inconsistent log messages.

## 3. Proposed Solution: `StructuredLogger`

We will introduce a new wrapper class, `StructuredLogger`, which is inspired by the structured logging patterns seen in backend frameworks.

**Benefits:**
-   **Consistency:** Enforces a `[ClassName.methodName] - message` format.
-   **Readability:** Makes logs easier to read and understand at a glance.
-   **Searchability:** The structured format makes it simple to filter logs by class or method.
-   **Integration:** It builds on top of the *existing* Timber setup, so we don't lose any of the current system's features (like feature-based filtering and file logging).

---

## 4. Step-by-Step Implementation Guide

### Step 1: Create the `StructuredLogger.kt` File

Create a new file in the `app/src/main/java/com/expensemanager/app/utils/logging/` directory.

**File:** `app/src/main/java/com/expensemanager/app/utils/logging/StructuredLogger.kt`

**Content:**
```kotlin
package com.expensemanager.app.utils.logging

import timber.log.Timber

/**
 * A structured logger that builds on top of the existing Timber and LogConfig setup.
 * It adds 'who' (className) and 'where' (methodName) to the log message.
 *
 * @param featureTag The primary feature tag from LogConfig.FeatureTags.
 * @param className The name of the class where the logger is used.
 */
class StructuredLogger(
    private val featureTag: String,
    private val className: String
) {
    private fun formatMessage(where: String, what: Any, why: String? = null): String {
        val builder = StringBuilder()
        // Format: [ClassName.methodName] - Log message - Why: details
        builder.append("[$className.$where] - $what")
        why?.let { builder.append(" - Why: $it") }
        return builder.toString()
    }

    fun info(where: String, what: Any, why: String? = null) {
        // Your existing LogConfig and TimberFileTree will handle the filtering and file writing
        Timber.tag(featureTag).i(formatMessage(where, what, why))
    }

    fun debug(where: String, what: Any, why: String? = null) {
        Timber.tag(featureTag).d(formatMessage(where, what, why))
    }

    fun error(where: String, what: Any, throwable: Throwable?) {
        val message = formatMessage(where, what, throwable?.message)
        Timber.tag(featureTag).e(throwable, message)
    }

    fun warn(where: String, what: Any, why: String? = null) {
        Timber.tag(featureTag).w(formatMessage(where, what, why))
    }
}
```

### Step 2: Select a Target Class for Initial Refactoring

To demonstrate the process, we will refactor `app/src/main/java/com/expensemanager/app/ui/dashboard/DashboardViewModel.kt`. This is a good candidate as it's a core feature.

### Step 3: Refactor `DashboardViewModel.kt`

Modify the `DashboardViewModel.kt` file to use the new `StructuredLogger`.

**Before:**
```kotlin
// app/src/main/java/com/expensemanager/app/ui/dashboard/DashboardViewModel.kt

import timber.log.Timber
// ...

class DashboardViewModel @Inject constructor(
    // ...
) : BaseViewModel() {

    // ...

    private fun getDashboardData() {
        // ...
        Timber.tag(LogConfig.FeatureTags.DASHBOARD).d("Getting dashboard data...")
        // ...
        viewModelScope.launch {
            // ...
            when (val result = getDashboardDataUseCase.execute(dateRange)) {
                is Result.Success -> {
                    // ...
                    Timber.tag(LogConfig.FeatureTags.DASHBOARD).i("Dashboard data loaded successfully.")
                }
                is Result.Error -> {
                    // ...
                    Timber.tag(LogConfig.FeatureTags.DASHBOARD).e(result.exception, "Error getting dashboard data.")
                }
            }
        }
    }
}
```

**After:**
```kotlin
// app/src/main/java/com/expensemanager/app/ui/dashboard/DashboardViewModel.kt

import com.expensemanager.app.utils.logging.LogConfig
import com.expensemanager.app.utils.logging.StructuredLogger
// ...

class DashboardViewModel @Inject constructor(
    // ...
) : BaseViewModel() {

    private val logger = StructuredLogger(LogConfig.FeatureTags.DASHBOARD, "DashboardViewModel")

    // ...

    private fun getDashboardData() {
        // ...
        logger.debug(where = "getDashboardData", what = "Starting to fetch dashboard data.")
        // ...
        viewModelScope.launch {
            // ...
            when (val result = getDashboardDataUseCase.execute(dateRange)) {
                is Result.Success -> {
                    // ...
                    logger.info(
                        where = "getDashboardData",
                        what = "Dashboard data loaded successfully.",
                        why = "Found ${result.data.expenses.size} expenses."
                    )
                }
                is Result.Error -> {
                    // ...
                    logger.error(
                        where = "getDashboardData",
                        what = "Error getting dashboard data.",
                        throwable = result.exception
                    )
                }
            }
        }
    }
}
```

### Step 4: Verify the Changes

1.  Run the application on an emulator or device.
2.  Navigate to the Dashboard screen.
3.  Open the **Logcat** window in Android Studio.
4.  Filter by the tag `DASHBOARD`.
5.  You should see the new, structured log messages:

    ```
    I/DASHBOARD: [DashboardViewModel.getDashboardData] - Dashboard data loaded successfully. - Why: Found 25 expenses.
    ```

### Step 5: Gradual Rollout Plan

This change does not need to be applied to the entire app at once. You can refactor the codebase incrementally.

1.  **Start with ViewModels:** Refactor all ViewModels to use the `StructuredLogger`.
2.  **Continue with Repositories:** Apply the same pattern to the data layer.
3.  **Refactor UseCases and Services:** Continue through the different layers of your architecture.
4.  **Create a Team Convention:** Document this new logging style as the standard for any new code being written.

## 5. Conclusion

By adopting the `StructuredLogger`, we can significantly improve the quality and consistency of our application's logs with minimal performance impact. This will make future debugging and monitoring much more efficient.
