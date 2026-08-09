package com.querycubix.queryshare.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("queryshare_settings", Context.MODE_PRIVATE)

    private val _keepScreenAwake = MutableStateFlow(sharedPreferences.getBoolean("keep_screen_awake", true))
    val keepScreenAwake = _keepScreenAwake.asStateFlow()

    fun setKeepScreenAwake(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("keep_screen_awake", enabled).apply()
        _keepScreenAwake.value = enabled
    }

    fun getLastIp(): String = sharedPreferences.getString("last_ip", "") ?: ""
    fun getLastPort(): Int = sharedPreferences.getInt("last_port", 3000)

    fun saveLastConnection(ip: String, port: Int) {
        sharedPreferences.edit()
            .putString("last_ip", ip)
            .putInt("last_port", port)
            .apply()
    }
}
