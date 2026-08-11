package nl.dicomcamera.identity

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * FHIR R4 order / exam context via ServiceRequest and ImagingStudy search.
 */
class FhirOrderDirectory(
    private val configProvider: () -> FhirConfig,
) : OrderDirectory {
    private val http: OkHttpClient = defaultClient()

    override val source: IdentitySource = IdentitySource.FHIR

    override suspend fun findOrders(query: PatientQuery): List<OrderExamRef> {
        val config = configProvider()
        require(config.isConfigured()) { "FHIR not configured" }
        val patientId = query.patientId?.trim().orEmpty()
        require(patientId.isNotBlank()) { "Patient ID required for FHIR order lookup" }

        val patient = PatientDemographics(
            patientId = patientId,
            patientName = query.patientName?.takeIf { it.isNotBlank() } ?: "UNKNOWN^UNKNOWN",
            source = IdentitySource.FHIR,
        )

        val serviceRequests = searchBundle(
            config = config,
            resource = "ServiceRequest",
            params = buildMap {
                put("patient:identifier", patientId)
                query.accessionNumber?.takeIf { it.isNotBlank() }?.let {
                    put("identifier", it)
                }
            },
        ).mapNotNull { toOrderFromServiceRequest(it, patient) }

        val imagingStudies = searchBundle(
            config = config,
            resource = "ImagingStudy",
            params = buildMap {
                put("patient:identifier", patientId)
                query.accessionNumber?.takeIf { it.isNotBlank() }?.let {
                    put("identifier", it)
                }
            },
        ).mapNotNull { toOrderFromImagingStudy(it, patient) }

        return (serviceRequests + imagingStudies).distinctBy {
            listOf(it.accessionNumber, it.studyInstanceUid, it.requestedProcedureId)
        }
    }

    private fun searchBundle(
        config: FhirConfig,
        resource: String,
        params: Map<String, String>,
    ): List<JSONObject> {
        val url = config.baseUrl.trim().trimEnd('/')
            .toHttpUrl()
            .newBuilder()
            .addPathSegment(resource)
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/fhir+json")
            .apply {
                config.bearerToken.trim().takeIf { it.isNotEmpty() }?.let {
                    header("Authorization", "Bearer $it")
                }
            }
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("FHIR $resource HTTP ${response.code}: ${response.message}")
            }
            if (body.isBlank()) return emptyList()
            return parseBundleResources(body, resource)
        }
    }

    companion object {
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        fun parseBundleResources(body: String, resourceType: String): List<JSONObject> {
            val root = JSONObject(body.trim())
            if (root.optString("resourceType") != "Bundle") {
                return if (root.optString("resourceType") == resourceType) listOf(root) else emptyList()
            }
            val entry = root.optJSONArray("entry") ?: JSONArray()
            return (0 until entry.length()).mapNotNull { i ->
                entry.optJSONObject(i)?.optJSONObject("resource")
                    ?.takeIf { it.optString("resourceType") == resourceType }
            }
        }

        fun toOrderFromServiceRequest(sr: JSONObject, patient: PatientDemographics): OrderExamRef? {
            val accession = firstIdentifier(sr)
            val procedureId = sr.optString("id").takeIf { it.isNotBlank() }
            val desc = sr.optJSONObject("code")?.optString("text")
                ?.takeIf { it.isNotBlank() }
                ?: sr.optJSONObject("code")?.optJSONArray("coding")
                    ?.optJSONObject(0)?.optString("display")?.takeIf { it.isNotBlank() }
            if (accession.isNullOrBlank() && procedureId.isNullOrBlank()) return null
            return OrderExamRef(
                accessionNumber = accession,
                studyInstanceUid = null,
                requestedProcedureId = procedureId,
                studyDescription = desc,
                patient = patient,
            )
        }

        fun toOrderFromImagingStudy(study: JSONObject, patient: PatientDemographics): OrderExamRef? {
            val studyUid = study.optString("uid").takeIf { it.isNotBlank() }
                ?: firstIdentifierValue(study, systemContains = "dicom")
            val accession = firstIdentifierValue(study, systemContains = "accession")
                ?: firstIdentifier(study)
            val desc = study.optString("description").takeIf { it.isNotBlank() }
            if (studyUid.isNullOrBlank() && accession.isNullOrBlank()) return null
            return OrderExamRef(
                accessionNumber = accession,
                studyInstanceUid = studyUid,
                requestedProcedureId = study.optString("id").takeIf { it.isNotBlank() },
                studyDescription = desc,
                patient = patient,
            )
        }

        private fun firstIdentifier(resource: JSONObject): String? {
            val ids = resource.optJSONArray("identifier") ?: return null
            for (i in 0 until ids.length()) {
                val value = ids.optJSONObject(i)?.optString("value").orEmpty()
                if (value.isNotBlank()) return value
            }
            return null
        }

        private fun firstIdentifierValue(resource: JSONObject, systemContains: String): String? {
            val ids = resource.optJSONArray("identifier") ?: return null
            for (i in 0 until ids.length()) {
                val obj = ids.optJSONObject(i) ?: continue
                val system = obj.optString("system")
                if (system.contains(systemContains, ignoreCase = true)) {
                    val value = obj.optString("value")
                    if (value.isNotBlank()) return value
                }
            }
            return null
        }
    }
}
