package com.example.proyectofinal

import android.graphics.Point
import android.media.Image
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class TextRecognitionActivity : AppCompatActivity() {

    // Enums para los modos de detección
    private enum class DetectionMode {
        OBJECT_DETECTION,
        TEXT_DETECTION
    }

    private lateinit var arFragment: ArFragment
    private lateinit var modeTitle: TextView
    private lateinit var modeToggle: SwitchMaterial
    private lateinit var modeIndicator: ImageView
    private lateinit var objectIcon: ImageView
    private lateinit var textIcon: ImageView

    private var lastProcessingTime = 0L
    private val PROCESSING_INTERVAL = 2000
    private var lastFrameTimestamp: Long = 0  // Variable añadida

    private val detectionExecutor = Executors.newSingleThreadExecutor()
    private val processingLock = AtomicBoolean(false)

    // Detector de objetos ML Kit
    private lateinit var objectDetector: com.google.mlkit.vision.objects.ObjectDetector
    // Detector de texto ML Kit
    private lateinit var textRecognizer: TextRecognizer

    // Modo actual de detección
    private var currentMode = DetectionMode.OBJECT_DETECTION

    // Lista para mantener todos los nodos de texto creados
    private val textNodes = mutableListOf<AnchorNode>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_recognition)

        // Configurar el fragmento AR
        arFragment = supportFragmentManager.findFragmentById(R.id.arFragment) as ArFragment
        modeTitle = findViewById(R.id.modeTitle)
        modeToggle = findViewById(R.id.modeToggle)
        modeIndicator = findViewById(R.id.modeIndicator)
        objectIcon = findViewById(R.id.objectIcon)
        textIcon = findViewById(R.id.textIcon)

        // Inicializar detectores
        initDetectors()

        setupUI()
        setupARScene()
    }

    private fun initDetectors() {
        // Configurar detector de objetos
        val objectOptions = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
        objectDetector = ObjectDetection.getClient(objectOptions)

        // Configurar detector de texto
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private fun setupUI() {
        // Configurar título inicial
        modeTitle.text = "Modo: Objetos"

        // Configurar el toggle para cambiar entre modos
        modeToggle.setOnCheckedChangeListener { _, isChecked ->
            currentMode = if (isChecked) {
                modeTitle.text = "Modo: Texto"
                moveIndicatorToView(textIcon)
                DetectionMode.TEXT_DETECTION
            } else {
                modeTitle.text = "Modo: Objetos"
                moveIndicatorToView(objectIcon)
                DetectionMode.OBJECT_DETECTION
            }
            removeAllTextNodes()
            // CORRECCIÓN: Cambiado de $current a $currentMode
            Log.d("ARActivity", "Modo cambiado a: $currentMode")
        }

        // Posicionar inicialmente el indicador sobre el icono de objetos
        objectIcon.post {
            moveIndicatorToView(objectIcon)
        }
    }

    private fun moveIndicatorToView(targetView: View) {
        val targetCenterX = targetView.x + targetView.width / 2f
        val targetCenterY = targetView.y + targetView.height / 2f
        modeIndicator.x = targetCenterX - modeIndicator.width / 2f
        modeIndicator.y = targetCenterY - modeIndicator.height / 2f
    }

    private fun setupARScene() {
        // Configurar listener para toques en planos
        arFragment.setOnTapArPlaneListener { hitResult: HitResult, plane: Plane, _: MotionEvent ->
            if (plane.trackingState != TrackingState.TRACKING) return@setOnTapArPlaneListener
            placeTextNode(hitResult.createAnchor(), "Punto de interés")
        }

        // Manejar actualizaciones de fotogramas
        arFragment.arSceneView.scene.addOnUpdateListener(this::onSceneUpdate)
    }

    private fun onSceneUpdate(frameTime: FrameTime) {
        if (processingLock.get()) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProcessingTime < PROCESSING_INTERVAL) return

        processingLock.set(true)
        lastProcessingTime = currentTime

        val frame = arFragment.arSceneView.arFrame ?: run {
            processingLock.set(false)
            return
        }

        processFrame(frame)
    }

    private fun placeTextNode(anchor: Anchor, text: String) {
        val anchorNode = AnchorNode(anchor).apply {
            setParent(arFragment.arSceneView.scene)
        }

        ViewRenderable.builder()
            .setView(this, R.layout.text_node_layout)
            .build()
            .thenAccept { renderable ->
                val textNode = TransformableNode(arFragment.transformationSystem).apply {
                    this.renderable = renderable
                    setParent(anchorNode)
                    localPosition = Vector3(0f, 0.2f, 0f) // 20 cm sobre el plano
                }

                // Establecer el texto en la vista
                val textView = renderable.view.findViewById<TextView>(R.id.textView)
                textView.text = text

                textNodes.add(anchorNode)
            }
            .exceptionally { throwable ->
                Toast.makeText(this, "Error al crear nodo: ${throwable.message}", Toast.LENGTH_LONG).show()
                null
            }
    }

    private fun getImageRotation(frame: Frame): Int {
        // Obtener la orientación del dispositivo
        val rotation = windowManager.defaultDisplay.rotation

        // Calcular la rotación basada en la orientación del dispositivo
        return when (rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    private fun processFrame(frame: Frame) {
        if (processingLock.get() || System.currentTimeMillis() - lastProcessingTime < PROCESSING_INTERVAL) {
            return
        }

        processingLock.set(true)
        lastProcessingTime = System.currentTimeMillis()

        val currentFrame = arFragment.arSceneView.arFrame ?: run {
            processingLock.set(false)
            return
        }

        // Verificar estado de seguimiento de la cámara
        if (currentFrame.camera.trackingState != TrackingState.TRACKING) {
            processingLock.set(false)
            return
        }

        // Verificar si el frame es el mismo que el último procesado
        if (frame.timestamp == lastFrameTimestamp) {
            processingLock.set(false)
            return
        }
        lastFrameTimestamp = frame.timestamp // Actualizar el último timestamp

        var image: Image? = null
        try {
            image = frame.acquireCameraImage()
            val rotation = getImageRotation(currentFrame)
            val inputImage = InputImage.fromMediaImage(image, rotation)

            detectionExecutor.execute {
                when (currentMode) {
                    DetectionMode.OBJECT_DETECTION -> detectObjects(inputImage, currentFrame)
                    DetectionMode.TEXT_DETECTION -> detectText(inputImage, currentFrame)
                }
            }
        } catch (e: NotYetAvailableException) {
            Log.w("ARActivity", "Frame no disponible, omitiendo...")
            processingLock.set(false)
        } catch (e: Exception) {
            Log.e("ARActivity", "Error al procesar frame: ${e.message}")
            processingLock.set(false)
        } finally {
            image?.close()
        }
    }

    private fun detectObjects(inputImage: InputImage, frame: Frame) {
        try {
            objectDetector.process(inputImage)
                .addOnSuccessListener { detectedObjects ->
                    Log.d("ARActivity", "Objetos detectados: ${detectedObjects.size}")

                    // Filtrar objetos con alta confianza
                    val filteredObjects = detectedObjects.filter { obj ->
                        obj.labels.any { label -> label.confidence > 0.3f }
                    }

                    // Obtener el objeto principal
                    val mainObject = filteredObjects.maxByOrNull { obj ->
                        obj.labels.maxOfOrNull { label -> label.confidence } ?: 0f
                    }

                    // Actualizar UI en el hilo principal
                    runOnUiThread {
                        removeAllTextNodes()

                        if (mainObject != null) {
                            val bestLabel = mainObject.labels.maxByOrNull { it.confidence }
                            if (bestLabel != null) {
                                val objectName = bestLabel.text
                                val translation = translateObject(objectName)

                                // Crear un nodo de texto para el objeto detectado
                                val centerPoint = frame.screenCenter()
                                val hitTestResults = frame.hitTest(centerPoint.x.toFloat(), centerPoint.y.toFloat())

                                for (hit in hitTestResults) {
                                    if (hit.trackable is Plane) {
                                        placeTextNode(hit.createAnchor(), "$objectName\n$translation")
                                        break
                                    }
                                }
                            }
                        }
                    }

                    // Liberar el lock
                    processingLock.set(false)
                }
                .addOnFailureListener { e ->
                    Log.e("ARActivity", "Detección de objetos fallida: ${e.message}")
                    processingLock.set(false)
                }
        } catch (e: Exception) {
            Log.e("ARActivity", "Error en detección de objetos: ${e.message}")
            processingLock.set(false)
        }
    }

    private fun detectText(inputImage: InputImage, frame: Frame) {
        try {
            textRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    processDetectedText(visionText, frame)
                }
                .addOnFailureListener { e ->
                    Log.e("ARActivity", "Detección de texto fallida: ${e.message}")
                    processingLock.set(false)
                }
        } catch (e: Exception) {
            Log.e("ARActivity", "Error en detección de texto: ${e.message}")
            processingLock.set(false)
        }
    }

    private fun processDetectedText(visionText: Text, frame: Frame) {
        Log.d("ARActivity", "Texto detectado")

        // Obtener todos los bloques de texto
        val textBlocks = visionText.textBlocks
        if (textBlocks.isEmpty()) {
            processingLock.set(false)
            return
        }

        runOnUiThread {
            removeAllTextNodes()

            for (block in textBlocks) {
                val text = block.text
                val boundingBox = block.boundingBox ?: continue
                val translation = translateText(text)

                // Crear un ancla en el centro del bloque de texto
                val centerX = boundingBox.centerX().toFloat()
                val centerY = boundingBox.centerY().toFloat()
                val hitTestResults = frame.hitTest(centerX, centerY)

                for (hit in hitTestResults) {
                    if (hit.trackable is Plane) {
                        placeTextNode(hit.createAnchor(), "$text\n$translation")
                        break
                    }
                }
            }
        }

        processingLock.set(false)
    }

    private fun Frame.screenCenter(): Point {
        return Point(
            arFragment.arSceneView.width / 2,
            arFragment.arSceneView.height / 2
        )
    }

    private fun removeAllTextNodes() {
        textNodes.forEach { it.anchor?.detach() }
        textNodes.clear()
    }

    private fun translateObject(text: String): String {
        return when (text.lowercase()) {
            "person" -> "persona"
            "dog" -> "perro"
            "cat" -> "gato"
            "car" -> "coche"
            "book" -> "libro"
            "bottle" -> "botella"
            "chair" -> "silla"
            "cup" -> "taza"
            "laptop" -> "portátil"
            "phone" -> "teléfono"
            else -> "Traducción no disponible"
        }
    }

    private fun translateText(text: String): String {
        // Traducciones más completas
        return when (text.lowercase()) {
            "hello" -> "hola"
            "hi" -> "hola"
            "goodbye" -> "adiós"
            "bye" -> "adiós"
            "exit" -> "salida"
            "entrance" -> "entrada"
            "open" -> "abierto"
            "closed" -> "cerrado"
            "welcome" -> "bienvenido"
            "thank you" -> "gracias"
            "thanks" -> "gracias"
            "please" -> "por favor"
            "help" -> "ayuda"
            "information" -> "información"
            "toilet" -> "baño"
            "restroom" -> "aseo"
            "men" -> "hombres"
            "women" -> "mujeres"
            "caution" -> "precaución"
            "danger" -> "peligro"
            "stop" -> "alto"
            "food" -> "comida"
            "drink" -> "bebida"
            "water" -> "agua"
            "coffee" -> "café"
            "bus" -> "autobús"
            "train" -> "tren"
            "airport" -> "aeropuerto"
            "hotel" -> "hotel"
            "room" -> "habitación"
            "price" -> "precio"
            "free" -> "gratis"
            "sale" -> "rebajas"  // Corregido de "极ebajas" a "rebajas"
            "emergency" -> "emergencia"
            "entrance" -> "entrada"
            "exit" -> "salida"
            "push" -> "empujar"
            "pull" -> "tirar"
            "enter" -> "entrar"
            "start" -> "inicio"
            "end" -> "fin"
            else -> "Traducción no disponible"
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            arFragment.onResume()
        } catch (ex: Exception) {
            Log.e("ARActivity", "Error al reanudar AR: ${ex.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        arFragment.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            // Cerrar los detectores para liberar recursos
            objectDetector.close()
            detectionExecutor.shutdownNow()
            arFragment.arSceneView.pause()
        } catch (e: Exception) {
            Log.e("ARActivity", "Error en onDestroy: ${e.message}")
        }
    }
}