package com.example.recipeapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.recipeapp.model.recipes.Comment
import com.example.recipeapp.model.recipes.Like
import com.example.recipeapp.model.recipes.Recipe
import com.example.recipeapp.model.users.User
import com.example.recipeapp.model.users.UserDao

@Database(entities = [Recipe::class, User::class, Like::class, Comment::class], version = 7, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppLocalDb : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun userDao(): UserDao
    abstract fun likeDao(): LikeDao
    abstract fun commentDao(): CommentDao

    companion object {
        @Volatile
        private var instance: AppLocalDb? = null

        fun getDatabase(context: Context): AppLocalDb {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppLocalDb::class.java,
                    "recipe_app_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}