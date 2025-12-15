package com.woofdetect.ui.main

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.woofdetect.R
import com.woofdetect.databinding.FragmentResultBinding

class ResultFragment : Fragment(R.layout.fragment_result) {

    private var _binding: FragmentResultBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentResultBinding.bind(view)

        // TODO: tu wstawisz dane z backendu
        binding.breedName.text = "Golden Retriever"
        binding.confidenceText.text = "Pewność: 92%"
        binding.breedDescription.text = "Golden Retriever to przyjazna, inteligentna rasa psa..."

        binding.tryAgainButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
