package com.example.recipeapp.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.recipeapp.model.recipes.Like

@Dao
interface LikeDao {
    @Query("SELECT * FROM likes WHERE recipeId = :recipeId")
    fun getLikesForRecipe(recipeId: String): LiveData<List<Like>>

    @Query("SELECT COUNT(*) FROM likes WHERE recipeId = :recipeId")
    fun getLikesCountForRecipe(recipeId: String): LiveData<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM likes WHERE recipeId = :recipeId AND userId = :userId)")
    fun isRecipeLikedByUser(recipeId: String, userId: String): LiveData<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(like: Like)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(likes: List<Like>)

    @Delete
    suspend fun delete(like: Like)

    @Query("DELETE FROM likes WHERE recipeId = :recipeId AND userId = :userId")
    suspend fun deleteLike(recipeId: String, userId: String)

    @Query("DELETE FROM likes WHERE recipeId = :recipeId")
    suspend fun deleteByRecipeId(recipeId: String)
}
