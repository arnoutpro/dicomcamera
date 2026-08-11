package nl.dicomcamera.app.capture

import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.FileProvider
import nl.dicomcamera.dicom.SecureStaging
import java.io.File
import java.io.FileOutputStream

/**
 * System-camera capture for OEM devices (esp. ColorOS / Oppo / OnePlus).
 *
 * ColorOS without EXTRA_OUTPUT often returns only a tiny thumbnail in Intent
 * extras ("data") — that looks pixelated when stretched fullscreen. We therefore:
 * 1. Prefer a MediaStore content URI as EXTRA_OUTPUT (full-res write target)
 * 2. Target com.oplus.camera via setPackage (never stub setClassName)
 * 3. Reject thumbnail-sized results and fall back to the newest gallery media
 *    taken after launch
 */
object SystemCameraCapture {

    /** Below this long-edge we treat the result as a thumbnail, not clinical capture. */
    private const val MIN_FULLRES_EDGE = 1000

    data class Pending(
        val photo: Boolean,
        val stagingFile: File,
        val outputUri: Uri?,
        val mediaStoreUri: Uri?,
        val useExtraOutput: Boolean,
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

        // Always prefer MediaStore EXTRA_OUTPUT — including ColorOS. Skipping it
        // previously left us with only Intent thumbnail bitmaps (~few hundred px).
        val media = insertMediaStore(context, photo)
        if (media != null) {
            return Pending(
                photo = photo,
                stagingFile = stagingFile,
                outputUri = media,
                mediaStoreUri = media,
                useExtraOutput = true,
            )
        }

        val extDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "capture",
        ).also { it.mkdirs() }
        val extFile = File(
            extDir,
            if (photo) {
                "capture-${System.currentTimeMillis()}.jpg"
            } else {
                "capture-${System.currentTimeMillis()}.mp4"
            },
        )
        if (!extFile.exists()) {
            check(extFile.createNewFile()) { "Cannot create external capture file" }
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            extFile,
        )
        return Pending(
            photo = photo,
            stagingFile = stagingFile,
            outputUri = uri,
            mediaStoreUri = null,
            useExtraOutput = true,
        )
    }

    fun buildIntent(context: Context, pending: Pending): Intent {
        val intent = baseIntent(pending.photo)

        preferOemCameraPackage(context)?.let { pkg ->
            val probe = Intent(intent).setPackage(pkg)
            if (probe.resolveActivity(context.packageManager) != null) {
                intent.setPackage(pkg)
            }
        }

        if (pending.useExtraOutput && pending.outputUri != null) {
            val outputUri = pending.outputUri
            intent.putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
            intent.putExtra("return-data", false)
            intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            val flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            val query = Intent(intent.action)
            val targets = context.packageManager.queryIntentActivities(
                query,
                PackageManager.MATCH_DEFAULT_ONLY,
            )
            val packages = buildSet {
                preferOemCameraPackage(context)?.let { add(it) }
                targets.forEach { add(it.activityInfo.packageName) }
                intent.`package`?.let { add(it) }
            }
            for (pkg in packages) {
                runCatching { context.grantUriPermission(pkg, outputUri, flags) }
            }
        }

        if (!pending.photo) {
            intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
        }
        return intent
    }

    fun finalizeCapture(context: Context, pending: Pending, resultData: Intent?): FinalizeInfo {
        data class Candidate(val uri: Uri?, val label: String, val allowThumb: Boolean = false)

        val candidates = buildList {
            pending.mediaStoreUri?.let { add(Candidate(it, "mediastore")) }
            pending.outputUri?.let { add(Candidate(it, "output")) }
            resultData?.data?.let { add(Candidate(it, "result.data")) }
            resultData?.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i)?.uri?.let { add(Candidate(it, "clip[$i]")) }
                }
            }
            // ColorOS often saves full-res to DCIM even when EXTRA_OUTPUT is ignored.
            findNewestMedia(context, pending.photo, pending.launchedAtEpochMs)?.let {
                add(Candidate(it, "gallery.newest"))
            }
        }

        for (candidate in candidates) {
            val uri = candidate.uri ?: continue
            if (!copyUriToFile(context, uri, pending.stagingFile)) continue
            val meta = readImageMeta(pending.stagingFile, pending.photo)
            if (pending.photo && !isFullResEnough(meta.width, meta.height, pending.stagingFile.length())) {
                // Keep looking for a full-res source; do not settle on a thumbnail URI.
                continue
            }
            cleanupTempMedia(context, pending, keepGallery = candidate.label.startsWith("gallery"))
            return FinalizeInfo(
                ok = true,
                width = meta.width,
                height = meta.height,
                bytes = pending.stagingFile.length(),
                source = candidate.label,
            )
        }

        // Last resort: Intent thumbnail bitmap (ColorOS classic). Prefer rejecting over
        // storing unusable clinical pixels when gallery lookup also failed.
        if (pending.photo) {
            @Suppress("DEPRECATION")
            val thumb = resultData?.extras?.getParcelable<android.graphics.Bitmap>("data")
            if (thumb != null) {
                pending.stagingFile.parentFile?.mkdirs()
                FileOutputStream(pending.stagingFile).use { out ->
                    thumb.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
                }
                val w = thumb.width
                val h = thumb.height
                val enough = isFullResEnough(w, h, pending.stagingFile.length())
                cleanupTempMedia(context, pending, keepGallery = false)
                return FinalizeInfo(
                    ok = enough,
                    width = w,
                    height = h,
                    bytes = pending.stagingFile.length(),
                    source = "intent.thumbnail",
                    warning = if (enough) {
                        null
                    } else {
                        "Camera returned thumbnail only (${w}x${h}). Full-res unavailable."
                    },
                )
            }
        }

        cleanupTempMedia(context, pending, keepGallery = false)
        return FinalizeInfo(ok = false, source = "none")
    }

    fun abandon(context: Context, pending: Pending?) {
        if (pending == null) return
        pending.mediaStoreUri?.let { uri ->
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
        runCatching {
            if (pending.stagingFile.exists()) pending.stagingFile.delete()
        }
    }

    private fun cleanupTempMedia(context: Context, pending: Pending, keepGallery: Boolean) {
        pending.mediaStoreUri?.let { uri ->
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
        // Do not delete gallery.newest — we already copied; deleting needs broad media access
        // and may remove the user's only DCIM copy if copy failed partially.
        if (!keepGallery && !pending.useExtraOutput) {
            // no-op reserved
        }
    }

    private fun isFullResEnough(width: Int, height: Int, bytes: Long): Boolean {
        if (maxOf(width, height) >= MIN_FULLRES_EDGE) return true
        // Videos / unknown bounds: accept reasonably large files.
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

    private fun findNewestMedia(context: Context, photo: Boolean, takenAfterEpochMs: Long): Uri? {
        return try {
            val collection = if (photo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
            }
            val idColumn = MediaStore.MediaColumns._ID
            val dateColumn = MediaStore.MediaColumns.DATE_ADDED
            val sizeColumn = MediaStore.MediaColumns.SIZE
            val minSec = ((takenAfterEpochMs / 1000L) - 15L).coerceAtLeast(0L)
            context.contentResolver.query(
                collection,
                arrayOf(idColumn, dateColumn, sizeColumn),
                "$dateColumn >= ?",
                arrayOf(minSec.toString()),
                "$dateColumn DESC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val size = cursor.getLong(2)
                    if (size in 1 until 50_000L && photo) continue // skip tiny thumbs in gallery
                    val id = cursor.getLong(0)
                    return@use ContentUris.withAppendedId(collection, id)
                }
                null
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
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

    private fun insertMediaStore(context: Context, photo: Boolean): Uri? {
        val resolver = context.contentResolver
        val name = "dicomcamera-${System.currentTimeMillis()}"
        return try {
            if (photo) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + "/DicomCamera",
                        )
                    }
                }
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                resolver.insert(collection, values)
            } else {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "$name.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.Video.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_MOVIES + "/DicomCamera",
                        )
                    }
                }
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
                resolver.insert(collection, values)
            }
        } catch (_: Exception) {
            null
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

/** Launches the system camera; result includes the data Intent (needed on ColorOS). */
class SystemCameraContract :
    ActivityResultContract<SystemCameraCapture.Pending, SystemCameraContract.CameraResult>() {
    data class CameraResult(val ok: Boolean, val data: Intent?)

    override fun createIntent(context: Context, input: SystemCameraCapture.Pending): Intent =
        SystemCameraCapture.buildIntent(context, input)

    override fun parseResult(resultCode: Int, intent: Intent?): CameraResult =
        CameraResult(ok = resultCode == Activity.RESULT_OK, data = intent)
}
