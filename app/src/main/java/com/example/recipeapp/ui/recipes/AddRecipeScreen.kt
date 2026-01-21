package com.example.recipeapp.ui.recipes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.recipeapp.data.recipes.Recipe

@Composable
fun AddRecipeScreen(
    onSaveRecipe: (Recipe) -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var ingredients by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var isFavorite by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Add New Recipe",
            style = MaterialTheme.typography.headlineMedium
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description (Optional)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = ingredients,
            onValueChange = { ingredients = it },
            label = { Text("Ingredients") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        OutlinedTextField(
            value = instructions,
            onValueChange = { instructions = it },
            label = { Text("Instructions") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 5
        )

        OutlinedTextField(
            value = imageUrl,
            onValueChange = { imageUrl = it },
            label = { Text("Image URL (Optional)") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = isFavorite,
                onCheckedChange = { isFavorite = it }
            )
            Text("Mark as Favorite")
        }

        Button(
            onClick = {
                if (title.isNotBlank() && ingredients.isNotBlank() && instructions.isNotBlank()) {
                    val newRecipe = Recipe(
                        title = title,
                        description = description.ifBlank { null },
                        ingredients = ingredients,
                        instructions = instructions,
                        imageUrl = imageUrl.ifBlank { null },
                        isFavorite = isFavorite,
                        ownerId = 1 // Hardcoded for now, you can pass the real user ID later
                    )
                    onSaveRecipe(newRecipe)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = title.isNotBlank() && ingredients.isNotBlank() && instructions.isNotBlank()
        ) {
            Text("Save Recipe")
        }
    }
}
