package com.example.recipeapp.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.recipeapp.R
import com.example.recipeapp.model.recipes.Recipe
import com.squareup.picasso.Picasso
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RecipesAdapter displays a list of recipes in a RecyclerView.
 *
 * @param recipes Initial list of recipes.
 * @param onEditClick Optional callback when the edit button is clicked. If null, edit button is hidden.
 * @param onDeleteClick Optional callback when the delete button is clicked. If null, delete button is hidden.
 */
class RecipesAdapter(
    private var recipes: List<Recipe>,
    private val onRecipeClick: ((Recipe) -> Unit)? = null,
    private val onEditClick: ((Recipe) -> Unit)? = null,
    private val onDeleteClick: ((Recipe) -> Unit)? = null
) : RecyclerView.Adapter<RecipesAdapter.RecipeViewHolder>() {

    // Update the list when data changes
    fun setRecipes(newRecipes: List<Recipe>) {
        this.recipes = newRecipes
        notifyDataSetChanged()
    }

    class RecipeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.recipeRowTitle)
        val instructionsTextView: TextView = itemView.findViewById(R.id.recipeRowInstructions)
        val imageView: ImageView = itemView.findViewById(R.id.recipeRowImage)

        val authorNameTextView: TextView = itemView.findViewById(R.id.recipeAuthorName)
        val dateTextView: TextView = itemView.findViewById(R.id.recipeDate)
        val likesCountTextView: TextView = itemView.findViewById(R.id.recipeLikesCount)
        val commentsCountTextView: TextView = itemView.findViewById(R.id.recipeCommentsCount)

        val editBtn: ImageButton? = itemView.findViewById(R.id.recipeRowEditBtn)
        val deleteBtn: ImageButton? = itemView.findViewById(R.id.recipeRowDeleteBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.recipe_list_row, parent, false)
        return RecipeViewHolder(view)
    }

    private fun formatCreatedAt(createdAt: Long): String {
        if (createdAt <= 0L) return "Unknown date"
        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return formatter.format(Date(createdAt))
    }

    private fun formatInstructionsPreview(instructions: List<String>, maxLines: Int = 2): String {
        if (instructions.isEmpty()) return ""
        val lines = instructions.take(maxLines)
        val suffix = if (instructions.size > maxLines) "..." else ""
        return (lines + suffix).joinToString("\n")
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        val recipe = recipes[position]
        holder.itemView.setOnClickListener {
            onRecipeClick?.invoke(recipe)
        }

        // 1. Set text data
        holder.titleTextView.text = recipe.title

        holder.authorNameTextView.text = recipe.authorName.ifBlank { "Recipe User" }
        holder.dateTextView.text = formatCreatedAt(recipe.createdAt)
        holder.likesCountTextView.text = "217"
        holder.commentsCountTextView.text = "38"
        holder.instructionsTextView.text = formatInstructionsPreview(recipe.instructions)

        // 2. Show/hide action buttons based on callbacks
        if (onEditClick != null) {
            holder.editBtn?.visibility = View.VISIBLE
            holder.editBtn?.setOnClickListener { onEditClick.invoke(recipe) }
        } else {
            holder.editBtn?.visibility = View.GONE
        }

        if (onDeleteClick != null) {
            holder.deleteBtn?.visibility = View.VISIBLE
            holder.deleteBtn?.setOnClickListener { onDeleteClick.invoke(recipe) }
        } else {
            holder.deleteBtn?.visibility = View.GONE
        }

        // 3. Cancel any previous Picasso request for this ImageView
        Picasso.get().cancelRequest(holder.imageView)

        // 4. Determine which image URL to display (prefer remote Base64/URL, fallback to local/web URL)
        val displayImageUrl = recipe.imageRemoteUrl ?: recipe.imageUrl

        if (!displayImageUrl.isNullOrEmpty()) {
            when {
                displayImageUrl.startsWith("data:image") -> {
                    // Base64 encoded image stored in Firestore — decode directly to Bitmap
                    try {
                        val base64 = displayImageUrl.substringAfter("base64,")
                        val bytes = Base64.decode(base64, Base64.NO_WRAP)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        holder.imageView.setImageBitmap(bitmap)
                    } catch (e: Exception) {
                        holder.imageView.setImageResource(R.drawable.ic_launcher_foreground)
                    }
                }
                displayImageUrl.startsWith("content://") || displayImageUrl.startsWith("file://") -> {
                    Picasso.get()
                        .load(Uri.parse(displayImageUrl))
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .error(R.drawable.ic_launcher_foreground)
                        .into(holder.imageView)
                }
                else -> {
                    Picasso.get()
                        .load(displayImageUrl)
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .error(R.drawable.ic_launcher_foreground)
                        .into(holder.imageView)
                }
            }
        } else {
            holder.imageView.setImageResource(R.drawable.ic_launcher_foreground)
        }
    }

    override fun getItemCount(): Int = recipes.size
}
