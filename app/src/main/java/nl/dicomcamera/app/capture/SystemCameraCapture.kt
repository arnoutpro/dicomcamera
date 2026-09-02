package nl.dicomcamera.app.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.FileProvider
import nl.dicomcamera.dicom.SecureStaging
import nl.dicomcamera.dicom.WipeResult
import java.io.File
import java.io.FileOutputStream

/**
 * System-camera capture without gallery / Photos permission.
 *
 * Full-resolution bytes are written into an **app-private** FileProvider URI
 * (external-files Pictures/capture). We never request READ_MEDIA_* and never
 * scan the device photo library.
 *
 * ColorOS previously returned only Intent thumbnail bitmaps when EXTRA_OUTPUT
 * was omitted — that looked pixelated fullscreen. We always pass EXTRA_OUTPUT
 * and reject sub-clinical thumbnail sizes.
 */
object SystemCameraCapture {

    /** Below this long-edge we treat the result as a thumbnail, not clinical capture. */
    private const val MIN_FULLRES_EDGE = 1000

    data class Pending(
        val photo: Boolean,
        val stagingFile: File,
        val outputUri: Uri,
        val outputFile: File,
        /** Package that received temporary URI grants for EXTRA_OUTPUT. */
        val cameraPackage: String? = null,
        val launchedAtEpochMs: Long = System.currentTimeMillis(),
    )

    data class FinalizeInfo(
        val ok: Boolean,
        val width: Int = 0,
        val height: Int = 0,
        val bytes: Long = 0,
        val source: String = "",
        val warning: String? = null,
    )

    private val OEM_CAMERA_PACKAGES = listOf(
        "com.oplus.camera",
        "com.oppo.camera",
        "com.oneplus.camera",
    )

    fun hasCameraApp(context: Context, photo: Boolean): Boolean {
        if (baseIntent(photo).resolveActivity(context.packageManager) != null) return true
        return preferOemCameraPackage(context) != null
    }

    fun prepare(context: Context, staging: SecureStaging, photo: Boolean): Pending {
        val stagingFile = File(
            File(staging.directory, "camera").also { it.mkdirs() },
            if (photo) {
                "capture-${System.currentTimeMillis()}.jpg"
            } else {
                "capture-${System.currentTimeMillis()}.mp4"
            },
        )

        // App-private external files — no shared gallery, no READ_MEDIA permission.
        val extDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "capture",
        ).also { it.mkdirs() }
        val outputFile = File(
            extDir,
            if (photo) {
                "capture-${System.currentTimeMillis()}.jpg"
            } else {
                "capture-${System.currentTimeMillis()}.mp4"
            },
        )
        if (outputFile.exists()) {
            outputFile.delete()
        }
        check(outputFile.createNewFile()) { "Cannot create capture output file" }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outputFile,
        )
        return Pending(
            photo = photo,
            stagingFile = stagingFile,
            outputUri = uri,
            outputFile = outputFile,
        )
    }

    /**
     * Builds the camera Intent and returns an updated [Pending] that records which
     * package received URI grants (so we can revoke them after capture).
     */
    fun buildCapture(context: Context, pending: Pending): Pair<Intent, Pending> {
        val intent = baseIntent(pending.photo)

        preferOemCameraPackage(context)?.let { pkg ->
            val probe = Intent(intent).setPackage(pkg)
            if (probe.resolveActivity(context.packageManager) != null) {
                intent.setPackage(pkg)
            }
        }

        intent.putExtra(MediaStore.EXTRA_OUTPUT, pending.outputUri)
        intent.putExtra("return-data", false)
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        val flags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        // Grant only to the camera package that will handle this Intent — not every
        // IMAGE_CAPTURE / VIDEO_CAPTURE handler on the device.
        val targetPackage = intent.`package`
            ?: intent.resolveActivity(context.packageManager)?.packageName
        if (targetPackage != null) {
            runCatching { context.grantUriPermission(targetPackage, pending.outputUri, flags) }
        }

        if (!pending.photo) {
            intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
        }
        return intent to pending.copy(cameraPackage = targetPackage)
    }

    /** @deprecated Prefer [buildCapture] so URI grants can be revoked. */
    fun buildIntent(context: Context, pending: Pending): Intent = buildCapture(context, pending).first

    fun finalizeCapture(context: Context, pending: Pending, resultData: Intent?): FinalizeInfo {
        data class Candidate(val label: String, val tryLoad: () -> Boolean)

        val candidates = buildList {
            // Primary: app-private FileProvider file the camera was asked to write.
            add(
                Candidate("fileprovider") {
                    copyFileIfPresent(pending.outputFile, pending.stagingFile)
                },
            )
            add(
                Candidate("fileprovider.uri") {
                    copyUriToFile(context, pending.outputUri, pending.stagingFile)
                },
            )
            resultData?.data?.let { uri ->
                add(
                    Candidate("result.data") {
                        copyUriToFile(context, uri, pending.stagingFile)
                    },
                )
            }
            resultData?.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) {
                    val uri = clip.getItemAt(i)?.uri ?: continue
                    add(
                        Candidate("clip[$i]") {
                            copyUriToFile(context, uri, pending.stagingFile)
                        },
                    )
                }
            }
        }

        for (candidate in candidates) {
            if (!candidate.tryLoad()) continue
            val meta = readImageMeta(pending.stagingFile, pending.photo)
            if (pending.photo && !isFullResEnough(meta.width, meta.height, pending.stagingFile.length())) {
                continue
            }
            wipeOutput(context, pending)
            return FinalizeInfo(
                ok = true,
                width = meta.width,
                height = meta.height,
                bytes = pending.stagingFile.length(),
                source = candidate.label,
            )
        }

        // Reject Intent thumbnail — not suitable for clinical use, and we do not
        // fall back to scanning the device photo library.
        if (pending.photo) {
            @Suppress("DEPRECATION")
            val thumb = resultData?.extras?.getParcelable<android.graphics.Bitmap>("data")
            if (thumb != null) {
                wipeOutput(context, pending)
                return FinalizeInfo(
                    ok = false,
                    width = thumb.width,
                    height = thumb.height,
                    bytes = 0,
                    source = "intent.thumbnail",
                    warning = "Camera returned thumbnail only (${thumb.width}x${thumb.height}). " +
                        "Full-resolution capture failed — try again.",
                )
            }
        }

        wipeOutput(context, pending)
        return FinalizeInfo(
            ok = false,
            source = "none",
            warning = "Camera did not write a full-resolution file — try again.",
        )
    }

    fun abandon(context: Context, pending: Pending?) {
        if (pending == null) return
        wipeOutput(context, pending)
        runCatching {
            if (pending.stagingFile.exists()) {
                secureDelete(pending.stagingFile)
            }
        }
    }

    /**
     * Wipe leftover EXTRA_OUTPUT files under app-private Pictures/capture
     * (crash / process kill between camera return and finalize).
     *
     * Also revokes any persistent [Context.grantUriPermission] grants to camera
     * packages for those URIs — Intent FLAG_GRANT_* alone is temporary, but we
     * also call grantUriPermission so a crash before finalize would otherwise
     * leave the camera app able to re-read clinical pixels until wipe.
     */
    fun purgeLeftoverOutputs(context: Context): Int {
        val extDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "capture",
        )
        if (!extDir.isDirectory) return 0
        var removed = 0
        extDir.listFiles()?.filter { it.isFile }?.forEach { file ->
            revokeGrantsForCaptureFile(context, file)
            when (secureDelete(file)) {
                is WipeResult.Wiped, is WipeResult.AlreadyGone -> removed++
                is WipeResult.Failed -> if (file.delete()) removed++
            }
        }
        return removed
    }

    private fun revokeGrantsForCaptureFile(context: Context, file: File) {
        val uri = runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        }.getOrNull() ?: return
        val flags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val packages = linkedSetOf<String>()
        OEM_CAMERA_PACKAGES.forEach { packages += it }
        baseIntent(true).resolveActivity(context.packageManager)?.packageName?.let { packages += it }
        baseIntent(false).resolveActivity(context.packageManager)?.packageName?.let { packages += it }
        packages.forEach { pkg ->
            runCatching { context.revokeUriPermission(pkg, uri, flags) }
        }
    }

    private fun wipeOutput(context: Context, pending: Pending) {
        revokeGrants(context, pending)
        runCatching {
            if (pending.outputFile.exists()) {
                secureDelete(pending.outputFile)
            }
        }
    }

    private fun secureDelete(file: File): WipeResult {
        val parent = file.parentFile ?: return WipeResult.Failed("No parent for ${file.absolutePath}")
        return SecureStaging(parent).wipe(file)
    }

    private fun revokeGrants(context: Context, pending: Pending) {
        val pkg = pending.cameraPackage
            ?: preferOemCameraPackage(context)
            ?: baseIntent(pending.photo).resolveActivity(context.packageManager)?.packageName
            ?: return
        val flags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.revokeUriPermission(pkg, pending.outputUri, flags) }
    }

    private fun isFullResEnough(width: Int, height: Int, bytes: Long): Boolean {
        if (maxOf(width, height) >= MIN_FULLRES_EDGE) return true
        if (width <= 0 && height <= 0 && bytes >= 200_000L) return true
        return bytes >= 500_000L
    }

    private data class Dims(val width: Int, val height: Int)

    private fun readImageMeta(file: File, photo: Boolean): Dims {
        if (!photo) return Dims(0, 0)
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        return Dims(opts.outWidth.coerceAtLeast(0), opts.outHeight.coerceAtLeast(0))
    }

    private fun baseIntent(photo: Boolean): Intent {
        val action = if (photo) {
            MediaStore.ACTION_IMAGE_CAPTURE
        } else {
            MediaStore.ACTION_VIDEO_CAPTURE
        }
        return Intent(action)
    }

    private fun preferOemCameraPackage(context: Context): String? {
        for (pkg in OEM_CAMERA_PACKAGES) {
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: PackageManager.NameNotFoundException) {
                // try next
            }
        }
        return null
    }

    private fun copyFileIfPresent(src: File, dest: File): Boolean {
        if (!src.exists() || src.length() == 0L) return false
        return try {
            dest.parentFile?.mkdirs()
            src.copyTo(dest, overwrite = true)
            dest.exists() && dest.length() > 0L
        } catch (_: Exception) {
            false
        }
    }

    private fun copyUriToFile(context: Context, uri: Uri, dest: File): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.parentFile?.mkdirs()
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            } ?: return false
            dest.exists() && dest.length() > 0L
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Launches the system camera; result includes the data Intent (needed on ColorOS).
 *
 * Input is a [SystemCameraCapture.Pending] that may be updated with the granted camera
 * package via [onPendingReady] before the activity starts.
 */
class SystemCameraContract(
    private val onPendingReady: (SystemCameraCapture.Pending) -> Unit = {},
) : ActivityResultContract<SystemCameraCapture.Pending, SystemCameraContract.CameraResult>() {
    data class CameraResult(val ok: Boolean, val data: Intent?)

    override fun createIntent(context: Context, input: SystemCameraCapture.Pending): Intent {
        val (intent, updated) = SystemCameraCapture.buildCapture(context, input)
        onPendingReady(updated)
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): CameraResult =
        CameraResult(ok = resultCode == Activity.RESULT_OK, data = intent)
}
