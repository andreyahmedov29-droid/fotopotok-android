package com.fotopotok.app.api

import com.google.gson.annotations.SerializedName

data class Me(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String
)

data class Photo(
    @SerializedName("id") val id: String,
    @SerializedName("authorId") val authorId: String,
    @SerializedName("authorName") val authorName: String,
    @SerializedName("caption") val caption: String?,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("likeCount") val likeCount: Int,
    @SerializedName("likedByMe") val likedByMe: Boolean,
    @SerializedName("chatStatus") val chatStatus: String?,
    @SerializedName("chatError") val chatError: String?,
    @SerializedName("url") val url: String?
)

data class PhotosResponse(
    @SerializedName("photos") val photos: List<Photo>?,
    @SerializedName("me") val me: Me?
)

data class Chat(
    @SerializedName("dialogId") val dialogId: String,
    @SerializedName("chatId") val chatId: Long,
    @SerializedName("title") val title: String
)

data class ConfigMeta(@SerializedName("warning") val warning: String?)

data class ConfigResponse(
    @SerializedName("keyPresent") val keyPresent: Boolean?,
    @SerializedName("portalEnabled") val portalEnabled: Boolean?,
    @SerializedName("chats") val chats: List<Chat>?,
    @SerializedName("selection") val selection: Chat?,
    @SerializedName("meta") val meta: ConfigMeta?
)

data class PhotoUploadBody(@SerializedName("dataUrl") val dataUrl: String)
data class UploadResponse(@SerializedName("id") val id: String?)

data class LikeResponse(
    @SerializedName("liked") val liked: Boolean,
    @SerializedName("likeCount") val likeCount: Int
)

data class OkResponse(@SerializedName("ok") val ok: Boolean?)

data class ChatBody(
    @SerializedName("chatId") val chatId: Long?,
    @SerializedName("dialogId") val dialogId: String?,
    @SerializedName("title") val title: String?
)

data class SendImageBody(
    @SerializedName("dataUrl") val dataUrl: String,
    @SerializedName("filename") val filename: String
)

data class ConfigSendResponse(
    @SerializedName("ok") val ok: Boolean?,
    @SerializedName("selection") val selection: Chat?
)
