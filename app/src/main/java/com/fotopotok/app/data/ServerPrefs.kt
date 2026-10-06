package com.fotopotok.app.data

import android.content.Context

object ServerPrefs {
    private const val PREFS = "fotopotok"
    private const val KEY_SERVER = "serverUrl"

    fun serverUrl(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SERVER, "")?.trim() ?: ""

    fun saveServerUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVER, url.trim())
            .apply()
    }
}
