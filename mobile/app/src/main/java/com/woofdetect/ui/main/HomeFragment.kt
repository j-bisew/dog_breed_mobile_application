package com.woofdetect.ui.main

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.woofdetect.R
import com.woofdetect.databinding.FragmentHomeBinding
import com.woofdetect.ui.camera.CameraFragment
import com.woofdetect.ui.result.ResultFragment
import com.woofdetect.ui.auth.AuthChoiceFragment
import com.woofdetect.auth.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class HomeFragment : Fragment(R.layout.fragment_home) {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val TAG = "HomeFragment"
    }

    private val pickImageLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                handleGalleryImage(it)
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)

        binding.galleryButton.setOnClickListener {
            // Launch the activity to pick an image
            pickImageLauncher.launch("image/*")
        }

        binding.cameraButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, CameraFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.logoutButton.setOnClickListener {
            lifecycleScope.launch {
                try {
                    TokenManager(requireContext()).clearToken()
                } catch (e: Exception) {
                    // ignore token clear errors
                }
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, AuthChoiceFragment())
                    .commit()
            }
        }
    }


    private fun handleGalleryImage(uri: Uri) {
        Toast.makeText(requireContext(), "Loading image...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val file = copyUriToCache(uri)

                if (file != null && file.exists()) {
                    Log.d(TAG, "Gallery image copied to: ${file.absolutePath}")
                    navigateToResultFragment(uri, file.absolutePath)
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Nie udało się wczytać zdjęcia",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error handling gallery image", e)
                Toast.makeText(
                    requireContext(),
                    "Błąd: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Kopiuje plik z URI (galeria) do cache directory
     * @return File w cache lub null jeśli błąd
     */
    private suspend fun copyUriToCache(uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream for URI: $uri")
                return@withContext null
            }

            // Utwórz plik w cache
            val fileName = "gallery_photo_${System.currentTimeMillis()}.jpg"
            val outputFile = File(requireContext().cacheDir, fileName)

            // Skopiuj zawartość
            inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "Copied gallery image to cache: ${outputFile.absolutePath}, size: ${outputFile.length()} bytes")
            outputFile

        } catch (e: Exception) {
            Log.e(TAG, "Error copying URI to cache", e)
            null
        }
    }

    private fun navigateToResultFragment(uri: Uri, filePath: String) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, ResultFragment.newInstance(uri, filePath))
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}