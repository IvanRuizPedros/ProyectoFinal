package com.example.proyectofinal

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.proyectofinal.ui.theme.ProyectoFinalTheme
import com.google.ar.core.ArCoreApk

class MainMenuActivity : ComponentActivity() {
    private val PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private var showPermissionDialog by mutableStateOf(false)
    private var shouldFinishActivity by mutableStateOf(false)
    private var permissionsGranted by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        permissionsGranted = allGranted

        if (!allGranted) {
            showPermissionDialog = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar permisos al iniciar
        checkPermissions()

        enableEdgeToEdge()
        setContent {
            ProyectoFinalTheme {
                // Manejar diálogo de permisos
                if (showPermissionDialog) {
                    PermissionDeniedDialog(
                        onDismiss = { showPermissionDialog = false },
                        onGoToSettings = { openAppSettings() },
                        onExitApp = { shouldFinishActivity = true }
                    )
                }

                // Salir si el usuario elige salir de la app
                if (shouldFinishActivity) {
                    LaunchedEffect(Unit) { finish() }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Aprendizaje de Idiomas",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )

                        Spacer(modifier = Modifier.height(40.dp))

                        // Botón para acceder a Reconocimiento de texto
                        MenuButton(
                            text = "Reconocimiento de texto",
                            onClick = {
                                startActivity(Intent(this@MainMenuActivity, TextRecognitionActivity::class.java))
                            },
                            enabled = permissionsGranted
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Botón para acceder a Detección de objetos
                        MenuButton(
                            text = "Detección de objetos",
                            onClick = {
                                startActivity(Intent(this@MainMenuActivity, ObjectDetectionActivity::class.java))
                            },
                            enabled = permissionsGranted
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Botón para volver a solicitar permisos
                        MenuButton(
                            text = "Verificar permisos",
                            onClick = { checkPermissions() },
                            enabled = true
                        )
                    }
                }
            }
        }
    }

    private fun checkPermissions() {
        val permissionsToRequest = PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(
                this,
                it
            ) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
        } else {
            permissionsGranted = true
            showPermissionDialog = false
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }
}

@Composable
fun MenuButton(text: String, onClick: () -> Unit, enabled: Boolean) {
    Button(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 40.dp),
        enabled = enabled
    ) {
        Text(text = text, fontSize = 18.sp)
    }
}

@Composable
fun PermissionDeniedDialog(
    onDismiss: () -> Unit,
    onGoToSettings: () -> Unit,
    onExitApp: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permisos requeridos") },
        text = { Text("La aplicación necesita acceso a la cámara y micrófono para funcionar correctamente. Puede conceder los permisos en la configuración de la aplicación.") },
        confirmButton = {
            Button(onClick = onGoToSettings) {
                Text("Abrir Configuración")
            }
        },
        dismissButton = {
            Button(onClick = onExitApp) {
                Text("Salir de la App")
            }
        }
    )
}

private fun isDeviceSupported(context: Context): Boolean {
    return ArCoreApk.getInstance().checkAvailability(context).isSupported
}

@Preview(showBackground = true)
@Composable
fun MainMenuPreview() {
    ProyectoFinalTheme {
        val context = LocalContext.current
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MenuButton(text = "Traductor AR", onClick = {
                // En preview no mostramos Toast
            }, enabled = true)
            Spacer(modifier = Modifier.height(20.dp))
            MenuButton(text = "Verificar permisos", onClick = {}, enabled = true)
        }
    }
}