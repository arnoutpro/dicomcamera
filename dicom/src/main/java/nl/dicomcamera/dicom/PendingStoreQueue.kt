package nl.dicomcamera.dicom

import java.io.File
import java.util.UUID

/**
 * Ephemeral failure queue: keeps DICOM (and optional raw JPEG) only until retry succeeds or user discards.
 * Never writes to the system gallery.
 */
class PendingStoreQueue(
    private val rootDir: File,
    private val staging: SecureStaging,
) {
    init {
        rootDir.mkdirs()
    }

    data class PendingItem(
        val id: String,
        val directory: File,
        val dicomFile: File,
        val rawFile: File?,
        val patientId: String,
        val patientName: String,
        val lastError: String,
        val createdAtEpochMs: Long,
    )

    fun enqueue(
        dicomFile: File,
        rawFile: File?,
        patientId: String,
        patientName: String,
        error: String,
    ): PendingItem {
        val id = UUID.randomUUID().toString()
        val dir = File(rootDir, id).also { check(it.mkdirs()) { "Cannot create pending dir" } }
        val destDicom = File(dir, "instance.dcm")
        check(dicomFile.copyTo(destDicom, overwrite = true).exists())
        staging.wipe(dicomFile)

        val destRaw = rawFile?.let { raw ->
            val out = File(dir, "raw.jpg")
            raw.copyTo(out, overwrite = true)
            staging.wipe(raw)
            out
        }

        File(dir, "meta.txt").writeText(
            buildString {
                appendLine("patientId=$patientId")
                appendLine("patientName=$patientName")
                appendLine("error=$error")
                appendLine("createdAt=${System.currentTimeMillis()}")
            },
        )

        return PendingItem(
            id = id,
            directory = dir,
            dicomFile = destDicom,
            rawFile = destRaw,
            patientId = patientId,
            patientName = patientName,
            lastError = error,
            createdAtEpochMs = System.currentTimeMillis(),
        )
    }

    fun list(): List<PendingItem> {
        return rootDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir -> readItem(dir) }
            ?.sortedByDescending { it.createdAtEpochMs }
            .orEmpty()
    }

    fun discard(id: String): Boolean {
        val dir = File(rootDir, id)
        if (!dir.exists()) return false
        dir.walkBottomUp().forEach { file ->
            if (file.isFile) staging.wipe(file)
            else file.delete()
        }
        return !dir.exists()
    }

    fun markStoredAndWipe(id: String) {
        discard(id)
    }

    private fun readItem(dir: File): PendingItem? {
        val dicom = File(dir, "instance.dcm")
        if (!dicom.exists()) return null
        val meta = File(dir, "meta.txt").takeIf { it.exists() }?.readLines().orEmpty()
        fun value(key: String) = meta.firstOrNull { it.startsWith("$key=") }?.substringAfter("=").orEmpty()
        val raw = File(dir, "raw.jpg").takeIf { it.exists() }
        return PendingItem(
            id = dir.name,
            directory = dir,
            dicomFile = dicom,
            rawFile = raw,
            patientId = value("patientId").ifBlank { "?" },
            patientName = value("patientName").ifBlank { "?" },
            lastError = value("error").ifBlank { "unknown" },
            createdAtEpochMs = value("createdAt").toLongOrNull() ?: dir.lastModified(),
        )
    }
}
