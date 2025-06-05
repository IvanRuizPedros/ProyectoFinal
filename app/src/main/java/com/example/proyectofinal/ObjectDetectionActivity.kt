package com.example.proyectofinal

import android.Manifest
import android.content.pm.PackageManager
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
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.objects.ObjectDetector
import java.util.Locale

class ObjectDetectionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityObjectDetectionBinding
    private lateinit var objectDetector: ObjectDetector
    private var translator: Translator? = null

    // Lista de códigos de idioma soportados
    private val langs = TranslateLanguage.getAllLanguages()

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 1001
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

        // Detector de objetos ML Kit
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
        objectDetector = ObjectDetection.getClient(options)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewfinder.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(ContextCompat.getMainExecutor(this), ::analyzeImage) }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun analyzeImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            objectDetector.process(inputImage)
                .addOnSuccessListener { results ->
                    val main = results
                        .filter { it.labels.isNotEmpty() }
                        .maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                    main?.let { obj ->
                        val label = obj.labels.maxByOrNull { it.confidence }!!.text
                        translateAndDisplay(obj.boundingBox, label)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("ObjDetect", "Error: ${e.localizedMessage}")
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun translateAndDisplay(bbox: android.graphics.Rect, label: String) {
        val srcLang = TranslateLanguage.ENGLISH

        val tgtLang = langs[binding.targetLangSelector.selectedItemPosition]

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(srcLang)
            .setTargetLanguage(tgtLang)
            .build()
        translator?.close()
        translator = Translation.getClient(options)
        translator!!
            .downloadModelIfNeeded()
            .addOnSuccessListener {
                translator!!.translate(label)
                    .addOnSuccessListener { translated ->
                        binding.overlay.setObjects(listOf( TranslatedObject(bbox, translated) ))
                    }
            }
            .addOnFailureListener { e ->
                Log.e("Translator", "Download failed: ${e.localizedMessage}")
            }
    }

    private fun allPermissionsGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        translator?.close()
    }
}
