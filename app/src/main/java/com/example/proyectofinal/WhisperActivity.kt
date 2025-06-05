package com.example.proyectofinal

import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.Manifest
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import org.json.JSONObject

class WhisperActivity : AppCompatActivity() {
    private lateinit var btnRecord: Button
    private lateinit var progress: ProgressBar
    private lateinit var tvTranscript: TextView
    private var recorder: MediaRecorder? = null
    private lateinit var audioFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_whisper)

        btnRecord    = findViewById(R.id.btnRecord)
        progress     = findViewById(R.id.progress)
        tvTranscript = findViewById(R.id.tvTranscript)

        btnRecord.setOnClickListener {
            if (recorder == null) startRecording() else stopRecordingAndTranscribe()
        }

        requestPermissionsIfNeeded()
    }

    private fun requestPermissionsIfNeeded() {
        val perms = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.INTERNET)
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 200)
        }
    }

    private fun startRecording() {
        audioFile = File(cacheDir, "whisper_input.m4a")
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioFile.absolutePath)
            prepare()
            start()
        }
        btnRecord.text = "Detener"
    }

    private fun stopRecordingAndTranscribe() {
        recorder?.apply {
            stop()
            release()
        }
        recorder = null
        btnRecord.text = "Grabar"
        transcribeWithWhisper(audioFile)
    }

    private fun transcribeWithWhisper(file: File) {
        progress.visibility = View.VISIBLE
        tvTranscript.text = ""

        val client = OkHttpClient()
        val mediaType = "audio/m4a".toMediaType()
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-1")
            .addFormDataPart(
                "file", file.name,
                file.asRequestBody(mediaType)
            )
            .build()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/transcriptions")
            .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    tvTranscript.text = "Error: ${e.localizedMessage}"
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string()
                Log.d("WhisperAPI", "Response: $json")
                val text = JSONObject(json ?: "{}").optString("text", "—")
                runOnUiThread {
                    progress.visibility = View.GONE
                    tvTranscript.text = text
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        recorder?.release()
    }
}
