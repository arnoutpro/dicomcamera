package nl.dicomcamera.dicom

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * DICOMweb SCU: QIDO-RS (studies) + STOW-RS (store) + HTTP reachability ping.
 */
class DicomWebClient(
    baseUrl: String,
    private val http: OkHttpClient = defaultClient(),
) : AutoCloseable {
    private val root = baseUrl.trim().trimEnd('/')

    fun ping(): EchoResult {
        return try {
            val request = Request.Builder()
                .url("$root/studies")
                .get()
                .header("Accept", "application/dicom+json")
                .build()
            http.newCall(request).execute().use { response ->
                if (response.code in 200..499) EchoResult.Success
                else EchoResult.Failed("HTTP ${response.code}")
            }
        } catch (e: Exception) {
            EchoResult.Failed(e.message ?: "DICOMweb ping failed", e)
        }
    }

    fun qidoStudies(query: StudyQuery): FindResult<StudyEntry> {
        return try {
            val urlBuilder = "$root/studies".toHttpUrl().newBuilder()
            query.patientId?.takeIf { it.isNotBlank() }?.let { urlBuilder.addQueryParameter("PatientID", it) }
            query.patientName?.takeIf { it.isNotBlank() }?.let { urlBuilder.addQueryParameter("PatientName", it) }
            query.accessionNumber?.takeIf { it.isNotBlank() }?.let {
                urlBuilder.addQueryParameter("AccessionNumber", it)
            }
            query.studyInstanceUid?.takeIf { it.isNotBlank() }?.let {
                urlBuilder.addQueryParameter("StudyInstanceUID", it)
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .get()
                .header("Accept", "application/dicom+json")
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return FindResult.Failed("QIDO-RS HTTP ${response.code}: ${response.message}")
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return FindResult.Success(emptyList())
                FindResult.Success(parseStudyJson(body))
            }
        } catch (e: Exception) {
            FindResult.Failed(e.message ?: e.javaClass.simpleName, e)
        }
    }

    fun stow(dicomFile: File): StoreResult {
        return try {
            val boundary = "dicomcamera-${System.currentTimeMillis()}"
            val payload = ByteArrayOutputStream().use { out ->
                out.write("--$boundary\r\n".toByteArray(Charsets.US_ASCII))
                out.write("Content-Type: application/dicom\r\n".toByteArray(Charsets.US_ASCII))
                out.write("Content-Location: ${dicomFile.name}\r\n\r\n".toByteArray(Charsets.US_ASCII))
                out.write(dicomFile.readBytes())
                out.write("\r\n--$boundary--\r\n".toByteArray(Charsets.US_ASCII))
                out.toByteArray()
            }
            val mediaType =
                "multipart/related; type=\"application/dicom\"; boundary=$boundary".toMediaType()
            val request = Request.Builder()
                .url("$root/studies")
                .post(payload.toRequestBody(mediaType))
                .header("Accept", "application/dicom+json")
                .build()

            http.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (response.isSuccessful || response.code == 409) {
                    val sop = extractSopUid(responseText)
                        ?: readSopFromFile(dicomFile)
                        ?: "stow-ok"
                    StoreResult.Success(sop)
                } else {
                    // Omit response body — may include DICOM JSON demographics; failures are
                    // persisted in the pending queue and shown in the UI.
                    StoreResult.Failed("STOW-RS HTTP ${response.code}: ${response.message}")
                }
            }
        } catch (e: Exception) {
            StoreResult.Failed(e.message ?: e.javaClass.simpleName, e)
        }
    }

    override fun close() = Unit

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        private fun readSopFromFile(file: File): String? = try {
            org.dcm4che3.io.DicomInputStream(file).use { input ->
                val fmi = input.readFileMetaInformation()
                fmi?.getString(org.dcm4che3.data.Tag.MediaStorageSOPInstanceUID)
                    ?: input.readDataset().getString(org.dcm4che3.data.Tag.SOPInstanceUID)
            }
        } catch (_: Exception) {
            null
        }

        private fun parseStudyJson(body: String): List<StudyEntry> {
            val trimmed = body.trim()
            val array = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> JSONArray().put(JSONObject(trimmed))
                else -> return emptyList()
            }
            val out = ArrayList<StudyEntry>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val studyUid = dicomJsonString(obj, "0020000D") ?: continue
                out += StudyEntry(
                    patientId = dicomJsonString(obj, "00100020").orEmpty(),
                    patientName = dicomJsonString(obj, "00100010").orEmpty(),
                    patientBirthDate = dicomJsonString(obj, "00100030"),
                    patientSex = dicomJsonString(obj, "00100040"),
                    accessionNumber = dicomJsonString(obj, "00080050"),
                    studyInstanceUid = studyUid,
                    studyDate = dicomJsonString(obj, "00080020"),
                    studyDescription = dicomJsonString(obj, "00081030"),
                    modalitiesInStudy = dicomJsonString(obj, "00080061"),
                )
            }
            return out
        }

        private fun dicomJsonString(obj: JSONObject, tag: String): String? {
            val attr = obj.optJSONObject(tag) ?: return null
            val value = attr.optJSONArray("Value") ?: return null
            if (value.length() == 0) return null
            val first = value.opt(0)
            return when (first) {
                is String -> first
                is JSONObject -> first.optString("Alphabetic").ifBlank { null }
                else -> first?.toString()
            }
        }

        private fun extractSopUid(responseText: String): String? {
            if (responseText.isBlank()) return null
            return try {
                val trimmed = responseText.trim()
                val obj = when {
                    trimmed.startsWith("{") -> JSONObject(trimmed)
                    trimmed.startsWith("[") -> JSONArray(trimmed).optJSONObject(0)
                    else -> null
                } ?: return null
                dicomJsonString(obj, "00080018")
            } catch (_: Exception) {
                null
            }
        }
    }
}
