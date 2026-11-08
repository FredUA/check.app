package com.example.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log
import com.example.R

object SoundEffectHelper {
    private const val TAG = "SoundEffectHelper"
    private var soundPool: SoundPool? = null
    private var taskDoneSoundId: Int = 0
    private var isLoaded: Boolean = false

    fun init(context: Context) {
        if (soundPool != null) return
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val pool = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(audioAttributes)
                .build()

            pool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0 && sampleId == taskDoneSoundId) {
                    isLoaded = true
                }
            }

            taskDoneSoundId = pool.load(context.applicationContext, R.raw.task_done, 1)
            soundPool = pool
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing SoundPool", e)
        }
    }

    fun playTaskDone(context: Context) {
        try {
            if (soundPool == null) {
                init(context)
            }
            if (isLoaded && soundPool != null) {
                soundPool?.play(taskDoneSoundId, 1f, 1f, 1, 0, 1f)
            } else {
                // Instant fallback with MediaPlayer
                val mp = MediaPlayer.create(context.applicationContext, R.raw.task_done)
                mp?.setOnCompletionListener { it.release() }
                mp?.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing task done sound", e)
        }
    }
}
