package com.example.proyectofinal

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.proyectofinal.databinding.ActivityTextRecognitionBinding
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale

class TextRecognitionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTextRecognitionBinding
    private lateinit var textRecognizer: TextRecognizer
    private lateinit var languageIdentifier: LanguageIdentifier
    private var translator: Translator? = null
    private var lastDetectedText: String? = null

    private val langs = TranslateLanguage.getAllLanguages()

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTextRecognitionBinding.inflate(layoutInflater)
        setContentView(binding.root)


        val displayNames = langs.map { code ->
            Locale(code).getDisplayName(Locale.getDefault()).ifEmpty { code }
        }
        ArrayAdapter(this, android.R.layout.simple_spinner_item, displayNames).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.targetLangSelector.adapter = adapter
        }

        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        languageIdentifier = LanguageIdentification.getClient()

        binding.targetLangSelector.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                    lastDetectedText?.let { identifyAndTranslate(it) }
                }
                override fun onNothingSelected(p: AdapterView<*>) {}
            }

        if (allPermissionsGranted()) startCamera()
        else ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            REQUEST_CAMERA_PERMISSION
        )
    }

    @OptIn(ExperimentalGetImage::class)
    private fun startCamera() {
        ProcessCameraProvider.getInstance(this).apply {
            addListener({
                val cameraProvider = get()
                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(binding.viewfinder.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
                    .build().apply {
                        setAnalyzer(ContextCompat.getMainExecutor(this@TextRecognitionActivity)) { proxy ->
                            analyzeImage(proxy)
                        }
                    }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this@TextRecognitionActivity,
                    androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            }, ContextCompat.getMainExecutor(this@TextRecognitionActivity))
        }
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun analyzeImage(imageProxy: ImageProxy) {
        imageProxy.image?.let { media ->
            val img = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
            textRecognizer.process(img)
                .addOnSuccessListener { visionText ->
                    val box = Rect(
                        binding.viewfinder.width/4,
                        binding.viewfinder.height/4,
                        binding.viewfinder.width*3/4,
                        binding.viewfinder.height*3/4
                    )
                    val block = visionText.textBlocks
                        .firstOrNull { it.boundingBox?.let { bb -> box.contains(bb.centerX(), bb.centerY()) } == true }
                    block?.text?.takeIf { isMeaningful(it) }?.let { identifyAndTranslate(it) }
                        ?: binding.overlay.setTextBlocks(emptyList())
                }
                .addOnFailureListener { e -> Log.e("TD", e.localizedMessage ?: "") }
                .addOnCompleteListener { imageProxy.close() }
        } ?: imageProxy.close()
    }

    private fun identifyAndTranslate(input: String) {
        lastDetectedText = input
        languageIdentifier.identifyLanguage(input)
            .addOnSuccessListener { langCode ->
                val src = if (langCode == "und") TranslateLanguage.ENGLISH else langCode
                val tgt = langs[binding.targetLangSelector.selectedItemPosition]
                translator?.close()
                translator = com.google.mlkit.nl.translate.Translation.getClient(
                    TranslatorOptions.Builder()
                        .setSourceLanguage(src)
                        .setTargetLanguage(tgt)
                        .build()
                )
                translator!!.downloadModelIfNeeded()
                    .addOnSuccessListener {
                        translator!!.translate(input)
                            .addOnSuccessListener { translated ->
                                binding.srcLang.text       = src.uppercase()
                                binding.srcText.text       = input
                                binding.translatedText.text = translated
                            }
                    }
                    .addOnFailureListener { Log.e("Trans", it.localizedMessage ?: "") }
            }
            .addOnFailureListener { _ ->
                binding.srcText.text       = input
                binding.translatedText.text = input
            }
    }

    private fun isMeaningful(text: String): Boolean {
        return Regex("\\p{L}{2,}").find(text) != null
    }

    private fun allPermissionsGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION && allPermissionsGranted()) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        translator?.close()
    }
}
