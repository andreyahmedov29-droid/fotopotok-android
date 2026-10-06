package com.fotopotok.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fotopotok.app.api.Chat
import com.fotopotok.app.api.Photo
import com.fotopotok.app.data.Repository
import com.fotopotok.app.data.ServerPrefs
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val _state = MutableStateFlow(UiState(serverUrl = ServerPrefs.serverUrl(app)))
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
        return if (url.isNotBlank()) Repository(url) else null
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

    fun sendPhotos(ids: List<String>) {
        val repo = repoOrNull() ?: return
        viewModelScope.launch {
            try {
                _state.update { it.copy(message = "Собираю фото в одно сообщение…") }
                val photos = _state.value.photos.filter { it.id in ids }
                val bytes = withContext(Dispatchers.IO) { buildCollage(repo, photos) }
                if (bytes == null) {
                    _state.update { it.copy(message = "Не удалось загрузить выбранные фото") }
                    return@launch
                }
                val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                repo.sendImage("data:image/jpeg;base64,$b64", "Фотопоток_${photos.size}фото.jpg")
                refreshAll()
            } catch (e: Exception) {
                _state.update { it.copy(message = friendly(e)) }
            }
        }
    }

    private fun buildCollage(repo: Repository, photos: List<Photo>): ByteArray? {
        val bitmaps = mutableListOf<Bitmap>()
        for (ph in photos) {
            val bytes = repo.imageBytes(ph) ?: continue
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
            bitmaps.add(bmp)
        }
        if (bitmaps.isEmpty()) return null

        val cols = ceil(sqrt(bitmaps.size.toDouble())).toInt().coerceAtLeast(1)
        val rows = ceil(bitmaps.size.toDouble() / cols).toInt()
        val pad = 10f
        val cell = max(240f, min(1600f / cols, 600f)).toInt()
        val width = cols * cell + (cols + 1) * pad.toInt()
        val height = rows * cell + (rows + 1) * pad.toInt()

        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.rgb(18, 16, 13))
        bitmaps.forEachIndexed { i, src ->
            val col = i % cols
            val row = i / cols
            val dx = pad + col * (cell + pad)
            val dy = pad + row * (cell + pad)
            val scale = max(cell.toFloat() / src.width, cell.toFloat() / src.height)
            val sw = src.width * scale
            val sh = src.height * scale
            val sx = dx + (cell - sw) / 2
            val sy = dy + (cell - sh) / 2
            canvas.drawBitmap(src, null, RectF(sx, sy, sx + sw, sy + sh), null)
        }
        bitmaps.forEach { it.recycle() }
        val bos = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 85, bos)
        out.recycle()
        return bos.toByteArray()
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
