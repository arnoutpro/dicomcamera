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
import nl.dicomcamera.app.ui.components.BrandWordmark
import nl.dicomcamera.app.ui.components.ChromeBottomBar
import nl.dicomcamera.app.ui.components.ChromeTopBar
import nl.dicomcamera.app.ui.components.DicomTextField
import nl.dicomcamera.app.ui.components.ForestButton
import nl.dicomcamera.app.ui.components.MainTab
import nl.dicomcamera.app.ui.components.MetaChip
import nl.dicomcamera.app.ui.components.QuietOutlinedButton
import nl.dicomcamera.app.ui.components.ScreenTitle
import nl.dicomcamera.app.ui.components.SectionLabel
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

private const val TAG = "Phase3App"

private enum class Destination {
    Worklist,
    Archive,
    Settings,
    Capture,
    Sending,
    Result,
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
    val audit = remember { AuditLog(File(context.filesDir, "audit/audit.csv")) }
    val batchSender = remember { SessionBatchSender(staging, pendingQueue, audit) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { staging.purgeOrphans() }
    }

    var destination by remember { mutableStateOf(Destination.Worklist) }
    var lastMainTab by remember { mutableStateOf(MainTab.Worklist) }
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

    fun selectMainTab(tab: MainTab) {
        lastMainTab = tab
        statusNote = ""
        destination = tab.toDestination()
    }

    fun goBack() {
        destination = when (destination) {
            Destination.Capture -> lastMainTab.toDestination()
            Destination.Sending, Destination.Result, Destination.Pending -> lastMainTab.toDestination()
            Destination.Worklist, Destination.Archive, Destination.Settings -> destination
        }
    }

    val showBottomBar = destination.isMainTab()
    val title = when (destination) {
        Destination.Worklist -> "Worklist"
        Destination.Archive -> "Archive"
        Destination.Settings -> "Settings"
        Destination.Capture -> "Session capture"
        Destination.Sending -> "Sending"
        Destination.Result -> "Result"
        Destination.Pending -> "Pending uploads"
    }

    Scaffold(
        containerColor = DicomColors.Linen,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ChromeTopBar(
                title = title,
                subtitle = when (destination) {
                    Destination.Worklist -> "Modality worklist & manual"
                    Destination.Archive -> "Find study to append"
                    Destination.Settings -> "PACS & modality"
                    Destination.Capture -> exam?.banner
                    else -> null
                },
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
                    pendingCount = pendingItems.size,
                    statusNote = statusNote,
                    selectedBanner = exam?.banner,
                    node = pacsSettings.toNode(),
                    callingAeTitle = pacsSettings.callingAeTitle,
                    onOpenPending = {
                        refreshPending()
                        destination = Destination.Pending
                    },
                    onOpenSettings = { selectMainTab(MainTab.Settings) },
                    onWorklistSelected = { entry: WorklistEntry ->
                        if (!pacsSettings.isConfigured()) {
                            statusNote = "Configure PACS in Settings first"
                            selectMainTab(MainTab.Settings)
                            return@WorklistTab
                        }
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
                        statusNote = ""
                        destination = Destination.Capture
                    },
                    onContinueManual = {
                        when {
                            !patient.isValid() -> statusNote = "Patient ID and Name are required"
                            !pacsSettings.isConfigured() -> {
                                statusNote = "Configure PACS in Settings first"
                                selectMainTab(MainTab.Settings)
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
                            statusNote = "Settings saved"
                            selectMainTab(MainTab.Settings)
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

                Destination.Archive -> {
                    if (!pacsSettings.isConfigured()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ScreenTitle(
                                title = "Archive",
                                subtitle = "Query existing studies to append photos or video.",
                            )
                            StatusBanner(
                                text = "Configure PACS in Settings before querying the archive.",
                                tone = StatusTone.Warn,
                            )
                            ForestButton(
                                text = "Open Settings",
                                onClick = { selectMainTab(MainTab.Settings) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        AppendStudyScreen(
                            node = pacsSettings.toNode(),
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
                    }
                }

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
                        selectMainTab(lastMainTab)
                    },
                )

                Destination.Sending -> SendingScreen(progress = sendProgress)

                Destination.Result -> ResultScreen(
                    message = resultMessage,
                    success = resultSuccess,
                    pendingCount = pendingItems.size,
                    remainingInSession = session.pendingSendCount,
                    onDone = { selectMainTab(lastMainTab) },
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
    pendingCount: Int,
    statusNote: String,
    selectedBanner: String?,
    node: nl.dicomcamera.dicom.DicomNode,
    callingAeTitle: String,
    onOpenPending: () -> Unit,
    onOpenSettings: () -> Unit,
    onWorklistSelected: (WorklistEntry) -> Unit,
    onContinueManual: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BrandWordmark(size = 22)
        ScreenTitle(
            title = "Worklist",
            subtitle = "Pick a scheduled exam, or enter demographics manually.",
        )

        if (!pacsConfigured) {
            StatusBanner(
                text = "PACS not configured. You can still draft a manual patient, then open Settings to connect.",
                tone = StatusTone.Warn,
            )
            QuietOutlinedButton(
                text = "Open Settings",
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!selectedBanner.isNullOrBlank()) {
            StatusBanner(text = "Selected: $selectedBanner", tone = StatusTone.Info)
        }
        if (statusNote.isNotBlank()) {
            StatusBanner(text = statusNote, tone = StatusTone.Info)
        }

        SoftPanel {
            SectionLabel("Modality worklist")
            if (pacsConfigured) {
                WorklistScreen(
                    node = node,
                    callingAeTitle = callingAeTitle,
                    onSelected = onWorklistSelected,
                    embedded = true,
                )
            } else {
                Text(
                    "Connect a PACS in Settings to query today’s worklist.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DicomColors.Slate700,
                )
            }
        }

        SoftPanel {
            SectionLabel("Manual demographics")
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
            ForestButton(
                text = "Continue with manual patient",
                onClick = onContinueManual,
                modifier = Modifier.fillMaxWidth(),
            )
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
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle(
            title = "PACS connection",
            subtitle = "DIMSE endpoint used for worklist, append, and C-STORE.",
        )
        SoftPanel {
            SectionLabel("Endpoint")
            DicomTextField(
                value = draft.host,
                onValueChange = { draft = draft.copy(host = it) },
                label = "PACS host",
            )
            DicomTextField(
                value = draft.port.toString(),
                onValueChange = { text ->
                    draft = draft.copy(port = text.filter { it.isDigit() }.toIntOrNull() ?: draft.port)
                },
                label = "PACS port",
            )
            DicomTextField(
                value = draft.calledAeTitle,
                onValueChange = { draft = draft.copy(calledAeTitle = it) },
                label = "Called AE Title",
            )
            DicomTextField(
                value = draft.callingAeTitle,
                onValueChange = { draft = draft.copy(callingAeTitle = it) },
                label = "Calling AE Title",
            )
        }
        SoftPanel {
            SectionLabel("DICOM TLS")
            Text(
                text = "Uses system trust store; hospital CA install via MDM later.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (draft.useTls) "TLS enabled" else "TLS disabled",
                    style = MaterialTheme.typography.titleSmall,
                )
                Switch(
                    checked = draft.useTls,
                    onCheckedChange = { draft = draft.copy(useTls = it) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = DicomColors.Forest,
                        checkedThumbColor = DicomColors.White,
                        uncheckedTrackColor = DicomColors.Hairline,
                        uncheckedThumbColor = DicomColors.Slate500,
                    ),
                )
            }
            QuietOutlinedButton(
                text = "Test C-ECHO",
                onClick = { onEcho(draft) },
                modifier = Modifier.fillMaxWidth(),
            )
            ForestButton(
                text = "Save settings",
                onClick = { onSave(draft) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (echoStatus.isNotBlank()) {
                val tone = when {
                    echoStatus.contains("OK") -> StatusTone.Success
                    echoStatus.contains("failed", ignoreCase = true) -> StatusTone.Error
                    else -> StatusTone.Info
                }
                StatusBanner(text = echoStatus, tone = tone)
            }
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
            text = "Send all (${session.pendingSendCount})",
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
        ScreenTitle(
            title = "Pending uploads",
            subtitle = "Retry failed C-STORE jobs or discard local copies.",
        )
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
