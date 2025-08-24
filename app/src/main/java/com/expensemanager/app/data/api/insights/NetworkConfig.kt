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
    
    // AI API base URL - Updated to point to localhost
    private const val BASE_URL = "http://localhost:8080/"
    
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
            .addInterceptor { chain ->
                // Fix malformed JSON responses (remove markdown code blocks)
                val response = chain.proceed(chain.request())
                if (response.isSuccessful && response.body != null) {
                    val originalBody = response.body!!.string()
                    val cleanedBody = originalBody
                        .replace("```json", "")
                        .replace("```", "")
                        .trim()
                    
                    Log.d(TAG, "Cleaned JSON response: ${cleanedBody.take(200)}...")
                    
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