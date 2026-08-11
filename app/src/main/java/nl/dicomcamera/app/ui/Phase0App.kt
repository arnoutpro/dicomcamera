package nl.dicomcamera.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.dicomcamera.app.BuildConfig
import nl.dicomcamera.dicom.DicomNode
import nl.dicomcamera.dicom.EchoResult
import nl.dicomcamera.dicom.PacsClient
import nl.dicomcamera.dicom.PatientStudyContext
import nl.dicomcamera.dicom.SecondaryCaptureEncoder
import nl.dicomcamera.dicom.SecureStaging
import nl.dicomcamera.dicom.StoreResult
import nl.dicomcamera.dicom.WipeResult
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors

private const val TAG = "Phase0App"

@Composable
fun Phase0App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val staging = remember {
        SecureStaging(File(context.filesDir, "staging").also { it.mkdirs() })
    }

    var patientId by remember { mutableStateOf("DEMO-001") }
    var patientName by remember { mutableStateOf("DEMO^PATIENT") }
    var host by remember { mutableStateOf(BuildConfig.DEFAULT_PACS_HOST) }
    var port by remember { mutableStateOf(BuildConfig.DEFAULT_PACS_PORT.toString()) }
    var calledAet by remember { mutableStateOf(BuildConfig.DEFAULT_CALLED_AET) }
    var callingAet by remember { mutableStateOf(BuildConfig.DEFAULT_CALLING_AET) }
    var status by remember { mutableStateOf("Phase 0 lab — capture → DICOM → C-STORE → wipe") }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "DICOM Camera",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Phase 0 foundations spike",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = patientId,
            onValueChange = { patientId = it },
            label = { Text("Patient ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = patientName,
            onValueChange = { patientName = it },
            label = { Text("Patient Name (DICOM PN)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("PACS host") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("PACS port") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = calledAet,
            onValueChange = { calledAet = it },
            label = { Text("Called AE Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = callingAet,
            onValueChange = { callingAet = it },
            label = { Text("Calling AE Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Button(
            onClick = {
                scope.launch {
                    status = "C-ECHO…"
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            PacsClient(
                                DicomNode(
                                    host = host.trim(),
                                    port = port.trim().toInt(),
                                    calledAeTitle = calledAet.trim(),
                                    callingAeTitle = callingAet.trim(),
                                ),
                            ).use { it.echo() }
                        }.getOrElse { EchoResult.Failed(it.message ?: "echo failed", it) }
                    }
                    status = when (result) {
                        EchoResult.Success -> "C-ECHO OK"
                        is EchoResult.Failed -> "C-ECHO failed: ${result.message}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Test C-ECHO")
        }

        if (!hasCameraPermission) {
            Button(
                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Grant camera permission")
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
            ) {
                CameraPreview(onImageCaptureReady = { imageCapture = it })
            }
            Button(
                onClick = {
                    val capture = imageCapture
                    if (capture == null) {
                        status = "Camera not ready"
                        return@Button
                    }
                    status = "Capturing…"
                    val rawFile = staging.createStagingFile("raw", "jpg")
                    val output = ImageCapture.OutputFileOptions.Builder(rawFile).build()
                    capture.takePicture(
                        output,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                scope.launch {
                                    status = "Encoding + C-STORE…"
                                    val outcome = withContext(Dispatchers.IO) {
                                        captureEncodeStoreWipe(
                                            staging = staging,
                                            rawJpeg = rawFile,
                                            patientId = patientId.trim(),
                                            patientName = patientName.trim(),
                                            node = DicomNode(
                                                host = host.trim(),
                                                port = port.trim().toInt(),
                                                calledAeTitle = calledAet.trim(),
                                                callingAeTitle = callingAet.trim(),
                                            ),
                                        )
                                    }
                                    status = outcome
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                status = "Capture failed: ${exception.message}"
                                staging.wipe(rawFile)
                            }
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Capture → Store → Wipe")
            }
        }

        Button(
            onClick = {
                val results = staging.wipeAll()
                status = "Purged staging: ${results.size} file(s)"
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Purge staging now")
        }

        Text(
            text = status,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun CameraPreview(onImageCaptureReady: (ImageCapture) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
            )
            onImageCaptureReady(imageCapture)
        }
        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun captureEncodeStoreWipe(
    staging: SecureStaging,
    rawJpeg: File,
    patientId: String,
    patientName: String,
    node: DicomNode,
): String {
    val dicomFile = staging.createStagingFile("sc", "dcm")
    return try {
        val jpegBytes = rawJpeg.readBytes()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, bounds)
        var rows = bounds.outHeight
        var columns = bounds.outWidth
        var bytes = jpegBytes
        if (rows <= 0 || columns <= 0) {
            // Fallback: re-encode via Bitmap
            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                ?: return "Failed to decode JPEG"
            rows = bitmap.height
            columns = bitmap.width
            bytes = bitmap.toJpegBytes()
            bitmap.recycle()
        }

        SecondaryCaptureEncoder().encodeJpegToFile(
            jpegBytes = bytes,
            context = PatientStudyContext(
                patientId = patientId,
                patientName = patientName,
                modality = "XC",
                seriesDescription = "Clinical photo",
            ),
            rows = rows,
            columns = columns,
            outputFile = dicomFile,
        )

        PacsClient(node).use { client ->
            when (val store = client.store(dicomFile)) {
                is StoreResult.Success -> {
                    val wipeDicom = staging.wipe(dicomFile)
                    val wipeRaw = staging.wipe(rawJpeg)
                    if (wipeDicom is WipeResult.Wiped && wipeRaw is WipeResult.Wiped) {
                        "Stored ${store.sopInstanceUid} and wiped local files"
                    } else {
                        "Stored ${store.sopInstanceUid} but wipe incomplete: $wipeDicom / $wipeRaw"
                    }
                }
                is StoreResult.Failed -> {
                    "C-STORE failed: ${store.message}"
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "captureEncodeStoreWipe failed", e)
        "Failed: ${e.message}"
    }
}

private fun Bitmap.toJpegBytes(quality: Int = 90): ByteArray {
    val stream = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, quality, stream)
    return stream.toByteArray()
}
