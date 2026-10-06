package com.fotopotok.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fotopotok.app.api.Photo
import com.fotopotok.app.databinding.ActivityMainBinding
import com.fotopotok.app.data.ServerPrefs
import com.fotopotok.app.ui.MainViewModel
import com.fotopotok.app.ui.PhotoAdapter
import com.fotopotok.app.util.UpdateManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: PhotoAdapter
    private var cameraUri: Uri? = null
    private var selectMode = false
    private val selected = LinkedHashSet<String>()
    private var lastMsg: String? = null
    private var lastOfferedBuild = 0

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) cameraUri?.let { uploadFromUri(it) }
        }
    private val pickMultiple =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            if (uris.isNotEmpty()) handleBatchImages(uris)
        }
    private val cameraPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchCameraInternal() else toast("Нет разрешения на камеру")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupRecycler()
        setupToolbar()
        setupFab()
        setupBatchBar()
        observe()
        checkForUpdate()
        startUpdateWatcher()
    }

    private companion object {
        const val UPDATE_CHECK_INTERVAL_MS = 120_000L
    }

    private fun checkForUpdate() {
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) { UpdateManager.fetch() }
            val installed = UpdateManager.installedBuild(this@MainActivity)
            if (info == null || info.latestBuild <= installed || info.latestBuild <= lastOfferedBuild) return@launch
            lastOfferedBuild = info.latestBuild
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle("Доступно обновление (сборка ${info.latestBuild})")
                .setMessage("Установить новую версию приложения?")
                .setPositiveButton("Обновить") { _, _ ->
                    lifecycleScope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            UpdateManager.downloadAndInstall(this@MainActivity, info.apkUrl)
                        }
                        if (!ok) toast("Не удалось скачать обновление")
                    }
                }
                .setNegativeButton("Позже", null)
                .show()
        }
    }

    // While the app is open, keep looking for a newer release so the update prompt
    // appears shortly after a new version is pushed (not only on a fresh launch).
    private fun startUpdateWatcher() {
        lifecycleScope.launch {
            while (isActive) {
                delay(UPDATE_CHECK_INTERVAL_MS)
                checkForUpdate()
            }
        }
    }

    private fun setupRecycler() {
        adapter = PhotoAdapter(
            imageUrl = { viewModel.imageUrl(it) },
            onLike = { viewModel.like(it) },
            onSend = { viewModel.sendOne(it) },
            onDelete = { p ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Удалить фото?")
                    .setPositiveButton("Удалить") { _, _ -> viewModel.delete(p) }
                    .setNegativeButton("Отмена", null)
                    .show()
            },
            onSelect = { toggleSelect(it.id) }
        )
        binding.rvPhotos.layoutManager = GridLayoutManager(this, 3)
        binding.rvPhotos.adapter = adapter
    }

    private fun setupToolbar() {
        binding.toolbar.inflateMenu(R.menu.menu_main)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_select -> { toggleSelectMode(); true }
                R.id.action_settings -> { showServerDialog(); true }
                else -> false
            }
        }
    }

    private fun setupFab() {
        binding.fabUpload.setOnClickListener {
            val items = arrayOf(getString(R.string.camera), "Галерея (несколько)")
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.choose_source))
                .setItems(items) { _, which ->
                    if (which == 0) launchCamera() else launchMultiGallery()
                }
                .show()
        }
    }

    private fun launchMultiGallery() {
        pickMultiple.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun setupBatchBar() {
        binding.btnSendSelected.setOnClickListener {
            val ids = ArrayList(selected)
            if (ids.isNotEmpty()) {
                viewModel.sendPhotos(ids)
                selected.clear()
                toggleSelectMode()
            }
        }
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { s ->
                    adapter.submitList(s.photos)
                    adapter.meId = s.meId
                    adapter.selectMode = selectMode
                    adapter.selected = selected

                    binding.offlineBanner.visibility = if (s.online) View.GONE else View.VISIBLE
                    binding.emptyView.visibility =
                        if (s.online && s.photos.isEmpty()) View.VISIBLE else View.GONE

                    s.configWarning?.let {
                        val w = "Внимание: $it"
                        if (w != lastMsg) { toast(w); lastMsg = w }
                    }
                    s.message?.let {
                        if (it != lastMsg) { toast(it); lastMsg = it }
                    }
                    if (s.message == null && s.configWarning == null) lastMsg = null
                }
            }
        }
    }

    // ---------- Selection ----------
    private fun toggleSelectMode() {
        selectMode = !selectMode
        if (!selectMode) selected.clear()
        selected.clear()
        updateSelectionUi()
        adapter.selectMode = selectMode
        adapter.selected = selected
        adapter.notifyDataSetChanged()
        val title = getString(if (selectMode) R.string.action_done else R.string.action_select)
        binding.toolbar.menu.findItem(R.id.action_select)?.title = title
    }

    private fun toggleSelect(id: String) {
        if (!selectMode) return
        if (!selected.remove(id)) selected.add(id)
        adapter.selected = selected
        updateSelectionUi()
        adapter.notifyItemChanged(positionOf(id))
    }

    private fun positionOf(id: String): Int {
        val photos = viewModel.state.value.photos
        return photos.indexOfFirst { it.id == id }
    }

    private fun updateSelectionUi() {
        binding.batchBar.visibility = if (selectMode) View.VISIBLE else View.GONE
        binding.selectCount.text = "Выбрано: ${selected.size}"
        binding.btnSendSelected.isEnabled = selected.isNotEmpty()
    }

    // ---------- Server & chat ----------
    private fun showServerDialog() {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        val pad = (16 * resources.displayMetrics.density).toInt()

        val nameInput = EditText(this)
        nameInput.hint = "Ваше имя (показывается у фото)"
        nameInput.setText(ServerPrefs.userName(this))
        nameInput.setPadding(pad, pad, pad, pad)

        val urlInput = EditText(this)
        urlInput.hint = "https://ваш-публичный-сервер"
        urlInput.setText(viewModel.state.value.serverUrl)
        urlInput.setPadding(pad, pad, pad, pad)

        container.addView(nameInput)
        container.addView(urlInput)
        MaterialAlertDialogBuilder(this)
            .setTitle("Адрес сервера")
            .setView(container)
            .setPositiveButton("Сохранить") { _, _ ->
                viewModel.setUserName(nameInput.text.toString())
                viewModel.setServerUrl(urlInput.text.toString())
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun launchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) launchCameraInternal()
        else cameraPerm.launch(Manifest.permission.CAMERA)
    }

    private fun launchCameraInternal() {
        val dir = File(cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        cameraUri = uri
        takePicture.launch(uri)
    }

    private fun uploadFromUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                }
                if (bytes.isEmpty()) return@launch
                if (bytes.size > 8 * 1024 * 1024) { toast("Фото больше 8 МБ"); return@launch }
                val mime = contentResolver.getType(uri) ?: "image/jpeg"
                val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                viewModel.upload("data:$mime;base64,$b64")
            } catch (e: Exception) {
                toast("Не удалось прочитать фото")
            }
        }
    }

    private fun handleBatchImages(uris: List<Uri>) {
        lifecycleScope.launch {
            val dataUrls = withContext(Dispatchers.IO) {
                uris.mapNotNull { uriToDataUrl(it) }
            }
            if (dataUrls.isEmpty()) { toast("Не удалось прочитать фото"); return@launch }
            if (dataUrls.size == 1) { viewModel.upload(dataUrls[0]); return@launch }
            showNameDialog { name -> viewModel.sendBatchAsArchive(dataUrls, name) }
        }
    }

    private fun uriToDataUrl(uri: Uri): String? {
        return try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            if (bytes.isEmpty() || bytes.size > 8 * 1024 * 1024) null
            else {
                val mime = contentResolver.getType(uri) ?: "image/jpeg"
                "data:$mime;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun showNameDialog(onOk: (String) -> Unit) {
        val input = EditText(this)
        input.hint = "Название архива и файла (дата добавится сама)"
        input.setText("Фотопоток")
        val pad = (16 * resources.displayMetrics.density).toInt()
        input.setPadding(pad, pad, pad, pad)
        MaterialAlertDialogBuilder(this)
            .setTitle("Название архива")
            .setView(input)
            .setPositiveButton("Отправить архивом") { _, _ -> onOk(input.text.toString()) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
