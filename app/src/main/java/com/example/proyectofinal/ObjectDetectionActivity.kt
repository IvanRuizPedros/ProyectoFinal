package com.example.proyectofinal

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
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
import com.example.proyectofinal.databinding.ActivityObjectDetectionBinding
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.Locale

class ObjectDetectionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityObjectDetectionBinding
    private lateinit var objectDetector: ObjectDetector
    private val languageIdentifier by lazy { LanguageIdentification.getClient() }
    private var translator: Translator? = null

    private val langs = TranslateLanguage.getAllLanguages()
    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 1001
        private const val MIN_CONFIDENCE = 0.6f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityObjectDetectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val displayNames = langs.map { code ->
            Locale(code).getDisplayName(Locale.getDefault()).ifEmpty { code }
        }
        ArrayAdapter(this, android.R.layout.simple_spinner_item, displayNames).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.targetLangSelector.adapter = adapter
        }

        initDetector()
        if (allPermissionsGranted()) startCamera()
        else ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION
        )
    }

    private fun initDetector() {
        val opts = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
        objectDetector = ObjectDetection.getClient(opts)
    }

    private fun startCamera() {
        ProcessCameraProvider.getInstance(this).apply {
            addListener({
                val cp = get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.viewfinder.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
                    .build().also {
                        it.setAnalyzer(ContextCompat.getMainExecutor(this@ObjectDetectionActivity)) { p ->
                            analyzeImage(p)
                        }
                    }
                cp.unbindAll()
                cp.bindToLifecycle(
                    this@ObjectDetectionActivity,
                    androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                    preview, analysis
                )
            }, ContextCompat.getMainExecutor(this@ObjectDetectionActivity))
        }
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun analyzeImage(imageProxy: ImageProxy) {
        imageProxy.image?.let { media ->
            val img = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
            objectDetector.process(img)
                .addOnSuccessListener { results ->
                    val loc = IntArray(2)
                    binding.objectGuideBox.getLocationOnScreen(loc)
                    val scanBox = Rect(
                        loc[0],
                        loc[1],
                        loc[0] + binding.objectGuideBox.width,
                        loc[1] + binding.objectGuideBox.height
                    )

                    val filtered = results.filter { obj ->
                        obj.labels.any { it.confidence >= MIN_CONFIDENCE } &&
                                scanBox.contains(
                                    obj.boundingBox.centerX(),
                                    obj.boundingBox.centerY()
                                )
                    }

                    if (filtered.isNotEmpty()) {
                        val best = filtered.maxByOrNull {
                            it.labels.maxOf { lbl -> lbl.confidence }
                        }!!

                        val label = best.labels
                            .filter { it.confidence >= MIN_CONFIDENCE }
                            .maxByOrNull { it.confidence }!!
                            .text

                        translateText(best.boundingBox, label)
                    } else {
                        binding.overlay.setObjects(emptyList())
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("OD", e.localizedMessage ?: "error procesando imagen")
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } ?: imageProxy.close()
    }

    private fun translateText(bbox: Rect, label: String) {
        languageIdentifier.identifyLanguage(label)
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
                        translator!!.translate(label)
                            .addOnSuccessListener { translated ->
                                binding.overlay.setObjects(listOf( TranslatedObject(bbox, translated) ))
                            }
                            .addOnFailureListener {
                                binding.overlay.setObjects(listOf( TranslatedObject(bbox, label) ))
                            }
                    }
                    .addOnFailureListener {
                        binding.overlay.setObjects(listOf( TranslatedObject(bbox, label) ))
                    }
            }
            .addOnFailureListener {
                binding.overlay.setObjects(listOf( TranslatedObject(bbox, label) ))
            }
    }


    private fun allPermissionsGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION && allPermissionsGranted()) startCamera()
        else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        translator?.close()
    }
}