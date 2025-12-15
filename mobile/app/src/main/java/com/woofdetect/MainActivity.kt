package com.woofdetect

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.woofdetect.ui.auth.AuthChoiceFragment
import com.woofdetect.ui.main.HomeFragment

class MainActivity : AppCompatActivity(R.layout.activity_main) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, AuthChoiceFragment())
                .commit()
        }
    }
}