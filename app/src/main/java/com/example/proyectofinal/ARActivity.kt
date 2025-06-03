package com.example.proyectofinal

import android.app.AlertDialog
import androidx.activity.ComponentActivity
import com.google.ar.core.ArCoreApk

class ARActivity : ComponentActivity()  {

    private fun checkARSupport() {
        val availability = ArCoreApk.getInstance().checkAvailability(this)
        if (!availability.isSupported) {
            AlertDialog.Builder(this)
                .setMessage("AR no soportado en este dispositivo")
                .setPositiveButton("Salir") { _, _ -> finish() }
                .show()
        }
    }
}