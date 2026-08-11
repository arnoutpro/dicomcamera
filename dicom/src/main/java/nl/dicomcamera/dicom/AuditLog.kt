package nl.dicomcamera.dicom

import java.io.File
import java.time.Instant

/**
 * Append-only local audit trail (technical metadata, no pixel data).
 */
class AuditLog(
    private val file: File,
) {
    init {
        file.parentFile?.mkdirs()
        if (!file.exists()) {
            file.writeText("timestamp,action,patientId,studyUid,sopUid,detail\n")
        }
    }

    @Synchronized
    fun record(
        action: String,
        patientId: String = "",
        studyUid: String = "",
        sopUid: String = "",
        detail: String = "",
    ) {
        val line = listOf(
            Instant.now().toString(),
            csv(action),
            csv(patientId),
            csv(studyUid),
            csv(sopUid),
            csv(detail),
        ).joinToString(",")
        file.appendText("$line\n")
    }

    fun readLines(): List<String> = if (file.exists()) file.readLines() else emptyList()

    private fun csv(value: String): String =
        "\"" + value.replace("\"", "\"\"") + "\""
}
