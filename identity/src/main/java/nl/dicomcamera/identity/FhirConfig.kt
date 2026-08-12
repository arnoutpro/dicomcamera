package nl.dicomcamera.identity

/**
 * FHIR R4 Patient / order gateway config (HTTPS to EPD or API management).
 */
data class FhirConfig(
    val enabled: Boolean = false,
    /** FHIR base URL, e.g. `https://ehr.hospital.local/fhir` (no trailing slash). */
    val baseUrl: String = "",
    /** Optional Bearer token (SMART / API key). */
    val bearerToken: String = "",
) {
    fun isConfigured(): Boolean =
        enabled && (baseUrl.startsWith("http://") || baseUrl.startsWith("https://"))

    fun summary(): String = when {
        !enabled -> "Disabled"
        baseUrl.isBlank() -> "Enabled — URL not set"
        baseUrl.trim().startsWith("http://") ->
            "${baseUrl.trim()} · CLEARTEXT HTTP (prefer HTTPS — bearer is unprotected)"
        else -> baseUrl.trim()
    }
}

/**
 * Which EHR identity adapters to use when both are configured.
 */
enum class IdentityLookupMode {
    /** Prefer FHIR, fall back to HL7 if empty / error. */
    FHIR_THEN_HL7,
    /** Prefer HL7, fall back to FHIR. */
    HL7_THEN_FHIR,
    /** FHIR only. */
    FHIR_ONLY,
    /** HL7 only. */
    HL7_ONLY,
}
