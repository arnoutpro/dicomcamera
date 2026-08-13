package nl.dicomcamera.dicom

import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom

/**
 * Ephemeral staging for capture bytes. Files live only until successful PACS send,
 * move into [PendingStoreQueue], or discard. Never writes to shared media galleries.
 *
 * Capture intermediates may live under a `camera/` subdirectory; wipe helpers recurse
 * so crash recovery does not leave PHI behind.
 */
class SecureStaging(
    private val stagingDir: File,
    private val random: SecureRandom = SecureRandom(),
) {
    init {
        if (!stagingDir.exists()) {
            check(stagingDir.mkdirs()) { "Unable to create staging dir: $stagingDir" }
        }
    }

    val directory: File get() = stagingDir

    fun createStagingFile(prefix: String, suffix: String): File {
        return File(stagingDir, "$prefix-${System.currentTimeMillis()}-$suffix").apply {
            check(createNewFile()) { "Unable to create staging file: $this" }
        }
    }

    /**
     * Overwrites file contents with random bytes, then deletes. Best-effort secure delete.
     */
    fun wipe(file: File): WipeResult {
        if (!file.exists()) return WipeResult.AlreadyGone
        if (file.isDirectory) {
            return wipeDirectory(file)
        }
        return wipeFileContents(file)
    }

    /**
     * Retries wipe once on failure. Prefer this after a successful PACS ACK.
     */
    fun wipeAfterAck(file: File): WipeResult {
        val first = wipe(file)
        if (first !is WipeResult.Failed) return first
        return wipe(file)
    }

    private fun wipeFileContents(file: File): WipeResult {
        return try {
            val length = file.length()
            if (length > 0) {
                RandomAccessFile(file, "rw").use { raf ->
                    val buffer = ByteArray(8192)
                    var remaining = length
                    raf.seek(0)
                    while (remaining > 0) {
                        val chunk = minOf(buffer.size.toLong(), remaining).toInt()
                        random.nextBytes(buffer)
                        raf.write(buffer, 0, chunk)
                        remaining -= chunk
                    }
                    raf.fd.sync()
                }
            }
            if (!file.delete()) {
                WipeResult.Failed("delete() returned false for ${file.absolutePath}")
            } else {
                WipeResult.Wiped
            }
        } catch (e: Exception) {
            WipeResult.Failed(e.message ?: e.javaClass.simpleName, e)
        }
    }

    private fun wipeDirectory(dir: File): WipeResult {
        var failed: WipeResult.Failed? = null
        dir.walkBottomUp().forEach { entry ->
            when {
                entry.isFile -> {
                    val result = wipeFileContents(entry)
                    if (result is WipeResult.Failed && failed == null) failed = result
                }
                entry.isDirectory && entry != dir -> {
                    if (!entry.delete() && entry.exists() && failed == null) {
                        failed = WipeResult.Failed("delete() returned false for ${entry.absolutePath}")
                    }
                }
            }
        }
        if (!dir.delete() && dir.exists() && failed == null) {
            failed = WipeResult.Failed("delete() returned false for ${dir.absolutePath}")
        }
        return failed ?: WipeResult.Wiped
    }

    fun wipeAll(): List<Pair<File, WipeResult>> {
        return listStagingFiles().map { it to wipe(it) }
    }

    fun listStagingFiles(): List<File> {
        if (!stagingDir.exists()) return emptyList()
        return stagingDir.walkTopDown()
            .filter { it.isFile }
            .toList()
    }

    /** Crash recovery: wipe leftover files under staging (including camera/), not pending/. */
    fun purgeOrphans(): Int = wipeAll().count { it.second is WipeResult.Wiped || it.second is WipeResult.AlreadyGone }
}

sealed interface WipeResult {
    data object Wiped : WipeResult
    data object AlreadyGone : WipeResult
    data class Failed(val message: String, val cause: Throwable? = null) : WipeResult
}
