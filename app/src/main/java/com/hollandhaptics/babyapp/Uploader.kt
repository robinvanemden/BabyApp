package com.hollandhaptics.babyapp

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class Uploader(private val endpoint: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Returns true if the server accepted the file (HTTP 2xx). */
    fun upload(file: File): Boolean {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "fileToUpload",
                file.name,
                file.asRequestBody("audio/3gpp".toMediaType()),
            )
            .build()
        val request = Request.Builder().url(endpoint).post(body).build()
        return try {
            client.newCall(request).execute().use { response ->
                Log.i(TAG, "upload ${file.name} -> ${response.code}")
                response.isSuccessful
            }
        } catch (t: Throwable) {
            Log.e(TAG, "upload failed for ${file.name}", t)
            false
        }
    }

    companion object {
        private const val TAG = "Uploader"
    }
}
