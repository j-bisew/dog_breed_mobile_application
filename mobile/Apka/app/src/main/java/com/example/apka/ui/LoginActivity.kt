package com.example.apka.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.apka.R
import com.example.apka.network.ApiClient
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import android.util.Log

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        supportActionBar?.hide()

        val username = findViewById<EditText>(R.id.usernameEdit)
        val password = findViewById<EditText>(R.id.passwordEdit)
        val loginBtn = findViewById<Button>(R.id.loginButton)

        loginBtn.setOnClickListener {
            val user = username.text.toString().trim()
            val pass = password.text.toString().trim()
            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Enter credentials", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val body = JsonObject()
                    val loginData = JsonObject()
                    loginData.addProperty("username", user)
                    loginData.addProperty("password", pass)
                    body.add("loginData", loginData)
                    val resp = withContext(Dispatchers.IO) { ApiClient.service.login(body) }
                    if (resp.isSuccessful) {
                        val raw = try { resp.body()?.string() } catch (e: Exception) { null }
                        val token = try { com.google.gson.JsonParser.parseString(raw ?: "").asJsonObject.get("token")?.asString ?: com.google.gson.JsonParser.parseString(raw ?: "").asJsonObject.get("access")?.asString } catch (e: Exception) { null }
                        if (!token.isNullOrEmpty()) {
                            // save token
                            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                            prefs.edit().putString("auth_token", token).apply()
                            startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                            finish()
                        } else {
                            Toast.makeText(this@LoginActivity, "Login OK (no token)", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                            finish()
                        }
                    } else {
                        Toast.makeText(this@LoginActivity, "Login failed: ${resp.code()}", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("LoginActivity", "Login error", e)
                    Toast.makeText(this@LoginActivity, "Login error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
