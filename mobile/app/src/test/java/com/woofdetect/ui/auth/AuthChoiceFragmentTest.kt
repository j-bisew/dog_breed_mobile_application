package com.woofdetect.ui.auth

import android.widget.Button
import androidx.fragment.app.FragmentActivity
import com.woofdetect.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthChoiceFragmentTest {

    private fun launchFragment(): AuthChoiceFragment {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java)
            .create()
            .start()
            .resume()
            .get()

        activity.setContentView(R.layout.activity_main)

        val fragment = AuthChoiceFragment()
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commitNow()

        return fragment
    }

    @Test
    fun `fragment launches successfully`() {
        val fragment = launchFragment()
        assertNotNull(fragment)
    }

    @Test
    fun `login button is visible`() {
        val fragment = launchFragment()
        val button = fragment.view?.findViewById<Button>(R.id.loginButton)
        assertNotNull(button)
    }

    @Test
    fun `register button is visible`() {
        val fragment = launchFragment()
        val button = fragment.view?.findViewById<Button>(R.id.registerButton)
        assertNotNull(button)
    }

    @Test
    fun `clicking login button navigates to login fragment`() {
        val fragment = launchFragment()
        fragment.view?.findViewById<Button>(R.id.loginButton)?.performClick()
    }

    @Test
    fun `clicking register button navigates to register fragment`() {
        val fragment = launchFragment()
        fragment.view?.findViewById<Button>(R.id.registerButton)?.performClick()
    }
}