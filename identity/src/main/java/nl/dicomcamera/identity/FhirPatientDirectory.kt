package nl.dicomcamera.identity

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * FHIR R4 Patient search (IHE PDQm-style over HTTPS).
 *
 * Contract:
 * `GET {baseUrl}/Patient?identifier={patientId}` with `Accept: application/fhir+json`
 * → Bundle of Patient resources, or a single Patient.
 */
class FhirPatientDirectory(
    private val configProvider: () -> FhirConfig,
) : PatientDirectory {
    private val http: OkHttpClient = defaultClient()

    override val source: IdentitySource = IdentitySource.FHIR

    override suspend fun findPatients(query: PatientQuery): List<PatientDemographics> {
        val config = configProvider()
        require(config.isConfigured()) {
            "FHIR not configured. Enable it and set the base URL in Settings."
        }
        val patientId = query.patientId?.trim().orEmpty()
        require(patientId.isNotBlank()) { "Patient ID required for FHIR lookup" }

        val url = config.baseUrl.trim().trimEnd('/')
            .toHttpUrl()
            .newBuilder()
            .addPathSegment("Patient")
            .addQueryParameter("identifier", patientId)
            .apply {
                query.patientName?.takeIf { it.isNotBlank() }?.let {
                    addQueryParameter("family", it.substringBefore('^').trim())
                }
            }
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
                // Never embed response bodies in exceptions — they may contain PHI and are
                // surfaced in UI / opt-in diagnostic logs.
                throw IllegalStateException(
                    "FHIR Patient HTTP ${response.code}: ${response.message}",
                )
            }
            if (body.isBlank()) return emptyList()
            return parsePatients(body)
        }
    }

    companion object {
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        fun parsePatients(body: String): List<PatientDemographics> {
            val trimmed = body.trim()
            val resources = when {
                trimmed.startsWith("{") -> {
                    val root = JSONObject(trimmed)
                    when (root.optString("resourceType")) {
                        "Bundle" -> {
                            val entry = root.optJSONArray("entry") ?: JSONArray()
                            (0 until entry.length()).mapNotNull { i ->
                                entry.optJSONObject(i)?.optJSONObject("resource")
                                    ?.takeIf { it.optString("resourceType") == "Patient" }
                            }
                        }
                        "Patient" -> listOf(root)
                        else -> emptyList()
                    }
                }
                else -> emptyList()
            }
            return resources.mapNotNull { patientToDemographics(it) }
        }

        internal fun patientToDemographics(patient: JSONObject): PatientDemographics? {
            val id = firstIdentifier(patient)
                ?: patient.optString("id").takeIf { it.isNotBlank() }
                ?: return null
            val name = humanNameToDicom(patient.optJSONArray("name"))
            val birth = patient.optString("birthDate").takeIf { it.isNotBlank() }
                ?.replace("-", "")
                ?.take(8)
            val sex = when (patient.optString("gender").lowercase()) {
                "male" -> "M"
                "female" -> "F"
                "other" -> "O"
                "unknown", "" -> null
                else -> patient.optString("gender").uppercase().take(1)
            }
            return PatientDemographics(
                patientId = id,
                patientName = name,
                birthDate = birth,
                sex = sex,
                source = IdentitySource.FHIR,
            )
        }

        private fun firstIdentifier(patient: JSONObject): String? {
            val ids = patient.optJSONArray("identifier") ?: return null
            for (i in 0 until ids.length()) {
                val value = ids.optJSONObject(i)?.optString("value").orEmpty()
                if (value.isNotBlank()) return value
            }
            return null
        }

        private fun humanNameToDicom(names: JSONArray?): String {
            if (names == null || names.length() == 0) return "UNKNOWN^UNKNOWN"
            val name = names.optJSONObject(0) ?: return "UNKNOWN^UNKNOWN"
            val text = name.optString("text").takeIf { it.isNotBlank() }
            if (text != null) {
                // Prefer DICOM PN if already FAMILY^GIVEN; else "Given Family" → FAMILY^GIVEN
                return if (text.contains('^')) {
                    text
                } else {
                    val parts = text.trim().split(Regex("\\s+"))
                    when {
                        parts.size >= 2 -> "${parts.last().uppercase()}^${parts.dropLast(1).joinToString(" ").uppercase()}"
                        else -> "${parts.first().uppercase()}^"
                    }
                }
            }
            val family = name.optString("family").ifBlank { "UNKNOWN" }
            val givenArr = name.optJSONArray("given")
            val given = if (givenArr != null && givenArr.length() > 0) {
                (0 until givenArr.length()).mapNotNull { givenArr.optString(it).takeIf { g -> g.isNotBlank() } }
                    .joinToString(" ")
            } else {
                ""
            }
            return if (given.isBlank()) "$family^" else "$family^$given"
        }
    }
}
