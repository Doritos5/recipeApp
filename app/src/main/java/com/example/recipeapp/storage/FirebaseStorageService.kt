package com.example.recipeapp.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for uploading images to Firebase Storage.
 * Includes image compression to save bandwidth and storage costs.
 *
 * NOTE: Firebase Storage requires the Blaze (pay-as-you-go) plan.
 * On the free Spark plan, uploads will fail with 404.
 * Currently, images are saved locally and this service is called as best-effort.
 */
@Singleton
class FirebaseStorageService @Inject constructor(
    private val firebaseStorage: FirebaseStorage
) {

    companion object {
        private const val TAG = "STORAGE_DEBUG"
        private const val RECIPE_IMAGES_PATH = "recipe_images"
        private const val PROFILE_IMAGES_PATH = "profile_images"
        private const val COMPRESSION_QUALITY = 70
        private const val MAX_IMAGE_DIMENSION = 1024
    }

    /**
     * Compresses an image from a URI before uploading.
     * Scales the image down if it exceeds [MAX_IMAGE_DIMENSION] and compresses to JPEG.
     */
    fun compressImage(context: Context, imageUri: Uri): ByteArray {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(imageUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        val (origWidth, origHeight) = options.outWidth to options.outHeight
        var sampleSize = 1
        while (origWidth / sampleSize > MAX_IMAGE_DIMENSION * 2 ||
            origHeight / sampleSize > MAX_IMAGE_DIMENSION * 2
        ) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = context.contentResolver.openInputStream(imageUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: throw IllegalStateException("Could not decode image from URI: $imageUri")

        val scaledBitmap = scaleBitmapIfNeeded(bitmap)

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, outputStream)

        if (scaledBitmap != bitmap) bitmap.recycle()
        scaledBitmap.recycle()

        return outputStream.toByteArray()
    }

    /**
     * Scales a bitmap proportionally so that neither dimension exceeds [MAX_IMAGE_DIMENSION].
     */
    private fun scaleBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= MAX_IMAGE_DIMENSION && height <= MAX_IMAGE_DIMENSION) {
            return bitmap
        }

        val ratio = minOf(
            MAX_IMAGE_DIMENSION.toFloat() / width,
            MAX_IMAGE_DIMENSION.toFloat() / height
        )
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Uploads a compressed recipe image to Firebase Storage.
     *
     * @param context Android context (needed for content resolver).
     * @param imageUri The local URI of the image to upload.
     * @param userId The current user's UID, used to organize storage paths.
     * @return The download URL string of the uploaded image.
     * @throws Exception if upload fails.
     */
    suspend fun uploadRecipeImage(context: Context, imageUri: Uri, userId: String): String {
        val compressedBytes = compressImage(context, imageUri)
        val fileName = "${UUID.randomUUID()}.jpg"
        val storageRef = firebaseStorage.reference
            .child(RECIPE_IMAGES_PATH)
            .child(userId)
            .child(fileName)

        Log.d(TAG, "Uploading recipe image to: ${storageRef.path}")
        storageRef.putBytes(compressedBytes).await()
        val url = storageRef.downloadUrl.await().toString()
        Log.d(TAG, "Recipe image uploaded: $url")
        return url
    }

    /**
     * Uploads a profile image to Firebase Storage under a dedicated path.
     *
     * @param context Android context (needed for content resolver).
     * @param imageUri The local URI of the image to upload.
     * @param userId The current user's UID.
     * @return The download URL string of the uploaded image.
     * @throws Exception if upload fails.
     */
    suspend fun uploadProfileImage(context: Context, imageUri: Uri, userId: String): String {
        val compressedBytes = compressImage(context, imageUri)
        val storageRef = firebaseStorage.reference
            .child(PROFILE_IMAGES_PATH)
            .child(userId)
            .child("profile.jpg")

        Log.d(TAG, "Uploading profile image to: ${storageRef.path}")
        storageRef.putBytes(compressedBytes).await()
        val url = storageRef.downloadUrl.await().toString()
        Log.d(TAG, "Profile image uploaded: $url")
        return url
    }
}

