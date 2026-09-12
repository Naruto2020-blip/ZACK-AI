package com.example.util

import android.content.Context
import android.util.Log
import coil.Coil
import coil.ImageLoader
import coil.request.ImageRequest
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object CoilUtils {
    private const val TAG = "CoilUtils"
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    @Volatile
    private var customImageLoader: ImageLoader? = null

    fun initialize(context: Context): ImageLoader {
        return getImageLoader(context)
    }

    fun getImageLoader(context: Context): ImageLoader {
        return customImageLoader ?: synchronized(this) {
            customImageLoader ?: run {
                val okHttpClient = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .addInterceptor { chain ->
                        val request = chain.request()
                        val builder = request.newBuilder()
                        // Ensure Wikimedia, Wikipedia and all image CDNs receive a legitimate browser User-Agent
                        builder.header("User-Agent", USER_AGENT)
                        builder.header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                        builder.header("Accept-Language", "es-ES,es;q=0.9,en-US;q=0.8,en;q=0.7")
                        chain.proceed(builder.build())
                    }
                    .build()

                ImageLoader.Builder(context.applicationContext)
                    .okHttpClient(okHttpClient)
                    .components {
                        add(coil.decode.SvgDecoder.Factory())
                    }
                    .crossfade(true)
                    .respectCacheHeaders(false)
                    .build().also {
                        customImageLoader = it
                        try {
                            Coil.setImageLoader(it)
                        } catch (t: Throwable) {
                            Log.w(TAG, "Coil setImageLoader warning: ${t.message}")
                        }
                    }
            }
        }
    }

    fun buildImageRequest(context: Context, imageUrl: String): ImageRequest {
        return ImageRequest.Builder(context)
            .data(imageUrl)
            .setHeader("User-Agent", USER_AGENT)
            .crossfade(true)
            .transformations(WatermarkRemovalTransformation(imageUrl = imageUrl))
            .build()
    }
}
