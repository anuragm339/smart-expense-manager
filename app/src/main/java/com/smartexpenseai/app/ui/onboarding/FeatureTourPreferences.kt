package com.smartexpenseai.app.ui.onboarding

import android.content.Context

object FeatureTourPreferences {
    private const val PREFS_NAME = "onboarding_preferences"
    private const val KEY_COMPLETED_VERSION = "completed_version"
    private const val CURRENT_TOUR_VERSION = 2

    fun shouldShow(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_COMPLETED_VERSION, 0) < CURRENT_TOUR_VERSION

    fun markCompleted(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_COMPLETED_VERSION, CURRENT_TOUR_VERSION)
            .apply()
    }
}
