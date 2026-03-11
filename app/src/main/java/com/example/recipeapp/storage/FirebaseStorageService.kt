package com.example.recipeapp.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
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
     * Opens an InputStream for a URI that may be content:// or file://.
     * ContentResolver.openInputStream() returns null for file:// URIs on Android 7+,
     * so we fall back to reading the file directly in that case.
     */
    private fun openInputStream(context: Context, uri: Uri): InputStream? {
        return if (uri.scheme == "file") {
            val path = uri.path ?: return null
            File(path).inputStream()
        } else {
            context.contentResolver.openInputStream(uri)
        }
    }

    // ----------------------------------------------------------------
    // Base64 compression (works on free Spark plan, no Storage needed)
    // ----------------------------------------------------------------

    /**
     * Compresses a recipe image to a Base64 data URI string for storage in Firestore.
     * Max 512×512px, 65% JPEG quality — readable on any device, no Firebase Storage needed.
     */
    fun compressRecipeImageToBase64(context: Context, imageUri: Uri): String {
        return compressToBase64(context, imageUri, maxDimension = 512, quality = 65).also {
            Log.d(TAG, "Recipe image compressed to Base64, size: ${it.length} chars")
        }
    }

    /**
     * Compresses a profile image to a Base64 data URI string for storage in Firestore.
     * Max 256×256px, 60% JPEG quality — keeps well under Firestore's 1 MB document limit.
     */
    fun compressProfileImageToBase64(context: Context, imageUri: Uri): String {
        return compressToBase64(context, imageUri, maxDimension = 256, quality = 60).also {
            Log.d(TAG, "Profile image compressed to Base64, size: ${it.length} chars")
        }
    }

    /**
     * Shared implementation: decode → scale → JPEG compress → Base64 encode.
     * Handles both content:// and file:// URIs correctly.
     */
    private fun compressToBase64(context: Context, imageUri: Uri, maxDimension: Int, quality: Int): String {
        // --- Step 1: read bounds only ---
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openInputStream(context, imageUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }

        // --- Step 2: calculate sample size ---
        var sampleSize = 1
        while (boundsOptions.outWidth / sampleSize > maxDimension * 2 ||
               boundsOptions.outHeight / sampleSize > maxDimension * 2) {
            sampleSize *= 2
        }

        // --- Step 3: decode scaled bitmap ---
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = openInputStream(context, imageUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: throw IllegalStateException("Could not open image stream for URI: $imageUri")

        // --- Step 4: fine-scale to exact max dimension ---
        val ratio = minOf(maxDimension.toFloat() / bitmap.width, maxDimension.toFloat() / bitmap.height, 1f)
        val scaled = if (ratio < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt(),
                (bitmap.height * ratio).toInt(),
                true
            )
        } else bitmap

        // --- Step 5: compress to JPEG bytes and Base64 encode ---
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (scaled != bitmap) bitmap.recycle()
        scaled.recycle()

        val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }

    // ----------------------------------------------------------------
    // Firebase Storage upload helpers (require Blaze plan)
    // ----------------------------------------------------------------

    /**
     * Compresses an image from a URI before uploading.
     * Scales the image down if it exceeds [MAX_IMAGE_DIMENSION] and compresses to JPEG.
     */
    fun compressImage(context: Context, imageUri: Uri): ByteArray {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openInputStream(context, imageUri)?.use { stream ->
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
        val bitmap = openInputStream(context, imageUri)?.use { stream ->
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
