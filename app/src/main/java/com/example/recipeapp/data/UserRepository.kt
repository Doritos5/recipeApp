package com.example.recipeapp.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for reading and writing user data in Cloud Firestore.
 *
 * Firestore document structure (users/{uid}):
 * {
 *   uid:              String   – Firebase Auth UID
 *   email:            String   – user's email address
 *   firstName:        String   – user's first name
 *   lastName:         String   – user's last name
 *   username:         String   – unique username
 *   name:             String   – full display name (firstName + lastName)
 *   imageUrl:         String?  – profile photo URL (Firebase Storage https:// URL)
 *   createdAt:        Timestamp – when the account was created
 *   updatedAt:        Timestamp – last profile update
 *
 * NOTE: Password is NOT stored here. Firebase Auth handles password hashing securely.
 *       Password verification is done through Firebase Auth, not Firestore.
 * }
 */
@Singleton
class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    companion object {
        private const val TAG = "USER_REPO"
        private const val USERS_COLLECTION = "users"
    }

    /**
     * Creates or fully merges a user document in Firestore.
     * Call this on signup and whenever profile data changes.
     * Only non-null values are written; missing keys are left untouched (merge).
     *
     * NOTE: Password parameter is accepted but NOT stored, as Firebase Auth handles it securely.
     */
    suspend fun createOrUpdateUser(
        uid: String,
        email: String? = null,
        name: String? = null,
        firstName: String? = null,
        lastName: String? = null,
        username: String? = null,
        password: String? = null,  // Accepted but not stored - Firebase Auth handles this securely
        imageUrl: String? = null,
        isNewUser: Boolean = false
    ) {
        val fields = mutableMapOf<String, Any>()
        fields["uid"] = uid
        email?.let { fields["email"] = it }
        name?.let { fields["name"] = it }
        firstName?.let { fields["firstName"] = it }
        lastName?.let { fields["lastName"] = it }
        username?.let { fields["username"] = it }
        imageUrl?.let { fields["imageUrl"] = it }
        fields["updatedAt"] = Timestamp.now()
        if (isNewUser) {
            fields["createdAt"] = Timestamp.now()
        }

        try {
            firestore.collection(USERS_COLLECTION)
                .document(uid)
                .set(fields, SetOptions.merge())
                .await()
            Log.d(TAG, "Firestore user doc saved for uid=$uid with email=$email, firstName=$firstName, lastName=$lastName, username=$username")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save user to Firestore: ${e.message}", e)
            throw e
        }
    }

    /**
     * Fetches the user document from Firestore.
     * Returns null if the document doesn't exist.
     */
    suspend fun getUser(uid: String): Map<String, Any>? {
        return try {
            val snapshot = firestore.collection(USERS_COLLECTION)
                .document(uid)
                .get()
                .await()
            if (snapshot.exists()) snapshot.data else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch user from Firestore: ${e.message}", e)
            null
        }
    }
}
