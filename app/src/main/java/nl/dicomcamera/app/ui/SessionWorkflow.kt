package nl.dicomcamera.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.dicomcamera.app.capture.SystemCameraCapture
import nl.dicomcamera.app.capture.SystemCameraContract
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
    onViewPending: () -> Unit = {},
    onStatus: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingCapture by remember { mutableStateOf<SystemCameraCapture.Pending?>(null) }
    var pendingPermissionPhoto by remember { mutableStateOf<Boolean?>(null) }
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
                seriesDescription = buildString {
                    append("Clinical photo/video")
                    if (form.bodyPartExamined.isNotBlank()) {
                        append(" · ")
                        append(form.bodyPartExamined)
                    }
                },
            ),
        )
    }

    fun ingestCapture(pending: SystemCameraCapture.Pending) {
        val kind = if (pending.photo) CaptureKind.PHOTO else CaptureKind.VIDEO
        val file = pending.stagingFile
        if (!file.exists() || file.length() == 0L) {
            SystemCameraCapture.abandon(context, pending)
            onStatus(if (pending.photo) "Camera returned an empty photo" else "Camera returned an empty video")
            diagnosticLog.log("camera_empty", if (pending.photo) "photo" else "video")
            return
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
                    framesPerSecond = if (kind == CaptureKind.VIDEO) 30 else 1,
                ),
            ),
        )
        diagnosticLog.log("capture", "${kind.name} ${file.name}")
        onStepChange(SessionStep.Review)
    }

    val systemCamera = rememberLauncherForActivityResult(SystemCameraContract()) { result ->
        val pending = pendingCapture
        pendingCapture = null
        if (pending == null) return@rememberLauncherForActivityResult
        if (!result.ok) {
            SystemCameraCapture.abandon(context, pending)
            if (session.items.isNotEmpty()) onStepChange(SessionStep.Review)
            else onStatus(if (pending.photo) "Photo capture cancelled" else "Video capture cancelled")
            diagnosticLog.log("camera_cancelled", if (pending.photo) "photo" else "video")
            return@rememberLauncherForActivityResult
        }
        val info = SystemCameraCapture.finalizeCapture(context, pending, result.data)
        diagnosticLog.log(
            "camera_finalize",
            "ok=${info.ok} ${info.width}x${info.height} bytes=${info.bytes} src=${info.source}" +
                (info.warning?.let { " warn=$it" } ?: ""),
        )
        if (!info.ok) {
            SystemCameraCapture.abandon(context, pending)
            onStatus(
                info.warning
                    ?: "Camera did not return a full-resolution image — try again",
            )
            return@rememberLauncherForActivityResult
        }
        if (info.warning != null) {
            onStatus(info.warning)
        }
        ingestCapture(pending)
    }

    fun launchSystemCameraNow(photo: Boolean) {
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
        val pending = try {
            SystemCameraCapture.prepare(context, staging, photo)
        } catch (e: Exception) {
            onStatus("Camera prepare failed: ${e.message}")
            diagnosticLog.log("camera_prepare_fail", e.message.orEmpty())
            return
        }
        pendingCapture = pending
        diagnosticLog.log(
            "camera_launch",
            buildString {
                append(if (photo) "photo" else "video")
                append(" extraOutput=").append(pending.useExtraOutput)
                append(" uri=").append(pending.outputUri)
            },
        )
        try {
            systemCamera.launch(pending)
        } catch (e: Exception) {
            pendingCapture = null
            SystemCameraCapture.abandon(context, pending)
            onStatus("Camera launch failed: ${e.message}")
            diagnosticLog.log("camera_launch_fail", e.message.orEmpty())
        }
    }

    fun capturePermissions(): Array<String> {
        val perms = mutableListOf(Manifest.permission.CAMERA)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            perms += Manifest.permission.READ_MEDIA_IMAGES
            perms += Manifest.permission.READ_MEDIA_VIDEO
        } else {
            perms += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return perms.toTypedArray()
    }

    fun hasAllCapturePermissions(): Boolean =
        capturePermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val photo = pendingPermissionPhoto
        pendingPermissionPhoto = null
        val cameraOk = grants[Manifest.permission.CAMERA] == true
        if (!cameraOk || photo == null) {
            onStatus("Camera permission is required to capture")
            diagnosticLog.log("camera_permission_denied", grants.toString())
            return@rememberLauncherForActivityResult
        }
        // Media read is optional (gallery fallback) — proceed even if denied.
        launchSystemCameraNow(photo)
    }

    fun startSystemCamera(photo: Boolean) {
        // ColorOS needs CAMERA; READ_MEDIA helps recover full-res from DCIM if EXTRA_OUTPUT is ignored.
        val cameraGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        if (cameraGranted && hasAllCapturePermissions()) {
            launchSystemCameraNow(photo)
            return
        }
        pendingPermissionPhoto = photo
        diagnosticLog.log("camera_permission_request", if (photo) "photo" else "video")
        cameraPermission.launch(capturePermissions())
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
                    if (!pacsSettings.isConfigured()) {
                        diagnosticLog.log(
                            "archive_pacs",
                            "PACS not configured — encoding and queuing locally",
                        )
                        onStatus("PACS not configured — items will go to the pending queue")
                    }
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
                            if (outcome.allSucceeded) {
                                "ok ${outcome.successCount}"
                            } else {
                                "fail queued=${outcome.failureCount} ${outcome.message}"
                            },
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
                            onArchivedRefresh()
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
                    onSaved = { markedUp ->
                        // Keep the original; mark-up is an additional capture in the session.
                        onSessionChange(session.add(markedUp))
                        diagnosticLog.log("markup_saved", "original=${item.id} markup=${markedUp.id}")
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
                onDone = {
                    onArchivedRefresh()
                    onFinished()
                },
                onSeeLog = onOpenLog,
                onRetryReview = { onStepChange(SessionStep.Review) },
                onViewPending = {
                    onArchivedRefresh()
                    onViewPending()
                },
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
