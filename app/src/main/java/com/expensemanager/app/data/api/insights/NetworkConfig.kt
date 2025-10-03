package com.expensemanager.app.data.api.insights

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Network configuration and factory for AI Insights API
 */
object NetworkConfig {

    // Backend API base URL - Replace with your actual backend URL
    private const val BACKEND_BASE_URL = "http://32679df5c841.ngrok-free.app/"
    // For local development: "http://10.0.2.2:3000/" (Android emulator)
    // For production: "https://your-backend-service.herokuapp.com/"
    
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
            // DEVELOPMENT: Completely disable SSL verification for ngrok
            // WARNING: ONLY USE IN DEVELOPMENT! Remove before production!
            .apply {
                try {
                    // Create a trust manager that does not validate certificate chains
                    val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    })

                    // Install the all-trusting trust manager
                    val sslContext = SSLContext.getInstance("SSL")
                    sslContext.init(null, trustAllCerts, java.security.SecureRandom())

                    sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                    hostnameVerifier { _, _ -> true }

                    Log.d(TAG, "SSL verification disabled for development")
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting up SSL bypass", e)
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
            .baseUrl(BACKEND_BASE_URL)
            .client(createOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Create Retrofit instance for backend service
     */
    private fun createBackendRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BACKEND_BASE_URL)
            .client(createBackendOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Create OkHttpClient specifically for backend API
     */
    private fun createBackendOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.d(TAG, message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                // Add headers for backend API
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", "ExpenseManager-Android/1.0")
                    // Add app-specific API key if your backend requires it
                    // .header("X-API-Key", "your_app_api_key")

                chain.proceed(requestBuilder.build())
            }
            // DEVELOPMENT: Completely disable SSL verification for ngrok
            // WARNING: ONLY USE IN DEVELOPMENT! Remove before production!
            .apply {
                try {
                    // Create a trust manager that does not validate certificate chains
                    val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    })

                    // Install the all-trusting trust manager
                    val sslContext = SSLContext.getInstance("SSL")
                    sslContext.init(null, trustAllCerts, java.security.SecureRandom())

                    sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                    hostnameVerifier { _, _ -> true }

                    Log.d(TAG, "SSL verification disabled for backend development")
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting up SSL bypass for backend", e)
                }
            }
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .build()
    }
    
    /**
     * Get AI Insights API service instance
     */
    fun getAIInsightsApiService(): AIInsightsApiService {
        return createRetrofit().create(AIInsightsApiService::class.java)
    }

    /**
     * Get Backend Insights API service instance
     */
    fun getBackendInsightsService(): BackendInsightsService {
        return createBackendRetrofit().create(BackendInsightsService::class.java)
    }
}

/**
 * Simple factory for creating API services
 * In a production app, this would be handled by dependency injection (Hilt/Dagger)
 */
object ApiServiceFactory {

    @Volatile
    private var aiInsightsApiService: AIInsightsApiService? = null

    @Volatile
    private var backendInsightsService: BackendInsightsService? = null

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

    /**
     * Get singleton instance of Backend Insights API service
     */
    fun getBackendInsightsService(): BackendInsightsService {
        return backendInsightsService ?: synchronized(this) {
            backendInsightsService ?: NetworkConfig.getBackendInsightsService().also {
                backendInsightsService = it
            }
        }
    }
}