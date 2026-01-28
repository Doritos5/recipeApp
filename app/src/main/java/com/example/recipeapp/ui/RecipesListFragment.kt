package com.example.recipeapp.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.recipeapp.R
import com.example.recipeapp.RecipeViewModel

class RecipesListFragment : Fragment() {

    private lateinit var viewModel: RecipeViewModel
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

        // Start with an empty list
        recipesAdapter = RecipesAdapter(emptyList())
        recyclerView.adapter = recipesAdapter

        // 2. Initialize ViewModel
        viewModel = ViewModelProvider(this)[RecipeViewModel::class.java]

        // 3. Observe LiveData
        viewModel.recipes.observe(viewLifecycleOwner) { recipes ->
            // Update the adapter with the new list from DB
            recipesAdapter.setRecipes(recipes)

            Log.d("RECIPE_TEST", "Updated UI with ${recipes.size} recipes")
        }

        // 4. Fetch fresh data
        viewModel.reloadRecipes()


        val fab: View = view.findViewById(R.id.addRecipeFab)
        fab.setOnClickListener {
            // The correct way to navigate using the Graph
            findNavController().navigate(R.id.action_list_to_add)

        }

        return view
    }
}