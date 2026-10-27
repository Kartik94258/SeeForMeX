package com.seeforme.x

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object AnalyzeClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(55, TimeUnit.SECONDS)
        .build()

    suspend fun analyze(photo: File, mode: String, question: String, languageTagForRequest: String): String = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')
        if (!baseUrl.startsWith("https://") && !baseUrl.startsWith("http://")) {
            throw IOException("Backend URL is not configured correctly.")
        }
        val jpegBytes = compressJpeg(photo)
        val payload = JSONObject()
            .put("image_base64", Base64.encodeToString(jpegBytes, Base64.NO_WRAP))
            .put("mode", mode)
            .put("question", question)
            .put("language", if (languageTagForRequest == "hi-IN") "hi" else "en")
            .toString()
        val request = Request.Builder()
            .url("$baseUrl/v1/analyze")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(bodyText) }.getOrNull()
            if (!response.isSuccessful) {
                val detail = json?.optString("detail")?.takeIf { it.isNotBlank() }
                throw IOException(detail ?: "Analysis failed (HTTP ${response.code}). Please try again.")
            }
            val answer = json?.optString("answer")?.trim().orEmpty()
            if (answer.isBlank()) throw IOException("The AI returned an empty answer. Please try again.")
            answer
        }
    }


    /** Analyze one sampled live frame with the optional YOLO perception endpoint. */
    suspend fun perceive(photo: File, sessionId: String): List<String> = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')
        if (!baseUrl.startsWith("https://") && !baseUrl.startsWith("http://")) {
            throw IOException("Backend URL is not configured correctly.")
        }
        val jpegBytes = compressJpeg(photo)
        val payload = JSONObject()
            .put("image_base64", Base64.encodeToString(jpegBytes, Base64.NO_WRAP))
            .put("session_id", sessionId)
            .toString()
        val request = Request.Builder()
            .url("$baseUrl/v1/perception")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(bodyText) }.getOrNull()
            if (!response.isSuccessful) {
                val detail = json?.optString("detail")?.takeIf { it.isNotBlank() }
                throw IOException(detail ?: "Live perception failed (HTTP ${response.code}). Check the backend setup.")
            }
            val objects = json?.optJSONArray("objects")
            val labels = mutableListOf<String>()
            if (objects != null) {
                for (i in 0 until objects.length()) {
                    val label = objects.optJSONObject(i)?.optString("class_name")?.trim().orEmpty()
                    if (label.isNotBlank() && label !in labels) labels.add(label)
                }
            }
            labels.take(6)
        }
    }

    /** Resize the captured image in memory before sending to keep mobile payloads manageable. */
    private fun compressJpeg(file: File): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Could not read the captured photo.")

        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            ?: throw IOException("Could not process the captured photo.")
        return try {
            ByteArrayOutputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)) {
                    throw IOException("Could not prepare the captured photo.")
                }
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
