package com.example.data

import android.content.Context
import java.util.UUID

/**
 * A random id that identifies this installation of the app. It is written next to every change
 * (lastUpdatedByDevice) and stored with the push token, so the server can avoid sending a push about
 * a change to the very device that made it, while still notifying the same user's other devices.
 */
object DeviceIdentity {
    private const val PREFS = "check_app_prefs"
    private const val KEY = "device_id"

    @Volatile
    var id: String = ""
        private set

    fun init(context: Context) {
        if (id.isNotEmpty()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY, null)
        id = existing ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY, it).apply() }
    }
}
