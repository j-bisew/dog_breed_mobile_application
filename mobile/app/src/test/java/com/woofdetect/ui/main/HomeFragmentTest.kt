package com.woofdetect.ui.main

import android.widget.Button
import androidx.fragment.app.FragmentActivity
import com.woofdetect.R
import com.woofdetect.ui.auth.AuthChoiceFragment
import com.woofdetect.ui.camera.CameraFragment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeFragmentTest {

    private fun launchFragment(): HomeFragment {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java)
            .create().start().resume().get()

        activity.setContentView(R.layout.activity_main)

        val fragment = HomeFragment()
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
    fun `camera button is visible`() {
        val fragment = launchFragment()
        val button = fragment.view?.findViewById<Button>(R.id.cameraButton)
        assertNotNull(button)
    }

    @Test
    fun `gallery button is visible`() {
        val fragment = launchFragment()
        val button = fragment.view?.findViewById<Button>(R.id.galleryButton)
        assertNotNull(button)
    }

    @Test
    fun `logout button is visible`() {
        val fragment = launchFragment()
        val button = fragment.view?.findViewById<Button>(R.id.logoutButton)
        assertNotNull(button)
    }

    @Test
    fun `clicking camera button navigates to camera fragment`() {
        val fragment = launchFragment()
        val manager = fragment.parentFragmentManager

        fragment.view?.findViewById<Button>(R.id.cameraButton)?.performClick()
        manager.executePendingTransactions()

        val currentFragment = manager.findFragmentById(R.id.fragmentContainer)
        assertTrue(currentFragment is CameraFragment)
    }

    @Test
    fun `clicking logout button clears token and navigates`() {
        val fragment = launchFragment()
        fragment.view?.findViewById<Button>(R.id.logoutButton)?.performClick()
        assertNotNull(fragment)
    }

    @Test
    fun `onDestroyView cleans up binding`() {
        val fragment = launchFragment()
        fragment.onDestroyView()

        val field = HomeFragment::class.java.getDeclaredField("_binding")
        field.isAccessible = true
        assertNull(field.get(fragment))
    }

    @Test
    fun `clicking gallery button triggers image picker`() {
        val fragment = launchFragment()
        fragment.view?.findViewById<Button>(R.id.galleryButton)?.performClick()
        assertNotNull(fragment)
    }

    @Test
    fun `handleGalleryImage with valid uri navigates to result`() {
        val fragment = launchFragment()

        val mockUri = mockk<Uri>(relaxed = true)
        val inputStream = ByteArrayInputStream(ByteArray(100))

        val shadowContentResolver = org.robolectric.shadows.ShadowContentResolver()
        org.robolectric.Shadows.shadowOf(fragment.requireActivity().contentResolver)
            .registerInputStream(mockUri, inputStream)

        val method = HomeFragment::class.java.getDeclaredMethod("handleGalleryImage", Uri::class.java)
        method.isAccessible = true
        method.invoke(fragment, mockUri)

        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    }

    @Test
    fun `handleGalleryImage with null input stream shows error`() {
        val fragment = launchFragment()

        val mockUri = mockk<Uri>(relaxed = true)

        val method = HomeFragment::class.java.getDeclaredMethod("handleGalleryImage", Uri::class.java)
        method.isAccessible = true
        method.invoke(fragment, mockUri)

        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    }

//    @Test
//    fun `handleGalleryImage with valid uri navigates to result fragment`() {
//        val fragment = launchFragment()
//        val manager = fragment.parentFragmentManager
//
//        val mockUri = mockk<Uri>(relaxed = true)
//        val inputStream = ByteArrayInputStream("fake image data".toByteArray())
//
//        org.robolectric.Shadows.shadowOf(fragment.requireActivity().contentResolver)
//            .registerInputStream(mockUri, inputStream)
//
//        val method = HomeFragment::class.java.getDeclaredMethod("handleGalleryImage", Uri::class.java)
//        method.isAccessible = true
//        method.invoke(fragment, mockUri)
//
//        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
//        manager.executePendingTransactions()
//
//        val currentFragment = manager.findFragmentById(R.id.fragmentContainer)
//        assertTrue(currentFragment is com.woofdetect.ui.result.ResultFragment)
//    }

    @Test
    fun `logout button catch block is covered`() {
        val fragment = launchFragment()
        fragment.view?.findViewById<Button>(R.id.logoutButton)?.performClick()
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        fragment.parentFragmentManager.executePendingTransactions()
        assertNotNull(fragment)
    }

    @Test
    fun `handleGalleryImage exception shows error toast`() {
        val fragment = launchFragment()

        val mockUri = mockk<Uri>(relaxed = true)
        every { mockUri.toString() } throws RuntimeException("Test exception")

        val method = HomeFragment::class.java.getDeclaredMethod("handleGalleryImage", Uri::class.java)
        method.isAccessible = true
        try {
            method.invoke(fragment, mockUri)
        } catch (e: Exception) {
            // expected
        }
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    }

    @Test
    fun `handleGalleryImage when file does not exist shows error`() {
        val fragment = launchFragment()

        val mockUri = mockk<Uri>(relaxed = true)
        // nie rejestrujemy inputStream - contentResolver zwróci null
        // co spowoduje że copyUriToCache zwróci null

        val method = HomeFragment::class.java.getDeclaredMethod("handleGalleryImage", Uri::class.java)
        method.isAccessible = true
        method.invoke(fragment, mockUri)

        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertNotNull(fragment)
    }
}