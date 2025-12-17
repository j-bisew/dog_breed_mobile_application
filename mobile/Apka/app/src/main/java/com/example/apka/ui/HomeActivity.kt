package com.example.apka.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.apka.R
import com.example.apka.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import okhttp3.RequestBody
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import okhttp3.Request
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import java.io.OutputStream
import okhttp3.OkHttpClient

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        supportActionBar?.hide()

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        var token = prefs.getString("auth_token", "") ?: ""
        if (token.isEmpty()) {
            // not logged in -> go to login
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val pickBtn = findViewById<Button>(R.id.pickPhotoButton)
        val takeBtn = findViewById<Button>(R.id.takePhotoButton)
        val logoutBtn = findViewById<Button>(R.id.logoutButton)

        pickBtn.isEnabled = true
        takeBtn.isEnabled = true

        // try to download test image from backend and add to gallery for emulator testing
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val client = OkHttpClient()
                    val req = Request.Builder().url("http://host.docker.internal:8000/mainPhoto.jpg").build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val bytes = resp.body?.bytes()
                        if (bytes != null && bytes.isNotEmpty()) {
                            saveImageToGallery(bytes, "mainPhoto.jpg")
                        }
                    }
                }
            } catch (e: Exception) {
                // ignore download errors in dev environment
            }
        }

        val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { uploadImage(it, token) }
        }

        val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp: Bitmap? ->
            if (bmp != null) {
                try {
                    val tmp = File(cacheDir, "capture.jpg")
                    val out = FileOutputStream(tmp)
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.flush()
                    out.close()
                    val uri = Uri.fromFile(tmp)
                    uploadImage(uri, token)
                } catch (e: Exception) {
                    Log.e("HomeActivity", "Saving capture failed", e)
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

        pickBtn.setOnClickListener { pickImage.launch("image/*") }

        takeBtn.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                takePictureLauncher.launch(null)
            } else {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        logoutBtn.setOnClickListener {
            prefs.edit().remove("auth_token").apply()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun uploadImage(uri: Uri, token: String) {
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
                    val authHeader = if (token.startsWith("Bearer ")) token else "Bearer $token"
                    val resp = ApiClient.service.getDogBreedInfo(authHeader, body)
                    // read raw response (success or error)
                    val raw = try { resp.body()?.string() ?: resp.errorBody()?.string() ?: "" } catch (e: Exception) { "" }
                    Pair(resp.code(), raw)
                }

                if (code == 200) {
                    val i = Intent(this@HomeActivity, ResultActivity::class.java)
                    i.putExtra("result", bodyString)
                    startActivity(i)
                } else if (code == 500) {
                    // server-side error (e.g. race not found) — show in result screen as friendly message
                    val i = Intent(this@HomeActivity, ResultActivity::class.java)
                    i.putExtra("error", "Server error: ${bodyString.ifEmpty { "Internal server error" }}")
                    startActivity(i)
                } else {
                    Toast.makeText(this@HomeActivity, "Upload failed: $code", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("HomeActivity", "Upload error", e)
                Toast.makeText(this@HomeActivity, "Upload error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveImageToGallery(bytes: ByteArray, filename: String) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = contentResolver
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val item = resolver.insert(collection, values)
            item?.let { uri ->
                var out: OutputStream? = null
                try {
                    out = resolver.openOutputStream(uri)
                    out?.write(bytes)
                } finally {
                    out?.close()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
            }
        } catch (e: Exception) {
            // ignore on failure
        }
    }
}
