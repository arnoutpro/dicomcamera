package nl.dicomcamera.dicom

import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom

/**
 * Ephemeral staging for capture bytes. Files live only until successful PACS send (or discard).
 * Never writes to shared media galleries.
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

    fun wipeAll(): List<Pair<File, WipeResult>> {
        val files = stagingDir.listFiles()?.toList().orEmpty()
        return files.map { it to wipe(it) }
    }

    fun listStagingFiles(): List<File> =
        stagingDir.listFiles()?.filter { it.isFile }?.toList().orEmpty()
}

sealed interface WipeResult {
    data object Wiped : WipeResult
    data object AlreadyGone : WipeResult
    data class Failed(val message: String, val cause: Throwable? = null) : WipeResult
}
