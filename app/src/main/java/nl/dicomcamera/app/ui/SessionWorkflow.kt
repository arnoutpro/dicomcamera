package nl.dicomcamera.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.dicomcamera.app.capture.CaptureVideoGranted
import nl.dicomcamera.app.capture.SystemCameraCapture
import nl.dicomcamera.app.capture.TakePictureGranted
import nl.dicomcamera.app.demo.ArchivedPatientStore
import nl.dicomcamera.app.demo.LocalArchiveStore
import nl.dicomcamera.app.diagnostics.DiagnosticLog
import nl.dicomcamera.app.session.CaptureKind
import nl.dicomcamera.app.session.CaptureSession
import nl.dicomcamera.app.session.ExamSelection
import nl.dicomcamera.app.session.ExamSource
import nl.dicomcamera.app.session.ManualPatientForm
import nl.dicomcamera.app.session.SessionBatchSender
import nl.dicomcamera.app.session.SessionItem
import nl.dicomcamera.app.session.SessionItemStatus
import nl.dicomcamera.app.settings.PacsSettings
import nl.dicomcamera.app.ui.components.ForestButton
import nl.dicomcamera.app.ui.components.QuietOutlinedButton
import nl.dicomcamera.app.ui.components.SectionLabel
import nl.dicomcamera.app.ui.components.SoftPanel
import nl.dicomcamera.app.ui.components.StatusBanner
import nl.dicomcamera.app.ui.components.StatusTone
import nl.dicomcamera.app.ui.theme.DicomColors
import nl.dicomcamera.dicom.AuditLog
import nl.dicomcamera.dicom.SecureStaging
import java.io.File

enum class SessionStep {
    Setup,
    Review,
    Markup,
    Archiving,
    Result,
}

/**
 * Patient setup → system camera → review → archive to PACS workflow.
 */
@Composable
fun SessionWorkflow(
    step: SessionStep,
    onStepChange: (SessionStep) -> Unit,
    patient: ManualPatientForm,
    onPatientChange: (ManualPatientForm) -> Unit,
    exam: ExamSelection?,
    onExamChange: (ExamSelection?) -> Unit,
    session: CaptureSession,
    onSessionChange: (CaptureSession) -> Unit,
    staging: SecureStaging,
    batchSender: SessionBatchSender,
    archivedStore: ArchivedPatientStore,
    localArchive: LocalArchiveStore,
    diagnosticLog: DiagnosticLog,
    audit: AuditLog,
    pacsSettings: PacsSettings,
    worklistHint: String?,
    onFinished: () -> Unit,
    onCancelWorkflow: () -> Unit,
    onOpenLog: () -> Unit,
    onArchivedRefresh: () -> Unit,
    onStatus: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingCapture by remember { mutableStateOf<Pair<File, CaptureKind>?>(null) }
    var markupItem by remember { mutableStateOf<SessionItem?>(null) }
    var sendProgress by remember { mutableStateOf("Archiving…") }
    var sendFraction by remember { mutableFloatStateOf(0f) }
    var resultSuccess by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }

    fun syncExamFromPatient(form: ManualPatientForm): ExamSelection? {
        val current = exam ?: return null
        return current.copy(
            context = current.context.copy(
                patientId = form.patientId.trim(),
                patientName = form.normalizedName(),
                patientBirthDate = form.birthDate.takeIf { it.length == 8 },
                patientSex = form.sex.takeIf { it.isNotBlank() },
                accessionNumber = form.accessionNumber.takeIf { it.isNotBlank() },
                studyDescription = form.studyDescription.takeIf { it.isNotBlank() },
                bodyPartExamined = form.bodyPartExamined.takeIf { it.isNotBlank() },
                laterality = form.laterality.takeIf { it.isNotBlank() },
                seriesDescription = "Clinical photo/video · ${form.bodyPartExamined}",
            ),
        )
    }

    val takePicture = rememberLauncherForActivityResult(TakePictureGranted()) { ok ->
        val pending = pendingCapture
        pendingCapture = null
        if (!ok || pending == null) {
            pending?.first?.let { staging.wipe(it) }
            if (session.items.isNotEmpty()) onStepChange(SessionStep.Review)
            else onStatus("Photo capture cancelled")
            return@rememberLauncherForActivityResult
        }
        val (file, kind) = pending
        if (!file.exists() || file.length() == 0L) {
            staging.wipe(file)
            onStatus("Camera returned an empty photo")
            diagnosticLog.log("camera_empty", "photo")
            return@rememberLauncherForActivityResult
        }
        val (rows, cols, frames) = readCaptureMeta(file, kind)
        onSessionChange(
            session.add(
                SessionItem(
                    kind = kind,
                    rawFile = file,
                    rows = rows,
                    columns = cols,
                    frameCount = frames,
                ),
            ),
        )
        diagnosticLog.log("capture", "${kind.name} ${file.name}")
        onStepChange(SessionStep.Review)
    }

    val captureVideo = rememberLauncherForActivityResult(CaptureVideoGranted()) { ok ->
        val pending = pendingCapture
        pendingCapture = null
        if (!ok || pending == null) {
            pending?.first?.let { staging.wipe(it) }
            if (session.items.isNotEmpty()) onStepChange(SessionStep.Review)
            else onStatus("Video capture cancelled")
            return@rememberLauncherForActivityResult
        }
        val (file, kind) = pending
        if (!file.exists() || file.length() == 0L) {
            staging.wipe(file)
            onStatus("Camera returned an empty video")
            diagnosticLog.log("camera_empty", "video")
            return@rememberLauncherForActivityResult
        }
        val (rows, cols, frames) = readCaptureMeta(file, kind)
        onSessionChange(
            session.add(
                SessionItem(
                    kind = kind,
                    rawFile = file,
                    rows = rows,
                    columns = cols,
                    frameCount = frames,
                    framesPerSecond = 30,
                ),
            ),
        )
        diagnosticLog.log("capture", "${kind.name} ${file.name}")
        onStepChange(SessionStep.Review)
    }

    fun startSystemCamera(photo: Boolean) {
        if (!SystemCameraCapture.hasCameraApp(context, photo)) {
            onStatus(
                if (photo) {
                    "No system camera app found for photos"
                } else {
                    "No system camera app found for video"
                },
            )
            diagnosticLog.log("camera_missing", if (photo) "photo" else "video")
            return
        }
        val (file, uri) = SystemCameraCapture.createOutput(context, staging, photo)
        pendingCapture = file to if (photo) CaptureKind.PHOTO else CaptureKind.VIDEO
        try {
            if (photo) {
                takePicture.launch(uri)
            } else {
                captureVideo.launch(uri)
            }
        } catch (e: Exception) {
            pendingCapture = null
            staging.wipe(file)
            onStatus("Camera launch failed: ${e.message}")
            diagnosticLog.log("camera_launch_fail", e.message.orEmpty())
        }
    }


    when (step) {
        SessionStep.Setup -> PatientSetupScreen(
            patient = patient,
            onPatientChange = {
                onPatientChange(it)
                syncExamFromPatient(it)?.let(onExamChange)
            },
            onCapturePhoto = {
                val updated = syncExamFromPatient(patient)
                if (updated != null) onExamChange(updated)
                startSystemCamera(photo = true)
            },
            onCaptureVideo = {
                val updated = syncExamFromPatient(patient)
                if (updated != null) onExamChange(updated)
                startSystemCamera(photo = false)
            },
            onCancel = {
                onSessionChange(batchSender.discardSession(session))
                onCancelWorkflow()
            },
            worklistHint = worklistHint,
        )

        SessionStep.Review -> {
            ReviewSessionScreen(
                patientBanner = exam?.banner
                    ?: "${patient.patientId} · ${patient.patientName}",
                bodyPart = patient.bodyPartExamined,
                session = session,
                staging = staging,
                onDeleteItem = { id ->
                    onSessionChange(batchSender.discardItem(session, id))
                },
                onMarkupItem = { item ->
                    markupItem = item
                    onStepChange(SessionStep.Markup)
                },
                onCaptureMorePhoto = { startSystemCamera(photo = true) },
                onCaptureMoreVideo = { startSystemCamera(photo = false) },
                onArchiveToPacs = {
                    val currentExam = syncExamFromPatient(patient)?.also(onExamChange) ?: exam
                    if (currentExam == null) return@ReviewSessionScreen
                    if (!pacsSettings.isConfigured()) return@ReviewSessionScreen
                    onStepChange(SessionStep.Archiving)
                    sendProgress = "Starting…"
                    sendFraction = 0f
                    scope.launch {
                        val outcome = withContext(Dispatchers.IO) {
                            batchSender.sendAll(
                                session = session,
                                examContext = currentExam.context.copy(
                                    studyInstanceUid = session.studyInstanceUid,
                                    seriesInstanceUid = session.seriesInstanceUid,
                                    bodyPartExamined = patient.bodyPartExamined.takeIf { it.isNotBlank() },
                                    laterality = patient.laterality.takeIf { it.isNotBlank() },
                                ),
                                settings = pacsSettings,
                                examSource = currentExam.source.name,
                            ) { progress ->
                                sendProgress = progress.message
                                sendFraction = progress.currentIndex.toFloat() / progress.total.coerceAtLeast(1)
                            }
                        }
                        onSessionChange(outcome.session)
                        resultSuccess = outcome.allSucceeded
                        resultMessage = outcome.message
                        diagnosticLog.log(
                            "archive_pacs",
                            if (outcome.allSucceeded) "ok ${outcome.successCount}" else "fail ${outcome.message}",
                        )
                        if (outcome.allSucceeded) {
                            // NEVER keep images after successful PACS store
                            onSessionChange(batchSender.discardSession(outcome.session))
                            archivedStore.recordSuccessfulArchive(
                                context = currentExam.context.copy(
                                    bodyPartExamined = patient.bodyPartExamined.takeIf { it.isNotBlank() },
                                    laterality = patient.laterality.takeIf { it.isNotBlank() },
                                ),
                                studyInstanceUid = session.studyInstanceUid,
                                seriesInstanceUid = session.seriesInstanceUid,
                                instanceCount = outcome.successCount,
                            )
                            audit.record(
                                "archive_pacs_ok",
                                patientId = patient.patientId,
                                studyUid = session.studyInstanceUid,
                                detail = "${outcome.successCount} instance(s)",
                            )
                            onArchivedRefresh()
                        } else {
                            audit.record(
                                "archive_pacs_fail",
                                patientId = patient.patientId,
                                studyUid = session.studyInstanceUid,
                                detail = outcome.message,
                            )
                        }
                        onStepChange(SessionStep.Result)
                    }
                },
                onCancelAll = {
                    onSessionChange(batchSender.discardSession(session))
                    onCancelWorkflow()
                },
                pacsConfigured = pacsSettings.isConfigured(),
            )
        }

        SessionStep.Markup -> {
            val item = markupItem
            if (item == null) {
                LaunchedEffect(Unit) { onStepChange(SessionStep.Review) }
            } else {
                MarkupScreen(
                    item = item,
                    staging = staging,
                    onSaved = { updated ->
                        staging.wipe(item.rawFile)
                        onSessionChange(session.update(item.id) { updated })
                        markupItem = null
                        onStepChange(SessionStep.Review)
                    },
                    onCancel = {
                        markupItem = null
                        onStepChange(SessionStep.Review)
                    },
                )
            }
        }

        SessionStep.Archiving -> {
            ArchiveProgressScreen(progress = sendProgress, fraction = sendFraction)
        }

        SessionStep.Result -> {
            ArchiveResultScreen(
                success = resultSuccess,
                message = resultMessage,
                onDone = onFinished,
                onSeeLog = onOpenLog,
                onRetryReview = { onStepChange(SessionStep.Review) },
            )
        }
    }
}

@Composable
fun ArchivedPatientsPanel(
    records: List<ArchivedPatientStore.Record>,
    onAddImaging: (ArchivedPatientStore.Record) -> Unit,
) {
    SoftPanel {
        SectionLabel("Recently archived (4 hours)")
        Text(
            "Metadata only - images are wiped after successful PACS store.",
            style = MaterialTheme.typography.bodySmall,
            color = DicomColors.Slate700,
        )
        if (records.isEmpty()) {
            Text(
                "No archived patients in the last 4 hours.",
                style = MaterialTheme.typography.bodySmall,
                color = DicomColors.Slate500,
            )
        }
        records.forEach { record ->
            SoftPanel {
                Text(
                    "${record.patientId} · ${record.patientName}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    listOfNotNull(
                        record.accessionNumber?.let { "Acc $it" },
                        record.bodyPartExamined,
                        "${record.instanceCount} instance(s)",
                        record.ageLabel(),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = DicomColors.Slate700,
                )
                ForestButton(
                    text = "Add additional imaging",
                    onClick = { onAddImaging(record) },
                    modifier = Modifier.fillMaxWidth(),
                    compact = true,
                )
            }
        }
    }
}
