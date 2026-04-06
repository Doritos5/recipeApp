package com.example.recipeapp

import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.example.recipeapp.auth.UserAuthManager
import com.example.recipeapp.ui.viewmodel.AuthViewModel
import com.google.android.material.navigation.NavigationView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private val authViewModel: AuthViewModel by viewModels()

    @Inject
    lateinit var userAuthManager: UserAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawer_layout)
        drawerLayout.setStatusBarBackgroundColor(android.graphics.Color.TRANSPARENT)

        navigationView = findViewById(R.id.navigation_view)

        val headerView = navigationView.getHeaderView(0)
        val closeBtn = headerView.findViewById<ImageView>(R.id.drawerCloseBtn)

        closeBtn.setOnClickListener {
            closeDrawer()
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        AppBarConfiguration(
            setOf(
                R.id.recipesListFragment,
                R.id.myRecipesFragment,
                R.id.profileFragment,
                R.id.addRecipeFragment
            ),
            drawerLayout
        )

        navigationView.setupWithNavController(navController)

        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_logout -> {
                    authViewModel.logout()
                    val navOptions = NavOptions.Builder()
                        .setPopUpTo(navController.graph.startDestinationId, true)
                        .build()
                    navController.navigate(R.id.loginFragment, null, navOptions)
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }

                else -> {
                    if (isGuestBlockedDestination(item.itemId)) {
                        Toast.makeText(this, getString(R.string.guest_blocked_action), Toast.LENGTH_SHORT).show()
                        drawerLayout.closeDrawer(GravityCompat.START)
                        return@setNavigationItemSelectedListener true
                    }

                    val handled = try {
                        navController.navigate(item.itemId)
                        true
                    } catch (_: Exception) {
                        false
                    }

                    drawerLayout.closeDrawer(GravityCompat.START)
                    handled
                }
            }
        }
    }

    private fun isGuestBlockedDestination(destinationId: Int): Boolean {
        if (!userAuthManager.isGuest()) return false
        return destinationId == R.id.profileFragment ||
                destinationId == R.id.editProfileFragment ||
                destinationId == R.id.addRecipeFragment
    }

    fun openDrawer() {
        drawerLayout.openDrawer(GravityCompat.START)
    }

    fun closeDrawer() {
        drawerLayout.closeDrawer(GravityCompat.START)
    }
}