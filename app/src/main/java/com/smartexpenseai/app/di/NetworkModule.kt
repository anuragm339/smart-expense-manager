package com.smartexpenseai.app.di

import timber.log.Timber
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.smartexpenseai.app.data.api.insights.AIInsightsApiService
import com.smartexpenseai.app.data.api.insights.BackendInsightsService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * NetworkModule provides network-related dependencies.
 * Handles Retrofit, OkHttpClient, and API service configurations.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    // Network configuration constants
    private const val BASE_URL = "https://smart-expense-bchtgdg7ahbhdmhy.canadacentral-01.azurewebsites.net/"
    private const val CONNECT_TIMEOUT = 30L
    private const val READ_TIMEOUT = 60L
    private const val WRITE_TIMEOUT = 60L
    private const val TAG = "NetworkModule"
    
    /**
     * Provides OkHttpClient with logging and common interceptors
     * Configured for AI API communication with JSON cleanup
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Timber.tag(TAG).d(message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                // Add common headers
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", "ExpenseManager-Android/1.0")
                
                // Add API key if available (can be extended later)
                // .header("Authorization", "Bearer $API_KEY")
                
                chain.proceed(requestBuilder.build())
            }
            .addInterceptor { chain ->
                // Fix malformed JSON responses (remove markdown code blocks)
                val response = chain.proceed(chain.request())
                if (response.isSuccessful && response.body != null) {
                    val originalBody = response.body!!.string()
                    val cleanedBody = originalBody
                        .replace("```json", "")
                        .replace("```", "")
                        .trim()
                    
                    Timber.tag(TAG).d("Cleaned JSON response: ${cleanedBody.take(200)}...")
                    
                    val newBody = okhttp3.ResponseBody.create(
                        response.body!!.contentType(),
                        cleanedBody
                    )
                    response.newBuilder().body(newBody).build()
                } else {
                    response
                }
            }
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .build()
    }
    
    /**
     * Provides Retrofit instance configured for AI Insights API
     * Uses Gson for JSON serialization/deserialization
     */
    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    /**
     * Provides AI Insights API service
     * Used for fetching spending insights and recommendations
     */
    @Provides
    @Singleton
    fun provideAIInsightsApiService(
        retrofit: Retrofit
    ): AIInsightsApiService {
        return retrofit.create(AIInsightsApiService::class.java)
    }

    /**
     * Provides Backend Insights service
     * Used for backend proxy communication with Claude AI API
     */
    @Provides
    @Singleton
    fun provideBackendInsightsService(
        retrofit: Retrofit
    ): BackendInsightsService {
        return retrofit.create(BackendInsightsService::class.java)
    }
}