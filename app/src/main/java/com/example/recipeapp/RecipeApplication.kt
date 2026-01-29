package com.example.recipeapp

import android.app.Application
import com.example.recipeapp.data.AppLocalDb

class RecipeApplication : Application() {
    val database: AppLocalDb by lazy { AppLocalDb.getDatabase(this) }
}