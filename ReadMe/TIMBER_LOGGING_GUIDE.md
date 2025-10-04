# Timber Logging System - Usage Guide

## Overview

Your Android app now uses an enhanced Timber logging system with feature-specific control. You can enable/disable logs for specific features like Dashboard, SMS processing, Transaction handling, etc.

## 🚀 Quick Start

### Basic Usage in Code

```kotlin
import timber.log.Timber
import com.expensemanager.app.utils.logging.LogConfig

// Dashboard logs
Timber.tag(LogConfig.FeatureTags.DASHBOARD).d("Loading dashboard data...")
Timber.tag(LogConfig.FeatureTags.DASHBOARD).e(exception, "Failed to load data")

// SMS processing logs
Timber.tag(LogConfig.FeatureTags.SMS).i("Processing SMS from bank")

// Transaction logs
Timber.tag(LogConfig.FeatureTags.TRANSACTION).w("Transaction validation failed")

// Database logs
Timber.tag(LogConfig.FeatureTags.DATABASE).d("Inserting %d transactions", count)
```

## 🎯 Feature-Specific Logging Control

### Available Feature Tags
- `DASHBOARD` - Dashboard screen and use cases
- `SMS` - SMS processing and parsing
- `TRANSACTION` - Transaction operations
- `CATEGORIES` - Category management
- `DATABASE` - Database operations
- `NETWORK` - API calls and network operations
- `UI` - UI interactions and fragment lifecycle
- `MERCHANT` - Merchant aliasing and management
- `INSIGHTS` - AI insights and analytics
- `MIGRATION` - Data migration operations

### Runtime Configuration

```kotlin
// Inject LoggingManager in your class
@Inject
lateinit var loggingManager: LoggingManager

// Enable only Dashboard logging for focused debugging
loggingManager.enableOnlyDashboard()

// Enable only SMS processing logs
loggingManager.enableOnlySms()

// Enable multiple features
loggingManager.enableFeatures(
    LogConfig.FeatureTags.DASHBOARD,
    LogConfig.FeatureTags.TRANSACTION
)

// Enable all features
loggingManager.enableAllFeatures()

// Disable all features (except critical ones)
loggingManager.disableAllFeatures()
```

## 📁 File-Based Logging

### Log File Locations
- **Internal:** `/data/data/com.expensemanager.app/cache/logs/`
- **External:** `/Android/data/com.expensemanager.app/files/logs/` (if enabled)

### Log File Types
- `expense_manager_all.log` - All logs combined
- `expense_manager_dashboard.log` - Dashboard-specific logs
- `expense_manager_sms.log` - SMS processing logs
- `expense_manager_transaction.log` - Transaction logs
- `expense_manager_database.log` - Database operation logs

### File Management

```kotlin
// Get log statistics
val stats = loggingManager.getLogStatistics()
println("Total log files: ${stats.fileCount}")
println("Total size: ${"%.2f".format(stats.totalSizeMB)} MB")

// Clear all log files
loggingManager.clearAllLogs()

// Export logs as ZIP
val zipFile = loggingManager.exportLogsAsZip()

// Share logs via intent
val shareIntent = loggingManager.shareLogs()
startActivity(shareIntent)
```

## 🔧 Configuration Examples

### Example 1: Debug Dashboard Issues
```kotlin
// Disable all logging except Dashboard
loggingManager.enableOnlyDashboard()
loggingManager.setLogLevelDebug()

// Your dashboard code will now log extensively
// SMS, Transaction logs are disabled for cleaner output
```

### Example 2: Debug SMS Processing
```kotlin
// Enable SMS and Database logs for SMS processing debugging
loggingManager.enableFeatures(
    LogConfig.FeatureTags.SMS,
    LogConfig.FeatureTags.DATABASE
)
loggingManager.setLogLevelVerbose()

// Now you'll see SMS parsing + database operations
```

### Example 3: Production Logging
```kotlin
// In production, log only errors
loggingManager.setLogLevelError()
loggingManager.enableAllFeatures() // But only errors will be logged

// Or disable file logging completely
loggingManager.disableFileLogging()
```

## 📊 Log Levels

```kotlin
// Set different log levels
loggingManager.setLogLevelVerbose()  // Most detailed
loggingManager.setLogLevelDebug()    // Development
loggingManager.setLogLevelInfo()     // General info
loggingManager.setLogLevelWarn()     // Warnings only
loggingManager.setLogLevelError()    // Errors only
```

## 🧪 Testing the System

```kotlin
// Test all feature logging
loggingManager.testAllFeatureLogging()

// Run diagnostic to check configuration
val diagnostic = loggingManager.runLoggingDiagnostic()
println(diagnostic)

// Check current status
val status = loggingManager.getLoggingStatus()
println("Enabled features: ${status.enabledFeatures}")
```

## 💡 Best Practices

### 1. Use Appropriate Feature Tags
```kotlin
// ✅ Good - Clear feature context
Timber.tag(LogConfig.FeatureTags.DASHBOARD).d("Loading dashboard for period: %s", period)

// ❌ Bad - Generic tag
Timber.d("Loading data")
```

### 2. Format Strings Properly
```kotlin
// ✅ Good - Use Timber's formatting
Timber.tag(LogConfig.FeatureTags.TRANSACTION).d("Processing %d transactions for merchant %s", count, merchant)

// ❌ Bad - String interpolation (performance impact)
Timber.tag(LogConfig.FeatureTags.TRANSACTION).d("Processing $count transactions for merchant $merchant")
```

### 3. Handle Exceptions Properly
```kotlin
// ✅ Good - Exception as first parameter
Timber.tag(LogConfig.FeatureTags.DATABASE).e(exception, "Failed to insert transaction")

// ❌ Bad - Exception in message
Timber.tag(LogConfig.FeatureTags.DATABASE).e("Failed to insert: ${exception.message}")
```

### 4. Use Conditional Logging for Expensive Operations
```kotlin
// For expensive string building
if (Timber.treeCount > 0) {
    val expensiveDebugInfo = buildComplexDebugString()
    Timber.tag(LogConfig.FeatureTags.DASHBOARD).d("Debug info: %s", expensiveDebugInfo)
}
```

## 🎛️ Advanced Configuration

### Programmatic Setup in Application Class
```kotlin
class ExpenseManagerApplication : Application() {
    
    @Inject lateinit var loggingManager: LoggingManager
    
    override fun onCreate() {
        super.onCreate()
        
        // Setup logging based on build variant
        if (BuildConfig.DEBUG) {
            loggingManager.enableAllFeatures()
            loggingManager.setLogLevelDebug()
        } else {
            loggingManager.setLogLevelWarn()
            loggingManager.disableFileLogging() // Save battery/storage
        }
        
        // Test the system on debug builds
        if (BuildConfig.DEBUG) {
            loggingManager.testAllFeatureLogging()
        }
    }
}
```

## 📱 Integration with Settings Screen

You can integrate the logging controls into your app's settings screen:

```kotlin
// In your Settings Fragment/Activity
class DeveloperSettingsFragment : Fragment() {
    
    @Inject lateinit var loggingManager: LoggingManager
    
    private fun setupLoggingControls() {
        // Feature toggles
        binding.dashboardLogsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                loggingManager.enableFeatures(LogConfig.FeatureTags.DASHBOARD)
            }
        }
        
        // Quick presets
        binding.debugDashboardButton.setOnClickListener {
            loggingManager.enableOnlyDashboard()
            showToast("Dashboard logging enabled")
        }
        
        // Export logs button
        binding.exportLogsButton.setOnClickListener {
            val shareIntent = loggingManager.shareLogs()
            shareIntent?.let { startActivity(it) }
        }
    }
}
```

## 🚨 Important Notes

1. **Performance**: File logging runs on a background thread, so it won't block the UI
2. **Storage**: Log files are automatically rotated when they exceed 5MB
3. **Cleanup**: Old log files (>7 days) are automatically cleaned up
4. **Memory**: The system uses minimal memory overhead
5. **Thread Safety**: All logging operations are thread-safe

## 🔍 Troubleshooting

### No logs appearing in files?
```kotlin
// Check if file logging is enabled
val status = loggingManager.getLoggingStatus()
if (!status.isFileLoggingEnabled) {
    loggingManager.enableFileLogging()
}
```

### Too many log files?
```kotlin
// Clear old files
loggingManager.clearAllLogs()

// Check current statistics
val stats = loggingManager.getLogStatistics()
println("Current files: ${stats.fileCount}")
```

### Want to see all configuration?
```kotlin
// Print complete diagnostic
println(loggingManager.runLoggingDiagnostic())
```

---

## 🎉 Migration Complete!

Your app now has a powerful, feature-specific logging system that gives you fine-grained control over debug output. No more sifting through thousands of irrelevant log lines - just enable the features you're debugging!