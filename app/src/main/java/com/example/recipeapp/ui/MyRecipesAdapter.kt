package com.example.recipeapp.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.recipeapp.R
import com.example.recipeapp.model.recipes.Recipe
import com.google.android.material.button.MaterialButton
import com.squareup.picasso.Picasso

class MyRecipesAdapter(
    private var recipes: List<Recipe>,
    private val onViewClick: (Recipe) -> Unit,
    private val onEditClick: (Recipe) -> Unit,
    private val onDeleteClick: (Recipe) -> Unit
) : RecyclerView.Adapter<MyRecipesAdapter.MyRecipeViewHolder>() {

    fun setRecipes(newRecipes: List<Recipe>) {
        recipes = newRecipes
        notifyDataSetChanged()
    }

    class MyRecipeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.myRecipeImage)
        val titleTv: TextView = itemView.findViewById(R.id.myRecipeTitle)

        val tagsContainer: View = itemView.findViewById(R.id.myRecipeTagsContainer)
        val tag1Tv: TextView = itemView.findViewById(R.id.myRecipeTag1)
        val tag2Tv: TextView = itemView.findViewById(R.id.myRecipeTag2)
        val moreTagsTv: TextView = itemView.findViewById(R.id.myRecipeMoreTags)

        val likesTv: TextView = itemView.findViewById(R.id.myRecipeLikes)
        val commentsTv: TextView = itemView.findViewById(R.id.myRecipeComments)

        val viewBtn: MaterialButton = itemView.findViewById(R.id.myRecipeViewBtn)
        val editBtn: MaterialButton = itemView.findViewById(R.id.myRecipeEditBtn)
        val deleteBtn: MaterialButton = itemView.findViewById(R.id.myRecipeDeleteBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyRecipeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.my_recipe_row, parent, false)
        return MyRecipeViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyRecipeViewHolder, position: Int) {
        val recipe = recipes[position]

        holder.titleTv.text = recipe.title

        // Handle tags dynamically - only show if tags exist in the recipe
        val tags = recipe.tags
        if (tags.isEmpty()) {
            // No tags - hide the entire tags container
            holder.tagsContainer.visibility = View.GONE
        } else {
            // Show tags container
            holder.tagsContainer.visibility = View.VISIBLE

            // Show first tag
            if (tags.isNotEmpty()) {
                holder.tag1Tv.text = "#${tags[0]}"
                holder.tag1Tv.visibility = View.VISIBLE
            } else {
                holder.tag1Tv.visibility = View.GONE
            }

            // Show second tag if exists
            if (tags.size > 1) {
                holder.tag2Tv.text = "#${tags[1]}"
                holder.tag2Tv.visibility = View.VISIBLE
            } else {
                holder.tag2Tv.visibility = View.GONE
            }

            // Show "+X more" if there are more than 2 tags
            if (tags.size > 2) {
                holder.moreTagsTv.visibility = View.VISIBLE
                holder.moreTagsTv.text = "+${tags.size - 2}"
            } else {
                holder.moreTagsTv.visibility = View.GONE
            }
        }

        // TODO: Replace placeholder values with real data from Firestore.
        holder.likesTv.text = "217 likes"
        holder.commentsTv.text = "38 comments"

        holder.viewBtn.setOnClickListener { onViewClick(recipe) }
        holder.editBtn.setOnClickListener { onEditClick(recipe) }
        holder.deleteBtn.setOnClickListener { onDeleteClick(recipe) }

        Picasso.get().cancelRequest(holder.imageView)
        val displayImageUrl = recipe.imageRemoteUrl ?: recipe.imageUrl

        if (!displayImageUrl.isNullOrEmpty()) {
            when {
                displayImageUrl.startsWith("data:image") -> {
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