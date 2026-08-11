package nl.dicomcamera.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
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
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.dicomcamera.app.demo.ArchivedPatientStore
import nl.dicomcamera.app.demo.DemoPatients
import nl.dicomcamera.app.demo.LocalArchiveStore
import nl.dicomcamera.app.diagnostics.DiagnosticLog
import nl.dicomcamera.app.diagnostics.HostPing
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
import nl.dicomcamera.app.ui.components.ChromeBottomBar
import nl.dicomcamera.app.ui.components.ChromeTopBar
import nl.dicomcamera.app.ui.components.DicomTextField
import nl.dicomcamera.app.ui.components.ForestButton
import nl.dicomcamera.app.ui.components.MainTab
import nl.dicomcamera.app.ui.components.MetaChip
import nl.dicomcamera.app.ui.components.QuietOutlinedButton
import nl.dicomcamera.app.ui.components.ResultRow
import nl.dicomcamera.app.ui.components.SectionLabel
import nl.dicomcamera.app.ui.components.SegmentedChoice
import nl.dicomcamera.app.ui.components.SoftPanel
import nl.dicomcamera.app.ui.components.StatusBanner
import nl.dicomcamera.app.ui.components.StatusTone
import nl.dicomcamera.app.ui.theme.DicomColors
import nl.dicomcamera.app.ui.theme.DicomShapes
import nl.dicomcamera.app.ui.theme.DicomType
import nl.dicomcamera.dicom.AuditLog
import nl.dicomcamera.dicom.EchoResult
import nl.dicomcamera.dicom.PacsClient
import nl.dicomcamera.dicom.PatientStudyContext
import nl.dicomcamera.dicom.PendingStoreQueue
import nl.dicomcamera.dicom.SecureStaging
import nl.dicomcamera.dicom.StoreResult
import nl.dicomcamera.dicom.StudyEntry
import nl.dicomcamera.dicom.WorklistEntry
import java.io.File
import java.io.FileInputStream
import java.time.Instant

private const val TAG = "Phase3App"

private enum class WorklistMode {
    Worklist,
    Manual,
}

private enum class Destination {
    Worklist,
    Archive,
    Settings,
    Session,
    Pending,
}

private fun Destination.isMainTab(): Boolean =
    this == Destination.Worklist || this == Destination.Archive || this == Destination.Settings

private fun Destination.toMainTab(): MainTab = when (this) {
    Destination.Worklist -> MainTab.Worklist
    Destination.Archive -> MainTab.Archive
    Destination.Settings -> MainTab.Settings
    else -> MainTab.Worklist
}

private fun MainTab.toDestination(): Destination = when (this) {
    MainTab.Worklist -> Destination.Worklist
    MainTab.Archive -> Destination.Archive
    MainTab.Settings -> Destination.Settings
}

@Composable
fun Phase3App() {
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
    val localArchive = remember {
        LocalArchiveStore(File(context.filesDir, "local-archive"), staging)
    }
    val archivedStore = remember {
        ArchivedPatientStore(File(context.filesDir, "archived-patients"))
    }
    val audit = remember { AuditLog(File(context.filesDir, "audit/audit.csv")) }
    val diagnosticLog = remember {
        DiagnosticLog(File(context.filesDir, "logs/diagnostic.log"))
    }
    val batchSender = remember { SessionBatchSender(staging, pendingQueue, audit) }

    var destination by remember { mutableStateOf(Destination.Worklist) }
    var lastMainTab by remember { mutableStateOf(MainTab.Worklist) }
    var settingsTitle by remember { mutableStateOf("Settings") }
    var patient by remember { mutableStateOf(ManualPatientForm()) }
    var exam by remember { mutableStateOf<ExamSelection?>(null) }
    var session by remember { mutableStateOf(CaptureSession()) }
    var resultMessage by remember { mutableStateOf("") }
    var resultSuccess by remember { mutableStateOf(false) }
    var statusNote by remember { mutableStateOf("") }
    var sendProgress by remember { mutableStateOf("") }
    var pendingItems by remember { mutableStateOf(pendingQueue.list()) }
    var readyStudies by remember { mutableStateOf(localArchive.list()) }
    var archivedRecords by remember { mutableStateOf(archivedStore.list()) }
    var sessionStep by remember { mutableStateOf(SessionStep.Setup) }
    var worklistHint by remember { mutableStateOf<String?>(null) }
    var logUiTick by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            staging.purgeOrphans()
            archivedStore.purgeExpired()
        }
        archivedRecords = archivedStore.list()
    }

    LaunchedEffect(pacsSettings.loggingEnabled) {
        diagnosticLog.setEnabled(pacsSettings.loggingEnabled)
    }

    val downloadLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri: Uri? ->
        if (uri == null) {
            statusNote = "Download cancelled"
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val src = diagnosticLog.snapshotFile()
                    if (!src.exists() || src.length() == 0L) {
                        error("Log file is empty — enable logging and reproduce the issue first")
                    }
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(src).use { input -> input.copyTo(out) }
                    } ?: error("Could not open destination")
                }
            }
            statusNote = outcome.fold(
                onSuccess = {
                    diagnosticLog.log("log_download", "exported")
                    "Log downloaded"
                },
                onFailure = { "Download failed: ${it.message}" },
            )
            logUiTick++
        }
    }

    fun refreshPending() {
        pendingItems = pendingQueue.list()
    }

    fun refreshArchive() {
        readyStudies = localArchive.list()
        archivedRecords = archivedStore.list()
    }

    fun startNewSession(selection: ExamSelection) {
        exam = selection
        session = CaptureSession(
            studyInstanceUid = selection.context.studyInstanceUid
                ?.takeIf { it.isNotBlank() }
                ?: nl.dicomcamera.dicom.DicomUid.newUid(),
            seriesInstanceUid = selection.context.seriesInstanceUid
                ?.takeIf { it.isNotBlank() }
                ?: nl.dicomcamera.dicom.DicomUid.newUid(),
        )
        sessionStep = SessionStep.Setup
    }

    fun selectMainTab(tab: MainTab) {
        lastMainTab = tab
        statusNote = ""
        destination = tab.toDestination()
    }

    fun goBack() {
        destination = when (destination) {
            Destination.Session, Destination.Pending -> lastMainTab.toDestination()
            Destination.Worklist, Destination.Archive, Destination.Settings -> destination
        }
    }

    val showBottomBar = destination.isMainTab()
    val title = when (destination) {
        Destination.Worklist -> "Worklist"
        Destination.Archive -> "Archive"
        Destination.Settings -> settingsTitle
        Destination.Session -> when (sessionStep) {
            SessionStep.Setup -> "Patient"
            SessionStep.Review -> "Review"
            SessionStep.Markup -> "Mark up"
            SessionStep.Archiving -> "Archiving"
            SessionStep.Result -> "Archive result"
        }
        Destination.Pending -> "Pending uploads"
    }

    Scaffold(
        containerColor = DicomColors.Linen,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ChromeTopBar(
                branded = destination.isMainTab(),
                title = title,
                navigationIcon = if (!destination.isMainTab()) {
                    {
                        IconButton(onClick = { goBack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = DicomColors.Forest,
                            )
                        }
                    }
                } else {
                    null
                },
            )
        },
        bottomBar = {
            if (showBottomBar) {
                ChromeBottomBar(
                    selected = destination.toMainTab(),
                    onSelect = { tab -> selectMainTab(tab) },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DicomColors.Linen),
        ) {
            when (destination) {
                Destination.Worklist -> WorklistTab(
                    patient = patient,
                    onPatientChange = { patient = it },
                    pacsConfigured = pacsSettings.isConfigured(),
                    hl7Configured = pacsSettings.toHl7Config().isConfigured(),
                    pendingCount = pendingItems.size,
                    statusNote = statusNote,
                    selectedBanner = exam?.banner,
                    node = pacsSettings.toNode(),
                    callingAeTitle = pacsSettings.callingAeTitle,
                    modality = pacsSettings.modality.ifBlank { "XC" },
                    onOpenPending = {
                        refreshPending()
                        destination = Destination.Pending
                    },
                    onOpenSettings = { selectMainTab(MainTab.Settings) },
                    onQueryHl7 = {
                        scope.launch {
                            statusNote = "HL7 ADT lookup…"
                            diagnosticLog.log("hl7_lookup_start", patient.patientId.trim())
                            val outcome = withContext(Dispatchers.IO) {
                                runCatching {
                                    nl.dicomcamera.identity.Hl7PatientDirectory(
                                        configProvider = { pacsSettings.toHl7Config() },
                                    ).findPatients(
                                        nl.dicomcamera.identity.PatientQuery(
                                            patientId = patient.patientId.trim(),
                                            patientName = patient.patientName.trim().ifBlank { null },
                                        ),
                                    )
                                }
                            }
                            outcome.onSuccess { list ->
                                val hit = list.firstOrNull()
                                if (hit == null) {
                                    statusNote = "HL7: no patient found"
                                    diagnosticLog.log("hl7_lookup", "no_patient")
                                } else {
                                    patient = patient.copy(
                                        patientId = hit.patientId,
                                        patientName = hit.patientName,
                                        birthDate = hit.birthDate.orEmpty(),
                                        sex = hit.sex.orEmpty(),
                                    )
                                    audit.record(
                                        "hl7_lookup",
                                        patientId = hit.patientId,
                                        detail = hit.patientName,
                                    )
                                    diagnosticLog.log("hl7_lookup", "ok ${hit.patientId}")
                                    statusNote = "HL7: filled ${hit.patientName}"
                                }
                            }.onFailure { e ->
                                diagnosticLog.log("hl7_lookup", "failed ${e.message}")
                                statusNote = "HL7 failed: ${e.message}"
                            }
                        }
                    },
                    onWorklistSelected = { entry: WorklistEntry ->
                        val ctx = entry.toPatientStudyContext("Clinical photo/video session").copy(
                            bodyPartExamined = null,
                            laterality = null,
                            patientBirthDate = null,
                            patientSex = null,
                            patientName = "",
                        )
                        startNewSession(ExamSelection(ctx, ExamSource.WORKLIST))
                        worklistHint = listOfNotNull(
                            entry.patientName.takeIf { it.isNotBlank() },
                            entry.patientBirthDate,
                            entry.patientSex,
                            entry.accessionNumber?.let { "Acc $it" },
                        ).joinToString(" · ")
                        // Start with clear Name / Birthdate / Sex — user confirms on setup screen.
                        patient = ManualPatientForm(
                            patientId = entry.patientId,
                            patientName = "",
                            birthDate = "",
                            sex = "",
                            accessionNumber = entry.accessionNumber.orEmpty(),
                            studyDescription = entry.studyDescription.orEmpty(),
                            bodyPartExamined = "",
                            laterality = "",
                        )
                        audit.record(
                            "select_worklist",
                            patientId = entry.patientId,
                            studyUid = entry.studyInstanceUid.orEmpty(),
                            detail = entry.accessionNumber.orEmpty(),
                        )
                        statusNote = ""
                        destination = Destination.Session
                    },
                    onContinueManual = {
                        val id = patient.patientId.trim()
                        when {
                            id.isBlank() -> statusNote = "Patient ID is required"
                            else -> {
                                statusNote = ""
                                worklistHint = null
                                val accession = patient.accessionNumber
                                val studyDesc = patient.studyDescription
                                patient = ManualPatientForm(
                                    patientId = id,
                                    patientName = "",
                                    birthDate = "",
                                    sex = "",
                                    accessionNumber = accession,
                                    studyDescription = studyDesc,
                                )
                                startNewSession(
                                    ExamSelection(
                                        context = PatientStudyContext(
                                            patientId = id,
                                            patientName = "",
                                            accessionNumber = accession.takeIf { it.isNotBlank() },
                                            studyDescription = studyDesc.takeIf { it.isNotBlank() },
                                            modality = pacsSettings.modality.ifBlank { "XC" },
                                            seriesDescription = "Clinical photo/video session",
                                        ),
                                        source = ExamSource.MANUAL,
                                    ),
                                )
                                audit.record("select_manual", patientId = id)
                                destination = Destination.Session
                            }
                        }
                    },
                )

                Destination.Settings -> SettingsFlow(
                    initial = pacsSettings,
                    connectivityStatus = statusNote,
                    logSummary = run {
                        logUiTick
                        val bytes = diagnosticLog.sizeBytes()
                        val lines = diagnosticLog.lineCount()
                        val state = if (diagnosticLog.enabled) "ON" else "OFF"
                        "State $state · $lines lines · $bytes bytes"
                    },
                    onSave = { updated ->
                        scope.launch {
                            settingsRepo.save(updated)
                            diagnosticLog.setEnabled(updated.loggingEnabled)
                            diagnosticLog.log("settings_saved", updated.remoteSummary())
                            statusNote = "Settings saved"
                            logUiTick++
                            selectMainTab(MainTab.Settings)
                        }
                    },
                    onPing = { draft ->
                        scope.launch {
                            statusNote = "Ping…"
                            val result = withContext(Dispatchers.IO) {
                                HostPing.ping(draft.host.trim())
                            }
                            diagnosticLog.log("ping", result.message)
                            statusNote = result.message
                            logUiTick++
                        }
                    },
                    onEcho = { draft ->
                        scope.launch {
                            statusNote = "C-ECHO…"
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    PacsClient(draft.toNode()).use { it.echo() }
                                }.getOrElse { EchoResult.Failed(it.message ?: "echo failed", it) }
                            }
                            statusNote = when (result) {
                                EchoResult.Success -> "C-ECHO OK"
                                is EchoResult.Failed -> "C-ECHO failed: ${result.message}"
                            }
                            diagnosticLog.log("c_echo", statusNote)
                            logUiTick++
                        }
                    },
                    onLoggingEnabledChange = { updated, enabled ->
                        scope.launch {
                            settingsRepo.save(updated.copy(loggingEnabled = enabled))
                            diagnosticLog.setEnabled(enabled)
                            statusNote = if (enabled) {
                                "Logging enabled"
                            } else {
                                "Logging disabled"
                            }
                            logUiTick++
                        }
                    },
                    onDownloadLog = {
                        val name = "dicomcamera-diagnostic-${Instant.now().toString().replace(':', '-')}.log"
                        downloadLogLauncher.launch(name)
                    },
                    onClearLog = {
                        diagnosticLog.clear()
                        statusNote = "Log cleared"
                        logUiTick++
                    },
                    onTitleChange = { settingsTitle = it },
                )

                Destination.Archive -> {
                    LaunchedEffect(Unit) { refreshArchive() }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        SoftPanel {
                            SectionLabel("Ready to send")
                            Text(
                                "Local captures waiting for PACS. Send when Remote DICOM is configured.",
                                style = MaterialTheme.typography.bodySmall,
                                color = DicomColors.Slate700,
                            )
                            if (readyStudies.isEmpty()) {
                                Text(
                                    "No local studies yet. Pick a demo patient on Worklist, take photos, then Save to Archive.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DicomColors.Slate500,
                                )
                            }
                            readyStudies.forEach { study ->
                                SoftPanel {
                                    Text(
                                        "${study.patientId} · ${study.patientName}",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        listOfNotNull(
                                            study.accessionNumber?.let { "Acc $it" },
                                            "${study.photoCount} photo(s)",
                                            study.studyDescription,
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DicomColors.Slate700,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ForestButton(
                                            text = "Review & archive",
                                            onClick = {
                                                val items = localArchive.toSessionItems(study)
                                                if (items.isEmpty()) {
                                                    statusNote = "No photos in this study"
                                                    return@ForestButton
                                                }
                                                localArchive.discard(study.id)
                                                refreshArchive()
                                                patient = ManualPatientForm(
                                                    patientId = study.patientId,
                                                    patientName = study.patientName,
                                                    birthDate = study.birthDate.orEmpty(),
                                                    sex = study.sex.orEmpty(),
                                                    accessionNumber = study.accessionNumber.orEmpty(),
                                                    studyDescription = study.studyDescription.orEmpty(),
                                                )
                                                worklistHint = null
                                                exam = ExamSelection(study.toContext(), ExamSource.APPEND_EXISTING)
                                                session = CaptureSession(
                                                    studyInstanceUid = study.studyInstanceUid,
                                                    seriesInstanceUid = study.seriesInstanceUid,
                                                    items = items,
                                                )
                                                sessionStep = SessionStep.Review
                                                destination = Destination.Session
                                            },
                                            compact = true,
                                        )
                                        QuietOutlinedButton(
                                            text = "Discard",
                                            onClick = {
                                                localArchive.discard(study.id)
                                                refreshArchive()
                                            },
                                        )
                                    }
                                    if (!pacsSettings.isConfigured()) {
                                        Text(
                                            "Configure Remote DICOM to enable Send.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = DicomColors.Slate500,
                                        )
                                    }
                                }
                            }
                        }

                        
                        ArchivedPatientsPanel(
                            records = archivedRecords,
                            onAddImaging = { record ->
                                worklistHint = listOfNotNull(
                                    record.patientName,
                                    record.birthDate,
                                    record.sex,
                                    record.accessionNumber?.let { "Acc $it" },
                                ).joinToString(" · ")
                                patient = ManualPatientForm(
                                    patientId = record.patientId,
                                    patientName = "",
                                    birthDate = "",
                                    sex = "",
                                    accessionNumber = record.accessionNumber.orEmpty(),
                                    studyDescription = record.studyDescription.orEmpty(),
                                    bodyPartExamined = "",
                                    laterality = "",
                                )
                                startNewSession(
                                    ExamSelection(
                                        context = record.toContext(newSeries = true),
                                        source = ExamSource.APPEND_EXISTING,
                                    ),
                                )
                                destination = Destination.Session
                            },
                        )

if (pacsSettings.isConfigured()) {
                            SoftPanel {
                                SectionLabel("Append from PACS")
                            }
                            AppendStudyScreen(
                                node = pacsSettings.toNode(),
                                embedded = true,
                                onSelected = { entry: StudyEntry ->
                                    val ctx = entry.toPatientStudyContext("Additional clinical photo/video").copy(
                                        bodyPartExamined = patient.bodyPartExamined.takeIf { it.isNotBlank() },
                                        laterality = patient.laterality.takeIf { it.isNotBlank() },
                                    )
                                    worklistHint = listOfNotNull(
                                        entry.patientName.takeIf { it.isNotBlank() },
                                        entry.patientBirthDate,
                                        entry.patientSex,
                                        entry.accessionNumber?.let { "Acc $it" },
                                    ).joinToString(" · ")
                                    startNewSession(ExamSelection(ctx.copy(
                                        patientName = "",
                                        patientBirthDate = null,
                                        patientSex = null,
                                        bodyPartExamined = null,
                                        laterality = null,
                                    ), ExamSource.APPEND_EXISTING))
                                    patient = ManualPatientForm(
                                        patientId = entry.patientId,
                                        patientName = "",
                                        birthDate = "",
                                        sex = "",
                                        accessionNumber = entry.accessionNumber.orEmpty(),
                                        studyDescription = entry.studyDescription.orEmpty(),
                                    )
                                    audit.record(
                                        "select_append_study",
                                        patientId = entry.patientId,
                                        studyUid = entry.studyInstanceUid,
                                        detail = entry.accessionNumber.orEmpty(),
                                    )
                                    destination = Destination.Session
                                },
                            )
                        } else {
                            StatusBanner(
                                text = "Configure Remote DICOM in Settings to query PACS archive for append.",
                                tone = StatusTone.Info,
                            )
                            QuietOutlinedButton(
                                text = "Open Settings",
                                onClick = { selectMainTab(MainTab.Settings) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                Destination.Session -> SessionWorkflow(
                    step = sessionStep,
                    onStepChange = { sessionStep = it },
                    patient = patient,
                    onPatientChange = { patient = it },
                    exam = exam,
                    onExamChange = { exam = it },
                    session = session,
                    onSessionChange = { session = it },
                    staging = staging,
                    batchSender = batchSender,
                    archivedStore = archivedStore,
                    localArchive = localArchive,
                    diagnosticLog = diagnosticLog,
                    audit = audit,
                    pacsSettings = pacsSettings,
                    worklistHint = worklistHint,
                    onFinished = {
                        session = CaptureSession()
                        exam = null
                        worklistHint = null
                        selectMainTab(MainTab.Archive)
                    },
                    onCancelWorkflow = {
                        session = batchSender.discardSession(session)
                        exam = null
                        worklistHint = null
                        selectMainTab(lastMainTab)
                    },
                    onOpenLog = {
                        diagnosticLog.setEnabled(true)
                        statusNote = "Logging enabled — download from Settings → Logging. Contact your PACS administrator if needed."
                        selectMainTab(MainTab.Settings)
                    },
                    onArchivedRefresh = { refreshArchive() },
                )


                Destination.Pending -> PendingScreen(
                    items = pendingItems,
                    onRetry = { item ->
                        scope.launch {
                            statusNote = "Retrying..."
                            val result = withContext(Dispatchers.IO) {
                                val batch = nl.dicomcamera.dicom.BatchStore(
                                    clientFactory = { PacsClient(pacsSettings.toNode()) },
                                )
                                batch.storeWithRetry(item.dicomFile).first
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
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SoftPanel {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator(color = DicomColors.Forest)
                Text(
                    text = progress.ifBlank { "Encoding and C-STORE to PACS..." },
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = DicomColors.Teal,
                    trackColor = DicomColors.Hairline,
                )
            }
        }
    }
}

@Composable
private fun WorklistTab(
    patient: ManualPatientForm,
    onPatientChange: (ManualPatientForm) -> Unit,
    pacsConfigured: Boolean,
    hl7Configured: Boolean,
    pendingCount: Int,
    statusNote: String,
    selectedBanner: String?,
    node: nl.dicomcamera.dicom.DicomNode,
    callingAeTitle: String,
    modality: String,
    onOpenPending: () -> Unit,
    onOpenSettings: () -> Unit,
    onQueryHl7: () -> Unit,
    onWorklistSelected: (WorklistEntry) -> Unit,
    onContinueManual: () -> Unit,
) {
    var mode by remember { mutableStateOf(WorklistMode.Worklist) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SegmentedChoice(
            leftLabel = "Worklist",
            rightLabel = "Manual",
            leftSelected = mode == WorklistMode.Worklist,
            onLeft = { mode = WorklistMode.Worklist },
            onRight = { mode = WorklistMode.Manual },
        )

        if (!selectedBanner.isNullOrBlank()) {
            StatusBanner(text = "Selected: $selectedBanner", tone = StatusTone.Info)
        }
        if (statusNote.isNotBlank()) {
            StatusBanner(text = statusNote, tone = StatusTone.Info)
        }

        when (mode) {
            WorklistMode.Worklist -> {
                SoftPanel {
                    SectionLabel("Demo patients")
                    Text(
                        "Two sample cases for exploring capture without a live PACS.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DicomColors.Slate700,
                    )
                    DemoPatients.entries.forEach { entry ->
                        ResultRow(
                            title = "${entry.patientId} · ${entry.patientName}",
                            subtitle = listOfNotNull(
                                entry.accessionNumber?.let { "Acc $it" },
                                entry.scheduledStartDate,
                                entry.studyDescription,
                            ).joinToString(" · "),
                            onClick = { onWorklistSelected(entry) },
                            trailing = {
                                MetaChip(text = "Demo", foreground = DicomColors.GoldInk)
                            },
                        )
                    }
                }
                if (pacsConfigured) {
                    WorklistScreen(
                        node = node,
                        callingAeTitle = callingAeTitle,
                        onSelected = onWorklistSelected,
                        embedded = true,
                        modality = modality,
                    )
                } else {
                    StatusBanner(
                        text = "Live MWL needs Remote DICOM in Settings. Demo patients work offline.",
                        tone = StatusTone.Info,
                    )
                    QuietOutlinedButton(
                        text = "Open Settings",
                        onClick = onOpenSettings,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            WorklistMode.Manual -> {
                SoftPanel {
                    SectionLabel("HL7 ADT query")
                    Text(
                        "Look up demographics on the hospital HL7 façade (ADT / QBP). Does not use DICOM C-ECHO.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DicomColors.Slate700,
                    )
                    DicomTextField(
                        value = patient.patientId,
                        onValueChange = { onPatientChange(patient.copy(patientId = it)) },
                        label = "Patient ID *",
                    )
                    DicomTextField(
                        value = patient.patientName,
                        onValueChange = { onPatientChange(patient.copy(patientName = it)) },
                        label = "Patient Name (optional for query)",
                    )
                    QuietOutlinedButton(
                        text = if (hl7Configured) {
                            "Query HL7 ADT"
                        } else {
                            "Query HL7 (configure in Settings)"
                        },
                        onClick = onQueryHl7,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = hl7Configured && patient.patientId.isNotBlank(),
                    )
                    if (!hl7Configured) {
                        Text(
                            "Enable Settings → HL7 demographics and set the façade URL.",
                            style = MaterialTheme.typography.bodySmall,
                            color = DicomColors.Slate500,
                        )
                    }
                }

                SoftPanel {
                    SectionLabel("Patient details")
                    Text(
                        "Edit fields after HL7 lookup, or enter everything manually.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DicomColors.Slate700,
                    )
                    DicomTextField(
                        value = patient.patientId,
                        onValueChange = { onPatientChange(patient.copy(patientId = it)) },
                        label = "Patient ID *",
                    )
                    DicomTextField(
                        value = patient.patientName,
                        onValueChange = { onPatientChange(patient.copy(patientName = it)) },
                        label = "Patient Name * (FAMILY^GIVEN)",
                    )
                    DicomTextField(
                        value = patient.birthDate,
                        onValueChange = {
                            onPatientChange(patient.copy(birthDate = it.filter { ch -> ch.isDigit() }.take(8)))
                        },
                        label = "Birth date (YYYYMMDD)",
                    )
                    SectionLabel("Sex")
                    ChoiceRow(
                        options = listOf("" to "-", "M" to "M", "F" to "F", "O" to "O"),
                        selected = patient.sex,
                        onSelect = { onPatientChange(patient.copy(sex = it)) },
                    )
                    DicomTextField(
                        value = patient.accessionNumber,
                        onValueChange = { onPatientChange(patient.copy(accessionNumber = it)) },
                        label = "Accession (optional)",
                    )
                    DicomTextField(
                        value = patient.studyDescription,
                        onValueChange = { onPatientChange(patient.copy(studyDescription = it)) },
                        label = "Study description (optional)",
                    )
                    DicomTextField(
                        value = patient.bodyPartExamined,
                        onValueChange = { onPatientChange(patient.copy(bodyPartExamined = it.uppercase())) },
                        label = "Body part (e.g. HAND, FOOT)",
                    )
                    SectionLabel("Laterality")
                    ChoiceRow(
                        options = listOf("" to "-", "L" to "L", "R" to "R", "U" to "U"),
                        selected = patient.laterality,
                        onSelect = { onPatientChange(patient.copy(laterality = it)) },
                    )
                    if (!pacsConfigured) {
                        Text(
                            "Without PACS, captures save to Archive ready to send later.",
                            style = MaterialTheme.typography.bodySmall,
                            color = DicomColors.Slate500,
                        )
                    }
                    ForestButton(
                        text = "Continue with this patient",
                        onClick = onContinueManual,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (pendingCount > 0) {
            QuietOutlinedButton(
                text = "Pending uploads ($pendingCount)",
                onClick = onOpenPending,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ChoiceRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (value, label) ->
            Row(
                Modifier.selectable(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    role = Role.RadioButton,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selected == value,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = DicomColors.Forest,
                        unselectedColor = DicomColors.Slate400,
                    ),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun CaptureSessionScreen(
    patientBanner: String,
    session: CaptureSession,
    staging: SecureStaging,
    primaryActionLabel: String,
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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SoftPanel(
            background = DicomColors.TealSoft,
            border = DicomColors.Teal.copy(alpha = 0.35f),
        ) {
            SectionLabel("Confirm patient", color = DicomColors.Forest)
            Text(
                text = patientBanner,
                style = MaterialTheme.typography.titleMedium.copy(color = DicomColors.Forest),
            )
            Text(
                text = "Capture multiple photos/videos, then Send all.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (!hasCameraPermission) {
            ForestButton(
                text = "Grant camera permission",
                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(DicomShapes.Panel)
                    .border(1.dp, DicomColors.Hairline, DicomShapes.Panel),
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
                ForestButton(
                    text = "Photo",
                    onClick = {
                        if (isRecording) return@ForestButton
                        val capture = imageCapture
                        if (capture == null) {
                            error = "Camera not ready"
                            return@ForestButton
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
                )
                ForestButton(
                    text = if (isRecording) "Stop video" else "Record video",
                    onClick = {
                        val capture = videoCapture
                        if (capture == null) {
                            error = "Video not ready"
                            return@ForestButton
                        }
                        if (isRecording) {
                            activeRecording?.stop()
                            activeRecording = null
                            isRecording = false
                            return@ForestButton
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
                    containerColor = if (isRecording) DicomColors.Rose else DicomColors.ForestMid,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        SoftPanel {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel("Session tray")
                MetaChip(text = "${session.items.size} item(s)")
            }
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
                                .width(148.dp)
                                .clip(DicomShapes.Control)
                                .background(DicomColors.White)
                                .border(1.dp, DicomColors.Hairline, DicomShapes.Control)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                item.label,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            MetaChip(
                                text = item.status.name,
                                background = when (item.status) {
                                    SessionItemStatus.STORED -> DicomColors.TealSoft
                                    SessionItemStatus.FAILED -> DicomColors.RoseSoft
                                    else -> DicomColors.GoldSoft
                                },
                                foreground = when (item.status) {
                                    SessionItemStatus.STORED -> DicomColors.ForestMid
                                    SessionItemStatus.FAILED -> DicomColors.Rose
                                    else -> DicomColors.GoldInk
                                },
                                mono = true,
                            )
                            item.error?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DicomColors.Rose,
                                )
                            }
                            if (item.status != SessionItemStatus.STORED) {
                                QuietOutlinedButton(
                                    text = "Remove",
                                    onClick = { onDiscardItem(item.id) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }

        ForestButton(
            text = "$primaryActionLabel (${session.pendingSendCount})",
            onClick = onSendAll,
            enabled = session.pendingSendCount > 0 && !isRecording,
            modifier = Modifier.fillMaxWidth(),
        )
        QuietOutlinedButton(
            text = "Discard session",
            onClick = onDiscardSession,
            enabled = !isRecording,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { StatusBanner(text = it, tone = StatusTone.Error) }
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
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SoftPanel(
            background = if (success) DicomColors.TealSoft else DicomColors.GoldSoft,
            border = if (success) {
                DicomColors.ForestMid.copy(alpha = 0.35f)
            } else {
                DicomColors.Gold.copy(alpha = 0.4f)
            },
        ) {
            SectionLabel(
                if (success) "Success" else "Incomplete",
                color = if (success) DicomColors.ForestMid else DicomColors.GoldInk,
            )
            Text(
                text = if (success) {
                    "Batch stored and wiped locally"
                } else {
                    "Batch incomplete — check pending / session"
                },
                style = MaterialTheme.typography.titleLarge,
                color = if (success) DicomColors.Forest else DicomColors.GoldInk,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = DicomType.Mono,
            )
        }
        ForestButton(text = "Done", onClick = onDone, modifier = Modifier.fillMaxWidth())
        if (remainingInSession > 0) {
            QuietOutlinedButton(
                text = "Back to session ($remainingInSession left)",
                onClick = onBackToSession,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!success || pendingCount > 0) {
            QuietOutlinedButton(
                text = "View pending ($pendingCount)",
                onClick = onPending,
                modifier = Modifier.fillMaxWidth(),
            )
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
            SoftPanel {
                Text("No pending uploads.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        items.forEach { item ->
            SoftPanel {
                Text(
                    text = "${item.patientId} · ${item.patientName}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = item.lastError,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = DicomType.Mono,
                    color = DicomColors.Rose,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ForestButton(
                        text = "Retry",
                        onClick = { onRetry(item) },
                        compact = true,
                    )
                    QuietOutlinedButton(
                        text = "Discard",
                        onClick = { onDiscard(item) },
                    )
                }
            }
        }
        if (statusNote.isNotBlank()) {
            StatusBanner(text = statusNote, tone = StatusTone.Info)
        }
    }
}
