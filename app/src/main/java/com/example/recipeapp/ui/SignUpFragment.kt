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
import com.example.recipeapp.RecipeViewModel
import com.example.recipeapp.auth.AuthResult
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SignUpFragment : Fragment() {

    private val viewModel: RecipeViewModel by activityViewModels()

    private lateinit var emailEt: TextInputEditText
    private lateinit var passwordEt: TextInputEditText
    private lateinit var signUpBtn: Button
    private lateinit var goToLoginBtn: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_sign_up, container, false)

        emailEt = view.findViewById(R.id.signUpEmailEt)
        passwordEt = view.findViewById(R.id.signUpPasswordEt)
        signUpBtn = view.findViewById(R.id.signUpBtn)
        goToLoginBtn = view.findViewById(R.id.goToLoginBtn)
        progressBar = view.findViewById(R.id.signUpProgressBar)

        signUpBtn.setOnClickListener {
            val email = emailEt.text.toString().trim()
            val password = passwordEt.text.toString().trim()

            if (email.isEmpty()) {
                emailEt.error = "Please enter your email"
                return@setOnClickListener
            }
            if (password.length < 6) {
                passwordEt.error = "Password must be at least 6 characters"
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            signUpBtn.isEnabled = false
            viewModel.signUp(email, password)
        }

        goToLoginBtn.setOnClickListener {
            findNavController().navigate(R.id.action_signUp_to_login)
        }

        observeAuthState()

        return view
    }

    private fun observeAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.authState.collect { result ->
                    when (result) {
                        is AuthResult.Success -> {
                            progressBar.visibility = View.GONE
                            signUpBtn.isEnabled = true
                            Toast.makeText(context, "Account created!", Toast.LENGTH_SHORT).show()
                            viewModel.resetAuthState()
                            findNavController().navigate(R.id.action_signUp_to_list)
                        }
                        is AuthResult.Error -> {
                            progressBar.visibility = View.GONE
                            signUpBtn.isEnabled = true
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            viewModel.resetAuthState()
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

