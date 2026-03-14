package com.example.recipeapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
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
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SignUpFragment : Fragment() {

    private val authViewModel: AuthViewModel by activityViewModels()

    private lateinit var firstNameEt: EditText
    private lateinit var lastNameEt: EditText
    private lateinit var usernameEt: EditText
    private lateinit var emailEt: EditText
    private lateinit var passwordEt: EditText
    private lateinit var signUpBtn: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_sign_up, container, false)

        // Initialize firstName field
        val firstNameContainer = view.findViewById<View>(R.id.firstNameContainer)
        firstNameEt = firstNameContainer.findViewById(R.id.fieldInput)
        firstNameContainer.findViewById<TextView>(R.id.fieldLabel).text = "First Name"

        // Initialize lastName field
        val lastNameContainer = view.findViewById<View>(R.id.lastNameContainer)
        lastNameEt = lastNameContainer.findViewById(R.id.fieldInput)
        lastNameContainer.findViewById<TextView>(R.id.fieldLabel).text = "Last Name"

        // Initialize username field
        val usernameContainer = view.findViewById<View>(R.id.usernameContainer)
        usernameEt = usernameContainer.findViewById(R.id.fieldInput)
        usernameContainer.findViewById<TextView>(R.id.fieldLabel).text = "Username"

        // Initialize email field
        val emailContainer = view.findViewById<View>(R.id.emailContainer)
        emailEt = emailContainer.findViewById(R.id.fieldInput)
        emailContainer.findViewById<TextView>(R.id.fieldLabel).text = "Email"

        // Initialize password field
        val passwordContainer = view.findViewById<View>(R.id.passwordContainer)
        passwordEt = passwordContainer.findViewById(R.id.fieldInput)
        passwordContainer.findViewById<TextView>(R.id.fieldLabel).text = "Password"

        signUpBtn = view.findViewById(R.id.signUpBtn)

        signUpBtn.setOnClickListener {
            val firstName = firstNameEt.text.toString().trim()
            val lastName = lastNameEt.text.toString().trim()
            val username = usernameEt.text.toString().trim()
            val email = emailEt.text.toString().trim()
            val password = passwordEt.text.toString().trim()

            // Validate all fields
            if (firstName.isEmpty()) {
                firstNameEt.error = "Please enter your first name"
                return@setOnClickListener
            }
            if (lastName.isEmpty()) {
                lastNameEt.error = "Please enter your last name"
                return@setOnClickListener
            }
            if (username.isEmpty()) {
                usernameEt.error = "Please enter a username"
                return@setOnClickListener
            }
            if (email.isEmpty()) {
                emailEt.error = "Please enter your email"
                return@setOnClickListener
            }
            if (!email.contains("@")) {
                emailEt.error = "Please enter a valid email"
                return@setOnClickListener
            }
            if (password.length < 6) {
                passwordEt.error = "Password must be at least 6 characters"
                return@setOnClickListener
            }

            signUpBtn.isEnabled = false
            // Call sign up with all user data
            authViewModel.signUp(
                email = email,
                password = password,
                firstName = firstName,
                lastName = lastName,
                username = username
            )
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
                            signUpBtn.isEnabled = true
                            Toast.makeText(context, "Account created!", Toast.LENGTH_SHORT).show()
                            authViewModel.resetAuthState()
                            findNavController().navigate(R.id.action_signUp_to_list)
                        }
                        is AuthResult.Error -> {
                            signUpBtn.isEnabled = true
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

