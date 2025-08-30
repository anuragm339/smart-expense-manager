package com.expensemanager.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * A custom test runner that uses HiltTestApplication for dependency injection in tests.
 * This runner is essential for running Hilt-based Android instrumentation tests.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}