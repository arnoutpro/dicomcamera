package nl.dicomcamera.app.capture

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.FileProvider
import nl.dicomcamera.app.session.CaptureKind
import nl.dicomcamera.dicom.SecureStaging
import java.io.File

/**
 * Reliable system-camera capture via [MediaStore.ACTION_IMAGE_CAPTURE] /
 * [MediaStore.ACTION_VIDEO_CAPTURE].
 *
 * Note: https://source.android.com/docs/core/camera/system-cameras describes
 * Camera HAL "system cameras" (platform/internal lens types for the framework).
 * Third-party apps do not open those HAL cameras directly — capture still goes
 * through the MediaStore camera Intent + FileProvider output URI, which launches
 * the device's camera *app*.
 *
 * Common failure modes this helper addresses:
 * - Staging names without a real `.jpg`/`.mp4` extension (camera apps reject them)
 * - Missing URI write grants (stock TakePicture often crashes the camera app)
 * - Non-camera handlers (DocumentsUI / file managers) claiming IMAGE_CAPTURE
 */
object SystemCameraCapture {
    fun createOutput(
        context: Context,
        staging: SecureStaging,
        photo: Boolean,
    ): Pair<File, Uri> {
        // Real extensions matter — many camera apps reject names like "cam-123-jpg".
        val dir = File(staging.directory, "camera").also { it.mkdirs() }
        val file = File(
            dir,
            if (photo) {
                "capture-${System.currentTimeMillis()}.jpg"
            } else {
                "capture-${System.currentTimeMillis()}.mp4"
            },
        )
        check(file.createNewFile()) { "Cannot create capture file" }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return file to uri
    }

    fun hasCameraApp(context: Context, photo: Boolean): Boolean =
        resolveCameraTargets(context, captureIntent(photo)).isNotEmpty()

    fun buildCaptureIntent(context: Context, photo: Boolean, outputUri: Uri): Intent {
        val intent = captureIntent(photo).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
            // Discourage thumbnail-in-Intent returns; we want the FileProvider file.
            putExtra("return-data", false)
            if (!photo) {
                putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
            }
        }
        val targets = resolveCameraTargets(context, intent)
        val flags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
        intent.addFlags(flags)
        intent.clipData = ClipData.newUri(context.contentResolver, "capture", outputUri)

        for (resolve in targets) {
            context.grantUriPermission(resolve.activityInfo.packageName, outputUri, flags)
        }

        // Pin to a real camera activity when possible — avoids file-manager handlers.
        preferCameraComponent(targets)?.let { preferred ->
            intent.setClassName(
                preferred.activityInfo.packageName,
                preferred.activityInfo.name,
            )
        }
        return intent
    }

    private fun captureIntent(photo: Boolean): Intent {
        val action = if (photo) {
            MediaStore.ACTION_IMAGE_CAPTURE
        } else {
            MediaStore.ACTION_VIDEO_CAPTURE
        }
        return Intent(action).addCategory(Intent.CATEGORY_DEFAULT)
    }

    private fun resolveCameraTargets(context: Context, seed: Intent): List<ResolveInfo> {
        val matches = context.packageManager.queryIntentActivities(
            seed,
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        val cameras = matches.filter(::looksLikeCameraApp)
        return if (cameras.isNotEmpty()) cameras else matches.filterNot(::looksLikeFileManager)
    }

    private fun looksLikeCameraApp(resolve: ResolveInfo): Boolean {
        val pkg = resolve.activityInfo.packageName.lowercase()
        val name = resolve.activityInfo.name.lowercase()
        if (looksLikeFileManager(resolve)) return false
        return pkg.contains("camera") ||
            name.contains("camera") ||
            pkg.contains("camerax") ||
            pkg.contains("googlecamera") ||
            pkg.endsWith(".cam")
    }

    private fun looksLikeFileManager(resolve: ResolveInfo): Boolean {
        val pkg = resolve.activityInfo.packageName.lowercase()
        val name = resolve.activityInfo.name.lowercase()
        return pkg.contains("documentsui") ||
            pkg.contains("filemanager") ||
            pkg.contains("file.manager") ||
            pkg.contains("files") && !pkg.contains("camera") ||
            name.contains("documentsui") ||
            pkg.contains("com.google.android.apps.nbu.files") ||
            pkg.contains("com.android.safe.file")
    }

    private fun preferCameraComponent(targets: List<ResolveInfo>): ResolveInfo? {
        if (targets.isEmpty()) return null
        val ranked =
            targets.sortedByDescending { resolve ->
                val pkg = resolve.activityInfo.packageName.lowercase()
                when {
                    pkg.contains("com.android.camera") -> 100
                    pkg.contains("googlecamera") -> 95
                    pkg.contains("samsung") && pkg.contains("camera") -> 90
                    pkg.contains("sec.android.app.camera") -> 90
                    pkg.contains("motorola.camera") -> 85
                    pkg.contains("oneplus.camera") || pkg.contains("oppo.camera") -> 85
                    pkg.contains("huawei.camera") || pkg.contains("honor") && pkg.contains("camera") -> 80
                    pkg.contains("xiaomi") || pkg.contains("miui.camera") -> 80
                    pkg.contains("camera") -> 50
                    else -> 10
                }
            }
        return ranked.firstOrNull()
    }
}

/** Photo capture that explicitly grants FileProvider write access to camera apps. */
class TakePictureGranted : ActivityResultContract<Uri, Boolean>() {
    override fun createIntent(context: Context, input: Uri): Intent =
        SystemCameraCapture.buildCaptureIntent(context, photo = true, outputUri = input)

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
        resultCode == Activity.RESULT_OK
}

/** Video capture with the same URI grant behaviour. */
class CaptureVideoGranted : ActivityResultContract<Uri, Boolean>() {
    override fun createIntent(context: Context, input: Uri): Intent =
        SystemCameraCapture.buildCaptureIntent(context, photo = false, outputUri = input)

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
        resultCode == Activity.RESULT_OK
}

fun captureKindFor(photo: Boolean): CaptureKind =
    if (photo) CaptureKind.PHOTO else CaptureKind.VIDEO
