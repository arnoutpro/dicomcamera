package nl.dicomcamera.app.demo

import android.graphics.BitmapFactory
import nl.dicomcamera.app.session.CaptureKind
import nl.dicomcamera.app.session.CaptureSession
import nl.dicomcamera.app.session.SessionItem
import nl.dicomcamera.dicom.PatientStudyContext
import nl.dicomcamera.dicom.SecureStaging
import nl.dicomcamera.dicom.StudyEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Local “ready to send” archive for demo / offline capture.
 * Photos stay on-device until the user sends to PACS or discards.
 */
class LocalArchiveStore(
    private val rootDir: File,
    private val staging: SecureStaging,
) {
    init {
        rootDir.mkdirs()
    }

    data class ReadyStudy(
        val id: String,
        val directory: File,
        val patientId: String,
        val patientName: String,
        val birthDate: String?,
        val sex: String?,
        val accessionNumber: String?,
        val studyDescription: String?,
        val studyInstanceUid: String,
        val seriesInstanceUid: String,
        val photoFiles: List<File>,
        val createdAtEpochMs: Long,
    ) {
        val photoCount: Int get() = photoFiles.size

        fun toStudyEntry(): StudyEntry = StudyEntry(
            patientId = patientId,
            patientName = patientName,
            patientBirthDate = birthDate,
            patientSex = sex,
            accessionNumber = accessionNumber,
            studyInstanceUid = studyInstanceUid,
            studyDate = null,
            studyDescription = studyDescription ?: "Local archive (${photoCount} photo(s))",
            modalitiesInStudy = "XC",
        )

        fun toContext(): PatientStudyContext = PatientStudyContext(
            patientId = patientId,
            patientName = patientName,
            patientBirthDate = birthDate,
            patientSex = sex,
            accessionNumber = accessionNumber,
            studyDescription = studyDescription,
            studyInstanceUid = studyInstanceUid,
            seriesInstanceUid = seriesInstanceUid,
            seriesDescription = "Clinical photo/video session",
            modality = "XC",
        )
    }

    fun saveSession(
        session: CaptureSession,
        context: PatientStudyContext,
    ): ReadyStudy {
        val id = UUID.randomUUID().toString()
        val dir = File(rootDir, id).also { check(it.mkdirs()) }
        val photos = mutableListOf<File>()
        session.items
            .filter { it.kind == CaptureKind.PHOTO && it.rawFile.exists() }
            .forEachIndexed { index, item ->
                val dest = File(dir, "photo_%02d.jpg".format(index))
                item.rawFile.copyTo(dest, overwrite = true)
                photos += dest
            }
        require(photos.isNotEmpty()) { "No photos to archive" }

        val createdAt = System.currentTimeMillis()
        val meta = JSONObject()
            .put("patientId", context.patientId)
            .put("patientName", context.patientName)
            .put("birthDate", context.patientBirthDate)
            .put("sex", context.patientSex)
            .put("accessionNumber", context.accessionNumber)
            .put("studyDescription", context.studyDescription)
            .put("studyInstanceUid", session.studyInstanceUid)
            .put("seriesInstanceUid", session.seriesInstanceUid)
            .put("createdAt", createdAt)
            .put(
                "photos",
                JSONArray().also { arr -> photos.forEach { arr.put(it.name) } },
            )
        File(dir, "meta.json").writeText(meta.toString())

        // Wipe session staging originals after copy.
        session.items.forEach { staging.wipe(it.rawFile); it.dicomFile?.let(staging::wipe) }

        return ReadyStudy(
            id = id,
            directory = dir,
            patientId = context.patientId,
            patientName = context.patientName,
            birthDate = context.patientBirthDate,
            sex = context.patientSex,
            accessionNumber = context.accessionNumber,
            studyDescription = context.studyDescription,
            studyInstanceUid = session.studyInstanceUid,
            seriesInstanceUid = session.seriesInstanceUid,
            photoFiles = photos,
            createdAtEpochMs = createdAt,
        )
    }

    fun list(): List<ReadyStudy> =
        rootDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { read(it) }
            ?.sortedByDescending { it.createdAtEpochMs }
            .orEmpty()

    fun discard(id: String): Boolean {
        val dir = File(rootDir, id)
        if (!dir.exists()) return false
        dir.walkBottomUp().forEach { file ->
            if (file.isFile) staging.wipe(file) else file.delete()
        }
        return !dir.exists()
    }

    fun markSentAndWipe(id: String) {
        discard(id)
    }

    fun toSessionItems(study: ReadyStudy): List<SessionItem> =
        study.photoFiles.mapNotNull { file ->
            if (!file.exists()) return@mapNotNull null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            SessionItem(
                kind = CaptureKind.PHOTO,
                rawFile = file,
                rows = bounds.outHeight.coerceAtLeast(1),
                columns = bounds.outWidth.coerceAtLeast(1),
            )
        }

    private fun read(dir: File): ReadyStudy? {
        val metaFile = File(dir, "meta.json")
        if (!metaFile.exists()) return null
        return runCatching {
            val meta = JSONObject(metaFile.readText())
            val names = meta.optJSONArray("photos") ?: JSONArray()
            val photos = buildList {
                for (i in 0 until names.length()) {
                    val f = File(dir, names.getString(i))
                    if (f.exists()) add(f)
                }
            }
            if (photos.isEmpty()) return null
            ReadyStudy(
                id = dir.name,
                directory = dir,
                patientId = meta.getString("patientId"),
                patientName = meta.getString("patientName"),
                birthDate = meta.optString("birthDate").takeIf { it.isNotBlank() && it != "null" },
                sex = meta.optString("sex").takeIf { it.isNotBlank() && it != "null" },
                accessionNumber = meta.optString("accessionNumber").takeIf { it.isNotBlank() && it != "null" },
                studyDescription = meta.optString("studyDescription").takeIf { it.isNotBlank() && it != "null" },
                studyInstanceUid = meta.getString("studyInstanceUid"),
                seriesInstanceUid = meta.getString("seriesInstanceUid"),
                photoFiles = photos,
                createdAtEpochMs = meta.optLong("createdAt", 0L),
            )
        }.getOrNull()
    }
}
