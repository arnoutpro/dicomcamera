package nl.dicomcamera.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.dicomcamera.app.session.CaptureKind
import nl.dicomcamera.app.session.CaptureSession
import nl.dicomcamera.app.session.ExamSelection
import nl.dicomcamera.app.session.ExamSource
import nl.dicomcamera.app.session.ManualPatientForm
import nl.dicomcamera.app.session.SessionBatchSender
import nl.dicomcamera.app.session.SessionItem
import nl.dicomcamera.app.session.SessionItemStatus
import nl.dicomcamera.app.settings.PacsSettings
import nl.dicomcamera.app.settings.SettingsRepository
import nl.dicomcamera.dicom.AtnaAuditExporter
import nl.dicomcamera.dicom.AuditLog
import nl.dicomcamera.dicom.BatchStore
import nl.dicomcamera.dicom.EchoResult
import nl.dicomcamera.dicom.PacsGateway
import nl.dicomcamera.dicom.PatientStudyContext
import nl.dicomcamera.dicom.PendingStoreQueue
import nl.dicomcamera.dicom.SecureStaging
import nl.dicomcamera.dicom.StoreResult
import nl.dicomcamera.dicom.StudyEntry
import nl.dicomcamera.dicom.TransportMode
import nl.dicomcamera.dicom.WorklistEntry
import java.io.File

private const val TAG = "Phase4App"

private enum class Destination {
    Patient,
    Settings,
    Worklist,
    AppendStudy,
    Capture,
    Sending,
    Result,
    Pending,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Phase4App() {
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
    val audit = remember { AuditLog(File(context.filesDir, "audit/audit.csv")) }
    val atnaExporter = remember {
        AtnaAuditExporter(File(context.filesDir, "audit/atna"), aet = "DICOMCAM")
    }
    val batchSender = remember { SessionBatchSender(staging, pendingQueue, audit) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { staging.purgeOrphans() }
    }

    var destination by remember { mutableStateOf(Destination.Patient) }
    var patient by remember { mutableStateOf(ManualPatientForm()) }
    var exam by remember { mutableStateOf<ExamSelection?>(null) }
    var session by remember { mutableStateOf(CaptureSession()) }
    var resultMessage by remember { mutableStateOf("") }
    var resultSuccess by remember { mutableStateOf(false) }
    var statusNote by remember { mutableStateOf("") }
    var sendProgress by remember { mutableStateOf("") }
    var pendingItems by remember { mutableStateOf(pendingQueue.list()) }

    fun refreshPending() {
        pendingItems = pendingQueue.list()
    }

    fun startNewSession(selection: ExamSelection) {
        exam = selection
        session = CaptureSession(
            studyInstanceUid = selection.context.studyInstanceUid
                ?.takeIf { it.isNotBlank() }
                ?: nl.dicomcamera.dicom.DicomUid.newUid(),
        )
    }

    fun goBack() {
        destination = when (destination) {
            Destination.Settings, Destination.Pending, Destination.Capture,
            Destination.Worklist, Destination.AppendStudy -> Destination.Patient
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
                            Destination.Worklist -> "Modality worklist"
                            Destination.AppendStudy -> "Append to study"
                            Destination.Capture -> "Session capture"
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
                    selectedBanner = exam?.banner,
                    onOpenPending = {
                        refreshPending()
                        destination = Destination.Pending
                    },
                    onOpenWorklist = {
                        if (!pacsSettings.isConfigured()) {
                            statusNote = "Configure PACS settings first"
                            destination = Destination.Settings
                        } else {
                            destination = Destination.Worklist
                        }
                    },
                    onOpenAppend = {
                        if (!pacsSettings.isConfigured()) {
                            statusNote = "Configure PACS settings first"
                            destination = Destination.Settings
                        } else {
                            destination = Destination.AppendStudy
                        }
                    },
                    onContinueManual = {
                        when {
                            !patient.isValid() -> statusNote = "Patient ID and Name are required"
                            !pacsSettings.isConfigured() -> {
                                statusNote = "Configure PACS settings first"
                                destination = Destination.Settings
                            }
                            else -> {
                                statusNote = ""
                                startNewSession(
                                    ExamSelection(
                                        context = PatientStudyContext(
                                            patientId = patient.patientId.trim(),
                                            patientName = patient.normalizedName(),
                                            patientBirthDate = patient.birthDate.takeIf { it.length == 8 },
                                            patientSex = patient.sex.takeIf { it.isNotBlank() },
                                            accessionNumber = patient.accessionNumber.takeIf { it.isNotBlank() },
                                            studyDescription = patient.studyDescription.takeIf { it.isNotBlank() },
                                            modality = "XC",
                                            seriesDescription = "Clinical photo/video session",
                                            bodyPartExamined = patient.bodyPartExamined.takeIf { it.isNotBlank() },
                                            laterality = patient.laterality.takeIf { it.isNotBlank() },
                                        ),
                                        source = ExamSource.MANUAL,
                                    ),
                                )
                                audit.record("select_manual", patientId = patient.patientId)
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
                            statusNote = if (updated.managedByMdm) {
                                "MDM managed — local save skipped"
                            } else {
                                "Settings saved"
                            }
                            destination = Destination.Patient
                        }
                    },
                    onEcho = { draft ->
                        scope.launch {
                            statusNote = when (draft.transportMode) {
                                TransportMode.DIMSE -> "C-ECHO..."
                                TransportMode.DICOMWEB -> "DICOMweb ping..."
                            }
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    PacsGateway.fromEndpoint(draft.toEndpoint()).ping()
                                }.getOrElse { EchoResult.Failed(it.message ?: "ping failed", it) }
                            }
                            statusNote = when (result) {
                                EchoResult.Success -> when (draft.transportMode) {
                                    TransportMode.DIMSE -> "C-ECHO OK"
                                    TransportMode.DICOMWEB -> "DICOMweb reachable"
                                }
                                is EchoResult.Failed -> "Connectivity failed: ${result.message}"
                            }
                        }
                    },
                    onExportAtna = {
                        scope.launch {
                            val exported = withContext(Dispatchers.IO) {
                                atnaExporter.exportFromCsv(File(context.filesDir, "audit/audit.csv"))
                            }
                            statusNote = "ATNA export: ${exported.eventCount} events → ${exported.file.name}"
                        }
                    },
                    echoStatus = statusNote,
                )

                Destination.Worklist -> WorklistScreen(
                    endpoint = pacsSettings.toEndpoint(),
                    callingAeTitle = pacsSettings.callingAeTitle,
                    onSelected = { entry: WorklistEntry ->
                        val ctx = entry.toPatientStudyContext("Clinical photo/video session").copy(
                            bodyPartExamined = patient.bodyPartExamined.takeIf { it.isNotBlank() },
                            laterality = patient.laterality.takeIf { it.isNotBlank() },
                        )
                        startNewSession(ExamSelection(ctx, ExamSource.WORKLIST))
                        patient = ManualPatientForm(
                            patientId = entry.patientId,
                            patientName = entry.patientName,
                            birthDate = entry.patientBirthDate.orEmpty(),
                            sex = entry.patientSex.orEmpty(),
                            accessionNumber = entry.accessionNumber.orEmpty(),
                            studyDescription = entry.studyDescription.orEmpty(),
                            bodyPartExamined = patient.bodyPartExamined,
                            laterality = patient.laterality,
                        )
                        audit.record(
                            "select_worklist",
                            patientId = entry.patientId,
                            studyUid = entry.studyInstanceUid.orEmpty(),
                            detail = entry.accessionNumber.orEmpty(),
                        )
                        destination = Destination.Capture
                    },
                )

                Destination.AppendStudy -> AppendStudyScreen(
                    endpoint = pacsSettings.toEndpoint(),
                    onSelected = { entry: StudyEntry ->
                        val ctx = entry.toPatientStudyContext("Additional clinical photo/video").copy(
                            bodyPartExamined = patient.bodyPartExamined.takeIf { it.isNotBlank() },
                            laterality = patient.laterality.takeIf { it.isNotBlank() },
                        )
                        startNewSession(ExamSelection(ctx, ExamSource.APPEND_EXISTING))
                        patient = ManualPatientForm(
                            patientId = entry.patientId,
                            patientName = entry.patientName,
                            birthDate = entry.patientBirthDate.orEmpty(),
                            sex = entry.patientSex.orEmpty(),
                            accessionNumber = entry.accessionNumber.orEmpty(),
                            studyDescription = entry.studyDescription.orEmpty(),
                            bodyPartExamined = patient.bodyPartExamined,
                            laterality = patient.laterality,
                        )
                        audit.record(
                            "select_append_study",
                            patientId = entry.patientId,
                            studyUid = entry.studyInstanceUid,
                            detail = entry.accessionNumber.orEmpty(),
                        )
                        destination = Destination.Capture
                    },
                )

                Destination.Capture -> CaptureSessionScreen(
                    patientBanner = exam?.banner ?: "${patient.patientId} · ${patient.patientName}",
                    session = session,
                    staging = staging,
                    onAddItem = { item -> session = session.add(item) },
                    onDiscardItem = { id ->
                        session = batchSender.discardItem(session, id)
                    },
                    onSendAll = {
                        val currentExam = exam
                        if (currentExam == null) {
                            statusNote = "No exam selected"
                            return@CaptureSessionScreen
                        }
                        if (session.pendingSendCount == 0) {
                            statusNote = "Add at least one photo or video"
                            return@CaptureSessionScreen
                        }
                        destination = Destination.Sending
                        sendProgress = "Starting batch..."
                        scope.launch {
                            val outcome = withContext(Dispatchers.IO) {
                                batchSender.sendAll(
                                    session = session,
                                    examContext = currentExam.context,
                                    settings = pacsSettings,
                                    examSource = currentExam.source.name,
                                ) { progress ->
                                    sendProgress = progress.message
                                }
                            }
                            session = outcome.session
                            refreshPending()
                            resultSuccess = outcome.allSucceeded
                            resultMessage = outcome.message
                            if (outcome.allSucceeded) {
                                session = CaptureSession(
                                    studyInstanceUid = outcome.session.studyInstanceUid,
                                    seriesInstanceUid = outcome.session.seriesInstanceUid,
                                )
                            }
                            destination = Destination.Result
                        }
                    },
                    onDiscardSession = {
                        session = batchSender.discardSession(session)
                        destination = Destination.Patient
                    },
                )

                Destination.Sending -> SendingScreen(progress = sendProgress)

                Destination.Result -> ResultScreen(
                    message = resultMessage,
                    success = resultSuccess,
                    pendingCount = pendingItems.size,
                    remainingInSession = session.pendingSendCount,
                    onDone = { destination = Destination.Patient },
                    onBackToSession = { destination = Destination.Capture },
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
                                BatchStore.gateway(pacsSettings.toEndpoint())
                                    .storeWithRetry(item.dicomFile).first
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
private fun SendingScreen(progress: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = progress.ifBlank { "Encoding and C-STORE to PACS..." },
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
    selectedBanner: String?,
    onOpenPending: () -> Unit,
    onOpenWorklist: () -> Unit,
    onOpenAppend: () -> Unit,
    onContinueManual: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Phase 4 — DIMSE/DICOMweb dual stack, session tray, MDM-ready",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!pacsConfigured) {
            Text(
                text = "PACS not configured yet. Open settings (gear) first.",
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        if (!selectedBanner.isNullOrBlank()) {
            Text(
                text = "Selected: $selectedBanner",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Button(onClick = onOpenWorklist, modifier = Modifier.fillMaxWidth()) {
            Text("Modality worklist")
        }
        Button(onClick = onOpenAppend, modifier = Modifier.fillMaxWidth()) {
            Text("Append to existing study")
        }
        Text("Or enter demographics manually:", style = MaterialTheme.typography.labelLarge)
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
        OutlinedTextField(
            value = patient.bodyPartExamined,
            onValueChange = { onPatientChange(patient.copy(bodyPartExamined = it.uppercase())) },
            label = { Text("Body part (e.g. HAND, FOOT)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Text(text = "Laterality", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("" to "-", "L" to "L", "R" to "R", "U" to "U").forEach { (value, label) ->
                Row(
                    Modifier.selectable(
                        selected = patient.laterality == value,
                        onClick = { onPatientChange(patient.copy(laterality = value)) },
                        role = Role.RadioButton,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = patient.laterality == value, onClick = null)
                    Text(text = label, modifier = Modifier.padding(end = 8.dp))
                }
            }
        }
        Button(onClick = onContinueManual, modifier = Modifier.fillMaxWidth()) {
            Text("Continue with manual patient")
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
    onExportAtna: () -> Unit,
    echoStatus: String,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    val locked = draft.managedByMdm
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (locked) {
            Text(
                text = "Managed by MDM — values come from app restrictions.",
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Text(text = "Transport", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(TransportMode.DIMSE to "DIMSE", TransportMode.DICOMWEB to "DICOMweb").forEach { (mode, label) ->
                Row(
                    Modifier.selectable(
                        selected = draft.transportMode == mode,
                        onClick = { if (!locked) draft = draft.copy(transportMode = mode) },
                        role = Role.RadioButton,
                        enabled = !locked,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = draft.transportMode == mode,
                        onClick = null,
                        enabled = !locked,
                    )
                    Text(text = label, modifier = Modifier.padding(end = 8.dp))
                }
            }
        }
        OutlinedTextField(
            value = draft.host,
            onValueChange = { if (!locked) draft = draft.copy(host = it) },
            label = { Text("PACS host (DIMSE / MWL fallback)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !locked,
        )
        OutlinedTextField(
            value = draft.port.toString(),
            onValueChange = { text ->
                if (!locked) {
                    draft = draft.copy(port = text.filter { it.isDigit() }.toIntOrNull() ?: draft.port)
                }
            },
            label = { Text("PACS DIMSE port") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !locked,
        )
        OutlinedTextField(
            value = draft.calledAeTitle,
            onValueChange = { if (!locked) draft = draft.copy(calledAeTitle = it) },
            label = { Text("Called AE Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !locked,
        )
        OutlinedTextField(
            value = draft.callingAeTitle,
            onValueChange = { if (!locked) draft = draft.copy(callingAeTitle = it) },
            label = { Text("Calling AE Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !locked,
        )
        OutlinedTextField(
            value = draft.dicomWebBaseUrl,
            onValueChange = { if (!locked) draft = draft.copy(dicomWebBaseUrl = it) },
            label = { Text("DICOMweb base URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !locked,
        )
        Text(text = "DICOM TLS (DIMSE)", style = MaterialTheme.typography.labelLarge)
        Text(
            text = "Uses system trust store. Install hospital private CA via MDM / device policy.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (draft.useTls) "TLS enabled" else "TLS disabled")
            Switch(
                checked = draft.useTls,
                onCheckedChange = { if (!locked) draft = draft.copy(useTls = it) },
                enabled = !locked,
            )
        }
        OutlinedButton(onClick = { onEcho(draft) }, modifier = Modifier.fillMaxWidth()) {
            Text(
                when (draft.transportMode) {
                    TransportMode.DIMSE -> "Test C-ECHO"
                    TransportMode.DICOMWEB -> "Test DICOMweb"
                },
            )
        }
        OutlinedButton(onClick = onExportAtna, modifier = Modifier.fillMaxWidth()) {
            Text("Export ATNA audit log")
        }
        Button(
            onClick = { onSave(draft) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !locked,
        ) {
            Text("Save settings")
        }
        if (echoStatus.isNotBlank()) {
            Text(echoStatus)
        }
    }
}

@Composable
private fun CaptureSessionScreen(
    patientBanner: String,
    session: CaptureSession,
    staging: SecureStaging,
    onAddItem: (SessionItem) -> Unit,
    onDiscardItem: (String) -> Unit,
    onSendAll: () -> Unit,
    onDiscardSession: () -> Unit,
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
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = patientBanner,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Confirm patient. Capture multiple photos/videos, then Send all.",
            style = MaterialTheme.typography.bodySmall,
        )

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
                    .height(280.dp),
            ) {
                SessionCameraPreview(
                    onReady = { image, video ->
                        imageCapture = image
                        videoCapture = video
                    },
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = {
                        if (isRecording) return@Button
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
                                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                    BitmapFactory.decodeFile(rawFile.absolutePath, bounds)
                                    onAddItem(
                                        SessionItem(
                                            kind = CaptureKind.PHOTO,
                                            rawFile = rawFile,
                                            rows = bounds.outHeight.coerceAtLeast(1),
                                            columns = bounds.outWidth.coerceAtLeast(1),
                                        ),
                                    )
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    error = exception.message
                                    staging.wipe(rawFile)
                                }
                            },
                        )
                    },
                    enabled = !isRecording,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Photo")
                }
                Button(
                    onClick = {
                        val capture = videoCapture
                        if (capture == null) {
                            error = "Video not ready"
                            return@Button
                        }
                        if (isRecording) {
                            activeRecording?.stop()
                            activeRecording = null
                            isRecording = false
                            return@Button
                        }
                        error = null
                        val rawFile = staging.createStagingFile("raw", "mp4")
                        val output = FileOutputOptions.Builder(rawFile).build()
                        isRecording = true
                        activeRecording = capture.output
                            .prepareRecording(context, output)
                            .start(ContextCompat.getMainExecutor(context)) { event ->
                                when (event) {
                                    is VideoRecordEvent.Finalize -> {
                                        isRecording = false
                                        activeRecording = null
                                        if (event.hasError()) {
                                            error = "Video error: ${event.cause?.message ?: event.error}"
                                            staging.wipe(rawFile)
                                        } else {
                                            val meta = readVideoMeta(rawFile)
                                            onAddItem(
                                                SessionItem(
                                                    kind = CaptureKind.VIDEO,
                                                    rawFile = rawFile,
                                                    rows = meta.rows,
                                                    columns = meta.columns,
                                                    frameCount = meta.frameCount,
                                                    framesPerSecond = meta.fps,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (isRecording) "Stop video" else "Record video")
                }
            }
        }

        Text(
            text = "Session tray (${session.items.size})",
            style = MaterialTheme.typography.titleSmall,
        )
        if (session.items.isEmpty()) {
            Text("No captures yet.", style = MaterialTheme.typography.bodySmall)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                session.items.forEach { item ->
                    Column(
                        modifier = Modifier
                            .width(140.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("${item.label} · ${item.status.name}")
                        item.error?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        if (item.status != SessionItemStatus.STORED) {
                            OutlinedButton(onClick = { onDiscardItem(item.id) }) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = onSendAll,
            enabled = session.pendingSendCount > 0 && !isRecording,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Send all (${session.pendingSendCount})")
        }
        OutlinedButton(
            onClick = onDiscardSession,
            enabled = !isRecording,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Discard session")
        }
        error?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
    }
}

private data class VideoMeta(val rows: Int, val columns: Int, val frameCount: Int, val fps: Int)

private fun readVideoMeta(file: File): VideoMeta {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 640
        val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 480
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 1000L
        val fpsFloat = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()
            ?: 30f
        val fps = fpsFloat.toInt().coerceAtLeast(1)
        val frames = ((durationMs / 1000.0) * fps).toInt().coerceAtLeast(1)
        VideoMeta(rows = h, columns = w, frameCount = frames, fps = fps)
    } catch (e: Exception) {
        Log.w(TAG, "readVideoMeta failed", e)
        VideoMeta(rows = 480, columns = 640, frameCount = 30, fps = 30)
    } finally {
        runCatching { retriever.release() }
    }
}

@Composable
private fun SessionCameraPreview(
    onReady: (ImageCapture, VideoCapture<Recorder>) -> Unit,
) {
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
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.SD))
                .build()
            val videoCapture = VideoCapture.withOutput(recorder)
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
                videoCapture,
            )
            onReady(imageCapture, videoCapture)
        }
        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun ResultScreen(
    message: String,
    success: Boolean,
    pendingCount: Int,
    remainingInSession: Int,
    onDone: () -> Unit,
    onBackToSession: () -> Unit,
    onPending: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (success) "Batch stored and wiped locally" else "Batch incomplete — check pending / session",
            style = MaterialTheme.typography.titleLarge,
            color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
        )
        Text(message)
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
        if (remainingInSession > 0) {
            OutlinedButton(onClick = onBackToSession, modifier = Modifier.fillMaxWidth()) {
                Text("Back to session ($remainingInSession left)")
            }
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
