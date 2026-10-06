package com.fotopotok.app.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface FotopotokApi {
    @GET("api/photos")
    suspend fun photos(): PhotosResponse

    @POST("api/photos")
    suspend fun upload(@Body body: PhotoUploadBody): UploadResponse

    @POST("api/photos/{id}/like")
    suspend fun like(@Path("id") id: String): LikeResponse

    @POST("api/photos/{id}/send")
    suspend fun send(@Path("id") id: String): OkResponse

    @POST("api/photos/send-bundle")
    suspend fun sendBundle(@Body body: SendBundleBody): OkResponse

    @DELETE("api/photos/{id}")
    suspend fun delete(@Path("id") id: String): OkResponse

    @GET("api/config")
    suspend fun config(): ConfigResponse

    @POST("api/config")
    suspend fun setConfig(@Body body: ChatBody): ConfigSendResponse
}
