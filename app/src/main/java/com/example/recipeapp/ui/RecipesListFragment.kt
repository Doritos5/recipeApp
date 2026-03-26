package com.example.recipeapp.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.recipeapp.R
import com.example.recipeapp.RecipeViewModel
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RecipesListFragment : Fragment() {

    private val viewModel: RecipeViewModel by activityViewModels()
    private lateinit var recipesAdapter: RecipesAdapter
    private lateinit var recyclerView: RecyclerView

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
                    putString("title", recipe.title)
                    putString("instructions", recipe.instructions)
                    putString("imageUrl", recipe.imageUrl)
                    putString("imageRemoteUrl", recipe.imageRemoteUrl)
                    // Add tags as JSON array string
                    putString("tags", Gson().toJson(recipe.tags))
                }

                findNavController().navigate(
                    R.id.action_recipeListFragment_to_recipeDetailFragment,
                    bundle
                )
            }
        )

        recyclerView.adapter = recipesAdapter

        // 2. Observe LiveData
        viewModel.recipes.observe(viewLifecycleOwner) { recipes ->
            recipesAdapter.setRecipes(recipes)
            Log.d("RECIPE_TEST", "Updated UI with ${recipes.size} recipes")
        }

        // 3. Fetch fresh data
        viewModel.reloadRecipes()

        val fab: View = view.findViewById(R.id.addRecipeFab)
        fab.setOnClickListener {
            findNavController().navigate(R.id.action_list_to_add)
        }

        return view
    }
}