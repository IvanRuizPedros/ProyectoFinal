package com.example.proyectofinal

import android.os.Bundle
import android.view.MotionEvent
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode

class ARActivity : AppCompatActivity() {

    private lateinit var arFragment: ArFragment
    private lateinit var overlayText: TextView
    private var currentAnchorNode: AnchorNode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar)

        // Configurar el fragmento AR
        arFragment = supportFragmentManager.findFragmentById(R.id.arFragment) as ArFragment
        overlayText = findViewById(R.id.arOverlayText)

        setupARScene()
    }

    private fun setupARScene() {
        // Configurar listener para toques en planos
        arFragment.setOnTapArPlaneListener { hitResult: HitResult, plane: Plane, _: MotionEvent ->
            if (plane.trackingState != TrackingState.TRACKING) return@setOnTapArPlaneListener
            placeTextNode(hitResult.createAnchor(), "Objeto detectado")
        }

        // Manejar actualizaciones de fotogramas
        arFragment.arSceneView.scene.addOnUpdateListener(this::onSceneUpdate)
    }

    private fun onSceneUpdate(frameTime: FrameTime) {
        val frame = arFragment.arSceneView.arFrame ?: return
        processFrame(frame)
    }

    private fun placeTextNode(anchor: Anchor, text: String) {
        // Eliminar nodo anterior si existe
        currentAnchorNode?.anchor?.detach()
        currentAnchorNode = null

        // Crear nuevo nodo de texto
        currentAnchorNode = AnchorNode(anchor).apply {
            setParent(arFragment.arSceneView.scene)
        }

        ViewRenderable.builder()
            .setView(this, R.layout.text_node_layout)
            .build()
            .thenAccept { renderable ->
                val textNode = TransformableNode(arFragment.transformationSystem).apply {
                    this.renderable = renderable
                    setParent(currentAnchorNode)
                    localPosition = Vector3(0f, 0.2f, 0f) // 20 cm sobre el plano
                }

                // Establecer el texto en la vista
                val textView = renderable.view.findViewById<TextView>(R.id.textView)
                textView.text = text
            }
    }

    private fun processFrame(frame: Frame) {
        // Aquí integrarías ML Kit para detección de objetos
        // Ejemplo simplificado:
        val detectedObjects = detectObjectsInFrame(frame)

        if (detectedObjects.isNotEmpty()) {
            val mainObject = detectedObjects[0]
            overlayText.text = "Detectado: ${mainObject.name}\nTraducción: ${translate(mainObject.name)}"
            overlayText.isVisible = true
        } else {
            overlayText.isVisible = false
        }
    }

    // Funciones de ejemplo (implementar con ML Kit)
    private fun detectObjectsInFrame(frame: Frame): List<DetectedObject> {
        return emptyList()
    }

    private fun translate(text: String): String {
        return when (text.lowercase()) {
            "dog" -> "perro"
            "cat" -> "gato"
            else -> "Traducción no disponible"
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            arFragment.onResume()
        } catch (ex: Exception) {
            // Manejar error si es necesario
        }
    }

    override fun onPause() {
        super.onPause()
        arFragment.onPause()
    }
}

// Clase de modelo para objetos detectados
data class DetectedObject(val name: String, val confidence: Float)