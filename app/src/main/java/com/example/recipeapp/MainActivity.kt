package com.example.recipeapp

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
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
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var drawerUserName: TextView
    private lateinit var drawerUserSubtitle: TextView
    private lateinit var drawerProfileImage: ImageView
    private var lastProfileImageUrl: String? = null
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
        drawerProfileImage = headerView.findViewById(R.id.drawerProfileImage)

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
            lastProfileImageUrl?.let { updateDrawerProfileImage(it) }
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
                            updateDrawerProfileImage(state.profileImageUrl)
                        }

                        is ProfileState.Updated -> {
                            profileViewModel.loadProfile()
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
        lastProfileImageUrl = null
        drawerProfileImage.setImageResource(R.drawable.icon_account)
    }

    private fun updateDrawerHeaderFromAuthFallback() {
        val name = userAuthManager.getDisplayName()
            ?.takeIf { it.isNotBlank() }
            ?: userAuthManager.getEmail()?.substringBefore("@")
            ?: ""
        val subtitle = userAuthManager.getEmail().orEmpty()
        updateDrawerHeaderForLoggedIn(name, subtitle)
        updateDrawerProfileImage(userAuthManager.getProfileImageUrl())
    }

    private fun updateDrawerHeaderForLoggedIn(displayName: String?, subtitle: String?) {
        val rawName = displayName?.trim()?.replace("\n", " ")?.replace("\r", " ").orEmpty()
        val nameText = normalizeDisplayName(rawName)
        val subtitleText = subtitle?.trim()?.replace("\n", " ")?.replace("\r", " ").orEmpty()
        val normalizedName = nameText.lowercase()
        val normalizedSubtitle = subtitleText.lowercase()
        val resolvedSubtitle = if (subtitleText.isNotBlank() && normalizedSubtitle != normalizedName) {
            subtitleText
        } else {
            ""
        }
        drawerUserName.text = nameText
        drawerUserSubtitle.text = resolvedSubtitle
        drawerUserSubtitle.visibility = if (resolvedSubtitle.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun normalizeDisplayName(rawName: String): String {
        val parts = rawName.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return ""
        val deDuped = parts.fold(mutableListOf<String>()) { acc, part ->
            val last = acc.lastOrNull()
            if (last == null || last.lowercase() != part.lowercase()) {
                acc.add(part)
            }
            acc
        }
        val distinct = deDuped.map { it.lowercase() }.distinct()
        if (distinct.size == 1) return deDuped.first()
        if (deDuped.size % 2 == 0) {
            val mid = deDuped.size / 2
            val firstHalf = deDuped.subList(0, mid)
            val secondHalf = deDuped.subList(mid, deDuped.size)
            if (firstHalf.map { it.lowercase() } == secondHalf.map { it.lowercase() }) {
                return firstHalf.joinToString(" ")
            }
        }
        return deDuped.joinToString(" ")
    }

    private fun updateDrawerProfileImage(imageUrl: String?) {
        val resolvedUrl = imageUrl?.takeIf { it.isNotBlank() } ?: lastProfileImageUrl
        if (resolvedUrl.isNullOrBlank()) {
            drawerProfileImage.setImageResource(R.drawable.icon_account)
            return
        }

        lastProfileImageUrl = resolvedUrl

        when {
            resolvedUrl.startsWith("data:image") -> {
                renderBase64Image(resolvedUrl.substringAfter("base64,"))
            }
            isProbablyBase64Image(resolvedUrl) -> {
                renderBase64Image(resolvedUrl)
            }
            else -> {
                val uri = Uri.parse(resolvedUrl)
                Picasso.get()
                    .load(uri)
                    .placeholder(R.drawable.icon_account)
                    .error(R.drawable.icon_account)
                    .fit()
                    .centerCrop()
                    .into(drawerProfileImage)
            }
        }
    }

    private fun renderBase64Image(base64: String) {
        try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                drawerProfileImage.setImageBitmap(bitmap)
            } else {
                drawerProfileImage.setImageResource(R.drawable.icon_account)
            }
        } catch (_: Exception) {
            drawerProfileImage.setImageResource(R.drawable.icon_account)
        }
    }

    private fun isProbablyBase64Image(value: String): Boolean {
        if (value.length < 200) return false
        return value.all { char ->
            char.isLetterOrDigit() || char == '+' || char == '/' || char == '=' || char == '\n' || char == '\r'
        }
    }

    fun openDrawer() {
        drawerLayout.openDrawer(GravityCompat.START)
    }

    fun closeDrawer() {
        drawerLayout.closeDrawer(GravityCompat.START)
    }
}