package com.expensemanager.app

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Test runner for unit tests.
 * This class organizes and runs all test suites for the Smart Expense Manager app.
 * Includes comprehensive tests for ViewModels, Use Cases, and Repository integration.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    // ViewModel Tests
    com.expensemanager.app.ui.dashboard.DashboardViewModelTest::class,
    com.expensemanager.app.ui.messages.MessagesViewModelTest::class,
    com.expensemanager.app.ui.categories.CategoriesViewModelTest::class,
    
    // Use Case Tests
    com.expensemanager.app.domain.usecase.dashboard.GetDashboardDataUseCaseTest::class,
    com.expensemanager.app.domain.usecase.transaction.GetTransactionsUseCaseTest::class
)
class TestRunner