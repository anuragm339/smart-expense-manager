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

    fun error(where: String, what: Any, throwable: Throwable? = null) {
        val message = formatMessage(where, what, throwable?.message)
        Timber.tag(featureTag).e(throwable, message)
    }

    fun warn(where: String, what: Any, why: String? = null) {
        Timber.tag(featureTag).w(formatMessage(where, what, why))
    }

    fun warnWithThrowable(where: String, what: Any, throwable: Throwable) {
        val message = formatMessage(where, what, throwable.message)
        Timber.tag(featureTag).w(throwable, message)
    }
}
