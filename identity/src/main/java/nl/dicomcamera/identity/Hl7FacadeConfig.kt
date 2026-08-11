package nl.dicomcamera.identity

/**
 * HTTPS façade config for HL7 v2 demographics (hospital interface engine or on-prem connector).
 * The phone never speaks raw MLLP.
 */
data class Hl7FacadeConfig(
    val enabled: Boolean = false,
    /** Base URL, e.g. `https://ehr-gw.hospital.local/hl7` (no trailing slash). */
    val baseUrl: String = "",
    /** Optional Bearer token for the façade. */
    val bearerToken: String = "",
) {
    fun isConfigured(): Boolean =
        enabled && (baseUrl.startsWith("http://") || baseUrl.startsWith("https://"))

    fun summary(): String = when {
        !enabled -> "Disabled"
        baseUrl.isBlank() -> "Enabled — URL not set"
        else -> baseUrl.trim()
    }
}
