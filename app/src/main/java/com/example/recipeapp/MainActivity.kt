package com.example.recipeapp

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.example.recipeapp.auth.AuthResult
import com.example.recipeapp.auth.UserAuthManager
import com.example.recipeapp.ui.viewmodel.AuthViewModel
import com.example.recipeapp.ui.viewmodel.ProfileState
import com.example.recipeapp.ui.viewmodel.ProfileViewModel
import com.google.android.material.navigation.NavigationView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var drawerUserName: TextView
    private lateinit var drawerUserSubtitle: TextView
    private val authViewModel: AuthViewModel by viewModels()
    private val profileViewModel: ProfileViewModel by viewModels()

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
        drawerUserName = headerView.findViewById(R.id.drawerUserName)
        drawerUserSubtitle = headerView.findViewById(R.id.drawerUserSubtitle)

        closeBtn.setOnClickListener {
            closeDrawer()
        }

        setupDrawerHeader()
        observeAuthState()

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
                    updateDrawerHeaderForGuest()
                    profileViewModel.clearProfileState()
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

                    val currentDestinationId = navController.currentDestination?.id
                    if (currentDestinationId == item.itemId) {
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

    override fun onResume() {
        super.onResume()
        if (userAuthManager.isGuest()) {
            updateDrawerHeaderForGuest()
            profileViewModel.clearProfileState()
        } else {
            updateDrawerHeaderFromAuthFallback()
            profileViewModel.loadProfile()
        }
    }

    private fun setupDrawerHeader() {
        if (userAuthManager.isGuest()) {
            updateDrawerHeaderForGuest()
        } else {
            updateDrawerHeaderFromAuthFallback()
            profileViewModel.loadProfile()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                profileViewModel.profileState.collect { state ->
                    if (userAuthManager.isGuest()) {
                        updateDrawerHeaderForGuest()
                        return@collect
                    }

                    when (state) {
                        is ProfileState.Loaded -> {
                            val subtitle = when {
                                state.username.isNotBlank() -> state.username
                                state.email.isNotBlank() -> state.email
                                else -> ""
                            }
                            updateDrawerHeaderForLoggedIn(state.displayName, subtitle)
                        }

                        else -> {
                            updateDrawerHeaderFromAuthFallback()
                        }
                    }
                }
            }
        }
    }

    private fun observeAuthState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.authState.collect { result ->
                    when (result) {
                        is AuthResult.Success -> {
                            updateDrawerHeaderFromAuthFallback()
                            profileViewModel.loadProfile()
                        }
                        is AuthResult.Error -> {
                            // No drawer update needed for auth errors.
                        }
                        null -> {
                            if (userAuthManager.isGuest()) {
                                updateDrawerHeaderForGuest()
                            } else {
                                updateDrawerHeaderFromAuthFallback()
                                profileViewModel.loadProfile()
                            }
                        }
                    }
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

    private fun updateDrawerHeaderForGuest() {
        drawerUserName.text = getString(R.string.drawer_guest_name)
        drawerUserSubtitle.text = getString(R.string.drawer_guest_subtitle)
    }

    private fun updateDrawerHeaderFromAuthFallback() {
        val name = userAuthManager.getDisplayName()
            ?.takeIf { it.isNotBlank() }
            ?: userAuthManager.getEmail()?.substringBefore("@")
            ?: ""
        val subtitle = userAuthManager.getEmail().orEmpty()
        updateDrawerHeaderForLoggedIn(name, subtitle)
    }

    private fun updateDrawerHeaderForLoggedIn(displayName: String?, subtitle: String?) {
        drawerUserName.text = displayName?.takeIf { it.isNotBlank() }.orEmpty()
        drawerUserSubtitle.text = subtitle?.takeIf { it.isNotBlank() }.orEmpty()
    }

    fun openDrawer() {
        drawerLayout.openDrawer(GravityCompat.START)
    }

    fun closeDrawer() {
        drawerLayout.closeDrawer(GravityCompat.START)
    }
}