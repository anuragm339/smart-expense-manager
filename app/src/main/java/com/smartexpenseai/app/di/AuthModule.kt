package com.smartexpenseai.app.di

import com.smartexpenseai.app.auth.AuthManager
import com.smartexpenseai.app.auth.GoogleAuthManager
import com.smartexpenseai.app.auth.MockAuthManager
import com.smartexpenseai.app.core.DebugConfig
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
