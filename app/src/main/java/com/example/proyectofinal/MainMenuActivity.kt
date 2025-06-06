package com.example.proyectofinal

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.proyectofinal.activities.*
import com.example.proyectofinal.ui.theme.ProyectoFinalTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.database.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.compose.ui.viewinterop.AndroidView
import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.Locale

class MainMenuActivity : ComponentActivity() {
    companion object {
        const val EXTRA_AUTO_NEXT = "auto_next"
        private val PREFS_NAME = "language_prefs"
        private val KEY_SELECTED_LANG = "selected_lang"
    }

    private val PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private var showPermissionDialog by mutableStateOf(false)
    private var shouldFinishActivity by mutableStateOf(false)
    private var permissionsGranted by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        permissionsGranted = perms.all { it.value }
        if (!permissionsGranted) showPermissionDialog = true
    }

    private val userId: String by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        checkPermissions()
        enableEdgeToEdge()

        setContent {
            ProyectoFinalTheme {
                var selectedLang by remember { mutableStateOf(loadSelectedLanguage()) }

                if (showPermissionDialog) {
                    PermissionDeniedDialog(
                        onDismiss = { showPermissionDialog = false },
                        onGoToSettings = { openAppSettings() },
                        onExitApp = { shouldFinishActivity = true }
                    )
                }
                if (shouldFinishActivity) {
                    LaunchedEffect(Unit) { finish() }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Column(
                        Modifier
                            .padding(padding)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Aprendizaje de Idiomas",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                        Spacer(Modifier.height(20.dp))

                        MenuButton("Reconocimiento de texto", {
                            startActivity(Intent(this@MainMenuActivity, TextRecognitionActivity::class.java))
                        }, permissionsGranted)
                        Spacer(Modifier.height(20.dp))
                        MenuButton("Detección de objetos", {
                            startActivity(Intent(this@MainMenuActivity, ObjectDetectionActivity::class.java))
                        }, permissionsGranted)
                        Spacer(Modifier.height(20.dp))
                        MenuButton("Reconocimiento de Voz", {
                            startActivity(Intent(this@MainMenuActivity, WhisperActivity::class.java))
                        }, permissionsGranted)
                        Spacer(Modifier.height(20.dp))
                        MenuButton("Text-To-Speech", {
                            startActivity(Intent(this@MainMenuActivity, TTSActivity::class.java))
                        }, permissionsGranted)
                        Spacer(Modifier.height(20.dp))
                        MenuButton("Ejercicios", {
                            launchNextExercise(selectedLang)
                        }, permissionsGranted)
                        Spacer(Modifier.height(10.dp))

                        Text("Idioma a aprender:", fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))

                        LanguageSpinner(selectedLang) {
                            selectedLang = it
                            saveSelectedLanguage(it)
                        }
                        Spacer(Modifier.height(40.dp))

                        MenuButton("Verificar permisos", {
                            checkPermissions()
                        }, true)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_AUTO_NEXT, false)) {
            launchNextExercise("en")
        }
    }

    private fun saveSelectedLanguage(lang: String) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_LANG, lang)
            .apply()
    }

    private fun loadSelectedLanguage(): String {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_SELECTED_LANG, "en") ?: "en"
    }

    private fun launchNextExercise(targetLang: String) {
        Log.d("MainMenu", "▶ launchNextExercise() for device $userId with lang $targetLang")
        Toast.makeText(this, "Buscando siguiente ejercicio…", Toast.LENGTH_SHORT).show()

        val dbRef = FirebaseDatabase.getInstance()
            .getReference("users")
            .child(userId)
            .child("exercises")

        dbRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val done = snapshot.children.associate {
                    it.key!! to (it.child("completed").getValue(Boolean::class.java) ?: false)
                }
                Log.d("MainMenu", "Estado ejercicios: $done")

                val types = listOf("mcq", "write", "pronounce")
                var nextId: String? = null
                for (level in 1..3) {
                    types.shuffled().forEach { type ->
                        val candidate = "$type$level"
                        if (done[candidate] != true) {
                            nextId = candidate
                            return@forEach
                        }
                    }
                    if (nextId != null) break
                }
                if (nextId == null) {
                    val levels = listOf("1","2","3")
                    nextId = "${types.shuffled().first()}${levels.shuffled().first()}"
                }

                Log.d("MainMenu", "▶ Siguiente ejercicio: $nextId")

                val intent = when (nextId) {
                    "mcq1"       -> Intent(this@MainMenuActivity, MCQLevel1Activity::class.java)
                    "write1"     -> Intent(this@MainMenuActivity, WriteLevel1Activity::class.java)
                    "pronounce1" -> Intent(this@MainMenuActivity, PronounceLevel1Activity::class.java)
                    "mcq2"       -> Intent(this@MainMenuActivity, MCQLevel2Activity::class.java)
                    "write2"     -> Intent(this@MainMenuActivity, WriteLevel2Activity::class.java)
                    "pronounce2" -> Intent(this@MainMenuActivity, PronounceLevel2Activity::class.java)
                    "mcq3"       -> Intent(this@MainMenuActivity, MCQLevel3Activity::class.java)
                    "write3"     -> Intent(this@MainMenuActivity, WriteLevel3Activity::class.java)
                    "pronounce3" -> Intent(this@MainMenuActivity, PronounceLevel3Activity::class.java)
                    else         -> Intent(this@MainMenuActivity, MCQLevel1Activity::class.java)
                }
                Log.d("MainMenu", "Lanzando intent a $nextId con clase ${intent.component?.className}")
                intent.putExtra("targetLang", targetLang)
                startActivity(intent)
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainMenuActivity, "Error BD", Toast.LENGTH_SHORT).show()
                val fallbackIntent = Intent(this@MainMenuActivity, MCQLevel1Activity::class.java)
                fallbackIntent.putExtra("targetLang", targetLang)
                startActivity(fallbackIntent)
            }
        })
    }

    private fun checkPermissions() {
        val toRequest = PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        if (toRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(toRequest)
        } else {
            permissionsGranted = true
            showPermissionDialog = false
        }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null))
        )
    }
}

@Composable
fun MenuButton(text: String, onClick: () -> Unit, enabled: Boolean) {
    Button(onClick = onClick, modifier = Modifier.padding(horizontal = 40.dp), enabled = enabled) {
        Text(text, fontSize = 18.sp)
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
        text = { Text("La app necesita cámara y micrófono.") },
        confirmButton = { Button(onClick = onGoToSettings) { Text("Abrir Ajustes") } },
        dismissButton = { Button(onClick = onExitApp) { Text("Salir") } }
    )
}

@Composable
fun LanguageSpinner(selectedLang: String, onLangSelected: (String) -> Unit) {
    val context = LocalContext.current
    val languageCodes = remember { TranslateLanguage.getAllLanguages().toList() }

    val displayNames = remember(languageCodes) {
        languageCodes.map { code ->
            Locale(code).getDisplayName(Locale.getDefault()).ifEmpty { code }
        }
    }

    AndroidView(
        factory = { ctx ->
            Spinner(ctx).apply {
                adapter = ArrayAdapter(
                    ctx,
                    android.R.layout.simple_spinner_dropdown_item,
                    displayNames
                )

                val initialIndex = languageCodes.indexOf(selectedLang).coerceAtLeast(0)
                setSelection(initialIndex)

                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        onLangSelected(languageCodes[position])
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth(0.6f)
            .height(60.dp),
        update = { spinner ->
            val newIndex = languageCodes.indexOf(selectedLang)
            if (newIndex >= 0 && newIndex != spinner.selectedItemPosition) {
                spinner.setSelection(newIndex)
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun MainMenuPreview() {
    ProyectoFinalTheme {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MenuButton("Ejercicios", {}, enabled = true)
        }
    }
}