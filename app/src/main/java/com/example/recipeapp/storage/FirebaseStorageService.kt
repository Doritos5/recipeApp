package com.example.recipeapp.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for uploading recipe images to Firebase Storage.
 * Includes image compression to save bandwidth and storage costs.
 */
@Singleton
class FirebaseStorageService @Inject constructor(
    private val firebaseStorage: FirebaseStorage
) {

    companion object {
        private const val RECIPE_IMAGES_PATH = "recipe_images"
        private const val COMPRESSION_QUALITY = 70 // 0-100, JPEG quality
        private const val MAX_IMAGE_DIMENSION = 1024 // Max width or height in pixels
    }

    /**
     * Compresses an image from a URI before uploading.
     * Scales the image down if it exceeds [MAX_IMAGE_DIMENSION] and compresses to JPEG.
     *
     * @param context Android context to open the content URI.
     * @param imageUri The URI of the image to compress.
     * @return A compressed ByteArray of the image in JPEG format.
     */
    fun compressImage(context: Context, imageUri: Uri): ByteArray {
        // Decode with inJustDecodeBounds to get dimensions without loading full bitmap
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(imageUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        // Calculate sample size for downscaling
        val (origWidth, origHeight) = options.outWidth to options.outHeight
        var sampleSize = 1
        while (origWidth / sampleSize > MAX_IMAGE_DIMENSION * 2 ||
            origHeight / sampleSize > MAX_IMAGE_DIMENSION * 2
        ) {
            sampleSize *= 2
        }

        // Decode the actual bitmap with the calculated sample size
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = context.contentResolver.openInputStream(imageUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: throw IllegalStateException("Could not decode image from URI: $imageUri")

        // Scale down further if still too large
        val scaledBitmap = scaleBitmapIfNeeded(bitmap)

        // Compress to JPEG
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, outputStream)

        // Recycle bitmaps to free memory
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
        // 1. Compress the image
        val compressedBytes = compressImage(context, imageUri)

        // 2. Create a unique storage reference: recipe_images/{userId}/{uuid}.jpg
        val fileName = "${UUID.randomUUID()}.jpg"
        val storageRef = firebaseStorage.reference
            .child(RECIPE_IMAGES_PATH)
            .child(userId)
            .child(fileName)

        // 3. Upload the compressed bytes
        storageRef.putBytes(compressedBytes).await()

        // 4. Get and return the download URL
        return storageRef.downloadUrl.await().toString()
    }
}

