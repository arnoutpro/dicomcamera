package nl.dicomcamera.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.widget.VideoView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import nl.dicomcamera.app.session.BodyParts
import nl.dicomcamera.app.session.CaptureKind
import nl.dicomcamera.app.session.CaptureSession
import nl.dicomcamera.app.session.ManualPatientForm
import nl.dicomcamera.app.session.SessionItem
import nl.dicomcamera.app.session.SessionItemStatus
import nl.dicomcamera.app.ui.components.DicomTextField
import nl.dicomcamera.app.ui.components.ForestButton
import nl.dicomcamera.app.ui.components.MetaChip
import nl.dicomcamera.app.ui.components.QuietOutlinedButton
import nl.dicomcamera.app.ui.components.SectionLabel
import nl.dicomcamera.app.ui.components.SoftPanel
import nl.dicomcamera.app.ui.components.StatusBanner
import nl.dicomcamera.app.ui.components.StatusTone
import nl.dicomcamera.app.ui.theme.DicomColors
import nl.dicomcamera.app.ui.theme.DicomShapes
import nl.dicomcamera.dicom.SecureStaging
import java.io.File
import java.io.FileOutputStream

/**
 * Step 1–2: patient demographics + optional body part, then system camera.
 */
@Composable
fun PatientSetupScreen(
    patient: ManualPatientForm,
    onPatientChange: (ManualPatientForm) -> Unit,
    onCapturePhoto: () -> Unit,
    onCaptureVideo: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SoftPanel {
            SectionLabel("Patient details")
            Text(
                "Confirm identity before capture.",
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
                label = "Name * (FAMILY^GIVEN)",
            )
            DicomTextField(
                value = patient.birthDate,
                onValueChange = {
                    onPatientChange(patient.copy(birthDate = it.filter { ch -> ch.isDigit() }.take(8)))
                },
                label = "Birth date (YYYYMMDD)",
            )
            SectionLabel("Sex")
            WorkflowChoiceRow(
                options = listOf("" to "-", "M" to "M", "F" to "F", "O" to "O"),
                selected = patient.sex,
                onSelect = { onPatientChange(patient.copy(sex = it)) },
            )
        }

        SoftPanel {
            SectionLabel("Body part")
            Text(
                "Optional anatomic region for this series.",
                style = MaterialTheme.typography.bodySmall,
                color = DicomColors.Slate700,
            )
            BodyPartGrid(
                selected = patient.bodyPartExamined,
                onSelect = { onPatientChange(patient.copy(bodyPartExamined = it)) },
            )
            SectionLabel("Laterality")
            WorkflowChoiceRow(
                options = listOf("" to "-", "L" to "L", "R" to "R", "U" to "U"),
                selected = patient.laterality,
                onSelect = { onPatientChange(patient.copy(laterality = it)) },
            )
        }

        val canCapture = patient.patientId.isNotBlank() && patient.patientName.isNotBlank()

        ForestButton(
            text = "Capture photo",
            onClick = onCapturePhoto,
            enabled = canCapture,
            modifier = Modifier.fillMaxWidth(),
        )
        QuietOutlinedButton(
            text = "Capture video",
            onClick = onCaptureVideo,
            enabled = canCapture,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!canCapture) {
            Text(
                "Patient ID and Name are required to capture.",
                style = MaterialTheme.typography.bodySmall,
                color = DicomColors.Slate500,
            )
        }
        QuietOutlinedButton(
            text = "Cancel",
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BodyPartGrid(
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BodyParts.options.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { (code, label) ->
                    val isSelected = selected == code
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) DicomColors.White else DicomColors.Ink,
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (isSelected) DicomColors.Forest else DicomColors.White,
                                DicomShapes.Control,
                            )
                            .border(1.dp, if (isSelected) DicomColors.Forest else DicomColors.Hairline, DicomShapes.Control)
                            .clickable { onSelect(code) }
                            .padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                repeat(3 - row.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun ReviewSessionScreen(
    patientBanner: String,
    bodyPart: String,
    session: CaptureSession,
    staging: SecureStaging,
    onDeleteItem: (String) -> Unit,
    onMarkupItem: (SessionItem) -> Unit,
    onCaptureMorePhoto: () -> Unit,
    onCaptureMoreVideo: () -> Unit,
    onArchiveToPacs: () -> Unit,
    onCancelAll: () -> Unit,
    pacsConfigured: Boolean,
) {
    var previewItem by remember { mutableStateOf<SessionItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SoftPanel {
            SectionLabel("Review")
            Text(patientBanner, style = MaterialTheme.typography.titleSmall)
            if (bodyPart.isNotBlank()) {
                MetaChip(text = "Body part · $bodyPart", foreground = DicomColors.ForestMid)
            }
            Text(
                "${session.items.size} capture(s) · review before archiving to PACS",
                style = MaterialTheme.typography.bodySmall,
                color = DicomColors.Slate700,
            )
        }

        if (session.items.isEmpty()) {
            StatusBanner(
                text = "No captures yet. Take a photo or video.",
                tone = StatusTone.Warn,
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                session.items.forEach { item ->
                    ReviewThumb(
                        item = item,
                        onOpen = { previewItem = item },
                        onDelete = { onDeleteItem(item.id) },
                        onMarkup = { onMarkupItem(item) },
                    )
                }
            }
        }

        previewItem?.let { item ->
            SoftPanel {
                SectionLabel("Preview")
                ReviewFullPreview(item = item)
                QuietOutlinedButton(
                    text = "Close preview",
                    onClick = { previewItem = null },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        SoftPanel {
            SectionLabel("Continue")
            ForestButton(
                text = "Take another photo",
                onClick = onCaptureMorePhoto,
                modifier = Modifier.fillMaxWidth(),
            )
            QuietOutlinedButton(
                text = "Take another video",
                onClick = onCaptureMoreVideo,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ForestButton(
            text = if (pacsConfigured) {
                "Archive to PACS"
            } else {
                "Archive to PACS (queue if not configured)"
            },
            onClick = onArchiveToPacs,
            enabled = session.pendingSendCount > 0,
            modifier = Modifier.fillMaxWidth(),
        )
        QuietOutlinedButton(
            text = "Cancel ALL",
            onClick = onCancelAll,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!pacsConfigured) {
            Text(
                "Remote DICOM is not configured yet. Archive will encode and place items in the pending queue.",
                style = MaterialTheme.typography.bodySmall,
                color = DicomColors.Slate500,
            )
        }
    }
}

@Composable
private fun ReviewThumb(
    item: SessionItem,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onMarkup: () -> Unit,
) {
    val context = LocalContext.current
    val thumb = remember(item.id, item.rawFile.path) {
        loadThumbBitmap(item)
    }
    Column(
        modifier = Modifier
            .width(120.dp)
            .border(1.dp, DicomColors.Hairline, DicomShapes.Panel)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clipBackground()
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = item.label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(item.label, style = MaterialTheme.typography.labelMedium)
            }
            MetaChip(
                text = item.label,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
                foreground = DicomColors.ForestMid,
            )
        }
        QuietOutlinedButton(text = "Delete", onClick = onDelete, modifier = Modifier.fillMaxWidth())
        if (item.kind == CaptureKind.PHOTO) {
            QuietOutlinedButton(text = "Mark up", onClick = onMarkup, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun Modifier.clipBackground(): Modifier =
    this
        .background(DicomColors.White, DicomShapes.Thumb)
        .border(1.dp, DicomColors.Hairline, DicomShapes.Thumb)

/** Thumbnail decode (~320px). */
private fun loadThumbBitmap(item: SessionItem): Bitmap? =
    loadBitmapForDisplay(item, maxEdge = 320)

/** Review / mark-up display decode (~1600px) — avoids stretching a 256px thumb. */
private fun loadPreviewBitmap(item: SessionItem): Bitmap? =
    loadBitmapForDisplay(item, maxEdge = 1600)

private fun loadBitmapForDisplay(item: SessionItem, maxEdge: Int): Bitmap? {
    if (!item.rawFile.exists() || item.rawFile.length() == 0L) return null
    return try {
        when (item.kind) {
            CaptureKind.PHOTO -> {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(item.rawFile.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
                val sample = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / maxEdge)
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeFile(item.rawFile.absolutePath, opts)
            }
            CaptureKind.VIDEO -> loadVideoFrame(item.rawFile, maxEdge)
        }
    } catch (_: Exception) {
        null
    }
}

private fun loadVideoFrame(file: File, maxEdge: Int): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val durationMs = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_DURATION,
        )?.toLongOrNull() ?: 0L
        val candidatesUs = listOf(
            0L,
            1_000_000L,
            (durationMs * 1000L) / 2,
        ).distinct()
        for (timeUs in candidatesUs) {
            val frame = if (android.os.Build.VERSION.SDK_INT >= 27) {
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST,
                    maxEdge,
                    maxEdge,
                )
            } else {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
            if (frame != null) return frame
        }
        null
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

@Composable
private fun ReviewFullPreview(item: SessionItem) {
    when (item.kind) {
        CaptureKind.PHOTO -> {
            val bmp = remember(item.id, item.rawFile.path) { loadPreviewBitmap(item) }
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = item.label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 280.dp, max = 480.dp),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.High,
                )
            } else {
                Text("Preview unavailable", color = DicomColors.Slate500)
            }
        }
        CaptureKind.VIDEO -> {
            if (!item.rawFile.exists()) {
                Text("Video file missing", color = DicomColors.Slate500)
                return
            }
            var playError by remember(item.id) { mutableStateOf<String?>(null) }
            val thumb = remember(item.id, item.rawFile.path) { loadPreviewBitmap(item) }
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoPath(item.rawFile.absolutePath)
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            start()
                        }
                        setOnErrorListener { _, what, extra ->
                            playError = "Video play error ($what/$extra)"
                            true
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(DicomColors.Ink),
                update = { view ->
                    if (view.tag != item.rawFile.absolutePath) {
                        view.tag = item.rawFile.absolutePath
                        view.setVideoPath(item.rawFile.absolutePath)
                        view.start()
                    }
                },
            )
            if (playError != null) {
                Text(playError!!, color = DicomColors.Rose, style = MaterialTheme.typography.bodySmall)
                if (thumb != null) {
                    Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = item.label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.High,
                    )
                }
            }
        }
    }
}

@Composable
fun MarkupScreen(
    item: SessionItem,
    staging: SecureStaging,
    onSaved: (SessionItem) -> Unit,
    onCancel: () -> Unit,
) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    // Full-resolution source for baking; display uses a sharper downsample.
    val baseFull = remember(item.id, item.rawFile.path) {
        BitmapFactory.decodeFile(
            item.rawFile.absolutePath,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
        )
    }
    val displayBmp = remember(item.id, item.rawFile.path) { loadPreviewBitmap(item) }
    val imageBitmap = remember(displayBmp) { displayBmp?.asImageBitmap() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SoftPanel {
            SectionLabel("Mark up")
            Text(
                "Draw on the photo, then save. Creates a new image — the original stays in the session.",
                style = MaterialTheme.typography.bodySmall,
                color = DicomColors.Slate700,
            )
        }
        if (imageBitmap == null || baseFull == null || displayBmp == null) {
            StatusBanner(text = "Could not load photo for mark-up", tone = StatusTone.Error)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, DicomColors.Hairline, DicomShapes.Panel),
            ) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset -> current = listOf(offset) },
                                onDragEnd = {
                                    if (current.isNotEmpty()) strokes.add(current)
                                    current = emptyList()
                                },
                                onDragCancel = { current = emptyList() },
                                onDrag = { change, _ ->
                                    change.consume()
                                    current = current + change.position
                                },
                            )
                        },
                ) {
                    canvasSize = size
                    val scale = minOf(size.width / imageBitmap.width, size.height / imageBitmap.height)
                    val dw = imageBitmap.width * scale
                    val dh = imageBitmap.height * scale
                    val left = (size.width - dw) / 2f
                    val top = (size.height - dh) / 2f
                    drawImage(
                        image = imageBitmap,
                        dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                        dstSize = androidx.compose.ui.unit.IntSize(
                            dw.toInt().coerceAtLeast(1),
                            dh.toInt().coerceAtLeast(1),
                        ),
                        filterQuality = FilterQuality.High,
                    )
                    val strokeColor = Color(0xFFE11D48)
                    (strokes + listOf(current)).forEach { path ->
                        for (i in 1 until path.size) {
                            drawLine(
                                color = strokeColor,
                                start = path[i - 1],
                                end = path[i],
                                strokeWidth = 6f,
                            )
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            QuietOutlinedButton(text = "Clear", onClick = { strokes.clear() })
            QuietOutlinedButton(text = "Cancel", onClick = onCancel)
            ForestButton(
                text = "Save mark-up",
                onClick = {
                    val src = baseFull ?: return@ForestButton
                    val display = displayBmp ?: return@ForestButton
                    val outDir = File(staging.directory, "camera").also { it.mkdirs() }
                    val out = File(outDir, "markup-${System.currentTimeMillis()}.jpg")
                    val baked = src.copy(Bitmap.Config.ARGB_8888, true)
                    val canvas = android.graphics.Canvas(baked)
                    val paint = Paint().apply {
                        color = android.graphics.Color.rgb(225, 29, 72)
                        strokeWidth = (src.width / 120f).coerceAtLeast(4f)
                        style = Paint.Style.STROKE
                        isAntiAlias = true
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }
                    // Map strokes from view coords → full bitmap using the same letterbox as the canvas.
                    val viewW = canvasSize.width.coerceAtLeast(1f)
                    val viewH = canvasSize.height.coerceAtLeast(1f)
                    val fit = minOf(viewW / display.width, viewH / display.height)
                    val dw = display.width * fit
                    val dh = display.height * fit
                    val left = (viewW - dw) / 2f
                    val top = (viewH - dh) / 2f
                    val sx = src.width / dw
                    val sy = src.height / dh
                    strokes.forEach { path ->
                        for (i in 1 until path.size) {
                            canvas.drawLine(
                                (path[i - 1].x - left) * sx,
                                (path[i - 1].y - top) * sy,
                                (path[i].x - left) * sx,
                                (path[i].y - top) * sy,
                                paint,
                            )
                        }
                    }
                    FileOutputStream(out).use { fos ->
                        baked.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                    }
                    baked.recycle()
                    onSaved(
                        SessionItem(
                            kind = CaptureKind.PHOTO,
                            rawFile = out,
                            rows = src.height,
                            columns = src.width,
                            status = SessionItemStatus.STAGED,
                            displayLabel = "Mark-up",
                        ),
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun ArchiveProgressScreen(
    progress: String,
    fraction: Float?,
) {
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
                SectionLabel("Archiving to PACS")
                if (fraction != null) {
                    LinearProgressIndicator(
                        progress = { fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = DicomColors.Forest,
                        trackColor = DicomColors.Hairline,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = DicomColors.Forest,
                        trackColor = DicomColors.Hairline,
                    )
                }
                Text(progress, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun ArchiveResultScreen(
    success: Boolean,
    message: String,
    onDone: () -> Unit,
    onSeeLog: () -> Unit,
    onRetryReview: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StatusBanner(
            text = if (success) "Success" else "Archiving failed",
            tone = if (success) StatusTone.Success else StatusTone.Error,
        )
        SoftPanel {
            Text(message, style = MaterialTheme.typography.bodyLarge)
            if (!success) {
                Text(
                    "Contact your PACS administrator if the problem continues.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DicomColors.Slate700,
                )
            } else {
                Text(
                    "Images were wiped from this device after successful store.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DicomColors.Slate700,
                )
            }
        }
        if (!success) {
            ForestButton(
                text = "See Log",
                onClick = onSeeLog,
                modifier = Modifier.fillMaxWidth(),
            )
            QuietOutlinedButton(
                text = "Back to review",
                onClick = onRetryReview,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ForestButton(
            text = "Done",
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun WorkflowChoiceRow(
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

fun readCaptureMeta(file: File, kind: CaptureKind): Triple<Int, Int, Int> {
    return when (kind) {
        CaptureKind.PHOTO -> {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            Triple(opts.outHeight.coerceAtLeast(1), opts.outWidth.coerceAtLeast(1), 1)
        }
        CaptureKind.VIDEO -> {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 640
                val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 480
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 1000L
                val fps = 30
                val frames = ((durationMs / 1000.0) * fps).toInt().coerceAtLeast(1)
                Triple(h, w, frames)
            } catch (_: Exception) {
                Triple(480, 640, 30)
            } finally {
                runCatching { retriever.release() }
            }
        }
    }
}
