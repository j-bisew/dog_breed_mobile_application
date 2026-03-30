package com.woofdetect

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import com.woofdetect.auth.TokenManager
import com.woofdetect.ui.auth.AuthChoiceFragment
import com.woofdetect.ui.main.HomeFragment
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking
import org.junit.Before

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            TokenManager(context).clearToken()
        }
    }

    @Test
    fun `activity launches successfully`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java)
            .create()
            .start()
            .resume()
            .get()
        assertNotNull(activity)
    }

    @Test
    fun `activity shows AuthChoiceFragment when no token`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java)
            .create()
            .start()
            .resume()
            .get()

        Thread.sleep(500)
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        activity.supportFragmentManager.executePendingTransactions()

        val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        assertTrue(fragment is AuthChoiceFragment)
    }

    @Test
    fun `activity shows HomeFragment when token exists`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // zapisz token przed uruchomieniem aktywności
        runBlocking {
            TokenManager(context).saveToken("valid_token_123", "testuser")
        }

        val activity = Robolectric.buildActivity(MainActivity::class.java)
            .create()
            .start()
            .resume()
            .get()

        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        activity.supportFragmentManager.executePendingTransactions()

        val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        assertTrue(fragment is HomeFragment)

        // wyczyść token po teście
        runBlocking {
            TokenManager(context).clearToken()
        }
    }
}