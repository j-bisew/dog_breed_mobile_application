package com.example.apka.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.apka.R
import com.example.apka.model.RaceNameRequest
import com.example.apka.model.RaceNameData
import com.example.apka.network.ApiClient
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import java.io.StringReader
import kotlinx.coroutines.launch

class ResultActivity : AppCompatActivity() {
    private var token: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)
        supportActionBar?.hide()
        token = "" // token could be passed or stored; simplified here

        val resultView = findViewById<TextView>(R.id.resultText)
        val feedbackBtn = findViewById<Button>(R.id.feedbackButton)
        val backBtn = findViewById<Button>(R.id.backButton)

        val result = intent.getStringExtra("result") ?: intent.getStringExtra("error") ?: "No data"
        // Try to parse and display different possible response shapes (be tolerant to single quotes / lenient JSON)
        val displayText = StringBuilder()
        var json: JsonObject? = null
        // 1) strict parse
        try { json = JsonParser.parseString(result).asJsonObject } catch (e: Exception) { json = null }
        // 2) lenient parser (allows single quotes, unquoted names, etc.)
        if (json == null) {
            try {
                val jr = JsonReader(StringReader(result))
                jr.isLenient = true
                json = JsonParser.parseReader(jr).asJsonObject
            } catch (e: Exception) { json = null }
        }
        // 3) fallback: replace single quotes with double quotes and try again
        if (json == null) {
            try {
                val alt = result.replace("'", "\"")
                json = try { JsonParser.parseString(alt).asJsonObject } catch (e: Exception) { null }
            } catch (e: Exception) { json = null }
        }
        if (json != null) {
            // recognition minimal response
            if (json.has("breedName")) {
                displayText.append("Breed: ${json.get("breedName").asString}\n")
            }
            if (json.has("confidence")) {
                displayText.append("Confidence: ${json.get("confidence").asFloat}\n")
            }
            // database full info
            if (json.has("breedFullName")) {
                displayText.append("Full name: ${json.get("breedFullName").asString}\n")
            }
            if (json.has("breedDescription")) {
                displayText.append("Description: ${json.get("breedDescription").asString}\n")
            }
            if (json.has("mainPhotoData")) {
                displayText.append("Photo received\n")
            }
            if (displayText.isEmpty()) {
                displayText.append(json.toString())
            }
        } else {
            displayText.append(result)
        }
        resultView.text = displayText.toString()

        feedbackBtn.setOnClickListener {
            // try to extract breedName from JSON and call feedback endpoint
            val json = try {
                JsonParser.parseString(result).asJsonObject
            } catch (e: Exception) { null }
            val breed = try { json?.get("breedName")?.asJsonPrimitive?.getAsString() ?: json?.get("breedName")?.asJsonPrimitive?.getAsString() } catch (e: Exception) { null } ?: return@setOnClickListener
            lifecycleScope.launch {
                    try {
                        val resp = withContext(Dispatchers.IO) {
                            val body = JsonObject()
                            val raceObj = JsonObject()
                            raceObj.addProperty("raceName", breed.lowercase())
                            body.add("raceNameData", raceObj)
                            val authHeader = if (token?.startsWith("Bearer ") == true) token!! else "Bearer ${token ?: ""}"
                            ApiClient.service.submitFeedback(authHeader, body)
                        }
                        android.widget.Toast.makeText(this@ResultActivity, "Feedback: ${resp.code()}", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        android.util.Log.e("ResultActivity", "Feedback error", e)
                        android.widget.Toast.makeText(this@ResultActivity, "Feedback error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
            }
        }
        backBtn.setOnClickListener { finish() }
    }
}
