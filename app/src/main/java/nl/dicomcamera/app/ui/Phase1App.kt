package nl.dicomcamera.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.dicomcamera.app.session.ManualPatientForm
import nl.dicomcamera.app.settings.PacsSettings
import nl.dicomcamera.app.settings.SettingsRepository
import nl.dicomcamera.dicom.EchoResult
import nl.dicomcamera.dicom.PacsClient
import nl.dicomcamera.dicom.PatientStudyContext
import nl.dicomcamera.dicom.PendingStoreQueue
import nl.dicomcamera.dicom.PhotographicImageEncoder
import nl.dicomcamera.dicom.SecureStaging
import nl.dicomcamera.dicom.StoreResult
import nl.dicomcamera.dicom.WipeResult
import java.io.ByteArrayOutputStream
import java.io.File

private const val TAG = "Phase1App"

private enum class Destination {
    Patient,
    Settings,
    Capture,
    Review,
    Sending,
    Result,
    Pending,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Phase1App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository(context) }
    val pacsSettings by settingsRepo.settings.collectAsState(initial = PacsSettings())

    val staging = remember {
        SecureStaging(File(context.filesDir, "staging").also { it.mkdirs() })
    }
    val pendingQueue = remember {
        PendingStoreQueue(File(context.filesDir, "pending"), staging)
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { staging.purgeOrphans() }
    }

    var destination by remember { mutableStateOf(Destination.Patient) }
    var patient by remember { mutableStateOf(ManualPatientForm()) }
    var reviewFile by remember { mutableStateOf<File?>(null) }
    var resultMessage by remember { mutableStateOf("") }
    var resultSuccess by remember { mutableStateOf(false) }
    var statusNote by remember { mutableStateOf("") }
    var pendingItems by remember { mutableStateOf(pendingQueue.list()) }

    fun refreshPending() {
        pendingItems = pendingQueue.list()
    }

    fun goBack() {
        destination = when (destination) {
            Destination.Settings, Destination.Pending, Destination.Capture -> Destination.Patient
            Destination.Review -> Destination.Capture
            Destination.Sending, Destination.Result -> Destination.Patient
            Destination.Patient -> Destination.Patient
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (destination) {
                            Destination.Patient -> "DICOM Camera"
                            Destination.Settings -> "PACS settings"
                            Destination.Capture -> "Capture"
                            Destination.Review -> "Review"
                            Destination.Sending -> "Sending"
                            Destination.Result -> "Result"
                            Destination.Pending -> "Pending uploads"
                        },
                    )
                },
                navigationIcon = {
                    if (destination != Destination.Patient) {
                        IconButton(onClick = { goBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (destination == Destination.Patient) {
                        IconButton(onClick = { destination = Destination.Settings }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            when (destination) {
                Destination.Patient -> PatientScreen(
                    patient = patient,
                    onPatientChange = { patient = it },
                    pacsConfigured = pacsSettings.isConfigured(),
                    pendingCount = pendingItems.size,
                    statusNote = statusNote,
                    onOpenPending = {
                        refreshPending()
                        destination = Destination.Pending
                    },
                    onContinue = {
                        when {
                            !patient.isValid() -> statusNote = "Patient ID and Name are required"
                            !pacsSettings.isConfigured() -> {
                                statusNote = "Configure PACS settings first"
                                destination = Destination.Settings
                            }
                            else -> {
                                statusNote = ""
                                destination = Destination.Capture
                            }
                        }
                    },
                )

                Destination.Settings -> SettingsScreen(
                    initial = pacsSettings,
                    onSave = { updated ->
                        scope.launch {
                            settingsRepo.save(updated)
                            statusNote = "Settings saved"
                            destination = Destination.Patient
                        }
                    },
                    onEcho = { draft ->
                        scope.launch {
                            statusNote = "C-ECHO..."
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    PacsClient(draft.toNode()).use { it.echo() }
                                }.getOrElse { EchoResult.Failed(it.message ?: "echo failed", it) }
                            }
                            statusNote = when (result) {
                                EchoResult.Success -> "C-ECHO OK"
                                is EchoResult.Failed -> "C-ECHO failed: ${result.message}"
                            }
                        }
                    },
                    echoStatus = statusNote,
                )

                Destination.Capture -> CaptureScreen(
                    patientBanner = "${patient.patientId} · ${patient.patientName}",
                    onCaptured = { file ->
                        reviewFile = file
                        destination = Destination.Review
                    },
                    staging = staging,
                )

                Destination.Review -> {
                    val file = reviewFile
                    if (file == null) {
                        destination = Destination.Capture
                    } else {
                        ReviewScreen(
                            jpegFile = file,
                            patientBanner = "${patient.patientId} · ${patient.patientName}",
                            onRetake = {
                                staging.wipe(file)
                                reviewFile = null
                                destination = Destination.Capture
                            },
                            onSend = {
                                destination = Destination.Sending
                                scope.launch {
                                    val outcome = withContext(Dispatchers.IO) {
                                        encodeStoreOrQueue(
                                            staging = staging,
                                            pendingQueue = pendingQueue,
                                            rawJpeg = file,
                                            patient = patient,
                                            settings = pacsSettings,
                                        )
                                    }
                                    reviewFile = null
                                    refreshPending()
                                    resultSuccess = outcome.success
                                    resultMessage = outcome.message
                                    destination = Destination.Result
                                }
                            },
                        )
                    }
                }

                Destination.Sending -> SendingScreen()

                Destination.Result -> ResultScreen(
                    message = resultMessage,
                    success = resultSuccess,
                    pendingCount = pendingItems.size,
                    onDone = { destination = Destination.Patient },
                    onPending = {
                        refreshPending()
                        destination = Destination.Pending
                    },
                )

                Destination.Pending -> PendingScreen(
                    items = pendingItems,
                    onRetry = { item ->
                        scope.launch {
                            statusNote = "Retrying..."
                            val result = withContext(Dispatchers.IO) {
                                PacsClient(pacsSettings.toNode()).use { it.store(item.dicomFile) }
                            }
                            when (result) {
                                is StoreResult.Success -> {
                                    pendingQueue.markStoredAndWipe(item.id)
                                    refreshPending()
                                    statusNote = "Stored ${result.sopInstanceUid}"
                                }
                                is StoreResult.Failed -> {
                                    statusNote = "Retry failed: ${result.message}"
                                }
                            }
                        }
                    },
                    onDiscard = { item ->
                        pendingQueue.discard(item.id)
                        refreshPending()
                    },
                    statusNote = statusNote,
                )
            }
        }
    }
}

@Composable
private fun SendingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = "Encoding and C-STORE to PACS...",
            modifier = Modifier.padding(top = 16.dp),
        )
        LinearProgressIndicator(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth(0.6f),
        )
    }
}

@Composable
private fun PatientScreen(
    patient: ManualPatientForm,
    onPatientChange: (ManualPatientForm) -> Unit,
    pacsConfigured: Boolean,
    pendingCount: Int,
    statusNote: String,
    onOpenPending: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Phase 1 — manual patient → photo → PACS → wipe",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!pacsConfigured) {
            Text(
                text = "PACS not configured yet. Open settings (gear) first.",
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        OutlinedTextField(
            value = patient.patientId,
            onValueChange = { onPatientChange(patient.copy(patientId = it)) },
            label = { Text("Patient ID *") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = patient.patientName,
            onValueChange = { onPatientChange(patient.copy(patientName = it)) },
            label = { Text("Patient Name * (FAMILY^GIVEN)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = patient.birthDate,
            onValueChange = { onPatientChange(patient.copy(birthDate = it.filter { ch -> ch.isDigit() }.take(8))) },
            label = { Text("Birth date (YYYYMMDD)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Text(text = "Sex", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("" to "-", "M" to "M", "F" to "F", "O" to "O").forEach { (value, label) ->
                Row(
                    Modifier.selectable(
                        selected = patient.sex == value,
                        onClick = { onPatientChange(patient.copy(sex = value)) },
                        role = Role.RadioButton,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = patient.sex == value, onClick = null)
                    Text(text = label, modifier = Modifier.padding(end = 8.dp))
                }
            }
        }
        OutlinedTextField(
            value = patient.accessionNumber,
            onValueChange = { onPatientChange(patient.copy(accessionNumber = it)) },
            label = { Text("Accession (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = patient.studyDescription,
            onValueChange = { onPatientChange(patient.copy(studyDescription = it)) },
            label = { Text("Study description (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text("Continue to camera")
        }
        if (pendingCount > 0) {
            OutlinedButton(onClick = onOpenPending, modifier = Modifier.fillMaxWidth()) {
                Text("Pending uploads ($pendingCount)")
            }
        }
        if (statusNote.isNotBlank()) {
            Text(statusNote)
        }
    }
}

@Composable
private fun SettingsScreen(
    initial: PacsSettings,
    onSave: (PacsSettings) -> Unit,
    onEcho: (PacsSettings) -> Unit,
    echoStatus: String,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = draft.host,
            onValueChange = { draft = draft.copy(host = it) },
            label = { Text("PACS host") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = draft.port.toString(),
            onValueChange = { text ->
                draft = draft.copy(port = text.filter { it.isDigit() }.toIntOrNull() ?: draft.port)
            },
            label = { Text("PACS port") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = draft.calledAeTitle,
            onValueChange = { draft = draft.copy(calledAeTitle = it) },
            label = { Text("Called AE Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = draft.callingAeTitle,
            onValueChange = { draft = draft.copy(callingAeTitle = it) },
            label = { Text("Calling AE Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Text(text = "DICOM TLS", style = MaterialTheme.typography.labelLarge)
        Text(
            text = "Uses system trust store; hospital CA install via MDM later.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (draft.useTls) "TLS enabled" else "TLS disabled")
            Switch(checked = draft.useTls, onCheckedChange = { draft = draft.copy(useTls = it) })
        }
        OutlinedButton(onClick = { onEcho(draft) }, modifier = Modifier.fillMaxWidth()) {
            Text("Test C-ECHO")
        }
        Button(onClick = { onSave(draft) }, modifier = Modifier.fillMaxWidth()) {
            Text("Save settings")
        }
        if (echoStatus.isNotBlank()) {
            Text(echoStatus)
        }
    }
}

@Composable
private fun CaptureScreen(
    patientBanner: String,
    onCaptured: (File) -> Unit,
    staging: SecureStaging,
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = patientBanner,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(text = "Confirm patient before capturing.", style = MaterialTheme.typography.bodySmall)

        if (!hasCameraPermission) {
            Button(
                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Grant camera permission")
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
            ) {
                CameraPreview(onImageCaptureReady = { imageCapture = it })
            }
            Button(
                onClick = {
                    val capture = imageCapture
                    if (capture == null) {
                        error = "Camera not ready"
                        return@Button
                    }
                    error = null
                    val rawFile = staging.createStagingFile("raw", "jpg")
                    capture.takePicture(
                        ImageCapture.OutputFileOptions.Builder(rawFile).build(),
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                onCaptured(rawFile)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                error = exception.message
                                staging.wipe(rawFile)
                            }
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Take photo")
            }
        }
        error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun ReviewScreen(
    jpegFile: File,
    patientBanner: String,
    onRetake: () -> Unit,
    onSend: () -> Unit,
) {
    val bitmap = remember(jpegFile) {
        BitmapFactory.decodeFile(jpegFile.absolutePath)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = patientBanner,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Captured photo",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text("Could not preview image")
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(onClick = onRetake, modifier = Modifier.fillMaxWidth(0.5f)) {
                Text("Retake")
            }
            Button(onClick = onSend, modifier = Modifier.fillMaxWidth()) {
                Text("Send to PACS")
            }
        }
    }
}

@Composable
private fun ResultScreen(
    message: String,
    success: Boolean,
    pendingCount: Int,
    onDone: () -> Unit,
    onPending: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (success) "Stored and wiped locally" else "Not stored — kept in pending queue",
            style = MaterialTheme.typography.titleLarge,
            color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
        )
        Text(message)
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
        if (!success || pendingCount > 0) {
            OutlinedButton(onClick = onPending, modifier = Modifier.fillMaxWidth()) {
                Text("View pending ($pendingCount)")
            }
        }
    }
}

@Composable
private fun PendingScreen(
    items: List<PendingStoreQueue.PendingItem>,
    onRetry: (PendingStoreQueue.PendingItem) -> Unit,
    onDiscard: (PendingStoreQueue.PendingItem) -> Unit,
    statusNote: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (items.isEmpty()) {
            Text("No pending uploads.")
        }
        items.forEach { item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "${item.patientId} · ${item.patientName}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(text = item.lastError, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onRetry(item) }) { Text("Retry") }
                    OutlinedButton(onClick = { onDiscard(item) }) { Text("Discard") }
                }
            }
        }
        if (statusNote.isNotBlank()) {
            Text(statusNote)
        }
    }
}

@Composable
private fun CameraPreview(onImageCaptureReady: (ImageCapture) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
            )
            onImageCaptureReady(imageCapture)
        }
        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

private data class StoreOutcome(val success: Boolean, val message: String)

private fun encodeStoreOrQueue(
    staging: SecureStaging,
    pendingQueue: PendingStoreQueue,
    rawJpeg: File,
    patient: ManualPatientForm,
    settings: PacsSettings,
): StoreOutcome {
    val dicomFile = staging.createStagingFile("vl", "dcm")
    return try {
        val jpegBytes = rawJpeg.readBytes()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, bounds)
        var rows = bounds.outHeight
        var columns = bounds.outWidth
        var bytes = jpegBytes
        if (rows <= 0 || columns <= 0) {
            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            if (bitmap == null) {
                staging.wipe(dicomFile)
                staging.wipe(rawJpeg)
                return StoreOutcome(false, "Failed to decode JPEG")
            }
            rows = bitmap.height
            columns = bitmap.width
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            bytes = stream.toByteArray()
            bitmap.recycle()
        }

        PhotographicImageEncoder().encodeJpegToFile(
            jpegBytes = bytes,
            context = PatientStudyContext(
                patientId = patient.patientId.trim(),
                patientName = patient.normalizedName(),
                patientBirthDate = patient.birthDate.takeIf { it.length == 8 },
                patientSex = patient.sex.takeIf { it.isNotBlank() },
                accessionNumber = patient.accessionNumber.takeIf { it.isNotBlank() },
                studyDescription = patient.studyDescription.takeIf { it.isNotBlank() },
                modality = "XC",
                seriesDescription = "Clinical photograph",
            ),
            rows = rows,
            columns = columns,
            outputFile = dicomFile,
        )

        PacsClient(settings.toNode()).use { client ->
            when (val store = client.store(dicomFile)) {
                is StoreResult.Success -> {
                    val wipeDicom = staging.wipe(dicomFile)
                    val wipeRaw = staging.wipe(rawJpeg)
                    val wipeOk = wipeDicom is WipeResult.Wiped && wipeRaw is WipeResult.Wiped
                    StoreOutcome(
                        success = true,
                        message = if (wipeOk) {
                            "Stored ${store.sopInstanceUid}. Local files wiped."
                        } else {
                            "Stored ${store.sopInstanceUid}, but wipe incomplete: $wipeDicom / $wipeRaw"
                        },
                    )
                }
                is StoreResult.Failed -> {
                    pendingQueue.enqueue(
                        dicomFile = dicomFile,
                        rawFile = rawJpeg,
                        patientId = patient.patientId,
                        patientName = patient.patientName,
                        error = store.message,
                    )
                    StoreOutcome(false, "C-STORE failed: ${store.message}. Kept in pending queue.")
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "encodeStoreOrQueue failed", e)
        if (dicomFile.exists() && dicomFile.length() > 0) {
            pendingQueue.enqueue(
                dicomFile = dicomFile,
                rawFile = rawJpeg.takeIf { it.exists() },
                patientId = patient.patientId,
                patientName = patient.patientName,
                error = e.message ?: e.javaClass.simpleName,
            )
        } else {
            staging.wipe(dicomFile)
            staging.wipe(rawJpeg)
        }
        StoreOutcome(false, "Failed: ${e.message}")
    }
}
