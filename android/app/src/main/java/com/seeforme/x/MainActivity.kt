package com.seeforme.x

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.camera.core.CameraSelector
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private val BrandBlue = Color(0xFF155EEF)
private val PageBackground = Color(0xFFF6F8FC)
private val Ink = Color(0xFF172033)
private val Muted = Color(0xFF515B6B)

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale.forLanguageTag("en-IN")
        }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = BrandBlue,
                onPrimary = Color.White,
                background = PageBackground,
                surface = Color.White,
                onSurface = Ink,
            )) {
                Surface(modifier = Modifier.fillMaxSize(), color = PageBackground) {
                    SeeForMeScreen(
                        onSpeak = { text, languageTag ->
                            if (ttsReady) {
                                tts?.language = Locale.forLanguageTag(languageTag)
                                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "seeforme-answer")
                            }
                        },
                        onStopSpeech = { tts?.stop() },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}

private enum class VisionMode(val apiValue: String, val title: String) {
    DESCRIBE("describe", "Describe"),
    READ_TEXT("read_text", "Read text"),
    ASK("ask", "Ask AI"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeeForMeScreen(
    onSpeak: (String, String) -> Unit,
    onStopSpeech: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var capturedFile by remember { mutableStateOf<File?>(null) }
    var selectedMode by remember { mutableStateOf(VisionMode.DESCRIBE) }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready. Capture a photo to get started.") }
    var busy by remember { mutableStateOf(false) }
    var languageTag by remember { mutableStateOf("en-IN") }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        status = if (granted) "Camera ready. Capture a photo." else "Camera permission is needed to capture a scene."
    }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { spoken ->
                question = spoken
                selectedMode = VisionMode.ASK
                status = "Question captured. Capture a scene if you have not already, then analyze."
            }
        }
    }

    DisposableEffect(capturedFile) {
        onDispose { capturedFile?.delete() }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("SeeForMe X", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.semantics { heading() })
            Text("Your voice-first visual copilot", fontSize = 16.sp, color = Muted)
        }

        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.Black), elevation = CardDefaults.cardElevation(2.dp)) {
            Box(modifier = Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
                if (cameraGranted) {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        onCameraError = { status = it },
                        onCaptured = { file ->
                            capturedFile?.delete()
                            capturedFile = file
                            answer = ""
                            status = "Photo captured. Choose Describe, Read text, or Ask AI."
                        },
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(20.dp)) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
                        Text("Camera preview", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("Allow camera access to capture a scene. Nothing is analyzed until you choose Analyze.", color = Color.White, fontSize = 14.sp)
                        Button(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.height(52.dp)) {
                            Text("Enable camera")
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (!cameraGranted) cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    else {
                        capturedFile?.delete()
                        capturedFile = null
                        answer = ""
                        status = "Ready for a new photo. Use Capture photo in the camera preview."
                    }
                },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (cameraGranted) "Retake photo" else "Enable camera", fontSize = 15.sp)
            }
            OutlinedButton(
                onClick = {
                    languageTag = if (languageTag == "en-IN") "hi-IN" else "en-IN"
                    status = if (languageTag == "hi-IN") "Hindi selected for speech input and output." else "English selected for speech input and output."
                },
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text(if (languageTag == "en-IN") "English" else "हिन्दी", fontSize = 15.sp) }
        }

        Text("What do you want help with?", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.semantics { heading() })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            VisionMode.values().forEach { mode ->
                if (selectedMode == mode) {
                    Button(onClick = { selectedMode = mode }, modifier = Modifier.weight(1f).height(52.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp), shape = RoundedCornerShape(14.dp)) {
                        Text(mode.title, fontSize = 14.sp)
                    }
                } else {
                    OutlinedButton(onClick = { selectedMode = mode }, modifier = Modifier.weight(1f).height(52.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp), shape = RoundedCornerShape(14.dp)) {
                        Text(mode.title, fontSize = 14.sp)
                    }
                }
            }
        }

        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            label = { Text("Ask a question (optional)") },
            placeholder = { Text("e.g. What does this sign say?") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(16.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, if (languageTag == "hi-IN") "अपना सवाल बोलें" else "Ask a question")
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    }
                    try { speechLauncher.launch(intent) }
                    catch (_: ActivityNotFoundException) { status = "Speech recognition is not available on this device." }
                },
                modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Speak a question")
                Spacer(Modifier.size(8.dp)); Text("Speak question")
            }
            OutlinedButton(onClick = onStopSpeech, modifier = Modifier.height(54.dp), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.Stop, contentDescription = "Stop spoken answer")
                Spacer(Modifier.size(6.dp)); Text("Stop")
            }
        }

        Button(
            onClick = {
                val image = capturedFile
                if (image == null || !image.exists()) {
                    status = "Capture a photo first."
                } else if (selectedMode == VisionMode.ASK && question.isBlank()) {
                    status = "Enter or speak a question first."
                } else {
                    busy = true
                    answer = ""
                    status = "Analyzing the selected photo…"
                    scope.launch {
                        try {
                            val result = AnalyzeClient.analyze(image, selectedMode.apiValue, question, languageTag)
                            answer = result
                            status = "Analysis complete. Review the answer and listen if helpful."
                            onSpeak(result, languageTag)
                        } catch (error: Exception) {
                            status = error.message?.takeIf { it.isNotBlank() } ?: "Could not analyze the photo. Check your connection and try again."
                        } finally {
                            busy = false
                        }
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            else Text("Analyze captured photo", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }

        Text("Status: $status", fontSize = 14.sp, color = Muted, modifier = Modifier.semantics { })

        if (answer.isNotBlank()) {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("AI response", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Ink, modifier = Modifier.semantics { heading() })
                    Text(answer, fontSize = 17.sp, lineHeight = 25.sp, color = Ink)
                    Text("AI can be wrong. Do not rely on this response for safe navigation or collision avoidance.", fontSize = 13.sp, color = Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { onSpeak(answer, languageTag) }, modifier = Modifier.height(52.dp), shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "Read answer aloud")
                            Spacer(Modifier.size(8.dp)); Text("Listen again")
                        }
                        OutlinedButton(onClick = onStopSpeech, modifier = Modifier.height(52.dp), shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop speech")
                            Spacer(Modifier.size(6.dp)); Text("Stop")
                        }
                    }
                }
            }
        }

        Text("Privacy: a photo is sent to the configured AI service only when you press Analyze. This prototype does not save images to a server. Avoid sending sensitive documents unless you understand the provider's data policies.", fontSize = 13.sp, color = Muted)
        Text("Safety: this prototype can make mistakes. It is not a substitute for a cane, guide dog, trusted mobility aid, or trained orientation and mobility guidance.", fontSize = 13.sp, color = Muted)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    onCameraError: (String) -> Unit,
    onCaptured: (File) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(context) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }
    LaunchedEffect(controller, lifecycleOwner) {
        try { controller.bindToLifecycle(lifecycleOwner) }
        catch (_: Exception) { onCameraError("Could not start the camera. Close other camera apps and retry.") }
    }
    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext -> PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                this.controller = controller
            } },
            update = { it.controller = controller },
        )
        Button(
            onClick = {
                val target = File(context.cacheDir, "seeforme_capture_${System.currentTimeMillis()}.jpg")
                val output = androidx.camera.core.ImageCapture.OutputFileOptions.Builder(target).build()
                controller.takePicture(output, ContextCompat.getMainExecutor(context), object : androidx.camera.core.ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: androidx.camera.core.ImageCapture.OutputFileResults) {
                        onCaptured(target)
                    }
                    override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                        target.delete()
                        onCameraError("Photo capture failed. Try again.")
                    }
                })
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Capture photo", fontSize = 16.sp)
        }
    }
}
