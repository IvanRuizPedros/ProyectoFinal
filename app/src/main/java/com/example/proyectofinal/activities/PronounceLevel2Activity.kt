package com.example.proyectofinal.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.proyectofinal.BuildConfig
import com.example.proyectofinal.MainMenuActivity
import com.example.proyectofinal.R
import com.google.firebase.database.FirebaseDatabase
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.translate.Translation
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.*

class PronounceLevel2Activity : AppCompatActivity() {
    private lateinit var translator: Translator
    private lateinit var correctPhrase: String
    private lateinit var spanishWord: String
    private val allWords = listOf("feliz", "triste", "rápido", "lento", "fuerte", "débil")

    private lateinit var audioFile: File
    private var recorder: MediaRecorder? = null

    override fun onCreate(s:Bundle?){
        super.onCreate(s)
        setContentView(R.layout.activity_pronounce_level)

        val targetLang = intent.getStringExtra("targetLang")
            ?: TranslateLanguage.ENGLISH

        val opts = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.SPANISH)
            .setTargetLanguage(targetLang)
            .build()
        translator = Translation.getClient(opts)

        translator.downloadModelIfNeeded()
            .addOnSuccessListener { prepareExercise(targetLang) }

        findViewById<Button>(R.id.btnRecord).setOnClickListener { startRecording() }
        requestPermissionsIfNeeded()
    }

    private fun prepareExercise(targetLang:String){
        spanishWord = allWords.random()
        translator.translate(spanishWord)
            .addOnSuccessListener {
                correctPhrase = it.trim()
                val displayLang =
                    Locale(targetLang).getDisplayLanguage(Locale.getDefault())
                        .replaceFirstChar{it.uppercase()}
                findViewById<TextView>(R.id.tvPhrase).text =
                    "Di en voz alta: $correctPhrase"
            }
    }

    private fun requestPermissionsIfNeeded() {
        val perms = arrayOf(Manifest.permission.RECORD_AUDIO)
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty())
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 0)
    }

    private fun startRecording(){
        val prog=findViewById<ProgressBar>(R.id.prog)
        prog.visibility=ProgressBar.VISIBLE
        audioFile=File(cacheDir,"pronounce2.m4a")
        recorder=MediaRecorder().apply{
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioFile.absolutePath)
            prepare(); start()
        }
        Handler(Looper.getMainLooper()).postDelayed({
            recorder?.stop(); recorder?.release(); recorder=null
            evaluatePronunciation()
        },3000)
    }

    private fun evaluatePronunciation(){
        val prog=findViewById<ProgressBar>(R.id.prog)
        val client=OkHttpClient()
        val body=MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model","whisper-1")
            .addFormDataPart("file",audioFile.name,
                audioFile.asRequestBody("audio/m4a".toMediaType()))
            .build()
        val req=Request.Builder()
            .url("https://api.openai.com/v1/audio/transcriptions")
            .addHeader("Authorization","Bearer ${BuildConfig.OPENAI_API_KEY}")
            .post(body).build()

        client.newCall(req).enqueue(object:Callback{
            override fun onFailure(c:Call,e:IOException)=runOnUiThread{
                prog.visibility=ProgressBar.GONE
                findViewById<TextView>(R.id.tvResult).text="Error"
            }
            override fun onResponse(c:Call,r:Response){
                val res=JSONObject(r.body!!.string()).optString("text","").trim()

                val normalizedRes = res.lowercase().replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
                val normalizedCorrect = correctPhrase.lowercase().replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
                val correct = normalizedRes == normalizedCorrect

                runOnUiThread{
                    prog.visibility=ProgressBar.GONE
                    findViewById<TextView>(R.id.tvResult).text=
                        if(correct)"✅ ¡Pronunciación correcta!"
                        else "❌ Dijiste: \"$res\""
                    saveProgress("pronounce2",correct)
                    if(correct){
                        Handler(Looper.getMainLooper()).postDelayed({
                            val intent = Intent(this@PronounceLevel2Activity, MainMenuActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                putExtra(MainMenuActivity.EXTRA_AUTO_NEXT, true)
                                putExtra("targetLang", intent.getStringExtra("targetLang"))
                            }
                            startActivity(intent)
                            finish()
                        }, 2000)
                    }
                }
            }
        })
    }

    override fun onBackPressed() {
        startActivity(Intent(this, MainMenuActivity::class.java))
        super.onBackPressed()
    }

    private fun saveProgress(id:String,comp:Boolean){
        val uid=Settings.Secure.getString(contentResolver,
            Settings.Secure.ANDROID_ID)
        FirebaseDatabase.getInstance()
            .getReference("users").child(uid)
            .child("exercises").child(id)
            .setValue(mapOf("completed" to comp))
    }

    override fun onDestroy(){
        super.onDestroy();translator.close()
    }
}