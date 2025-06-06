package com.example.proyectofinal.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
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
import java.util.*

class MCQLevel1Activity : AppCompatActivity() {

    private lateinit var translator: Translator
    private lateinit var correctAnswer: String
    private lateinit var spanishWord: String

    private val allCandidates = listOf(
        "perro", "gato", "pájaro", "caballo", "ratón", "vaca"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mcq_level)

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
                Toast.makeText(this, "Error cargando modelo: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun prepareExercise(targetLang: String) {
        val three = allCandidates.shuffled().take(3)

        spanishWord = three.random()

        translateList(three) { translations ->
            val idx = three.indexOf(spanishWord)
            correctAnswer = translations[idx]

            setupUI(targetLang, translations.shuffled())
        }
    }

    private fun translateList(words: List<String>, onDone: (List<String>) -> Unit) {
        val results = mutableListOf<String>()
        fun recurse(i: Int) {
            if (i == words.size) {
                onDone(results)
                return
            }
            translator.translate(words[i])
                .addOnSuccessListener {
                    results.add(it.trim())
                    recurse(i + 1)
                }
                .addOnFailureListener {
                    results.add("…")
                    recurse(i + 1)
                }
        }
        recurse(0)
    }

    private fun setupUI(targetLang: String, options: List<String>) {
        val displayLang = Locale(targetLang)
            .getDisplayLanguage(Locale.getDefault())
            .replaceFirstChar { it.uppercase() }

        findViewById<TextView>(R.id.tvQuestion).text =
            "¿Cómo se dice '$spanishWord' en $displayLang?"

        val rg = findViewById<RadioGroup>(R.id.radioGroup)
        options.forEachIndexed { idx, opt ->
            (rg.getChildAt(idx) as RadioButton).text = opt
        }

        findViewById<Button>(R.id.btnSubmit).setOnClickListener {
            val selId = rg.checkedRadioButtonId
            if (selId != -1) {
                val choice = findViewById<RadioButton>(selId).text.toString()
                val correct = choice.equals(correctAnswer, ignoreCase = true)
                Toast.makeText(
                    this,
                    if (correct) "✅ Correcto" else "❌ Incorrecto",
                    Toast.LENGTH_SHORT
                ).show()
                saveProgress("mcq1", correct)
                if (correct) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        Intent(this, MainMenuActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            .putExtra(MainMenuActivity.EXTRA_AUTO_NEXT, true)
                            .putExtra("targetLang", targetLang)
                            .also { startActivity(it) }
                        finish()
                    }, 2000)
                }
            } else {
                Toast.makeText(this, "Selecciona una opción", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onBackPressed() {
        startActivity(Intent(this, MainMenuActivity::class.java))
        super.onBackPressed()
    }

    private fun saveProgress(exerciseId: String, completed: Boolean) {
        val userId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        FirebaseDatabase.getInstance()
            .getReference("users")
            .child(userId)
            .child("exercises")
            .child(exerciseId)
            .setValue(mapOf("completed" to completed))
    }

    override fun onDestroy() {
        super.onDestroy()
        translator.close()
    }
}