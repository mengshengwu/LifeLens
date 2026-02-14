package com.example.lifelens

import android.Manifest
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.lifelens.camera.takePhoto
import com.example.lifelens.nexa.ModelManager
import com.example.lifelens.nexa.NexaVlmClient
import com.example.lifelens.tool.*
import com.example.lifelens.ui.theme.LifeLensTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

enum class Phase { INTRO, SETUP, READY }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LifeLensTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                var phase by remember { mutableStateOf(Phase.INTRO) }

                // status
                var headline by remember { mutableStateOf("Welcome to LifeLens") }
                var detail by remember { mutableStateOf("One-tap setup, then point and understand.") }
                var progress by remember { mutableStateOf<Int?>(null) }

                // setup error
                var setupError by remember { mutableStateOf<String?>(null) }
                var setupRunning by remember { mutableStateOf(false) }

                // permissions
                var cameraGranted by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_GRANTED
                    )
                }
                val cameraPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted -> cameraGranted = granted }

                // emulator detect (keep your original logic)
                val isEmulator = remember {
                    Build.FINGERPRINT.contains("generic", true) ||
                            Build.FINGERPRINT.contains("emulator", true) ||
                            Build.MODEL.contains("google_sdk", true) ||
                            Build.MODEL.contains("Emulator", true) ||
                            Build.BRAND.contains("generic", true) ||
                            Build.DEVICE.contains("generic", true) ||
                            Build.PRODUCT.contains("sdk", true)
                }
                var pluginId by remember { mutableStateOf(if (isEmulator) "cpu" else "npu") }

                // camera
                val previewView = remember { PreviewView(context) }
                val imageCapture = remember { ImageCapture.Builder().build() }
                var cameraReady by remember { mutableStateOf(false) }

                // model
                val modelManager = remember { ModelManager(context) }
                val spec = remember { modelManager.models.first() }
                val modelPath by remember(spec.id) { mutableStateOf(modelManager.entryPath(spec)) }
                var modelReady by remember { mutableStateOf(false) }

                // nexa
                var nexaReady by remember { mutableStateOf(false) }
                var activeClient by remember { mutableStateOf<NexaVlmClient?>(null) }

                DisposableEffect(Unit) {
                    onDispose { scope.launch { runCatching { activeClient?.destroy() } } }
                }

                // tools (kept, but demo test won't use them)
                val knowledgeServer = remember { KnowledgeServer(context) }
                val safetyServer = remember { SafetyServer() }
                val actionServer = remember { ActionServer(context) }
                val toolRouter = remember { ToolRouter(knowledgeServer, safetyServer, actionServer) }

                var audience by remember { mutableStateOf(Audience.ELDERLY) }
                var rawOutput by remember { mutableStateOf("") }
                var detect by remember { mutableStateOf<VisionDetectResult?>(null) }
                var bundle by remember { mutableStateOf<ToolBundle?>(null) }

                // Upload photo (kept, but we won't call describeImage in demo mode)
                val uploadLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    scope.launch {
                        runCatching {
                            headline = "Not supported"
                            detail = "Demo mode: use Test button (text generation)."
                            progress = null
                        }.onFailure {
                            headline = "Failed"
                            detail = it.message ?: "Unknown error"
                        }
                    }
                }

                fun bindCamera() {
                    if (!cameraGranted) return
                    scope.launch {
                        runCatching {
                            val cameraProvider = ProcessCameraProvider.getInstance(context).get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                this@MainActivity,
                                androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture
                            )
                        }.onSuccess {
                            cameraReady = true
                        }.onFailure {
                            cameraReady = false
                            headline = "Camera not ready"
                            detail = "If emulator preview is black: Emulator → Settings → Camera → Back/Front = Webcam0."
                        }
                    }
                }

                // ✅ demo init: do not pass mmproj, do not do describeImage
                suspend fun createAndInitClient(pid: String): Result<NexaVlmClient> = withContext(Dispatchers.IO) {
                    runCatching {
                        val entryPath = modelManager.entryPath(spec)
                        val entry = File(entryPath)

                        Log.d("LifeLens", "createAndInitClient(pid=$pid)")
                        Log.d("LifeLens", "entryPath=$entryPath exists=${entry.exists()} isFile=${entry.isFile} len=${entry.length()}")

                        require(entry.exists() && entry.isFile && entry.length() > 0L) {
                            "Model entry file missing: ${entry.absolutePath}"
                        }

                        val c = NexaVlmClient(
                            context = context,
                            modelPath = entryPath
                        )
                        c.init()
                        c
                    }
                }

                fun startSetup() {
                    if (setupRunning) return
                    phase = Phase.SETUP
                    setupError = null
                    setupRunning = true

                    scope.launch {
                        try {
                            // 1) check model
                            headline = "Checking model..."
                            detail = "Looking for local model files."
                            progress = null

                            Log.d("LifeLens", "spec.id=${spec.id}")
                            Log.d("LifeLens", "modelDir=${modelManager.modelDir(spec).absolutePath}")
                            Log.d("LifeLens", "entryPath(modelPath)=$modelPath")

                            val entry = File(modelPath)
                            Log.d("LifeLens", "entry exists=${entry.exists()} isFile=${entry.isFile} len=${entry.length()}")

                            val missing = modelManager.missingFiles(spec)
                            modelReady = missing.isEmpty()

                            // 2) download if missing
                            if (!modelReady) {
                                headline = "Downloading model..."
                                detail = "This may take a while (large file). Keep the app open."
                                progress = 0

                                modelManager.downloadModel(spec).collect { p ->
                                    progress = p.overallPercent.coerceIn(0, 100)
                                    detail = "Downloading… ${progress}%  (${p.fileIndex}/${p.fileCount})"
                                }

                                val missingAfter = modelManager.missingFiles(spec)
                                modelReady = missingAfter.isEmpty()
                                if (!modelReady) error("Download incomplete. Missing: ${missingAfter.joinToString()}")
                            }

                            // 3) init (NO CPU fallback)
                            headline = "Initializing..."
                            detail = if (pluginId == "cpu") "Starting on CPU…" else "Starting on NPU…"
                            progress = null

                            val r1 = createAndInitClient(pluginId)
                            if (r1.isSuccess) {
                                runCatching { activeClient?.destroy() }
                                activeClient = r1.getOrNull()
                                nexaReady = true
                            } else {
                                val e1 = r1.exceptionOrNull()
                                Log.e("LifeLens", "Init failed for plugin=$pluginId", e1)
                                throw e1 ?: RuntimeException("Init failed (unknown)")
                            }

                            // 4) camera permission + bind (optional)
                            headline = "Almost ready"
                            detail = "We’ll ask for camera permission so you can capture."
                            progress = null

                            if (!cameraGranted) cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            bindCamera()

                            // 5) ready
                            phase = Phase.READY
                            headline = "Ready"
                            detail = "Tap Test to run the official demo prompt."
                            progress = null
                        } catch (t: Throwable) {
                            setupError = buildString {
                                append(t.message ?: "Unknown error")
                                val st = t.stackTraceToString().take(2000)
                                if (st.isNotBlank()) {
                                    append("\n\n")
                                    append(st)
                                }
                                if (isEmulator) {
                                    append("\n\nTip: Emulator often can't load a 4.9GB on-device model. Try a physical device.")
                                }
                            }
                            headline = "Setup failed"
                            detail = "See details below."
                            progress = null
                            phase = Phase.SETUP
                        } finally {
                            setupRunning = false
                        }
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    when (phase) {
                        Phase.INTRO -> {
                            IntroScreen(
                                title = "LifeLens",
                                subtitle = "Understand what you see.\nMade for Elderly & Kids.",
                                primaryText = if (setupRunning) "Starting…" else "Get Started",
                                secondaryText = "Upload a photo",
                                onPrimary = { startSetup() },
                                onSecondary = { uploadLauncher.launch("image/*") }
                            )
                        }

                        Phase.SETUP -> {
                            SetupScreen(
                                headline = headline,
                                detail = detail,
                                progress = progress,
                                errorText = setupError,
                                running = setupRunning,
                                onRetry = { startSetup() },
                                onBack = {
                                    if (!setupRunning) {
                                        phase = Phase.INTRO
                                        headline = "Welcome to LifeLens"
                                        detail = "One-tap setup, then point and understand."
                                        progress = null
                                        setupError = null
                                    }
                                }
                            )
                        }

                        Phase.READY -> {
                            ReadyScreen(
                                previewView = previewView,
                                cameraGranted = cameraGranted,
                                cameraReady = cameraReady,
                                onRequestCamera = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                onBindCamera = { bindCamera() },
                                audience = audience,
                                onAudienceChange = { audience = it },
                                onUpload = { uploadLauncher.launch("image/*") },
                                onCapture = {
                                    scope.launch {
                                        headline = "Not supported"
                                        detail = "Demo mode: use Test button (text generation)."
                                    }
                                },
                                onTestText = {
                                    scope.launch {
                                        runCatching {
                                            val client = activeClient ?: error("Model not initialized. Tap Get Started first.")
                                            headline = "Generating..."
                                            detail = "Who are you?"
                                            progress = null

                                            val out = client.generate("Who are you?")
                                            rawOutput = out

                                            headline = "Done"
                                            detail = "Text generation finished."
                                        }.onFailure {
                                            headline = "Failed"
                                            detail = it.message ?: "Unknown error"
                                        }
                                    }
                                },
                                headline = headline,
                                detail = detail,
                                rawOutput = rawOutput,
                                detect = detect,
                                bundle = bundle,
                                actionServer = actionServer
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------- UI Composables ----------------

@Composable
private fun IntroScreen(
    title: String,
    subtitle: String,
    primaryText: String,
    secondaryText: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Spacer(Modifier.height(28.dp))

            Button(
                onClick = onPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text(primaryText) }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text(secondaryText) }

            Spacer(Modifier.height(16.dp))
            Text(
                "Tip: The first run downloads the on-device model.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

@Composable
private fun SetupScreen(
    headline: String,
    detail: String,
    progress: Int?,
    errorText: String?,
    running: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(headline, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Text(detail, style = MaterialTheme.typography.bodyMedium)

                    Spacer(Modifier.height(14.dp))
                    if (progress != null) {
                        LinearProgressIndicator(progress = { progress / 100f })
                        Spacer(Modifier.height(8.dp))
                        Text("$progress%", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LinearProgressIndicator()
                    }

                    if (errorText != null) {
                        Spacer(Modifier.height(14.dp))
                        Divider()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Error details",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            errorText,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(14.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onRetry,
                                enabled = !running,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) { Text("Retry") }

                            OutlinedButton(
                                onClick = onBack,
                                enabled = !running,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) { Text("Back") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadyScreen(
    previewView: PreviewView,
    cameraGranted: Boolean,
    cameraReady: Boolean,
    onRequestCamera: () -> Unit,
    onBindCamera: () -> Unit,
    audience: Audience,
    onAudienceChange: (Audience) -> Unit,
    onUpload: () -> Unit,
    onCapture: () -> Unit,
    onTestText: () -> Unit,
    headline: String,
    detail: String,
    rawOutput: String,
    detect: VisionDetectResult?,
    bundle: ToolBundle?,
    actionServer: ActionServer
) {
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("LifeLens", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            FilterChip(
                selected = audience == Audience.ELDERLY,
                onClick = { onAudienceChange(Audience.ELDERLY) },
                label = { Text("Elderly") }
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = audience == Audience.CHILD,
                onClick = { onAudienceChange(Audience.CHILD) },
                label = { Text("Child") }
            )
        }

        Spacer(Modifier.height(12.dp))

        Card(shape = RoundedCornerShape(20.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                if (!cameraGranted) {
                    OverlayHint(
                        title = "Camera permission needed",
                        subtitle = "You can still upload a photo without camera.",
                        primary = "Grant",
                        onPrimary = onRequestCamera
                    )
                } else if (!cameraReady) {
                    OverlayHint(
                        title = "Camera not ready",
                        subtitle = "Try: Emulator → Settings → Camera → Webcam0",
                        primary = "Retry",
                        onPrimary = onBindCamera
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onCapture,
                enabled = cameraGranted && cameraReady,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Capture") }

            OutlinedButton(
                onClick = onUpload,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Upload Photo") }
        }

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = onTestText,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text("Test: Who are you?") }

        Spacer(Modifier.height(14.dp))

        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text(headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(detail, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (rawOutput.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Raw output", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(rawOutput, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun OverlayHint(
    title: String,
    subtitle: String,
    primary: String,
    onPrimary: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(shape = RoundedCornerShape(18.dp)) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onPrimary,
                    shape = RoundedCornerShape(14.dp)
                ) { Text(primary) }
            }
        }
    }
}

// ---------------- helpers ----------------

private suspend fun copyUriToFile(resolver: ContentResolver, uri: Uri, outFile: File) {
    withContext(Dispatchers.IO) {
        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output ->
                val buf = ByteArray(1024 * 256)
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    output.write(buf, 0, read)
                }
            }
        } ?: throw IllegalStateException("Cannot open input stream for uri=$uri")
    }
}

// ---------- JSON parsing utilities ----------
private val json = Json { ignoreUnknownKeys = true }

private fun extractFirstJsonObject(text: String): String? {
    val start = text.indexOf('{')
    if (start < 0) return null
    var depth = 0
    for (i in start until text.length) {
        when (text[i]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return text.substring(start, i + 1)
            }
        }
    }
    return null
}

private fun parseVisionDetectResult(raw: String): VisionDetectResult {
    val jsonObj = extractFirstJsonObject(raw)
        ?: return VisionDetectResult(
            label = "unknown",
            category = "other",
            confidence = 0.0,
            hazards = listOf("Model output not JSON")
        )

    return try {
        json.decodeFromString(VisionDetectResult.serializer(), jsonObj)
    } catch (e: SerializationException) {
        VisionDetectResult(
            label = "unknown",
            category = "other",
            confidence = 0.0,
            hazards = listOf("JSON parse failed: ${e.message}")
        )
    }
}
