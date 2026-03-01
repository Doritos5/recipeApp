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
import dagger.hilt.android.AndroidEntryPoint

/**
 * MyRecipesFragment displays only recipes created by the currently logged-in user.
 * Supports edit and delete actions via SafeArgs.
 */
@AndroidEntryPoint
class MyRecipesFragment : Fragment() {

    private val recipeViewModel: RecipeViewModel by activityViewModels()
    private val authViewModel: AuthViewModel by activityViewModels()
    private lateinit var recipesAdapter: RecipesAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyTv: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_my_recipes, container, false)

        recyclerView = view.findViewById(R.id.myRecipesRv)
        emptyTv = view.findViewById(R.id.myRecipesEmptyTv)
        recyclerView.layoutManager = LinearLayoutManager(context)

        val userId = authViewModel.getCurrentUserId()

        if (userId == null) {
            // Guest mode
            emptyTv.text = getString(R.string.guest_mode)
            emptyTv.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            // Logged in — show user's recipes with edit/delete
            recipesAdapter = RecipesAdapter(
                recipes = emptyList(),
                onEditClick = { recipe ->
                    val action = MyRecipesFragmentDirections
                        .actionMyRecipesToEditRecipe(recipeId = recipe.id)
                    findNavController().navigate(action)
                },
                onDeleteClick = { recipe ->
                    try {
                        if (!isAdded) return@RecipesAdapter
                        MaterialAlertDialogBuilder(requireActivity())
                            .setTitle("Delete Recipe")
                            .setMessage("Are you sure you want to delete \"${recipe.title}\"?")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete") { _, _ ->
                                recipeViewModel.deleteRecipe(recipe)
                                if (isAdded) {
                                    Toast.makeText(requireActivity(), "Recipe deleted", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .show()
                    } catch (e: Exception) {
                        Log.e("RECIPE_TEST", "Error showing delete dialog: ${e.message}")
                    }
                }
            )
            recyclerView.adapter = recipesAdapter

            // Observe only this user's recipes
            recipeViewModel.getMyRecipes(userId).observe(viewLifecycleOwner) { recipes ->
                recipesAdapter.setRecipes(recipes)
                if (recipes.isEmpty()) {
                    emptyTv.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyTv.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
                Log.d("RECIPE_TEST", "My Recipes: ${recipes.size} items")
            }

            // Refresh from Firestore (data isolation)
            recipeViewModel.reloadMyRecipes(userId)
        }

        // FAB to add a new recipe
        val fab: FloatingActionButton = view.findViewById(R.id.myRecipesAddFab)
        fab.setOnClickListener {
            findNavController().navigate(R.id.action_myRecipes_to_add)
        }

        return view
    }
}

