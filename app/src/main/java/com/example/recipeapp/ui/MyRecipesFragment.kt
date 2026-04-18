package com.example.recipeapp.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.recipeapp.R
import com.example.recipeapp.RecipeViewModel
import com.example.recipeapp.ui.viewmodel.AuthViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import android.app.AlertDialog
import android.view.WindowManager
import com.google.android.material.button.MaterialButton
import com.example.recipeapp.model.recipes.Recipe

@AndroidEntryPoint
class MyRecipesFragment : Fragment() {

    private val recipeViewModel: RecipeViewModel by activityViewModels()
    private val authViewModel: AuthViewModel by activityViewModels()

    private lateinit var recipesAdapter: MyRecipesAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyTv: TextView
    private lateinit var recipesCountTv: TextView
    private lateinit var backBtn: View

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_my_recipes, container, false)

        recyclerView = view.findViewById(R.id.myRecipesRv)
        emptyTv = view.findViewById(R.id.myRecipesEmptyTv)
        recipesCountTv = view.findViewById(R.id.recipesCountTv)
        backBtn = view.findViewById(R.id.backBtn)

        recyclerView.layoutManager = LinearLayoutManager(context)

        backBtn.setOnClickListener {
            findNavController().navigateUp()
        }

        val userId = authViewModel.getCurrentUserId()

        if (userId == null) {
            emptyTv.text = getString(R.string.guest_mode)
            emptyTv.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            recipesCountTv.text = "0 recipes"
        } else {
            recipesAdapter = MyRecipesAdapter(
                recipes = emptyList(),
                onViewClick = { recipe ->
                    val bundle = Bundle().apply {
                        putString("id", recipe.id)
                        putString("title", recipe.title)
                        putString("instructions", recipe.instructions.joinToString("\n"))
                        putString("authorName", recipe.authorName)
                        putLong("createdAt", recipe.createdAt)
                        putString("ingredients", recipe.ingredients)
                        putString("imageUrl", recipe.imageUrl)
                        putString("imageRemoteUrl", recipe.imageRemoteUrl)
                        putString("tags", Gson().toJson(recipe.tags))
                    }

                    findNavController().navigate(
                        R.id.action_myRecipes_to_recipeDetailFragment,
                        bundle
                    )
                },
                onEditClick = { recipe ->
                    val action = MyRecipesFragmentDirections
                        .actionMyRecipesToEditRecipe(recipeId = recipe.id)
                    findNavController().navigate(action)
                },
                onDeleteClick = { recipe ->
                    try {
                        showDeleteRecipeDialog(recipe)
                    } catch (e: Exception) {
                        Log.e("RECIPE_TEST", "Error showing delete dialog: ${e.message}")
                    }
                }
            )

            recyclerView.adapter = recipesAdapter

            recipeViewModel.getMyRecipes(userId).observe(viewLifecycleOwner) { recipes ->
                recipesAdapter.setRecipes(recipes)

                recipesCountTv.text = if (recipes.size == 1) {
                    "1 recipe"
                } else {
                    "${recipes.size} recipes"
                }

                if (recipes.isEmpty()) {
                    emptyTv.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyTv.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }

                Log.d("RECIPE_TEST", "My Recipes: ${recipes.size} items")
            }

            recipeViewModel.reloadMyRecipes(userId)
        }

        val fab: FloatingActionButton = view.findViewById(R.id.myRecipesAddFab)
        fab.setOnClickListener {
            findNavController().navigate(R.id.action_myRecipes_to_add)
        }

        return view
    }

    private fun showDeleteRecipeDialog(recipe: Recipe) {
        if (!isAdded) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_recipe, null)

        val cancelBtn = dialogView.findViewById<MaterialButton>(R.id.deleteDialogCancelBtn)
        val deleteBtn = dialogView.findViewById<MaterialButton>(R.id.deleteDialogDeleteBtn)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_window_transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        cancelBtn.setOnClickListener {
            dialog.dismiss()
        }

        deleteBtn.setOnClickListener {
            recipeViewModel.deleteRecipe(recipe)

            if (isAdded) {
                Toast.makeText(requireActivity(), "Recipe deleted", Toast.LENGTH_SHORT).show()
            }

            dialog.dismiss()
        }

        dialog.show()

        dialog.window?.setDimAmount(0.55f)
    }
}