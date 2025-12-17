package com.example.apka.ui

import android.app.Activity
import android.Manifest
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.apka.network.ApiClient
import com.example.apka.R
import com.google.gson.JsonObject
import kotlinx.coroutines.launch
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private var token: String? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { uploadImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val username = findViewById<EditText>(R.id.usernameEdit)
        val password = findViewById<EditText>(R.id.passwordEdit)
        val loginBtn = findViewById<Button>(R.id.loginButton)
        val pickBtn = findViewById<Button>(R.id.pickPhotoButton)
        val takeBtn = findViewById<Button>(R.id.takePhotoButton)
        pickBtn.isEnabled = false
        takeBtn.isEnabled = false

        // register launchers that need Activity context
        val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp: Bitmap? ->
            if (bmp != null) {
                try {
                    val tmp = File(cacheDir, "capture.jpg")
                    val out = FileOutputStream(tmp)
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.flush()
                    out.close()
                    val uri = Uri.fromFile(tmp)
                    uploadImage(uri)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Saving capture failed", e)
                    Toast.makeText(this, "Saving capture failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "No photo captured", Toast.LENGTH_SHORT).show()
            }
        }

        val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                takePictureLauncher.launch(null)
            } else {
                Toast.makeText(this, "Camera permission required to take photos", Toast.LENGTH_SHORT).show()
            }
        }

        loginBtn.setOnClickListener {
            val user = username.text.toString().trim()
            val pass = password.text.toString().trim()
            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Enter credentials", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                try {
                    val body = JsonObject()
                    val loginData = JsonObject()
                    loginData.addProperty("username", user)
                    loginData.addProperty("password", pass)
                    body.add("loginData", loginData)
                            val resp = withContext(Dispatchers.IO) { ApiClient.service.login(body) }
                        if (resp.isSuccessful) {
                            // read raw response string (be tolerant to server formatting)
                            val raw = try { resp.body()?.string() } catch (e: Exception) { null }
                            val obj = try {
                                raw?.let { JsonParser.parseString(it).asJsonObject }
                            } catch (e: Exception) {
                                null
                            }
                            token = try { obj?.get("token")?.asJsonPrimitive?.getAsString() ?: obj?.get("access")?.asJsonPrimitive?.getAsString() ?: "" } catch (e: Exception) { "" }
                            if (!token.isNullOrEmpty()) {
                                Toast.makeText(this@MainActivity, "Logged in", Toast.LENGTH_SHORT).show()
                                pickBtn.isEnabled = true
                                takeBtn.isEnabled = true
                            } else {
                                Toast.makeText(this@MainActivity, "Login OK (no token returned)", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@MainActivity, "Login failed: ${resp.code()}", Toast.LENGTH_SHORT).show()
                        }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Login error", e)
                    Toast.makeText(this@MainActivity, "Login error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        pickBtn.setOnClickListener {
            pickImage.launch("image/*")
        }

        takeBtn.setOnClickListener {
            // check camera permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                takePictureLauncher.launch(null)
            } else {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun uploadImage(uri: Uri) {
        val t = token ?: ""
        if (t.isEmpty()) {
            Toast.makeText(this, "Login first to use protected endpoints", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val (code, bodyString) = withContext(Dispatchers.IO) {
                    val input = contentResolver.openInputStream(uri)
                    val tmp = File(cacheDir, "upload.jpg")
                    val out = FileOutputStream(tmp)
                    input?.copyTo(out)
                    input?.close(); out.close()

                    val reqFile: RequestBody = tmp.asRequestBody("image/*".toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("photo", tmp.name, reqFile)
                    val authHeader = if (t.startsWith("Bearer ")) t else "Bearer $t"
                    val resp = ApiClient.service.getDogBreedInfo(authHeader, body)
                    val raw = try { resp.body()?.string() ?: resp.errorBody()?.string() ?: "" } catch (e: Exception) { "" }
                    Pair(resp.code(), raw)
                }

                if (code == 200) {
                    val i = Intent(this@MainActivity, ResultActivity::class.java)
                    i.putExtra("result", bodyString)
                    startActivity(i)
                } else if (code == 500) {
                    val i = Intent(this@MainActivity, ResultActivity::class.java)
                    i.putExtra("error", "Server error: ${bodyString.ifEmpty { "Internal server error" }}")
                    startActivity(i)
                } else {
                    Toast.makeText(this@MainActivity, "Upload failed: $code", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Upload error", e)
                Toast.makeText(this@MainActivity, "Upload error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
