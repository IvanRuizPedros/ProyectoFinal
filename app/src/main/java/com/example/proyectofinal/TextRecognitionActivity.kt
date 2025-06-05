package com.example.proyectofinal

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.RectF
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
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale

class TextRecognitionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTextRecognitionBinding
    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val languageIdentifier by lazy {
        LanguageIdentification.getClient()
    }
    private var translator: Translator? = null

    private val langs = TranslateLanguage.getAllLanguages()
    private var lastDetectedText: String? = null
    private var lastUpdateTime = 0L
    private val MIN_UPDATE_INTERVAL = 1500L

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

        binding.targetLangSelector.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                    lastDetectedText?.let { translateText(it) }
                }
                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

        if (allPermissionsGranted()) startCamera()
        else ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION
        )
    }

    @OptIn(ExperimentalGetImage::class)
    private fun startCamera() {
        ProcessCameraProvider.getInstance(this).apply {
            addListener({
                val cameraProvider = get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.viewfinder.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
                    .build().also {
                        it.setAnalyzer(ContextCompat.getMainExecutor(this@TextRecognitionActivity)) { proxy ->
                            analyzeImage(proxy)
                        }
                    }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this@TextRecognitionActivity,
                    androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                    preview, analysis
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
                    val imgW = img.width.toFloat()
                    val imgH = img.height.toFloat()

                    val locVF = IntArray(2)
                    binding.viewfinder.getLocationOnScreen(locVF)
                    val vfLeft = locVF[0].toFloat()
                    val vfTop  = locVF[1].toFloat()
                    val vfW = binding.viewfinder.width.toFloat()
                    val vfH = binding.viewfinder.height.toFloat()

                    val scaleX = vfW / imgW
                    val scaleY = vfH / imgH

                    val locG = IntArray(2)
                    binding.textGuideBox.getLocationOnScreen(locG)
                    val scanBox = RectF(
                        locG[0].toFloat(),
                        locG[1].toFloat(),
                        locG[0] + binding.textGuideBox.width.toFloat(),
                        locG[1] + binding.textGuideBox.height.toFloat()
                    )

                    val mappedBlocks = visionText.textBlocks.mapNotNull { blk ->
                        blk.boundingBox?.let { bbImg ->
                            val left   = vfLeft + bbImg.left   * scaleX
                            val top    = vfTop  + bbImg.top    * scaleY
                            val right  = vfLeft + bbImg.right  * scaleX
                            val bottom = vfTop  + bbImg.bottom * scaleY
                            val bbView = RectF(left, top, right, bottom)
                            if (RectF.intersects(bbView, scanBox)) {
                                Pair(blk, bbView)
                            } else null
                        }
                    }

                    if (mappedBlocks.isNotEmpty()) {
                        val newText = mappedBlocks.first().first.text
                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTime > MIN_UPDATE_INTERVAL
                            && newText.length >= 2
                            && levenshtein(newText, lastDetectedText ?: "") > 3
                        ) {
                            lastDetectedText = newText
                            lastUpdateTime = now
                            translateText(newText)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("TD", e.localizedMessage ?: "error")
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } ?: imageProxy.close()
    }

    private fun translateText(input: String) {
        languageIdentifier.identifyLanguage(input)
            .addOnSuccessListener { lang ->
                val src = if (lang == "und") TranslateLanguage.ENGLISH else lang
                val tgt = langs[binding.targetLangSelector.selectedItemPosition]
                translator?.close()
                translator = Translation.getClient(
                    TranslatorOptions.Builder()
                        .setSourceLanguage(src)
                        .setTargetLanguage(tgt)
                        .build()
                )
                translator!!.downloadModelIfNeeded()
                    .addOnSuccessListener {
                        translator!!.translate(input)
                            .addOnSuccessListener { translated ->
                                binding.srcLang.text = src.uppercase()
                                binding.srcText.text = input
                                binding.translatedText.text = translated
                            }
                    }
            }
            .addOnFailureListener {
                binding.srcText.text = input
                binding.translatedText.text = input
            }
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            dp[i][j] = minOf(
                dp[i - 1][j] + 1,
                dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
            )
        }
        return dp[a.length][b.length]
    }

    private fun allPermissionsGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION && allPermissionsGranted()) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun RectF.toRect(): Rect =
        Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

    override fun onDestroy() {
        super.onDestroy()
        translator?.close()
    }
}