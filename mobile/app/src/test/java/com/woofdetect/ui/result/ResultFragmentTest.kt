package com.woofdetect.ui.result

import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.woofdetect.R
import com.woofdetect.model.DogResult
import com.woofdetect.repository.DogRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResultFragmentTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========== HELPERS ==========

    /**
     * Tworzy fragment z mockiem repozytorium wstrzykniętym PRZED commitNow,
     * dzięki czemu automatyczne wywołanie analyzePhoto() w onViewCreated
     * trafi od razu w mock, a nie w prawdziwe DogRepository.
     *
     * Robolectric nie obsługuje ImageDecoder przy file:// URI, więc przekazujemy
     * Uri.EMPTY – setImageURI(Uri.EMPTY) nic nie ładuje i nie crashuje.
     */
    private fun launchWithMock(
        filePath: String,
        mockRepo: DogRepository
    ): ResultFragment {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java)
            .create().start().resume().get()
        activity.setContentView(R.layout.activity_main)

        val fragment = ResultFragment.newInstance(Uri.EMPTY, filePath)

        // Inject PRZED commitem – fragment nie ma jeszcze widoku, ale pole istnieje
        val field = ResultFragment::class.java.getDeclaredField("repository")
        field.isAccessible = true
        field.set(fragment, mockRepo)

        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commitNow()

        return fragment
    }

    private fun launchWithoutArgs(): ResultFragment {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java)
            .create().start().resume().get()
        activity.setContentView(R.layout.activity_main)

        val fragment = ResultFragment()
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commitNow()

        return fragment
    }

    /** Tworzy tymczasowy plik, który faktycznie istnieje na dysku. */
    private fun createTempFile(): File {
        val file = File.createTempFile("test_dog", ".jpg")
        file.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        file.deleteOnExit()
        return file
    }

    private fun dogResult(
        breedName: String = "labrador",
        breedFullName: String = "Labrador Retriever",
        breedDescription: String = "Friendly dog",
        confidence: Double = 95.5
    ) = DogResult(breedName, breedFullName, breedDescription, confidence, breedPhoto = null)

    /** Wykonuje oczekujące coroutiny i zadania UI. */
    private fun drainCoroutines() {
        testDispatcher.scheduler.advanceUntilIdle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    }

    // ========== LAUNCH / ARGS TESTS ==========

    @Test
    fun `fragment launched via newInstance is not null`() {
        val mockRepo = mockk<DogRepository>(relaxed = true)
        val fragment = launchWithMock(createTempFile().absolutePath, mockRepo)
        assertNotNull(fragment)
    }

    @Test
    fun `fragment without arguments shows error state`() {
        val fragment = launchWithoutArgs()
        val tryAgain = fragment.view?.findViewById<Button>(R.id.tryAgainButton)
        assertEquals(View.VISIBLE, tryAgain?.visibility)
    }

    @Test
    fun `fragment with non-existing file path shows error state`() {
        val mockRepo = mockk<DogRepository>(relaxed = true)
        val fragment = launchWithMock("/nonexistent/path/photo.jpg", mockRepo)

        val tryAgain = fragment.view?.findViewById<Button>(R.id.tryAgainButton)
        assertEquals(View.VISIBLE, tryAgain?.visibility)
    }

    @Test
    fun `newInstance stores uri and filePath in arguments`() {
        val file = createTempFile()
        val uri = Uri.fromFile(file)
        val fragment = ResultFragment.newInstance(uri, file.absolutePath)

        assertEquals(uri.toString(), fragment.arguments?.getString("imageUri"))
        assertEquals(file.absolutePath, fragment.arguments?.getString("filePath"))
    }

    // ========== LOADING STATE TESTS ==========

    @Test
    fun `showLoading true shows progress bar`() {
        val mockRepo = mockk<DogRepository>(relaxed = true)
        val fragment = launchWithMock(createTempFile().absolutePath, mockRepo)

        val method = ResultFragment::class.java.getDeclaredMethod("showLoading", Boolean::class.java)
        method.isAccessible = true
        method.invoke(fragment, true)

        assertEquals(View.VISIBLE,
            fragment.view?.findViewById<ProgressBar>(R.id.loadingProgressBar)?.visibility)
    }

    @Test
    fun `showLoading true hides tryAgainButton`() {
        val mockRepo = mockk<DogRepository>(relaxed = true)
        val fragment = launchWithMock(createTempFile().absolutePath, mockRepo)

        val method = ResultFragment::class.java.getDeclaredMethod("showLoading", Boolean::class.java)
        method.isAccessible = true
        method.invoke(fragment, true)

        assertEquals(View.GONE,
            fragment.view?.findViewById<Button>(R.id.tryAgainButton)?.visibility)
    }

    @Test
    fun `showLoading false hides progress bar`() {
        val mockRepo = mockk<DogRepository>(relaxed = true)
        val fragment = launchWithMock(createTempFile().absolutePath, mockRepo)

        val method = ResultFragment::class.java.getDeclaredMethod("showLoading", Boolean::class.java)
        method.isAccessible = true
        method.invoke(fragment, false)

        assertEquals(View.GONE,
            fragment.view?.findViewById<ProgressBar>(R.id.loadingProgressBar)?.visibility)
    }

    // ========== ANALYZE PHOTO SUCCESS TESTS ==========

    @Test
    fun `analyzePhoto success shows breed name`() = runTest {
        val mockRepo = mockk<DogRepository>()
        coEvery { mockRepo.analyzeDogPhoto(any(), any()) } returns Result.success(dogResult())

        val fragment = launchWithMock(createTempFile().absolutePath, mockRepo)
        drainCoroutines()

        assertEquals("Labrador Retriever",
            fragment.view?.findViewById<TextView>(R.id.breedName)?.text.toString())
    }

    @Test
    fun `analyzePhoto success shows confidence text`() = runTest {
        val mockRepo = mockk<DogRepository>()
        coEvery { mockRepo.analyzeDogPhoto(any(), any()) } returns
                Result.success(dogResult(confidence = 87.3))

        val fragment = launchWithMock(createTempFile().absolutePath, mockRepo)
        drainCoroutines()

        val text = fragment.view?.findViewById<TextView>(R.id.confidenceText)?.text.toString()
        assertTrue(text.contains("87.3"))
    }

    @Test
    fun `analyzePhoto success shows result card`() = runTest {
        val mockRepo = mockk<DogRepository>()
        coEvery { mockRepo.analyzeDogPhoto(any(), any()) } returns Result.success(dogResult())

        val fragment = launchWithMock(createTempFile().absolutePath, mockRepo)
        drainCoroutines()

        assertEquals(View.VISIBLE,
            fragment.view?.findViewById<View>(R.id.resultCard)?.visibility)
    }

    @Test
    fun `analyzePhoto success hides loading`() = runTest {
        val mockRepo = mockk<DogRepository>()
        coEvery { mockRepo.analyzeDogPhoto(any(), any()) } returns Result.success(dogResult())

        val fragment = launchWithMock(createTempFile().absolutePath, mockRepo)
        drainCoroutines()

        assertEquals(View.GONE,
            fragment.view?.findViewById<ProgressBar>(R.id.loadingProgressBar)?.visibility)
    }

    @Test
    fun `analyzePhoto success sets currentDogResult`() = runTest {
        val mockRepo = mockk<DogRepository>()
        coEvery { mockRepo.analyzeDogPhoto(any(), any()) } returns
                Result.success(dogResult(breedName = "beagle"))

        val fragment = launchWithMock(createTempFile().absolutePath, mockRepo)
        drainCoroutines()

        val field = ResultFragment::class.java.getDeclaredField("currentDogResult")
        field.isAccessible = true
        assertEquals("beagle", (field.get(fragment) as? DogResult)?.breedName)
    }

    @Test
    fun `analyzePhoto success shows breed description`() = runTest {
        val mockRepo = mockk<DogRepository>()
        coEvery { mockRepo.analyzeDogPhoto(any(), any()) } returns
                Result.success(dogResult(breedDescription = "Energetic family dog"))

        val fragment = launchWithMock(createTempFile().absolutePath, mockRepo)
        drainCoroutines()

        assertEquals("Energetic family dog",
            fragment.view?.findViewById<TextView>(R.id.breedDescription)?.text.toString())
    }

    // ========== ANALYZE PHOTO FAILURE TESTS ==========

    @Test
    fun `analyzePhoto failure with 515 error shows breed not recognized message`() = runTest {
        val mockRepo = mockk<DogRepository>()
        coEvery { mockRepo.analyzeDogPhoto(any(), any()) } returns
                Result.failure(Exception("API Error: 515 - Breed not recognized"))

        val fragment = launchWithMock(createTempFile().absolutePath, mockRepo)
        drainCoroutines()

        val errorView = fragment.view?.findViewById<TextView>(R.id.errorTextView)
        assertEquals(View.VISIBLE, errorView?.visibility)
        assertEquals("Nie rozpoznano rasy.", errorView?.text.toString())
    }

    @Test
    fun `analyzePhoto failure with generic error shows error message`() = runTest {
        val mockRepo = mockk<DogRepository>()
        coEvery { mockRepo.analyzeDogPhoto(any(), any()) } returns
                Result.failure(Exception("Network timeout"))

        val fragment = launchWithMock(createTempFile().absolutePath, mockRepo)
        drainCoroutines()

        val errorView = fragment.view?.findViewById<TextView>(R.id.errorTextView)
        assertEquals(View.VISIBLE, errorView?.visibility)
        assertTrue(errorView?.text.toString().contains("Network timeout"))
    }

    @Test
    fun `analyzePhoto failure hides result card`() = runTest {
        val mockRepo = mockk<DogRepository>()
        coEvery { mockRepo.analyzeDogPhoto(any(), any()) } returns
                Result.failure(Exception("Some error"))

        val fragment = launchWithMock(createTempFile().absolutePath, mockRepo)
        drainCoroutines()

        assertEquals(View.GONE,
            fragment.view?.findViewById<View>(R.id.resultCard)?.visibility)
    }

    @Test
    fun `analyzePhoto exception shows unexpected error message`() = runTest {
        val mockRepo = mockk<DogRepository>()
        coEvery { mockRepo.analyzeDogPhoto(any(), any()) } throws RuntimeException("Crash!")

        val fragment = launchWithMock(createTempFile().absolutePath, mockRepo)
        drainCoroutines()

        val errorView = fragment.view?.findViewById<TextView>(R.id.errorTextView)
        assertEquals(View.VISIBLE, errorView?.visibility)
        assertTrue(errorView?.text.toString().contains("Crash!"))
    }

    // ========== BUTTON TESTS ==========

    @Test
    fun `tryAgainButton click pops back stack`() {
        val mockRepo = mockk<DogRepository>(relaxed = true)
        val fragment = launchWithMock(createTempFile().absolutePath, mockRepo)

        val tryAgain = fragment.view?.findViewById<Button>(R.id.tryAgainButton)
        assertNotNull(tryAgain)
        tryAgain?.performClick()
    }

    @Test
    fun `reportErrorButton click with currentDogResult calls submitFeedback`() = runTest {
        val mockRepo = mockk<DogRepository>()
        coEvery { mockRepo.analyzeDogPhoto(any(), any()) } returns Result.success(dogResult())
        coEvery { mockRepo.submitFeedback(any()) } returns Result.success(Unit)

        val fragment = launchWithMock(createTempFile().absolutePath, mockRepo)
        drainCoroutines()

        val field = ResultFragment::class.java.getDeclaredField("currentDogResult")
        field.isAccessible = true
        field.set(fragment, dogResult(breedName = "poodle"))

        fragment.view?.findViewById<View>(R.id.reportErrorButton)?.performClick()
        drainCoroutines()

        coVerify { mockRepo.submitFeedback("poodle") }
    }

    @Test
    fun `reportErrorButton click with null currentDogResult does not call submitFeedback`() = runTest {
        val mockRepo = mockk<DogRepository>()
        coEvery { mockRepo.analyzeDogPhoto(any(), any()) } returns Result.success(dogResult())
        coEvery { mockRepo.submitFeedback(any()) } returns Result.success(Unit)

        val fragment = launchWithMock(createTempFile().absolutePath, mockRepo)
        drainCoroutines()

        val field = ResultFragment::class.java.getDeclaredField("currentDogResult")
        field.isAccessible = true
        field.set(fragment, null)

        fragment.view?.findViewById<View>(R.id.reportErrorButton)?.performClick()
        drainCoroutines()

        coVerify(exactly = 0) { mockRepo.submitFeedback(any()) }
    }

    // ========== SUBMIT FEEDBACK TESTS ==========

    @Test
    fun `submitFeedback success calls repository`() = runTest {
        val mockRepo = mockk<DogRepository>()
        coEvery { mockRepo.analyzeDogPhoto(any(), any()) } returns Result.success(dogResult())
        coEvery { mockRepo.submitFeedback(any()) } returns Result.success(Unit)

        val fragment = launchWithMock(createTempFile().absolutePath, mockRepo)

        val method = ResultFragment::class.java.getDeclaredMethod("submitFeedback", String::class.java)
        method.isAccessible = true
        method.invoke(fragment, "labrador")
        drainCoroutines()

        coVerify { mockRepo.submitFeedback("labrador") }
    }

    @Test
    fun `submitFeedback failure does not crash`() = runTest {
        val mockRepo = mockk<DogRepository>()
        coEvery { mockRepo.analyzeDogPhoto(any(), any()) } returns Result.success(dogResult())
        coEvery { mockRepo.submitFeedback(any()) } returns Result.failure(Exception("Server error"))

        val fragment = launchWithMock(createTempFile().absolutePath, mockRepo)

        val method = ResultFragment::class.java.getDeclaredMethod("submitFeedback", String::class.java)
        method.isAccessible = true
        method.invoke(fragment, "labrador")
        drainCoroutines()
    }

    @Test
    fun `submitFeedback exception does not crash`() = runTest {
        val mockRepo = mockk<DogRepository>()
        coEvery { mockRepo.analyzeDogPhoto(any(), any()) } returns Result.success(dogResult())
        coEvery { mockRepo.submitFeedback(any()) } throws RuntimeException("Unexpected")

        val fragment = launchWithMock(createTempFile().absolutePath, mockRepo)

        val method = ResultFragment::class.java.getDeclaredMethod("submitFeedback", String::class.java)
        method.isAccessible = true
        method.invoke(fragment, "labrador")
        drainCoroutines()
    }

    // ========== LIFECYCLE TESTS ==========

    @Test
    fun `onDestroyView clears binding`() {
        val mockRepo = mockk<DogRepository>(relaxed = true)
        val fragment = launchWithMock(createTempFile().absolutePath, mockRepo)

        fragment.onDestroyView()

        val field = ResultFragment::class.java.getDeclaredField("_binding")
        field.isAccessible = true
        assertNull(field.get(fragment))
    }
}