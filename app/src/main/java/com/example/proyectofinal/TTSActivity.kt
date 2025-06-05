package com.example.proyectofinal

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class TTSActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var etText: EditText
    private lateinit var btnSpeak: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tts)

        etText = findViewById(R.id.etText)
        btnSpeak = findViewById(R.id.btnSpeak)

        tts = TextToSpeech(this, this)

        btnSpeak.setOnClickListener {
            val text = etText.text.toString()
            if (text.isNotBlank()) {
                speak(text)
            } else {
                Toast.makeText(this, "Escribe algún texto", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale("es", "ES"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Idioma no soportado", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Inicialización de TTS fallida", Toast.LENGTH_SHORT).show()
        }
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}
