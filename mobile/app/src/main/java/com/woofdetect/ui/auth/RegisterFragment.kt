package com.woofdetect.ui.auth

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.woofdetect.R
import com.woofdetect.auth.TokenManager
import com.woofdetect.databinding.FragmentRegisterBinding
import com.woofdetect.repository.AuthRepository
import kotlinx.coroutines.launch

class RegisterFragment : Fragment(R.layout.fragment_register) {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val authRepository = AuthRepository()
    private lateinit var tokenManager: TokenManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentRegisterBinding.bind(view)

        tokenManager = TokenManager(requireContext())

        setupListeners()
    }

    private fun setupListeners() {
        binding.registerButton.setOnClickListener {
            val name = binding.nameInput.text.toString().trim()
            val username = binding.usernameInput.text.toString().trim()
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            if (validateInputs(name, username, email, password)) {
                performRegistration(name, username, email, password)
            }
        }

        binding.goToLogin?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun validateInputs(
        name: String,
        username: String,
        email: String,
        password: String
    ): Boolean {
        var isValid = true

        when {
            name.isEmpty() -> {
                binding.errorText.text = "Imię nie może być puste"
                binding.errorText.visibility = View.VISIBLE
                isValid = false
            }
            username.isEmpty() -> {
                binding.errorText.text = "Username nie może być pusty"
                binding.errorText.visibility = View.VISIBLE
                isValid = false
            }
            email.isEmpty() -> {
                binding.errorText.text = "Email nie może być pusty"
                binding.errorText.visibility = View.VISIBLE
                isValid = false
            }
            password.isEmpty() -> {
                binding.errorText.text = "Hasło nie może być puste"
                binding.errorText.visibility = View.VISIBLE
                isValid = false
            }
            password.length < 8 -> {
                binding.errorText.text = "Hasło musi mieć minimum 8 znaków"
                binding.errorText.visibility = View.VISIBLE
                isValid = false
            }
        }

        return isValid
    }

    private fun performRegistration(
        name: String,
        username: String,
        email: String,
        password: String
    ) {
        // Pokaż loading
        showLoading(true)
        binding.errorText.visibility = View.GONE

        lifecycleScope.launch {
            val result = authRepository.register(name, username, email, password)

            result.onSuccess { registerResponse ->
                // Zapisz token (opcjonalnie - jeśli chcesz od razu zalogować)
                // tokenManager.saveToken(registerResponse.token, username)

                showLoading(false)

                // Pokaż sukces i przejdź do logowania
                binding.errorText.text = "Rejestracja udana! Teraz się zaloguj."
                binding.errorText.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
                binding.errorText.visibility = View.VISIBLE

                // Po 2 sekundach przejdź do logowania
                view?.postDelayed({
                    parentFragmentManager.popBackStack()
                }, 2000)
            }

            result.onFailure { error ->
                showLoading(false)
                binding.errorText.text = "Błąd rejestracji: ${error.message}"
                binding.errorText.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                binding.errorText.visibility = View.VISIBLE
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.registerButton.isEnabled = !isLoading
        binding.nameInput.isEnabled = !isLoading
        binding.usernameInput.isEnabled = !isLoading
        binding.emailInput.isEnabled = !isLoading
        binding.passwordInput.isEnabled = !isLoading

        if (isLoading) {
            binding.registerButton.text = "Rejestracja..."
        } else {
            binding.registerButton.text = "Zarejestruj"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}