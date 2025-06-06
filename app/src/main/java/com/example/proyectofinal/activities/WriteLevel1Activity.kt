package com.example.proyectofinal.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.proyectofinal.MainMenuActivity
import com.example.proyectofinal.R
import com.google.firebase.database.FirebaseDatabase
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.translate.Translation
import java.text.Normalizer
import java.util.*

class WriteLevel1Activity : AppCompatActivity() {
    private lateinit var translator: Translator
    private lateinit var correctAnswer: String
    private lateinit var spanishWord: String

    private val allWords = listOf("sol", "luna", "estrella", "coche", "manzana", "montaña")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_write_level)

        val targetLang = intent.getStringExtra("targetLang")
            ?: TranslateLanguage.ENGLISH

        val opts = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.SPANISH)
            .setTargetLanguage(targetLang)
            .build()
        translator = Translation.getClient(opts)

        translator.downloadModelIfNeeded()
            .addOnSuccessListener { prepareExercise(targetLang) }
            .addOnFailureListener {
                Toast.makeText(this, "Error cargando modelo", Toast.LENGTH_LONG).show()
            }
    }

    private fun prepareExercise(targetLang: String) {
        spanishWord = allWords.random()

        translator.translate(spanishWord)
            .addOnSuccessListener {
                correctAnswer = it.trim()
                setupUI(targetLang)
            }
    }

    private fun setupUI(targetLang: String) {
        val displayLang = Locale(targetLang).getDisplayLanguage(Locale.getDefault())
            .replaceFirstChar { it.uppercase() }
        findViewById<TextView>(R.id.tvPrompt).text =
            "¿Cómo se escribe '$spanishWord' en $displayLang?"

        val et = findViewById<EditText>(R.id.etAnswer)
        val btn = findViewById<Button>(R.id.btnCheck)
        btn.setOnClickListener {
            val input = normalize(et.text.toString())
            val ok = input == normalize(correctAnswer)
            Toast.makeText(this,
                if (ok) "✅ Correcto" else "❌ Intenta de nuevo",
                Toast.LENGTH_SHORT).show()
            saveProgress("write1", ok)
            if (ok) {
                Handler(Looper.getMainLooper()).postDelayed({
                    Intent(this, MainMenuActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(MainMenuActivity.EXTRA_AUTO_NEXT, true)
                        .putExtra("targetLang", targetLang)
                        .also { startActivity(it) }
                    finish()
                }, 2000)
            }
        }
    }

    override fun onBackPressed() {
        startActivity(Intent(this, MainMenuActivity::class.java))
        super.onBackPressed()
    }

    private fun normalize(s:String) = Normalizer.normalize(s,Normalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(),"")
        .lowercase(Locale.getDefault())

    private fun saveProgress(id:String, completed:Boolean){
        val uid = Settings.Secure.getString(contentResolver,Settings.Secure.ANDROID_ID)
        FirebaseDatabase.getInstance()
            .getReference("users").child(uid)
            .child("exercises").child(id)
            .setValue(mapOf("completed" to completed))
    }

    override fun onDestroy(){
        super.onDestroy(); translator.close()
    }
}