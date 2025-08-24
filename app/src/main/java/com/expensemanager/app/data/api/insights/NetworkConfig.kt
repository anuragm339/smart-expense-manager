package com.expensemanager.app.data.api.insights

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Network configuration and factory for AI Insights API
 */
object NetworkConfig {
    
    // TODO: Replace with your actual AI API base URL
    private const val BASE_URL = "https://your-ai-api-server.com/"
    
    // Network timeouts
    private const val CONNECT_TIMEOUT = 30L
    private const val READ_TIMEOUT = 60L
    private const val WRITE_TIMEOUT = 60L
    
    private const val TAG = "NetworkConfig"
    
    /**
     * Create and configure OkHttpClient with interceptors
     */
    private fun createOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.d(TAG, message)
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
                
                // Add API key if available (you can add this later)
                // .header("Authorization", "Bearer $API_KEY")
                
                chain.proceed(requestBuilder.build())
            }
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .build()
    }
    
    /**
     * Create and configure Retrofit instance
     */
    private fun createRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(createOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    /**
     * Get AI Insights API service instance
     */
    fun getAIInsightsApiService(): AIInsightsApiService {
        return createRetrofit().create(AIInsightsApiService::class.java)
    }
}

/**
 * Simple factory for creating API services
 * In a production app, this would be handled by dependency injection (Hilt/Dagger)
 */
object ApiServiceFactory {
    
    @Volatile
    private var aiInsightsApiService: AIInsightsApiService? = null
    
    /**
     * Get singleton instance of AI Insights API service
     */
    fun getAIInsightsApiService(): AIInsightsApiService {
        return aiInsightsApiService ?: synchronized(this) {
            aiInsightsApiService ?: NetworkConfig.getAIInsightsApiService().also { 
                aiInsightsApiService = it 
            }
        }
    }
}