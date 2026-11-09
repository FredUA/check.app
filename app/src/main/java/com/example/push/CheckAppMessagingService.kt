package com.example.push

import android.util.Log
import com.example.data.DeviceIdentity
import com.example.data.CheckAppRepository
import com.example.ui.AppForegroundTracker
import com.example.ui.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives pushes sent by the Cloud Function when something changes in a shared project.
 * The pushes are data-only, so this runs whether the app is in the background or not running at all,
 * and the notification looks the same as the ones the app raises itself.
 */
class CheckAppMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        DeviceIdentity.init(applicationContext)
        scope.launch {
            try {
                CheckAppRepository(applicationContext).registerDevice(token)
            } catch (e: Exception) {
                Log.w(TAG, "Could not store the new push token", e)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        // A token can outlive a sign-out or an account switch: only show pushes meant for the current user.
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null || data["recipientUid"] != uid) return
        // While the app is open the in-app banner already tells the user.
        if (AppForegroundTracker.isInForeground) return

        val text = data["text"].orEmpty()
        if (text.isBlank()) return
        NotificationHelper.showTaskUpdate(
            context = applicationContext,
            key = data["key"].orEmpty().ifBlank { text },
            title = data["title"].orEmpty().ifBlank { "check.app" },
            text = text
        )
    }

    private companion object {
        const val TAG = "CheckAppMessaging"
    }
}
