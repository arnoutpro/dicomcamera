package nl.dicomcamera.dicom

import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Ephemeral failure queue: keeps DICOM (and optional raw) until manual retry succeeds,
 * user discards, or the 4-hour TTL expires. Never writes to the system gallery.
 * Retries are always user-initiated — never automatic.
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
        val studyInstanceUid: String,
        val lastError: String,
        val createdAtEpochMs: Long,
    ) {
        val ageMs: Long get() = (System.currentTimeMillis() - createdAtEpochMs).coerceAtLeast(0L)
        val remainingMs: Long get() = (TTL_MS - ageMs).coerceAtLeast(0L)
    }

    data class PatientGroup(
        val patientId: String,
        val patientName: String,
        val studyInstanceUid: String,
        val items: List<PendingItem>,
        val latestError: String,
        val createdAtEpochMs: Long,
    ) {
        val instanceCount: Int get() = items.size
    }

    fun enqueue(
        dicomFile: File,
        rawFile: File?,
        patientId: String,
        patientName: String,
        error: String,
        studyInstanceUid: String = "",
    ): PendingItem {
        val id = UUID.randomUUID().toString()
        val dir = File(rootDir, id).also { check(it.mkdirs()) { "Cannot create pending dir" } }
        val destDicom = File(dir, "instance.dcm")
        check(dicomFile.copyTo(destDicom, overwrite = true).exists())
        staging.wipe(dicomFile)

        val destRaw = rawFile?.let { raw ->
            val outName = when {
                raw.name.endsWith(".mp4", ignoreCase = true) -> "raw.mp4"
                raw.name.endsWith(".jpg", ignoreCase = true) -> "raw.jpg"
                raw.name.endsWith(".jpeg", ignoreCase = true) -> "raw.jpg"
                else -> "raw.bin"
            }
            val out = File(dir, outName)
            raw.copyTo(out, overwrite = true)
            staging.wipe(raw)
            out
        }

        val createdAt = System.currentTimeMillis()
        File(dir, "meta.txt").writeText(
            buildString {
                appendLine("patientId=$patientId")
                appendLine("patientName=$patientName")
                appendLine("studyInstanceUid=$studyInstanceUid")
                appendLine("error=$error")
                appendLine("createdAt=$createdAt")
            },
        )

        return PendingItem(
            id = id,
            directory = dir,
            dicomFile = destDicom,
            rawFile = destRaw,
            patientId = patientId,
            patientName = patientName,
            studyInstanceUid = studyInstanceUid,
            lastError = error,
            createdAtEpochMs = createdAt,
        )
    }

    fun list(): List<PendingItem> {
        purgeExpired()
        return rootDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir -> readItem(dir) }
            ?.sortedByDescending { it.createdAtEpochMs }
            .orEmpty()
    }

    /** Group pending instances by patient (and study when present) for Archive / Pending UI. */
    fun listGroupedByPatient(): List<PatientGroup> {
        return list()
            .groupBy { item ->
                listOf(item.patientId, item.studyInstanceUid).joinToString("|")
            }
            .values
            .map { items ->
                val newest = items.maxBy { it.createdAtEpochMs }
                PatientGroup(
                    patientId = newest.patientId,
                    patientName = newest.patientName,
                    studyInstanceUid = newest.studyInstanceUid,
                    items = items.sortedByDescending { it.createdAtEpochMs },
                    latestError = newest.lastError,
                    createdAtEpochMs = newest.createdAtEpochMs,
                )
            }
            .sortedByDescending { it.createdAtEpochMs }
    }

    fun discard(id: String): Boolean {
        val dir = File(rootDir, id)
        if (!dir.exists()) return false
        wipePendingDirectory(dir)
        return !dir.exists()
    }

    fun markStoredAndWipe(id: String) {
        discard(id)
    }

    fun discardPatient(patientId: String, studyInstanceUid: String = ""): Int {
        return list()
            .filter {
                it.patientId == patientId &&
                    (studyInstanceUid.isBlank() || it.studyInstanceUid == studyInstanceUid)
            }
            .count { discard(it.id) }
    }

    /** Drop entries older than [TTL_MS]. Returns number of discarded directories. */
    fun purgeExpired(nowEpochMs: Long = System.currentTimeMillis()): Int {
        val cutoff = nowEpochMs - TTL_MS
        var removed = 0
        rootDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            val item = readItem(dir) ?: run {
                // Unreadable / partial dirs may still hold raw.jpg + meta (PHI).
                // Never use plain deleteRecursively — match discard()'s secure wipe.
                wipePendingDirectory(dir)
                removed++
                return@forEach
            }
            if (item.createdAtEpochMs < cutoff) {
                if (discard(item.id)) removed++
            }
        }
        return removed
    }

    /** Securely overwrite file contents then remove the pending entry directory. */
    private fun wipePendingDirectory(dir: File) {
        dir.walkBottomUp().forEach { file ->
            if (file.isFile) staging.wipe(file)
            else file.delete()
        }
    }

    private fun readItem(dir: File): PendingItem? {
        val dicom = File(dir, "instance.dcm")
        if (!dicom.exists()) return null
        val meta = File(dir, "meta.txt").takeIf { it.exists() }?.readLines().orEmpty()
        fun value(key: String) = meta.firstOrNull { it.startsWith("$key=") }?.substringAfter("=").orEmpty()
        val raw = listOf("raw.jpg", "raw.mp4", "raw.bin")
            .map { File(dir, it) }
            .firstOrNull { it.exists() }
        return PendingItem(
            id = dir.name,
            directory = dir,
            dicomFile = dicom,
            rawFile = raw,
            patientId = value("patientId").ifBlank { "?" },
            patientName = value("patientName").ifBlank { "?" },
            studyInstanceUid = value("studyInstanceUid"),
            lastError = value("error").ifBlank { "unknown" },
            createdAtEpochMs = value("createdAt").toLongOrNull() ?: dir.lastModified(),
        )
    }

    companion object {
        val TTL_MS: Long = TimeUnit.HOURS.toMillis(4)
    }
}
