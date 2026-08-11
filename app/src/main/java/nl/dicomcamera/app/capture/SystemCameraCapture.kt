package nl.dicomcamera.app.capture

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
 * AOSP “system cameras” HAL docs are unrelated — apps launch the vendor camera
 * *app* via [MediaStore.ACTION_IMAGE_CAPTURE] / [ACTION_VIDEO_CAPTURE].
 *
 * ColorOS (Oppo Find X9 Pro, etc.) often:
 * - Exits straight to the Home launcher when EXTRA_OUTPUT is present (FileProvider
 *   *or* a pinned stub `com.android.camera` component)
 * - Works when the Intent targets `com.oplus.camera` **without** EXTRA_OUTPUT;
 *   the full image URI comes back in the result Intent
 *
 * After capture we copy bytes into SecureStaging and delete any temporary
 * MediaStore/gallery row we created (clinical images must not linger).
 */
object SystemCameraCapture {

    data class Pending(
        val photo: Boolean,
        val stagingFile: File,
        /** URI for EXTRA_OUTPUT when [useExtraOutput] is true. */
        val outputUri: Uri?,
        val mediaStoreUri: Uri?,
        /**
         * ColorOS: false — do not pass EXTRA_OUTPUT (avoids home-screen crash).
         * Other OEMs: true — write to MediaStore / FileProvider URI.
         */
        val useExtraOutput: Boolean,
    )

    /** Known ColorOS / OxygenOS camera packages (Find X9 Pro uses oplus). */
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

        val oem = preferOemCameraPackage(context)
        // Oppo/OnePlus ColorOS: EXTRA_OUTPUT frequently crashes the camera process
        // (user lands on the launcher). Launch without it and read result.data.
        if (oem != null) {
            return Pending(
                photo = photo,
                stagingFile = stagingFile,
                outputUri = null,
                mediaStoreUri = null,
                useExtraOutput = false,
            )
        }

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

    /**
     * Copy camera output into [Pending.stagingFile].
     * ColorOS path uses [resultData] URI; EXTRA_OUTPUT path uses MediaStore/FileProvider.
     */
    fun finalizeCapture(context: Context, pending: Pending, resultData: Intent?): Boolean {
        val candidates = buildList {
            resultData?.data?.let { add(it) }
            // Some OEMs put the URI in ClipData instead of data.
            resultData?.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i)?.uri?.let { add(it) }
                }
            }
            pending.mediaStoreUri?.let { add(it) }
            pending.outputUri?.let { add(it) }
        }.distinct()

        var copied = false
        for (uri in candidates) {
            if (copyUriToFile(context, uri, pending.stagingFile)) {
                copied = true
                // Remove gallery copy when we can (privacy). Result URIs from ColorOS
                // are often under DCIM — delete only rows we inserted ourselves.
                break
            }
        }

        // Thumbnail-only fallback (last resort) when OEM returns bitmap in extras.
        if (!copied && pending.photo) {
            @Suppress("DEPRECATION")
            val thumb = resultData?.extras?.getParcelable<android.graphics.Bitmap>("data")
            if (thumb != null) {
                pending.stagingFile.parentFile?.mkdirs()
                FileOutputStream(pending.stagingFile).use { out ->
                    thumb.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
                }
                copied = pending.stagingFile.length() > 0L
            }
        }

        if (copied) {
            pending.mediaStoreUri?.let { uri ->
                runCatching { context.contentResolver.delete(uri, null, null) }
            }
            // Best-effort delete of OEM gallery URI when it looks like our capture folder
            // is not involved — skip deleting result.data (may be user's only copy until
            // we copied; after copy, try delete if we have write access).
            if (!pending.useExtraOutput) {
                resultData?.data?.let { uri ->
                    runCatching { context.contentResolver.delete(uri, null, null) }
                }
            }
        }

        return copied && pending.stagingFile.exists() && pending.stagingFile.length() > 0L
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
