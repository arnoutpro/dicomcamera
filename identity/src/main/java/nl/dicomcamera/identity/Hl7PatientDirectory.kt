package nl.dicomcamera.identity

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * HL7 v2 demographics via hospital HTTPS façade (QBP/RSP upstream).
 *
 * Contract (MVP):
 * `GET {baseUrl}/patients?patientId=…` → JSON object or array of
 * `{ "patientId", "patientName", "birthDate?", "sex?" }`
 *
 * Construction takes only a config provider so `:app` does not need OkHttp on its
 * compile classpath (OkHttp stays an implementation detail of `:identity`).
 */
class Hl7PatientDirectory(
    private val configProvider: () -> Hl7FacadeConfig,
) : PatientDirectory {
    private val http: OkHttpClient = defaultClient()

    override val source: IdentitySource = IdentitySource.HL7_V2

    override suspend fun findPatients(query: PatientQuery): List<PatientDemographics> {
        val config = configProvider()
        require(config.isConfigured()) {
            "HL7 façade not configured. Enable it and set the base URL in Settings."
        }
        val patientId = query.patientId?.trim().orEmpty()
        require(patientId.isNotBlank()) { "Patient ID required for HL7 lookup" }

        val url = config.baseUrl.trim().trimEnd('/')
            .toHttpUrl()
            .newBuilder()
            .addPathSegment("patients")
            .addQueryParameter("patientId", patientId)
            .apply {
                query.patientName?.takeIf { it.isNotBlank() }?.let {
                    addQueryParameter("patientName", it)
                }
                query.accessionNumber?.takeIf { it.isNotBlank() }?.let {
                    addQueryParameter("accessionNumber", it)
                }
            }
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
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
                    "HL7 façade HTTP ${response.code}: ${response.message}",
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
            val list = when {
                trimmed.startsWith("[") -> {
                    val arr = JSONArray(trimmed)
                    (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                }
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    // Support { "patients": [ ... ] } or a single patient object
                    val nested = obj.optJSONArray("patients")
                    if (nested != null) {
                        (0 until nested.length()).mapNotNull { nested.optJSONObject(it) }
                    } else {
                        listOf(obj)
                    }
                }
                else -> emptyList()
            }
            return list.mapNotNull { obj ->
                val id = obj.optString("patientId").ifBlank { null } ?: return@mapNotNull null
                val name = obj.optString("patientName").ifBlank { "UNKNOWN^UNKNOWN" }
                PatientDemographics(
                    patientId = id,
                    patientName = name,
                    birthDate = obj.optString("birthDate").takeIf { it.isNotBlank() },
                    sex = obj.optString("sex").takeIf { it.isNotBlank() },
                    source = IdentitySource.HL7_V2,
                )
            }
        }
    }
}
