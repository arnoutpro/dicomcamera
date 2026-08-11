package nl.dicomcamera.app.session

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import nl.dicomcamera.dicom.AuditLog
import nl.dicomcamera.dicom.BatchStore
import nl.dicomcamera.dicom.PacsClient
import nl.dicomcamera.dicom.PatientStudyContext
import nl.dicomcamera.dicom.PendingStoreQueue
import nl.dicomcamera.dicom.PhotographicImageEncoder
import nl.dicomcamera.dicom.SecureStaging
import nl.dicomcamera.dicom.StoreResult
import nl.dicomcamera.dicom.VideoPhotographicEncoder
import nl.dicomcamera.app.settings.PacsSettings
import java.io.ByteArrayOutputStream
import java.io.File

private const val TAG = "SessionBatchSender"

data class BatchSendProgress(
    val currentIndex: Int,
    val total: Int,
    val itemId: String,
    val status: SessionItemStatus,
    val message: String,
)

data class BatchSendResult(
    val session: CaptureSession,
    val successCount: Int,
    val failureCount: Int,
    val message: String,
) {
    val allSucceeded: Boolean get() = failureCount == 0 && successCount > 0
}

/**
 * Encode each staged session item, C-STORE with retry/backoff, wipe on ACK, queue failures.
 */
class SessionBatchSender(
    private val staging: SecureStaging,
    private val pendingQueue: PendingStoreQueue,
    private val audit: AuditLog,
) {
    fun sendAll(
        session: CaptureSession,
        examContext: PatientStudyContext,
        settings: PacsSettings,
        examSource: String,
        onProgress: (BatchSendProgress) -> Unit = {},
    ): BatchSendResult {
        val toSend = session.items.filter {
            it.status == SessionItemStatus.STAGED || it.status == SessionItemStatus.FAILED
        }
        if (toSend.isEmpty()) {
            return BatchSendResult(session, 0, 0, "Nothing to send")
        }

        var working = session
        var successCount = 0
        var failureCount = 0
        val encodeContext = examContext.copy(
            studyInstanceUid = session.studyInstanceUid,
            seriesInstanceUid = session.seriesInstanceUid,
            seriesDescription = examContext.seriesDescription
                ?: "Clinical photo/video session",
        )

        // When Remote DICOM is not configured, still encode and park in the pending queue.
        if (!settings.isConfigured()) {
            toSend.forEachIndexed { index, item ->
                working = working.update(item.id) { it.copy(status = SessionItemStatus.ENCODING, error = null) }
                onProgress(
                    BatchSendProgress(
                        currentIndex = index + 1,
                        total = toSend.size,
                        itemId = item.id,
                        status = SessionItemStatus.ENCODING,
                        message = "Encoding ${item.label} ${index + 1}/${toSend.size} (PACS not configured)",
                    ),
                )
                val dicomFile = item.dicomFile?.takeIf { it.exists() }
                    ?: staging.createStagingFile(if (item.kind == CaptureKind.PHOTO) "vl" else "vid", "dcm")
                try {
                    when (item.kind) {
                        CaptureKind.PHOTO -> encodePhoto(item, encodeContext, dicomFile)
                        CaptureKind.VIDEO -> encodeVideo(item, encodeContext, dicomFile)
                    }
                    val err = "PACS not configured"
                    pendingQueue.enqueue(
                        dicomFile = dicomFile,
                        rawFile = item.rawFile.takeIf { it.exists() },
                        patientId = encodeContext.patientId,
                        patientName = encodeContext.patientName,
                        error = err,
                    )
                    audit.record(
                        action = "c_store_queued_unconfigured",
                        patientId = encodeContext.patientId,
                        studyUid = session.studyInstanceUid,
                        detail = "$examSource;${item.kind};$err",
                    )
                    working = working.update(item.id) {
                        it.copy(status = SessionItemStatus.FAILED, dicomFile = null, error = err)
                    }
                    failureCount++
                    onProgress(
                        BatchSendProgress(
                            currentIndex = index + 1,
                            total = toSend.size,
                            itemId = item.id,
                            status = SessionItemStatus.FAILED,
                            message = "Queued (PACS not configured)",
                        ),
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "encode for queue failed", e)
                    staging.wipe(dicomFile)
                    working = working.update(item.id) {
                        it.copy(
                            status = SessionItemStatus.FAILED,
                            dicomFile = null,
                            error = e.message ?: e.javaClass.simpleName,
                        )
                    }
                    failureCount++
                }
            }
            val message =
                "PACS not configured — queued $failureCount of ${toSend.size} for later store. Study ${session.studyInstanceUid}"
            return BatchSendResult(working, successCount, failureCount, message)
        }

        val batch = BatchStore(
            clientFactory = { PacsClient(settings.toNode()) },
            maxAttempts = 3,
            initialBackoffMs = 400,
        )

        toSend.forEachIndexed { index, item ->
            working = working.update(item.id) { it.copy(status = SessionItemStatus.ENCODING, error = null) }
            onProgress(
                BatchSendProgress(
                    currentIndex = index + 1,
                    total = toSend.size,
                    itemId = item.id,
                    status = SessionItemStatus.ENCODING,
                    message = "Encoding ${item.label} ${index + 1}/${toSend.size}",
                ),
            )

            val dicomFile = item.dicomFile?.takeIf { it.exists() }
                ?: staging.createStagingFile(if (item.kind == CaptureKind.PHOTO) "vl" else "vid", "dcm")

            try {
                when (item.kind) {
                    CaptureKind.PHOTO -> encodePhoto(item, encodeContext, dicomFile)
                    CaptureKind.VIDEO -> encodeVideo(item, encodeContext, dicomFile)
                }

                working = working.update(item.id) {
                    it.copy(status = SessionItemStatus.SENDING, dicomFile = dicomFile)
                }
                onProgress(
                    BatchSendProgress(
                        currentIndex = index + 1,
                        total = toSend.size,
                        itemId = item.id,
                        status = SessionItemStatus.SENDING,
                        message = "Sending ${item.label} ${index + 1}/${toSend.size}",
                    ),
                )

                val (storeResult, attempts) = batch.storeWithRetry(dicomFile)
                when (storeResult) {
                    is StoreResult.Success -> {
                        audit.record(
                            action = "c_store_success",
                            patientId = encodeContext.patientId,
                            studyUid = session.studyInstanceUid,
                            sopUid = storeResult.sopInstanceUid,
                            detail = "$examSource;${item.kind};attempts=$attempts",
                        )
                        staging.wipe(dicomFile)
                        staging.wipe(item.rawFile)
                        working = working.update(item.id) {
                            it.copy(
                                status = SessionItemStatus.STORED,
                                dicomFile = null,
                                sopInstanceUid = storeResult.sopInstanceUid,
                                error = null,
                            )
                        }
                        successCount++
                        onProgress(
                            BatchSendProgress(
                                currentIndex = index + 1,
                                total = toSend.size,
                                itemId = item.id,
                                status = SessionItemStatus.STORED,
                                message = "Stored ${item.label}",
                            ),
                        )
                    }
                    is StoreResult.Failed -> {
                        pendingQueue.enqueue(
                            dicomFile = dicomFile,
                            rawFile = item.rawFile.takeIf { it.exists() },
                            patientId = encodeContext.patientId,
                            patientName = encodeContext.patientName,
                            error = storeResult.message,
                        )
                        working = working.update(item.id) {
                            it.copy(
                                status = SessionItemStatus.FAILED,
                                dicomFile = null,
                                error = storeResult.message,
                            )
                        }
                        failureCount++
                        onProgress(
                            BatchSendProgress(
                                currentIndex = index + 1,
                                total = toSend.size,
                                itemId = item.id,
                                status = SessionItemStatus.FAILED,
                                message = "Failed: ${storeResult.message}",
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "send item failed", e)
                if (dicomFile.exists() && dicomFile.length() > 0) {
                    pendingQueue.enqueue(
                        dicomFile = dicomFile,
                        rawFile = item.rawFile.takeIf { it.exists() },
                        patientId = encodeContext.patientId,
                        patientName = encodeContext.patientName,
                        error = e.message ?: e.javaClass.simpleName,
                    )
                } else {
                    staging.wipe(dicomFile)
                }
                working = working.update(item.id) {
                    it.copy(
                        status = SessionItemStatus.FAILED,
                        dicomFile = null,
                        error = e.message ?: e.javaClass.simpleName,
                    )
                }
                failureCount++
                onProgress(
                    BatchSendProgress(
                        currentIndex = index + 1,
                        total = toSend.size,
                        itemId = item.id,
                        status = SessionItemStatus.FAILED,
                        message = "Failed: ${e.message}",
                    ),
                )
            }
        }

        val message = buildString {
            append("Stored $successCount of ${toSend.size}")
            if (failureCount > 0) append("; $failureCount failed (pending queue)")
            append(". Study ${session.studyInstanceUid}")
        }
        return BatchSendResult(working, successCount, failureCount, message)
    }

    private fun encodePhoto(item: SessionItem, context: PatientStudyContext, output: File) {
        val jpegBytes = item.rawFile.readBytes()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, bounds)
        var rows = item.rows.takeIf { it > 0 } ?: bounds.outHeight
        var columns = item.columns.takeIf { it > 0 } ?: bounds.outWidth
        var bytes = jpegBytes
        if (rows <= 0 || columns <= 0) {
            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                ?: error("Failed to decode JPEG")
            rows = bitmap.height
            columns = bitmap.width
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            bytes = stream.toByteArray()
            bitmap.recycle()
        }
        PhotographicImageEncoder().encodeJpegToFile(
            jpegBytes = bytes,
            context = context,
            rows = rows,
            columns = columns,
            outputFile = output,
        )
    }

    private fun encodeVideo(item: SessionItem, context: PatientStudyContext, output: File) {
        val mp4 = item.rawFile.readBytes()
        VideoPhotographicEncoder().encodeMp4ToFile(
            mp4Bytes = mp4,
            context = context,
            rows = item.rows.coerceAtLeast(1),
            columns = item.columns.coerceAtLeast(1),
            frameCount = item.frameCount.coerceAtLeast(1),
            framesPerSecond = item.framesPerSecond.coerceAtLeast(1),
            outputFile = output,
        )
    }

    fun discardItem(session: CaptureSession, id: String): CaptureSession {
        val item = session.items.firstOrNull { it.id == id } ?: return session
        item.dicomFile?.let { staging.wipe(it) }
        staging.wipe(item.rawFile)
        return session.remove(id)
    }

    fun discardSession(session: CaptureSession): CaptureSession {
        session.items.forEach { item ->
            item.dicomFile?.let { staging.wipe(it) }
            if (item.status != SessionItemStatus.STORED) {
                staging.wipe(item.rawFile)
            }
        }
        return session.clear()
    }
}
