package com.github.rodrigotimoteo.mutation.sample.android

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    fun getUsername(): String? = prefs.getString("username", null)

    fun setUsername(name: String) {
        prefs.edit().putString("username", name).apply()
    }

    fun getLoginCount(): Int = prefs.getInt("login_count", 0)

    fun incrementLoginCount() {
        val current = getLoginCount()
        if (current < Int.MAX_VALUE) {
            prefs.edit().putInt("login_count", current + 1).apply()
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean = !getUsername().isNullOrEmpty()
}
