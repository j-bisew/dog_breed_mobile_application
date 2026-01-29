package com.woofdetect.ui.result

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.woofdetect.R
import com.woofdetect.databinding.FragmentResultBinding
import com.woofdetect.model.DogResult
import com.woofdetect.repository.DogRepository
import kotlinx.coroutines.launch
import java.io.File

/*
    States:
        - Loading: ProgressBar shown, content hidden
        - Success: Analysis result shown
        - Error: Error message shown, retry button shown
 */
class ResultFragment : Fragment(R.layout.fragment_result) {
    private var _binding: FragmentResultBinding? = null
    private val binding get() = _binding!!

    private val repository = DogRepository()

    private var photoUri: Uri? = null
    private var photoFile: File? = null
    private var currentDogResult: DogResult? = null

    companion object {
        private const val TAG = "ResultFragment"
        private const val ARG_IMAGE_URI = "imageUri"
        private const val ARG_FILE_PATH = "filePath"

        fun newInstance(uri: Uri, filePath: String): ResultFragment {
            return ResultFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_IMAGE_URI, uri.toString())
                    putString(ARG_FILE_PATH, filePath)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentResultBinding.bind(view)

        photoUri = arguments?.getString(ARG_IMAGE_URI)?.let { Uri.parse(it) }

        val filePath = arguments?.getString(ARG_FILE_PATH)

        if (photoUri == null || filePath == null) {
            showError("Error: No file found")
            return
        }

        binding.resultImage.setImageURI(photoUri)

        photoFile = File(filePath)

        if (!photoFile!!.exists()) {
            showError("Error: File does not exist: $filePath")
            Log.e(TAG, "File does not exist: $filePath")
            return
        }

        setupButtons()

        analyzePhoto()
    }

    private fun setupButtons() {
        binding.tryAgainButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.reportErrorButton?.setOnClickListener {
            currentDogResult?.let { dogResult ->
                submitFeedback(dogResult.breedName)
            }
        }
    }

    private fun analyzePhoto() {
        val file = photoFile ?: return
        val uri = photoUri ?: return

        showLoading(true)

        lifecycleScope.launch {
            try {
                val result = repository.analyzeDogPhoto(file, uri)

                result.onSuccess { dogResult ->
                    Log.d(TAG, "Analysis successful: ${dogResult.breedFullName}")
                    currentDogResult = dogResult
                    showResult(dogResult)
                }

                result.onFailure { error ->
                    Log.e(TAG, "Analysis failed", error)
                    if (error.message?.contains("515") == true) {
                        showError("Nie rozpoznano rasy.")                            
                    }
                    else{
                        showError("Błąd analizy: ${error.message}")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Exception during analysis", e)
                showError("Wystąpił nieoczekiwany błąd: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    /**
     * Wyświetla wynik analizy
     */
    private fun showResult(dogResult: DogResult) {
        binding.apply {

            //Zaloguj wynik
            Log.d(TAG, "Analysis result: $dogResult")

            // Ukryj loading, pokaż zawartość
            loadingProgressBar.visibility = View.GONE
            loadingText.visibility = View.GONE
            resultScrollView.visibility = View.VISIBLE
            resultCard.visibility = View.VISIBLE
            tryAgainButton.visibility = View.VISIBLE

            // Wypełnij dane
            breedName.text = dogResult.breedFullName
            confidenceText.text = "Pewność: ${String.format("%.1f", dogResult.confidence)}%"
            breedDescription.text = dogResult.breedDescription

            // Jeśli backend zwrócił zdjęcie rasy, wyświetl je
            // (w przeciwnym razie zostaje zdjęcie użytkownika)
            dogResult.breedPhoto?.let { bitmap ->
                resultImage.setImageBitmap(bitmap)
            }

            // Pokaż przycisk zgłaszania błędu (jeśli istnieje w layoucie)
            reportErrorButton?.visibility = View.VISIBLE
        }
    }

    /**
     * Wyświetla błąd
     */
    private fun showError(message: String) {
        binding.apply {
            loadingProgressBar.visibility = View.GONE
            loadingText.visibility = View.GONE
            resultScrollView.visibility = View.GONE
            resultCard.visibility = View.GONE
            errorTextView?.apply {
                visibility = View.VISIBLE
                text = message
            }
            tryAgainButton.visibility = View.VISIBLE
        }

        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    /**
     * Pokazuje/ukrywa loading
     */
    private fun showLoading(isLoading: Boolean) {
        binding.apply {
            if (isLoading) {
                loadingProgressBar.visibility = View.VISIBLE
                loadingText.visibility = View.VISIBLE
                resultScrollView.visibility = View.GONE
                resultCard.visibility = View.GONE
                tryAgainButton.visibility = View.GONE
                errorTextView?.visibility = View.GONE
            } else {
                loadingProgressBar.visibility = View.GONE
                loadingText.visibility = View.GONE
            }
        }
    }

    /**
     * Zgłasza feedback o błędnej rasie
     */
    private fun submitFeedback(breedName: String) {
        lifecycleScope.launch {
            try {
                val result = repository.submitFeedback(breedName)

                result.onSuccess {
                    Toast.makeText(
                        requireContext(),
                        "Dziękujemy za zgłoszenie!",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                result.onFailure { error ->
                    Toast.makeText(
                        requireContext(),
                        "Nie udało się wysłać zgłoszenia: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Exception during feedback submission", e)
                Toast.makeText(
                    requireContext(),
                    "Błąd: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}