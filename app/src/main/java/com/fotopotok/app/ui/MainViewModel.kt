package com.fotopotok.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fotopotok.app.BuildConfig
import com.fotopotok.app.api.Chat
import com.fotopotok.app.api.Photo
import com.fotopotok.app.data.Repository
import com.fotopotok.app.data.ServerPrefs
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val serverUrl: String = "",
    val online: Boolean = false,
    val photos: List<Photo> = emptyList(),
    val meId: String? = null,
    val meName: String? = null,
    val chats: List<Chat> = emptyList(),
    val selectedChatId: Long? = null,
    val configWarning: String? = null,
    val message: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val initialUrl = ServerPrefs.serverUrl(app).ifEmpty { BuildConfig.DEFAULT_API_BASE_URL }
    private val _state = MutableStateFlow(UiState(serverUrl = initialUrl))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        if (_state.value.serverUrl.isBlank()) {
            _state.update { it.copy(online = false, message = "Укажите адрес сервера в настройках") }
        } else {
            refreshAll()
        }
    }

    private fun repoOrNull(): Repository? {
        val url = _state.value.serverUrl
        return if (url.isNotBlank()) Repository(url, ServerPrefs.userName(getApplication())) else null
    }

    fun setUserName(name: String) {
        ServerPrefs.saveUserName(getApplication(), name)
    }

    fun setServerUrl(url: String) {
        ServerPrefs.saveServerUrl(getApplication(), url)
        _state.update { it.copy(serverUrl = url.trim(), message = null) }
        if (url.isNotBlank()) refreshAll()
        else _state.update { it.copy(online = false, message = "Укажите адрес сервера в настройках") }
    }

    fun refreshAll() {
        val repo = repoOrNull() ?: return
        viewModelScope.launch {
            try {
                val photos = repo.photos()
                val config = repo.config()
                _state.update {
                    it.copy(
                        online = true,
                        photos = photos.photos ?: emptyList(),
                        meId = photos.me?.id,
                        meName = photos.me?.name,
                        chats = config.chats ?: emptyList(),
                        selectedChatId = config.selection?.chatId,
                        configWarning = config.meta?.warning,
                        message = null
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(online = false, message = friendly(e)) }
            }
        }
    }

    fun upload(dataUrl: String) {
        val repo = repoOrNull() ?: return
        viewModelScope.launch {
            try {
                _state.update { it.copy(message = "Загружаю…") }
                repo.upload(dataUrl)
                refreshAll()
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    // Upload a whole batch and send it to the default chat as ONE ZIP archive with the
    // given name (the server appends the date and a per-day counter to the name).
    fun sendBatchAsArchive(dataUrls: List<String>, name: String) {
        val repo = repoOrNull() ?: return
        viewModelScope.launch {
            try {
                _state.update { it.copy(message = "Загружаю фото и собираю архив…") }
                val group = "g" + java.lang.Long.toHexString(System.currentTimeMillis()) +
                    java.lang.Integer.toHexString((Math.random() * 0xFFFF).toInt())
                val ids = mutableListOf<String>()
                for (dataUrl in dataUrls) {
                    val res = repo.upload(dataUrl, sendToChat = false, group = group, groupTitle = name)
                    res.id?.let { ids.add(it) }
                }
                if (ids.isEmpty()) {
                    _state.update { it.copy(message = "Не удалось загрузить фото") }
                    return@launch
                }
                _state.update { it.copy(message = "Отправляю архив…") }
                repo.sendBundle(ids, name)
                refreshAll()
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    fun like(photo: Photo) {
        val repo = repoOrNull() ?: return
        viewModelScope.launch {
            try {
                repo.like(photo.id)
                refreshAll()
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    fun groupLike(group: String) {
        val repo = repoOrNull() ?: return
        viewModelScope.launch {
            try {
                repo.likeGroup(group)
                refreshAll()
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    fun sendPhotos(ids: List<String>) {
        val repo = repoOrNull() ?: return
        viewModelScope.launch {
            try {
                _state.update { it.copy(message = "Собираю фото в ZIP и отправляю…") }
                repo.sendBundle(ids)
                refreshAll()
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    fun sendOne(photo: Photo) {
        val repo = repoOrNull() ?: return
        viewModelScope.launch {
            try {
                _state.update { it.copy(message = "Отправляю фото в чат…") }
                repo.send(photo.id)
                refreshAll()
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    fun delete(photo: Photo) {
        val repo = repoOrNull() ?: return
        viewModelScope.launch {
            try {
                repo.delete(photo.id)
                refreshAll()
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    fun setChat(chat: Chat?) {
        val repo = repoOrNull() ?: return
        viewModelScope.launch {
            try {
                repo.setChat(chat)
                _state.update { it.copy(selectedChatId = chat?.chatId) }
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    fun imageUrl(photo: Photo): String? = repoOrNull()?.imageUrl(photo)

    private fun friendly(e: Exception): String = when (e) {
        is IOException -> "Не удаётся связаться с сервером. Проверьте адрес в настройках."
        else -> e.message ?: "Ошибка сети"
    }
}
