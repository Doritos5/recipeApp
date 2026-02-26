package com.example.recipeapp.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Utility for copying images to the app's internal storage.
 * Shared between AddRecipeFragment and EditRecipeFragment.
 */
object ImageUtils {

    /**
     * Copies an image from a temporary content:// URI to the app's internal storage.
     * Each image gets a unique filename so different recipes never share the same file.
     *
     * @param context Android context.
     * @param sourceUri The temporary content:// URI from the gallery picker.
     * @return A permanent file:// URI pointing to the saved copy, or null if copy failed.
     */
    fun copyImageToInternalStorage(context: Context, sourceUri: Uri): Uri? {
        return try {
            val imagesDir = File(context.filesDir, "recipe_images")
            if (!imagesDir.exists()) imagesDir.mkdirs()

            val fileName = "recipe_${UUID.randomUUID()}.jpg"
            val destFile = File(imagesDir, fileName)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            Uri.fromFile(destFile)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}

