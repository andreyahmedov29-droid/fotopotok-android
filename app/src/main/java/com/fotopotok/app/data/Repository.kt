package com.fotopotok.app.data

import com.fotopotok.app.api.Chat
import com.fotopotok.app.api.ChatBody
import com.fotopotok.app.api.ConfigResponse
import com.fotopotok.app.api.FotopotokApi
import com.fotopotok.app.api.GroupLikeBody
import com.fotopotok.app.api.Photo
import com.fotopotok.app.api.PhotoUploadBody
import com.fotopotok.app.api.PhotosResponse
import com.fotopotok.app.api.SendBundleBody
import com.fotopotok.app.api.UploadResponse
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class Repository(rawBaseUrl: String, private val userName: String) {

    private val baseUrl = if (rawBaseUrl.endsWith("/")) rawBaseUrl else "$rawBaseUrl/"

    private val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }

    private val http = OkHttpClient.Builder()
        .addInterceptor(logging)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("X-User-Name", userName)
                .build()
            chain.proceed(req)
        }
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
    suspend fun upload(
        dataUrl: String,
        sendToChat: Boolean? = null,
        group: String? = null,
        groupTitle: String? = null
    ): UploadResponse = api.upload(PhotoUploadBody(dataUrl, sendToChat, group, groupTitle))
    suspend fun like(id: String) { api.like(id) }
    suspend fun send(id: String) { api.send(id) }
    suspend fun sendBundle(ids: List<String>, name: String? = null) {
        api.sendBundle(SendBundleBody(ids, name))
    }
    suspend fun likeGroup(group: String) {
        api.likeGroup(GroupLikeBody(group))
    }
    suspend fun delete(id: String) { api.delete(id) }
    suspend fun setChat(chat: Chat?) {
        api.setConfig(ChatBody(chat?.chatId, chat?.dialogId, chat?.title))
    }

    fun imageUrl(photo: Photo): String? =
        photo.url?.let { baseUrl + it.removePrefix("/") }
}
