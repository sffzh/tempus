package com.cappielloantonio.tempo.subsonic

import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.BuildConfig
import com.cappielloantonio.tempo.subsonic.utils.CacheUtil
import com.cappielloantonio.tempo.subsonic.utils.EmptyDateTypeAdapter
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 优化后的 Retrofit 工具类
 */
object RetrofitManager {

    // 1. 缓存 Retrofit 实例。Key 是 BaseUrl，确保切换服务器时也能复用
    private val retrofitCache = ConcurrentHashMap<String, Retrofit>()

    // 2. 共享 Gson，避免重复创建
    private val sharedGson by lazy {
        GsonBuilder()
            .registerTypeAdapter(Date::class.java, EmptyDateTypeAdapter())
            .setStrictness(Strictness.LENIENT)
            .create()
    }

    // 3. 共享 OkHttpClient (保持你之前的优化)
    private val sharedOkHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .callTimeout(2, TimeUnit.MINUTES)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .cache(sharedCache)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
        }

        val cacheUtil = CacheUtil(60, 60 * 60 * 24 * 30)
        builder.addInterceptor(cacheUtil.offlineInterceptor)
        builder.build()
    }

    private val sharedCache: Cache by lazy {
        Cache(App.getContext().cacheDir, 10 * 1024 * 1024)
    }

    /**
     * 获取 Service 的静态方法
     */
    @JvmStatic
    fun <T> createService(baseUrl: String, serviceClass: Class<T>): T {

        // 如果缓存里有这个 URL 的 Retrofit 实例，直接用；没有则创建
        val retrofit = retrofitCache.getOrPut(baseUrl) {
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create(sharedGson))
                .client(sharedOkHttpClient)
                .build()
        }

        return retrofit.create(serviceClass)
    }

}