package com.example.recipeapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.recipeapp.model.recipes.Recipe
import com.example.recipeapp.model.users.User
import com.example.recipeapp.model.users.UserDao

@Database(entities = [Recipe::class, User::class], version = 4, exportSchema = false)
abstract class AppLocalDb : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun userDao(): UserDao

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