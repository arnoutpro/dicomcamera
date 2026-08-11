package nl.dicomcamera.app.demo

import nl.dicomcamera.dicom.PatientStudyContext
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Metadata-only record of successfully archived patients (no pixel data).
 * Entries older than [TTL_MS] are purged — images must already be wiped on PACS success.
 */
class ArchivedPatientStore(
    private val rootDir: File,
) {
    init {
        rootDir.mkdirs()
    }

    data class Record(
        val id: String,
        val patientId: String,
        val patientName: String,
        val birthDate: String?,
        val sex: String?,
        val accessionNumber: String?,
        val studyDescription: String?,
        val studyInstanceUid: String,
        val seriesInstanceUid: String,
        val bodyPartExamined: String?,
        val laterality: String?,
        val modality: String,
        val archivedAtEpochMs: Long,
        val instanceCount: Int,
    ) {
        fun toContext(newSeries: Boolean = true): PatientStudyContext = PatientStudyContext(
            patientId = patientId,
            patientName = patientName,
            patientBirthDate = birthDate,
            patientSex = sex,
            accessionNumber = accessionNumber,
            studyDescription = studyDescription,
            studyInstanceUid = studyInstanceUid,
            seriesInstanceUid = if (newSeries) null else seriesInstanceUid,
            seriesDescription = "Additional clinical photo/video",
            modality = modality.ifBlank { "XC" },
            bodyPartExamined = null, // step 2 again — pick body part fresh
            laterality = null,
        )

        fun ageLabel(now: Long = System.currentTimeMillis()): String {
            val mins = TimeUnit.MILLISECONDS.toMinutes(now - archivedAtEpochMs).coerceAtLeast(0)
            return if (mins < 60) "${mins}m ago" else "${mins / 60}h ${mins % 60}m ago"
        }
    }

    fun recordSuccessfulArchive(
        context: PatientStudyContext,
        studyInstanceUid: String,
        seriesInstanceUid: String,
        instanceCount: Int,
    ): Record {
        val id = UUID.randomUUID().toString()
        val archivedAt = System.currentTimeMillis()
        val record = Record(
            id = id,
            patientId = context.patientId,
            patientName = context.patientName,
            birthDate = context.patientBirthDate,
            sex = context.patientSex,
            accessionNumber = context.accessionNumber,
            studyDescription = context.studyDescription,
            studyInstanceUid = studyInstanceUid,
            seriesInstanceUid = seriesInstanceUid,
            bodyPartExamined = context.bodyPartExamined,
            laterality = context.laterality,
            modality = context.modality,
            archivedAtEpochMs = archivedAt,
            instanceCount = instanceCount,
        )
        val meta = JSONObject()
            .put("patientId", record.patientId)
            .put("patientName", record.patientName)
            .put("birthDate", record.birthDate)
            .put("sex", record.sex)
            .put("accessionNumber", record.accessionNumber)
            .put("studyDescription", record.studyDescription)
            .put("studyInstanceUid", record.studyInstanceUid)
            .put("seriesInstanceUid", record.seriesInstanceUid)
            .put("bodyPartExamined", record.bodyPartExamined)
            .put("laterality", record.laterality)
            .put("modality", record.modality)
            .put("archivedAt", record.archivedAtEpochMs)
            .put("instanceCount", record.instanceCount)
        File(rootDir, "$id.json").writeText(meta.toString())
        return record
    }

    fun list(): List<Record> {
        purgeExpired()
        return rootDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") }
            ?.mapNotNull { read(it) }
            ?.sortedByDescending { it.archivedAtEpochMs }
            .orEmpty()
    }

    /** Deletes metadata older than 4 hours. Never stores images here. */
    fun purgeExpired(now: Long = System.currentTimeMillis()): Int {
        var removed = 0
        rootDir.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }?.forEach { file ->
            val record = read(file) ?: run {
                file.delete()
                removed++
                return@forEach
            }
            if (now - record.archivedAtEpochMs > TTL_MS) {
                file.delete()
                removed++
            }
        }
        return removed
    }

    private fun read(file: File): Record? = runCatching {
        val meta = JSONObject(file.readText())
        Record(
            id = file.nameWithoutExtension,
            patientId = meta.getString("patientId"),
            patientName = meta.getString("patientName"),
            birthDate = meta.optString("birthDate").takeIf { it.isNotBlank() && it != "null" },
            sex = meta.optString("sex").takeIf { it.isNotBlank() && it != "null" },
            accessionNumber = meta.optString("accessionNumber").takeIf { it.isNotBlank() && it != "null" },
            studyDescription = meta.optString("studyDescription").takeIf { it.isNotBlank() && it != "null" },
            studyInstanceUid = meta.getString("studyInstanceUid"),
            seriesInstanceUid = meta.getString("seriesInstanceUid"),
            bodyPartExamined = meta.optString("bodyPartExamined").takeIf { it.isNotBlank() && it != "null" },
            laterality = meta.optString("laterality").takeIf { it.isNotBlank() && it != "null" },
            modality = meta.optString("modality", "XC"),
            archivedAtEpochMs = meta.getLong("archivedAt"),
            instanceCount = meta.optInt("instanceCount", 0),
        )
    }.getOrNull()

    companion object {
        val TTL_MS: Long = TimeUnit.HOURS.toMillis(4)
    }
}
