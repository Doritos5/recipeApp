package com.example.recipeapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.recipeapp.R
import com.example.recipeapp.model.recipes.Recipe
import com.squareup.picasso.Picasso

class RecipesAdapter(private var recipes: List<Recipe>) :
    RecyclerView.Adapter<RecipesAdapter.RecipeViewHolder>() {

    // Update the list when data changes
    fun setRecipes(newRecipes: List<Recipe>) {
        this.recipes = newRecipes
        notifyDataSetChanged()
    }

    class RecipeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.recipeRowTitle)
        val instructionsTextView: TextView = itemView.findViewById(R.id.recipeRowInstructions)
        val imageView: ImageView = itemView.findViewById(R.id.recipeRowImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.recipe_list_row, parent, false)
        return RecipeViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        val recipe = recipes[position]

        // 1. Set text data
        holder.titleTextView.text = recipe.title
        holder.instructionsTextView.text = recipe.instructions ?: "No instructions available"

        // 2. Load Image using Picasso
        if (!recipe.imageUrl.isNullOrEmpty()) {
            Picasso.get()
                .load(recipe.imageUrl)
                .placeholder(R.drawable.ic_launcher_foreground) // Optional: placeholder while loading
                .into(holder.imageView)
        } else {
            holder.imageView.setImageResource(R.drawable.ic_launcher_foreground)
        }
    }

    override fun getItemCount(): Int = recipes.size
}