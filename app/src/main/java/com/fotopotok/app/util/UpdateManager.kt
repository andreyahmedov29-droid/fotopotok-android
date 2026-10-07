package com.fotopotok.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * In-app auto-update: checks the latest GitHub Release of this app and, if it is
 * newer than the installed build, hands the freshly downloaded APK to the system
 * package installer (the OS asks the user to confirm the install — Android does not
 * allow a normal sideloaded app to reinstall itself silently).
 */
object UpdateManager {
    private const val REPO = "andreyahmedov29-droid/fotopotok-android"
    private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"

    data class UpdateInfo(val latestBuild: Int, val apkUrl: String)

    private fun getJson(url: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "Fotopotok")
        conn.connectTimeout = 20_000
        conn.readTimeout = 30_000
        return try {
            if (conn.responseCode != 200) JSONObject()
            else JSONObject(conn.inputStream.bufferedReader().readText())
        } finally {
            conn.disconnect()
        }
    }

    /**
     * The phone network may block api.github.com, so prefer the app's own server
     * (it proxies the GitHub lookup) and fall back to GitHub directly.
     */
    fun fetch(serverBaseUrl: String?): UpdateInfo? {
        serverVersion(serverBaseUrl)?.let { return it }
        return githubLatest()
    }

    private fun serverVersion(base: String?): UpdateInfo? = try {
        if (base.isNullOrBlank()) return null
        val root = if (base.endsWith("/")) base else "$base/"
        val conn = URL(root + "api/version").openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        val json = if (conn.responseCode == 200) {
            JSONObject(conn.inputStream.bufferedReader().readText())
        } else null
        conn.disconnect()
        json?.let {
            val b = it.optInt("latestBuild", 0)
            val u = it.optString("apkUrl", "")
            if (b > 0 && u.isNotBlank()) UpdateInfo(b, u) else null
        }
    } catch (e: Exception) {
        null
    }

    private fun githubLatest(): UpdateInfo? {
        return try {
            val json = getJson(LATEST_URL)
            val tag = json.optString("tag_name", "")
            val latestBuild = Regex("build-(\\d+)").find(tag)?.groupValues?.get(1)?.toIntOrNull()
                ?: return null
            val assets = json.optJSONArray("assets")
            val apkUrl = if (assets != null && assets.length() > 0) {
                assets.getJSONObject(0).optString("browser_download_url", "")
            } else return null
            if (apkUrl.isBlank()) null else UpdateInfo(latestBuild, apkUrl)
        } catch (e: Exception) {
            null
        }
    }

    fun installedBuild(context: Context): Int =
        context.packageManager.getPackageInfo(context.packageName, 0).versionCode

    /** Downloads the APK into the cache and launches the system installer. */
    fun downloadAndInstall(context: Context, url: String): Boolean = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connect()
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        val file = File(dir, "fotopotok-update.apk")
        conn.inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }

        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}
