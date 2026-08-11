package nl.dicomcamera.dicom

/**
 * Site-selectable PACS transport (Phase 4 dual stack).
 */
enum class TransportMode {
    DIMSE,
    DICOMWEB,
}

/**
 * Full endpoint configuration for DIMSE and/or DICOMweb.
 */
data class PacsEndpoint(
    val transportMode: TransportMode = TransportMode.DIMSE,
    val host: String = "",
    val port: Int = 11112,
    val calledAeTitle: String = "",
    val callingAeTitle: String = "",
    val useTls: Boolean = false,
    /**
     * Base URL for DICOMweb root, e.g. `http://pacs.example/dicom-web`
     * (no trailing slash). Used when [transportMode] is [TransportMode.DICOMWEB].
     */
    val dicomWebBaseUrl: String = "",
) {
    fun toNode(): DicomNode = DicomNode(
        host = host.trim(),
        port = port,
        calledAeTitle = calledAeTitle.trim(),
        callingAeTitle = callingAeTitle.trim(),
        useTls = useTls,
    )

    fun isConfigured(): Boolean = when (transportMode) {
        TransportMode.DIMSE ->
            host.isNotBlank() &&
                calledAeTitle.isNotBlank() &&
                callingAeTitle.isNotBlank() &&
                port in 1..65535
        TransportMode.DICOMWEB ->
            dicomWebBaseUrl.trim().startsWith("http://") ||
                dicomWebBaseUrl.trim().startsWith("https://")
    }
}
