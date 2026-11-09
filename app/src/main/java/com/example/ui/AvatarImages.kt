package com.example.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.R

/**
 * Built-in avatars. They are stored in the profile as "avatar:N" and drawn from the app's own
 * drawables, so they work offline and need no image hosting. Anything else is treated as an image URL.
 */
object AvatarImages {
    private const val PREFIX = "avatar:"

    private val drawables = mapOf(
        "${PREFIX}1" to R.drawable.avatar_hero_1,
        "${PREFIX}2" to R.drawable.avatar_hero_2,
        "${PREFIX}3" to R.drawable.avatar_hero_3,
        "${PREFIX}4" to R.drawable.avatar_hero_4
    )

    val presets: List<String> = drawables.keys.toList()

    /** What to hand to Coil's `AsyncImage` for a stored avatar value. */
    fun model(value: String): Any = drawables[value.trim()] ?: value
}

/** A short, light buzz, e.g. to confirm that something was saved. */
fun lightVibrate(context: Context) {
    try {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator == null || !vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(30, 90))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }
    } catch (_: Exception) {
        // Vibration is a nicety; never fail because of it.
    }
}
