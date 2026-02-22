package com.example.recipeapp.di

import android.content.Context
import com.example.recipeapp.data.AppLocalDb
import com.example.recipeapp.data.RecipeDao
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides Firebase and Room instances as singletons.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance()

    @Provides
    @Singleton
    fun provideAppLocalDb(@ApplicationContext context: Context): AppLocalDb {
        return AppLocalDb.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideRecipeDao(database: AppLocalDb): RecipeDao {
        return database.recipeDao()
    }
}

