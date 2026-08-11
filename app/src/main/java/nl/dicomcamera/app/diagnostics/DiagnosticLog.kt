package nl.dicomcamera.app.diagnostics

import java.io.File
import java.time.Instant

/**
 * Opt-in diagnostic log for troubleshooting (off by default).
 * Technical lines only — no pixel payloads. May include hostnames / IDs when logging is on.
 */
class DiagnosticLog(
    private val file: File,
) {
    @Volatile
    var enabled: Boolean = false
        private set

    init {
        file.parentFile?.mkdirs()
    }

    @Synchronized
    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (value) {
            ensureFile()
            appendLine("logging_enabled", "Diagnostic logging started")
        } else if (file.exists()) {
            appendLine("logging_disabled", "Diagnostic logging stopped")
        }
    }

    fun log(tag: String, message: String = "") {
        if (!enabled) return
        synchronized(this) {
            ensureFile()
            appendLine(tag, message)
        }
    }

    fun exists(): Boolean = file.exists() && file.length() > 0L

    fun sizeBytes(): Long = if (file.exists()) file.length() else 0L

    fun lineCount(): Int = if (file.exists()) file.readLines().size else 0

    fun snapshotFile(): File = file

    @Synchronized
    fun clear() {
        if (file.exists()) {
            file.writeText("")
        }
        if (enabled) {
            ensureFile()
            appendLine("logging_cleared", "Log file cleared")
        }
    }

    private fun ensureFile() {
        file.parentFile?.mkdirs()
        if (!file.exists() || file.length() == 0L) {
            file.writeText("timestamp\tlevel\ttag\tmessage\n")
        }
    }

    private fun appendLine(tag: String, message: String) {
        val safeTag = tag.replace('\t', ' ').replace('\n', ' ')
        val safeMessage = message.replace('\t', ' ').replace('\n', ' ')
        file.appendText("${Instant.now()}\tINFO\t$safeTag\t$safeMessage\n")
    }
}
