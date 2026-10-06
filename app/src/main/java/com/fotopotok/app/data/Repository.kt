package com.fotopotok.app.data

import com.fotopotok.app.api.Chat
import com.fotopotok.app.api.ChatBody
import com.fotopotok.app.api.ConfigResponse
import com.fotopotok.app.api.FotopotokApi
import com.fotopotok.app.api.Photo
import com.fotopotok.app.api.PhotoUploadBody
import com.fotopotok.app.api.PhotosResponse
import com.fotopotok.app.api.SendImageBody
import com.fotopotok.app.api.UploadResponse
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class Repository(rawBaseUrl: String) {

    private val baseUrl = if (rawBaseUrl.endsWith("/")) rawBaseUrl else "$rawBaseUrl/"

    private val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }

    private val http = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private val api: FotopotokApi by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FotopotokApi::class.java)
    }

    suspend fun photos(): PhotosResponse = api.photos()
    suspend fun config(): ConfigResponse = api.config()
    suspend fun upload(dataUrl: String): UploadResponse = api.upload(PhotoUploadBody(dataUrl))
    suspend fun like(id: String) { api.like(id) }
    suspend fun send(id: String) { api.send(id) }
    suspend fun sendImage(dataUrl: String, filename: String) {
        api.sendImage(SendImageBody(dataUrl, filename))
    }
    suspend fun delete(id: String) { api.delete(id) }
    suspend fun setChat(chat: Chat?) {
        api.setConfig(ChatBody(chat?.chatId, chat?.dialogId, chat?.title))
    }

    fun imageUrl(photo: Photo): String? =
        photo.url?.let { baseUrl + it.removePrefix("/") }

    suspend fun imageBytes(photo: Photo): ByteArray? {
        val url = imageUrl(photo) ?: return null
        return try {
            val req = Request.Builder().url(url).build()
            http.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.bytes() else null
            }
        } catch (e: IOException) {
            null
        }
    }
}
