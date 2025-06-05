package com.example.proyectofinal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.objects.DetectedObject

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = 0xAAFF0000.toInt()
    }
    private val textPaint = Paint().apply {
        textSize = 48f
        color = Color.WHITE
    }

    // Para texto
    private var textBlocks: List<Text.TextBlock> = emptyList()
    // Para objetos
    private var objects: List<TranslatedObject> = emptyList()

    fun setTextBlocks(blocks: List<Text.TextBlock>) {
        textBlocks = blocks
        invalidate()
    }

    fun setObjects(list: List<TranslatedObject>) {
        objects = list
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Dibuja bloques de texto
        textBlocks.forEach { block ->
            block.boundingBox?.let { bb ->
                canvas.drawRect(bb, boxPaint)
                canvas.drawText(block.text, bb.left.toFloat(), bb.top - 8f, textPaint)
            }
        }
        // Dibuja objetos traducidos
        objects.forEach { to ->
            canvas.drawRect(to.bbox, boxPaint)
            canvas.drawText(to.text, to.bbox.left.toFloat(), to.bbox.top - 8f, textPaint)
        }
    }
}

data class TranslatedObject(val bbox: Rect, val text: String)