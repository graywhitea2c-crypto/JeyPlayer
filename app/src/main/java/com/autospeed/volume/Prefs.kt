package com.autospeed.volume

import android.content.Context

object AppPrefs {
    private const val FILE = "autospeed_settings"
    const val MIN_VOLUME = "min_volume"
    const val MAX_VOLUME = "max_volume"
    const val MAX_SPEED = "max_speed"
    const val CURRENT_SPEED = "current_speed"
    const val CURRENT_PERCENT = "current_percent"
    const val IS_RUNNING = "is_running"

    fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
