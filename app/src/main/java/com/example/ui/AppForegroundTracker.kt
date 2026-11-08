package com.example.ui

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Tracks whether any activity of the app is currently visible, so we only raise
 * system tray notifications when the in-app banner can't be seen.
 */
object AppForegroundTracker {
    @Volatile
    var isInForeground: Boolean = false
        private set

    private var startedCount = 0
    private var registered = false

    fun register(application: Application) {
        if (registered) return
        registered = true
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedCount++
                isInForeground = true
            }

            override fun onActivityStopped(activity: Activity) {
                startedCount = (startedCount - 1).coerceAtLeast(0)
                isInForeground = startedCount > 0
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
