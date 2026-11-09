package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Profile pictures. A picked image is shrunk to a small square JPEG and uploaded to Firebase Storage
 * (`avatars/<uid>/<time>.jpg`); the profile only keeps the download URL. Anything else in the avatar
 * field is a plain image URL.
 */
object AvatarImages {
    private const val SIZE_PX = 512
    private const val JPEG_QUALITY = 85

    /** Values from the removed built-in avatars ("avatar:N") no longer point to anything. */
    fun displayable(value: String): String = if (value.startsWith("avatar:")) "" else value

    /** Reads the picked image, crops it to a centred square, scales it down and encodes it. Null on failure. */
    suspend fun prepareJpeg(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= SIZE_PX && bounds.outHeight / (sample * 2) >= SIZE_PX) sample *= 2
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext null

            val side = minOf(decoded.width, decoded.height)
            val square = Bitmap.createBitmap(
                decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side
            )
            val scaled = if (side > SIZE_PX) Bitmap.createScaledBitmap(square, SIZE_PX, SIZE_PX, true) else square

            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        } catch (e: Exception) {
            Log.w(TAG, "Could not prepare the picked image", e)
            null
        }
    }

    /** Uploads the picture for the signed-in user and returns its download URL. Throws on failure. */
    suspend fun upload(jpeg: ByteArray): String {
        val uid = Firebase.auth.currentUser?.uid ?: throw IllegalStateException("Not signed in")
        val ref = FirebaseStorage.getInstance().reference
            .child("avatars/$uid/${System.currentTimeMillis()}.jpg")
        ref.putBytes(jpeg, StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
        return ref.downloadUrl.await().toString()
    }

    private const val TAG = "AvatarImages"
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
