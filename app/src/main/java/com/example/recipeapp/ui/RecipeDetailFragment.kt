package com.example.recipeapp.ui

import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recipeapp.R
import com.example.recipeapp.RecipeViewModel
import com.example.recipeapp.databinding.FragmentRecipeDetailBinding
import com.google.android.flexbox.FlexboxLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class RecipeDetailFragment : Fragment(R.layout.fragment_recipe_detail) {

    private var _binding: FragmentRecipeDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecipeViewModel by viewModels()
    private lateinit var commentAdapter: CommentAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentRecipeDetailBinding.bind(view)

        val recipeId = arguments?.getString("id").orEmpty()
        val title = arguments?.getString("title").orEmpty()
        val instructions = arguments?.getString("instructions").orEmpty()
        val imageUrl = arguments?.getString("imageUrl")
        val imageRemoteUrl = arguments?.getString("imageRemoteUrl")
        val tagsJson = arguments?.getString("tags")
        val authorName = arguments?.getString("authorName").orEmpty()
        val createdAt = arguments?.getLong("createdAt", 0L) ?: 0L
        val ingredients = arguments?.getString("ingredients").orEmpty()

        binding.detailTitleTv.text = title
        binding.instructionsContentTv.text = instructions

        binding.detailAuthorTv.text = if (authorName.isNotBlank()) authorName else "Recipe User"
        binding.detailDateTv.text = formatCreatedAt(createdAt)
        binding.ingredientsContentTv.text = formatIngredients(ingredients)

        setupCommentsRecyclerView()

        if (recipeId.isNotEmpty()) {
            observeLikesAndComments(recipeId)
            setupInteractions(recipeId)
        }

        // Display tags dynamically
        displayTags(tagsJson)

        binding.backBtn.setOnClickListener {
            findNavController().navigateUp()
        }

        val displayImageUrl = imageRemoteUrl ?: imageUrl

        if (!displayImageUrl.isNullOrEmpty()) {
            when {
                displayImageUrl.startsWith("data:image") -> {
                    try {
                        val base64 = displayImageUrl.substringAfter("base64,")
                        val bytes = Base64.decode(base64, Base64.NO_WRAP)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        binding.detailImageIv.setImageBitmap(bitmap)
                    } catch (e: Exception) {
                        binding.detailImageIv.setImageResource(R.drawable.ic_launcher_foreground)
                    }
                }
                displayImageUrl.startsWith("content://") || displayImageUrl.startsWith("file://") -> {
                    Picasso.get()
                        .load(Uri.parse(displayImageUrl))
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .error(R.drawable.ic_launcher_foreground)
                        .into(binding.detailImageIv)
                }
                else -> {
                    Picasso.get()
                        .load(displayImageUrl)
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .error(R.drawable.ic_launcher_foreground)
                        .into(binding.detailImageIv)
                }
            }
        } else {
            binding.detailImageIv.setImageResource(R.drawable.ic_launcher_foreground)
        }
    }

    private fun setupCommentsRecyclerView() {
        val currentUserId = viewModel.getCurrentUserId()
        commentAdapter = CommentAdapter(
            comments = emptyList(),
            currentUserId = currentUserId,
            onEdit = { comment ->
                showEditCommentDialog(comment)
            },
            onDelete = { comment ->
                showDeleteCommentDialog(comment)
            }
        )
        binding.commentsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = commentAdapter
        }
    }

    private fun showEditCommentDialog(comment: com.example.recipeapp.model.recipes.Comment) {
        val editText = android.widget.EditText(requireContext()).apply {
            setText(comment.text)
            setPadding(40, 20, 40, 20)
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Edit Comment")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val newText = editText.text.toString().trim()
                if (newText.isNotEmpty()) {
                    viewModel.editComment(comment.recipeId, comment.id, newText)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteCommentDialog(comment: com.example.recipeapp.model.recipes.Comment) {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Comment")
            .setMessage("Are you sure you want to delete this comment?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteComment(comment.recipeId, comment.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeLikesAndComments(recipeId: String) {
        viewModel.getRecipeById(recipeId).observe(viewLifecycleOwner) { recipe ->
            recipe?.let {
                binding.detailLikesCountTv.text = getString(R.string.likes_count, it.likesCount)
            }
        }

        viewModel.checkIfLiked(recipeId).observe(viewLifecycleOwner) { isLiked ->
            val heartTint = if (isLiked) {
                Color.RED
            } else {
                Color.parseColor("#757575")
            }
            binding.detailLikeBtn.setColorFilter(heartTint)
        }

        viewModel.getCommentsForRecipe(recipeId).observe(viewLifecycleOwner) { comments ->
            commentAdapter.setComments(comments)
        }

        viewModel.refreshComments(recipeId)
        viewModel.refreshLikeStatus(recipeId)
    }

    private fun setupInteractions(recipeId: String) {
        binding.detailLikeBtn.setOnClickListener {
            if (viewModel.isLoggedIn()) {
                viewModel.toggleLike(recipeId)
            } else {
                Toast.makeText(requireContext(), "Please login to like", Toast.LENGTH_SHORT).show()
            }
        }

        binding.sendCommentBtn.setOnClickListener {
            val text = binding.commentInputEt.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            if (viewModel.isLoggedIn()) {
                viewModel.addComment(recipeId, text, viewModel.getCurrentUserName())
                binding.commentInputEt.text.clear()
            } else {
                Toast.makeText(requireContext(), "Please login to comment", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun formatCreatedAt(createdAt: Long): String {
        if (createdAt <= 0L) return "Unknown date"
        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return formatter.format(Date(createdAt))
    }

    private fun formatIngredients(raw: String): String {
        if (raw.isBlank()) return "• Ingredients not available yet"
        val items = raw
            .split("\n", ",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return if (items.isEmpty()) {
            "• Ingredients not available yet"
        } else {
            items.joinToString("\n") { "• $it" }
        }
    }

    private fun displayTags(tagsJson: String?) {
        binding.tagsContainer.removeAllViews() // Clear existing views

        if (!tagsJson.isNullOrEmpty()) {
            try {
                val gson = Gson()
                val tagListType = object : TypeToken<List<String>>() {}.type
                val tags: List<String> = gson.fromJson(tagsJson, tagListType)

                tags.forEach { tag ->
                    val tagView = TextView(requireContext()).apply {
                        text = tag
                        textSize = 14f
                        setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                        background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_login_button)
                        setPadding(
                            dp(18),
                            dp(8),
                            dp(18),
                            dp(8)
                        )
                        layoutParams = FlexboxLayout.LayoutParams(
                            FlexboxLayout.LayoutParams.WRAP_CONTENT,
                            FlexboxLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            marginEnd = dp(10)
                            bottomMargin = dp(10)
                        }
                    }

                    binding.tagsContainer.addView(tagView)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Helper function to convert dp to pixels
    private fun dp(value: Int): Int {
        return (value * requireContext().resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}