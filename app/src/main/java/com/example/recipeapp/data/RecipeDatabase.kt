package com.example.recipeapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.recipeapp.data.recipes.Recipe
import com.example.recipeapp.data.users.User
import com.example.recipeapp.data.recipes.RecipeDao
import com.example.recipeapp.data.users.UserDao

@Database(entities = [Recipe::class, User::class], version = 3)
abstract class RecipeDatabase : RoomDatabase() {
    abstract fun getRecipeDao() : RecipeDao
    abstract fun getUserDao() : UserDao

    // This basically gives us the option to call this function using RecipeDatabase.createDatabase()
    companion object{
        fun createDatabase(context: Context) : RecipeDatabase{
            val database = Room.databaseBuilder(
                context,
                RecipeDatabase::class.java,
                "recipe_database"
            )   // This means, each time we will increase "Version" - the behavior for this table is to delete all data
                .fallbackToDestructiveMigration(true).build()

            return database
        }
    }
}