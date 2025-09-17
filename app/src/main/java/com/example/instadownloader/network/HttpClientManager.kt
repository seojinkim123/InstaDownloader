package com.example.instadownloader.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object HttpClientManager {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}