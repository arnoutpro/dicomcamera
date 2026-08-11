package nl.dicomcamera.app.session

import nl.dicomcamera.dicom.DicomUid
import java.io.File
import java.util.UUID

enum class CaptureKind {
    PHOTO,
    VIDEO,
}

enum class SessionItemStatus {
    STAGED,
    ENCODING,
    SENDING,
    STORED,
    FAILED,
}

data class SessionItem(
    val id: String = UUID.randomUUID().toString(),
    val kind: CaptureKind,
    val rawFile: File,
    val rows: Int,
    val columns: Int,
    val frameCount: Int = 1,
    val framesPerSecond: Int = 30,
    val dicomFile: File? = null,
    val status: SessionItemStatus = SessionItemStatus.STAGED,
    val error: String? = null,
    val sopInstanceUid: String? = null,
) {
    val label: String
        get() = when (kind) {
            CaptureKind.PHOTO -> "Photo"
            CaptureKind.VIDEO -> "Video"
        }
}

/**
 * Ephemeral multi-shot tray for one exam session. Study/Series UIDs are fixed for the session
 * so mixed photo+video land in the same study/series.
 */
data class CaptureSession(
    val studyInstanceUid: String = DicomUid.newUid(),
    val seriesInstanceUid: String = DicomUid.newUid(),
    val items: List<SessionItem> = emptyList(),
) {
    val stagedCount: Int get() = items.count { it.status == SessionItemStatus.STAGED }
    val failedCount: Int get() = items.count { it.status == SessionItemStatus.FAILED }
    val storedCount: Int get() = items.count { it.status == SessionItemStatus.STORED }
    val pendingSendCount: Int
        get() = items.count {
            it.status == SessionItemStatus.STAGED || it.status == SessionItemStatus.FAILED
        }

    fun add(item: SessionItem): CaptureSession = copy(items = items + item)

    fun remove(id: String): CaptureSession = copy(items = items.filterNot { it.id == id })

    fun update(id: String, transform: (SessionItem) -> SessionItem): CaptureSession =
        copy(items = items.map { if (it.id == id) transform(it) else it })

    fun clear(): CaptureSession = copy(items = emptyList())
}
