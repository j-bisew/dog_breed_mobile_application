package com.woofdetect.ui.auth

import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.FragmentActivity
import com.woofdetect.R
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoginFragmentTest {

    private fun launchFragment(): LoginFragment {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java)
            .create().start().resume().get()

        activity.setContentView(R.layout.activity_main)

        val fragment = LoginFragment()
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
        assertNotNull(fragment.view?.findViewById<Button>(R.id.loginButton))
    }

    @Test
    fun `username input is visible`() {
        val fragment = launchFragment()
        assertNotNull(fragment.view?.findViewById<EditText>(R.id.usernameInput))
    }

    @Test
    fun `password input is visible`() {
        val fragment = launchFragment()
        assertNotNull(fragment.view?.findViewById<EditText>(R.id.passwordInput))
    }

    @Test
    fun `clicking login with empty username shows error`() {
        val fragment = launchFragment()
        fragment.view?.findViewById<EditText>(R.id.usernameInput)?.setText("")
        fragment.view?.findViewById<EditText>(R.id.passwordInput)?.setText("password123")
        fragment.view?.findViewById<Button>(R.id.loginButton)?.performClick()

        val errorText = fragment.view?.findViewById<android.widget.TextView>(R.id.errorText)
        assertEquals(View.VISIBLE, errorText?.visibility)
        assertEquals("Username nie może być pusty", errorText?.text.toString())
    }

    @Test
    fun `clicking login with empty password shows error`() {
        val fragment = launchFragment()
        fragment.view?.findViewById<EditText>(R.id.usernameInput)?.setText("testuser")
        fragment.view?.findViewById<EditText>(R.id.passwordInput)?.setText("")
        fragment.view?.findViewById<Button>(R.id.loginButton)?.performClick()

        val errorText = fragment.view?.findViewById<android.widget.TextView>(R.id.errorText)
        assertEquals(View.VISIBLE, errorText?.visibility)
        assertEquals("Hasło nie może być puste", errorText?.text.toString())
    }

    @Test
    fun `clicking go to register navigates to register fragment`() {
        val fragment = launchFragment()
        fragment.view?.findViewById<android.widget.TextView>(R.id.goToRegister)?.performClick()
        fragment.parentFragmentManager.executePendingTransactions()

        val currentFragment = fragment.parentFragmentManager.findFragmentById(R.id.fragmentContainer)
        assertTrue(currentFragment is RegisterFragment)
    }

    @Test
    fun `performLogin success navigates to home`() {
        val mockRepo = mockk<com.woofdetect.repository.AuthRepository>()
        coEvery { mockRepo.login(any(), any()) } returns
                Result.success(com.woofdetect.network.dto.LoginResponse("ok", "token123"))

        val fragment = launchFragment()

        val field = LoginFragment::class.java.getDeclaredField("authRepository")
        field.isAccessible = true
        field.set(fragment, mockRepo)

        fragment.view?.findViewById<EditText>(R.id.usernameInput)?.setText("testuser")
        fragment.view?.findViewById<EditText>(R.id.passwordInput)?.setText("password123")
        fragment.view?.findViewById<Button>(R.id.loginButton)?.performClick()

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    }

    @Test
    fun `performLogin failure shows error message`() {
        val mockRepo = mockk<com.woofdetect.repository.AuthRepository>()
        coEvery { mockRepo.login(any(), any()) } returns
                Result.failure(Exception("Invalid credentials"))

        val fragment = launchFragment()

        val field = LoginFragment::class.java.getDeclaredField("authRepository")
        field.isAccessible = true
        field.set(fragment, mockRepo)

        fragment.view?.findViewById<EditText>(R.id.usernameInput)?.setText("testuser")
        fragment.view?.findViewById<EditText>(R.id.passwordInput)?.setText("password123")
        fragment.view?.findViewById<Button>(R.id.loginButton)?.performClick()

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val errorText = fragment.view?.findViewById<android.widget.TextView>(R.id.errorText)
        assertEquals(View.VISIBLE, errorText?.visibility)
    }

    @Test
    fun `onDestroyView cleans up binding`() {
        val fragment = launchFragment()
        fragment.onDestroyView()

        val field = LoginFragment::class.java.getDeclaredField("_binding")
        field.isAccessible = true
        assertNull(field.get(fragment))
    }

    @Test
    fun `showLoading true disables button`() {
        val fragment = launchFragment()
        val method = LoginFragment::class.java.getDeclaredMethod("showLoading", Boolean::class.java)
        method.isAccessible = true
        method.invoke(fragment, true)

        val button = fragment.view?.findViewById<Button>(R.id.loginButton)
        assertEquals(false, button?.isEnabled)
    }

    @Test
    fun `showLoading false restores button text`() {
        val fragment = launchFragment()
        val method = LoginFragment::class.java.getDeclaredMethod("showLoading", Boolean::class.java)
        method.isAccessible = true
        method.invoke(fragment, false)

        val button = fragment.view?.findViewById<Button>(R.id.loginButton)
        assertEquals("Zaloguj", button?.text.toString())
    }

    @Test
    fun `navigateToHome replaces fragment with HomeFragment`() {
        val mockRepo = mockk<com.woofdetect.repository.AuthRepository>()
        coEvery { mockRepo.login(any(), any()) } returns
                Result.success(com.woofdetect.network.dto.LoginResponse("ok", "token123"))

        val fragment = launchFragment()
        val manager = fragment.parentFragmentManager

        val field = LoginFragment::class.java.getDeclaredField("authRepository")
        field.isAccessible = true
        field.set(fragment, mockRepo)

        fragment.view?.findViewById<EditText>(R.id.usernameInput)?.setText("testuser")
        fragment.view?.findViewById<EditText>(R.id.passwordInput)?.setText("password123")
        fragment.view?.findViewById<Button>(R.id.loginButton)?.performClick()

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        manager.executePendingTransactions()

        val currentFragment = manager.findFragmentById(R.id.fragmentContainer)
        assertTrue(currentFragment is com.woofdetect.ui.main.HomeFragment)
    }
}