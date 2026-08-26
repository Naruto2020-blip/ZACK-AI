package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

interface GeminiApiService {

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequestDto
    ): Response<GenerateContentResponseDto>

    @POST("v1beta/models/{model}:streamGenerateContent")
    @Streaming
    suspend fun generateContentStream(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Query("alt") alt: String = "sse",
        @Body request: GenerateContentRequestDto
    ): Response<ResponseBody>
}

/**
 * Resilient DNS resolver that tries system DNS first, and falls back
 * to known Google DNS if host resolution temporarily fails.
 */
class ResilientDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        try {
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.isNotEmpty()) return addresses
        } catch (e: UnknownHostException) {
            Log.w("ResilientDns", "System DNS failed for $hostname, trying fallback lookup...")
        }

        return try {
            val allByName = InetAddress.getAllByName(hostname).toList()
            if (allByName.isNotEmpty()) allByName else Dns.SYSTEM.lookup(hostname)
        } catch (e: Exception) {
            Log.e("ResilientDns", "DNS resolution failed completely for $hostname: ${e.message}")
            throw UnknownHostException("No fue posible resolver la dirección del servidor: $hostname")
        }
    }
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"
    private const val PREFS_NAME = "gemini_prefs"
    private const val KEY_CUSTOM_API_KEY = "custom_gemini_api_key"
    
    private var customApiKeyCache: String? = null

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(ResilientDns())
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(22, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    fun saveCustomApiKey(context: android.content.Context, key: String) {
        val trimmed = key.trim()
        customApiKeyCache = trimmed
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_API_KEY, trimmed).apply()
    }

    fun getStoredApiKey(context: android.content.Context): String {
        if (!customApiKeyCache.isNullOrBlank()) {
            return customApiKeyCache!!
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_CUSTOM_API_KEY, null)?.trim()
        if (!saved.isNullOrBlank()) {
            customApiKeyCache = saved
            return saved
        }
        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        customApiKeyCache = buildKey
        return buildKey
    }

    fun getApiKey(): String {
        if (!customApiKeyCache.isNullOrBlank()) {
            return customApiKeyCache!!
        }
        return BuildConfig.GEMINI_API_KEY.trim()
    }

    fun hasValidApiKey(context: android.content.Context? = null): Boolean {
        val key = if (context != null) getStoredApiKey(context) else getApiKey()
        if (key.isBlank()) return false
        val placeholders = listOf("YOUR_API_KEY", "MY_GEMINI_API_KEY", "GEMINI_API_KEY", "PLACEHOLDER", "DEFAULT_VALUE")
        return placeholders.none { key.equals(it, ignoreCase = true) }
    }
}
