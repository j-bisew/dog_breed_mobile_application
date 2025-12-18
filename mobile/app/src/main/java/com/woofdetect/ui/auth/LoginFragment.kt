package com.woofdetect.ui.auth

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.woofdetect.R
import com.woofdetect.auth.TokenManager
import com.woofdetect.databinding.FragmentLoginBinding
import com.woofdetect.repository.AuthRepository
import com.woofdetect.ui.main.HomeFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class LoginFragment : Fragment(R.layout.fragment_login) {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val authRepository = AuthRepository()
    private lateinit var tokenManager: TokenManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLoginBinding.bind(view)

        tokenManager = TokenManager(requireContext())

        setupListeners()

    }

    private fun setupListeners() {
        binding.loginButton.setOnClickListener {
            val username = binding.usernameInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            if (validateInputs(username, password)) {
                performLogin(username, password)
            }
        }

        binding.goToRegister.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, RegisterFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun validateInputs(username: String, password: String): Boolean {
        var isValid = true

        if (username.isEmpty()) {
            binding.errorText.text = "Username nie może być pusty"
            binding.errorText.visibility = View.VISIBLE
            isValid = false
        } else if (password.isEmpty()) {
            binding.errorText.text = "Hasło nie może być puste"
            binding.errorText.visibility = View.VISIBLE
            isValid = false
        }

        return isValid
    }

    private fun performLogin(username: String, password: String) {
        // Pokaż loading
        showLoading(true)
        binding.errorText.visibility = View.GONE

        lifecycleScope.launch {
            val result = authRepository.login(username, password)

            result.onSuccess { loginResponse ->
                // Zapisz token
                tokenManager.saveToken(loginResponse.token, username)

                // Przejdź do HomeFragment
                showLoading(false)
                navigateToHome()
            }

            result.onFailure { error ->
                showLoading(false)
                binding.errorText.text = "Błąd logowania: ${error.message}"
                binding.errorText.visibility = View.VISIBLE
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.loginButton.isEnabled = !isLoading
        binding.usernameInput.isEnabled = !isLoading
        binding.passwordInput.isEnabled = !isLoading

        if (isLoading) {
            binding.loginButton.text = "Logowanie..."
        } else {
            binding.loginButton.text = "Zaloguj"
        }
    }

    private fun navigateToHome() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, HomeFragment())
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
