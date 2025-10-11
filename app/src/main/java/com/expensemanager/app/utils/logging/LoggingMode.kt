package com.expensemanager.app.utils.logging

/**
 * Quick Logging Mode Switcher for Bug Fixing
 *
 * INSTRUCTIONS:
 * 1. Change the MODE value below to focus on specific bug
 * 2. Rebuild and run the app
 * 3. Check logcat for focused logs
 *
 * Available Modes:
 * - BUG_1: SMS Import Permission Timing (SMS, Migration logs)
 * - BUG_2: Dashboard Value Inconsistency (Dashboard, Database logs)
 * - BUG_3: Category Classification (Categories, Merchant logs)
 * - ALL_BUGS: All critical bug-related logs
 * - NORMAL: All features enabled (default)
 * - MINIMAL: Only errors (production-like)
 */

object LoggingMode {

    enum class Mode {
        BUG_1,          // SMS Import Permission Timing
        BUG_2,          // Dashboard Value Inconsistency
        BUG_3,          // Category Classification
        ALL_BUGS,       // All critical bugs
        NORMAL,         // All features (default)
        MINIMAL         // Errors only
    }

    /**
     * 🔧 CHANGE THIS VALUE TO SWITCH LOGGING MODE
     */
    val CURRENT_MODE = Mode.NORMAL  // ← Change this to focus on different bug

    /**
     * Get description of current mode
     */
    fun getDescription(): String {
        return when (CURRENT_MODE) {
            Mode.BUG_1 -> "🐛 BUG #1: SMS Import Permission - Focus on SMS, Migration"
            Mode.BUG_2 -> "🐛 BUG #2: Dashboard Values - Focus on Dashboard, Database"
            Mode.BUG_3 -> "🐛 BUG #3: Category Classification - Focus on Categories, Merchant"
            Mode.ALL_BUGS -> "🐛 ALL BUGS: All critical features enabled"
            Mode.NORMAL -> "✅ NORMAL: All features enabled"
            Mode.MINIMAL -> "📉 MINIMAL: Errors only"
        }
    }
}
