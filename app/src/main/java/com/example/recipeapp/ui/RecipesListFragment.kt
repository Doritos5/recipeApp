package com.example.recipeapp.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.recipeapp.R
import com.example.recipeapp.RecipeViewModel
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import com.example.recipeapp.MainActivity
import com.example.recipeapp.model.recipes.Recipe

@AndroidEntryPoint
class RecipesListFragment : Fragment() {

    private val viewModel: RecipeViewModel by activityViewModels()
    private lateinit var recipesAdapter: RecipesAdapter
    private lateinit var recyclerView: RecyclerView
    private var allRecipes: List<Recipe> = emptyList()
    private var currentQuery: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_recipes_list, container, false)

        // 1. Initialize RecyclerView and Adapter
        recyclerView = view.findViewById(R.id.recipesListRv)
        recyclerView.layoutManager = LinearLayoutManager(context)

        recipesAdapter = RecipesAdapter(
            recipes = emptyList(),
            onRecipeClick = { recipe ->
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
                    R.id.action_recipeListFragment_to_recipeDetailFragment,
                    bundle
                )
            },
            onLikeClick = { recipe ->
                if (viewModel.isLoggedIn()) {
                    viewModel.toggleLike(recipe.id)
                } else {
                    android.widget.Toast.makeText(requireContext(), "Please login to like", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        )

        recyclerView.adapter = recipesAdapter

        val searchInput: EditText = view.findViewById(R.id.searchRecipesEt)
        searchInput.addTextChangedListener { text ->
            currentQuery = text?.toString().orEmpty()
            applyFilter(currentQuery)
        }

        // 2. Observe LiveData
        viewModel.recipes.observe(viewLifecycleOwner) { recipes ->
            allRecipes = recipes
            applyFilter(currentQuery)
            Log.d("RECIPE_TEST", "Updated UI with ${recipes.size} recipes")
        }

        // 3. Fetch fresh data
        viewModel.reloadRecipes()

        val menuIcon: View = view.findViewById(R.id.menuIcon)
        menuIcon.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        val fab: View = view.findViewById(R.id.addRecipeFab)
        fab.setOnClickListener {
            findNavController().navigate(R.id.action_list_to_add)
        }

        return view
    }

    private fun applyFilter(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isEmpty()) {
            recipesAdapter.setRecipes(allRecipes)
            return
        }

        val filtered = allRecipes.filter { recipe ->
            recipe.title.contains(query, ignoreCase = true) ||
                (recipe.ingredients?.contains(query, ignoreCase = true) == true) ||
                recipe.tags.any { tag -> tag.contains(query, ignoreCase = true) }
        }
        recipesAdapter.setRecipes(filtered)
    }
}