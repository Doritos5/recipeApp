package com.example.recipeapp.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.recipeapp.model.recipes.Comment

@Dao
interface CommentDao {
    @Query("SELECT * FROM comments WHERE recipeId = :recipeId ORDER BY timestamp DESC")
    fun getCommentsForRecipe(recipeId: String): LiveData<List<Comment>>

    @Query("SELECT COUNT(*) FROM comments WHERE recipeId = :recipeId")
    fun getCommentsCountForRecipe(recipeId: String): LiveData<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(comment: Comment)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(comments: List<Comment>)

    @Delete
    suspend fun delete(comment: Comment)

    @Query("DELETE FROM comments WHERE recipeId = :recipeId")
    suspend fun deleteByRecipeId(recipeId: String)

    @Query("UPDATE comments SET text = :newText WHERE id = :commentId")
    suspend fun updateText(commentId: String, newText: String)

    @Query("DELETE FROM comments WHERE id = :commentId")
    suspend fun deleteById(commentId: String)
}
