package com.woofdetect.ui.main

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.woofdetect.R
import com.woofdetect.databinding.FragmentHomeBinding
import com.woofdetect.ui.camera.CameraFragment
import com.woofdetect.ui.result.ResultFragment

class HomeFragment : Fragment(R.layout.fragment_home) {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val pickImageLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                navigateToResultFragment(it)
            }
        }


    private var imageUri: Uri? = null


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
    }

    private fun navigateToResultFragment(uri: Uri) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, ResultFragment.newInstance(uri))
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}