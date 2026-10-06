package com.fotopotok.app.data

import android.content.Context

object ServerPrefs {
    private const val PREFS = "fotopotok"
    private const val KEY_SERVER = "serverUrl"
    private const val KEY_USER = "userName"

    fun serverUrl(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SERVER, "")?.trim() ?: ""

    fun saveServerUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVER, url.trim())
            .apply()
    }

    fun userName(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_USER, "")?.trim() ?: ""

    fun saveUserName(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER, name.trim())
            .apply()
    }
}
