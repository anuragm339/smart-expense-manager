package com.expensemanager.app.di

import com.expensemanager.app.auth.AuthManager
import com.expensemanager.app.auth.GoogleAuthManager
import com.expensemanager.app.auth.MockAuthManager
import com.expensemanager.app.core.DebugConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideAuthManager(
        mockAuthManager: MockAuthManager,
        googleAuthManager: GoogleAuthManager
    ): AuthManager {
        return if (DebugConfig.useMockAuth) {
            mockAuthManager
        } else {
            googleAuthManager
        }
    }
}
