package com.example.recipeapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.recipeapp.R
import com.example.recipeapp.auth.AuthResult
import com.example.recipeapp.ui.viewmodel.AuthViewModel
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private val authViewModel: AuthViewModel by activityViewModels()

    private lateinit var emailEt: TextInputEditText
    private lateinit var passwordEt: TextInputEditText
    private lateinit var loginBtn: Button
    private lateinit var goToSignUpBtn: Button
    private lateinit var continueAsGuestBtn: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // If already logged in, skip to recipe list
        if (authViewModel.isLoggedIn()) {
            findNavController().navigate(R.id.action_login_to_list)
            return null
        }

        val view = inflater.inflate(R.layout.fragment_login, container, false)

        emailEt = view.findViewById(R.id.loginEmailEt)
        passwordEt = view.findViewById(R.id.loginPasswordEt)
        loginBtn = view.findViewById(R.id.loginBtn)
        goToSignUpBtn = view.findViewById(R.id.goToSignUpBtn)
        continueAsGuestBtn = view.findViewById(R.id.continueAsGuestBtn)
        progressBar = view.findViewById(R.id.loginProgressBar)

        loginBtn.setOnClickListener {
            val email = emailEt.text.toString().trim()
            val password = passwordEt.text.toString().trim()

            if (email.isEmpty()) {
                emailEt.error = "Please enter your email"
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                passwordEt.error = "Please enter your password"
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            loginBtn.isEnabled = false
            authViewModel.login(email, password)
        }

        goToSignUpBtn.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_signUp)
        }

        continueAsGuestBtn.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_list)
        }

        observeAuthState()

        return view
    }

    private fun observeAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.authState.collect { result ->
                    when (result) {
                        is AuthResult.Success -> {
                            progressBar.visibility = View.GONE
                            loginBtn.isEnabled = true
                            authViewModel.resetAuthState()
                            findNavController().navigate(R.id.action_login_to_list)
                        }
                        is AuthResult.Error -> {
                            progressBar.visibility = View.GONE
                            loginBtn.isEnabled = true
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            authViewModel.resetAuthState()
                        }
                        null -> {
                            // Idle state — do nothing
                        }
                    }
                }
            }
        }
    }
}




