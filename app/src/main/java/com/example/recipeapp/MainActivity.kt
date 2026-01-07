package com.example.recipeapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.recipeapp.data.RecipeDatabase
import com.example.recipeapp.data.recipes.RecipeDao
import com.example.recipeapp.data.users.User
import com.example.recipeapp.data.users.UserDao
import com.example.recipeapp.ui.recipes.AddRecipeScreen
import com.example.recipeapp.ui.recipes.HomeScreen
import com.example.recipeapp.ui.theme.RecipeAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen {
    Home, AddRecipe
}

class MainActivity : ComponentActivity() {

    private lateinit var database: RecipeDatabase
    private val recipeDao: RecipeDao by lazy { database.getRecipeDao() }
    private val userDao: UserDao by lazy { database.getUserDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = RecipeDatabase.createDatabase(this)

        // Ensure default user exists
        lifecycleScope.launch(Dispatchers.IO) {
            val users = userDao.getAllUsers()
            if (users.none { it.id == 1 }) {
                userDao.createUser(
                    User(
                        id = 1,
                        name = "Default",
                        lastName = "User",
                        email = "default@example.com",
                        password = "password"
                    )
                )
            }
        }

        enableEdgeToEdge()
        setContent {
            RecipeAppTheme {
                var currentScreen by remember { mutableStateOf(Screen.Home) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (currentScreen) {
                        Screen.Home -> HomeScreen(
                            modifier = Modifier.padding(innerPadding),
                            onCreateRecipeClick = { currentScreen = Screen.AddRecipe }
                        )
                        Screen.AddRecipe -> AddRecipeScreen(
                            onSaveRecipe = { newRecipe ->
                                lifecycleScope.launch(Dispatchers.IO) {
                                    // Ensure ownerId is 1
                                    val recipeWithUser = newRecipe.copy(ownerId = 1)
                                    recipeDao.createRecipe(recipeWithUser)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Recipe '${recipeWithUser.title}' saved!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        currentScreen = Screen.Home
                                    }
                                }
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
