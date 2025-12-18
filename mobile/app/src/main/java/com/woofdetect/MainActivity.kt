package com.woofdetect

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.woofdetect.auth.TokenManager
import com.woofdetect.network.ApiClient
import com.woofdetect.ui.auth.AuthChoiceFragment
import com.woofdetect.ui.main.HomeFragment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(R.layout.activity_main) {
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ApiClient.initialize(this)

        tokenManager = TokenManager(this)

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                val token = tokenManager.getToken().first()

                val startFragment = if (!token.isNullOrEmpty()) {
                    HomeFragment()
                } else {
                    AuthChoiceFragment()
                }

                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, startFragment)
                    .commit()
            }
        }
    }
}