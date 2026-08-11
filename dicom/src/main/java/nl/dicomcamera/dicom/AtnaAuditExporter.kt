package nl.dicomcamera.dicom

import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * ATNA-style audit export for SIEM ingestion (file-based RFC5424 syslog lines).
 * Does not transmit off-device by itself — IT pulls or MDM syncs the export directory.
 */
class AtnaAuditExporter(
    private val exportDir: File,
    private val aet: String = "DICOMCAM",
) {
    init {
        exportDir.mkdirs()
    }

    data class ExportResult(val file: File, val eventCount: Int)

    fun exportFromCsv(auditCsv: File): ExportResult {
        val lines = if (auditCsv.exists()) auditCsv.readLines() else emptyList()
        val events = lines.drop(1).filter { it.isNotBlank() }
        val out = File(exportDir, "atna-${System.currentTimeMillis()}.log")
        out.printWriter().use { writer ->
            events.forEach { csvLine ->
                writer.println(toSyslog(csvLine))
            }
        }
        return ExportResult(out, events.size)
    }

    fun exportEvent(
        action: String,
        patientId: String = "",
        studyUid: String = "",
        sopUid: String = "",
        outcome: String = "0",
    ): File {
        val out = File(exportDir, "atna-live.log")
        val eventId = UUID.randomUUID().toString()
        val msg = buildString {
            append("<134>1 ") // local0 info
            append(Instant.now())
            append(' ')
            append(aet)
            append(" DICOMCamera ")
            append(eventId)
            append(" - ")
            append("DICOM=")
            append(action)
            if (patientId.isNotBlank()) append(" PatientID=$patientId")
            if (studyUid.isNotBlank()) append(" StudyUID=$studyUid")
            if (sopUid.isNotBlank()) append(" SopUID=$sopUid")
            append(" Outcome=$outcome")
        }
        out.appendText("$msg\n")
        return out
    }

    private fun toSyslog(csvLine: String): String {
        val cols = parseCsv(csvLine)
        val ts = cols.getOrElse(0) { Instant.now().toString() }
        val action = cols.getOrElse(1) { "unknown" }
        val patientId = cols.getOrElse(2) { "" }
        val studyUid = cols.getOrElse(3) { "" }
        val sopUid = cols.getOrElse(4) { "" }
        val detail = cols.getOrElse(5) { "" }
        return buildString {
            append("<134>1 ")
            append(ts)
            append(' ')
            append(aet)
            append(" DICOMCamera - - - ")
            append("DICOM=")
            append(action)
            if (patientId.isNotBlank()) append(" PatientID=$patientId")
            if (studyUid.isNotBlank()) append(" StudyUID=$studyUid")
            if (sopUid.isNotBlank()) append(" SopUID=$sopUid")
            if (detail.isNotBlank()) append(" Detail=$detail")
            append(" Outcome=0")
        }
    }

    private fun parseCsv(line: String): List<String> {
        val result = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    result += sb.toString()
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        result += sb.toString()
        return result
    }
}
