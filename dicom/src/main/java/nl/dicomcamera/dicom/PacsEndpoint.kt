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
 *
 * Archive DIMSE ([host]/[port]/[calledAeTitle]) is used for C-STORE, C-ECHO,
 * and Study Root C-FIND. Modality Worklist is a separate DIMSE destination
 * ([mwlHost]/[mwlPort]/[mwlCalledAeTitle]). When MWL fields are left empty,
 * [resolveMwlNode] falls back to the archive DIMSE node so lab Orthanc and
 * existing MDM bundles keep working.
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
    /** Dedicated MWL SCP host. Empty → fall back to archive DIMSE. */
    val mwlHost: String = "",
    val mwlPort: Int = 11112,
    val mwlCalledAeTitle: String = "",
    val mwlUseTls: Boolean = false,
) {
    fun toNode(): DicomNode = DicomNode(
        host = host.trim(),
        port = port,
        calledAeTitle = calledAeTitle.trim(),
        callingAeTitle = callingAeTitle.trim(),
        useTls = useTls,
    )

    /**
     * Dedicated worklist SCP, or `null` when MWL fields are empty or incomplete.
     * A partially filled MWL destination does **not** fall back — that would
     * silently query the archive.
     */
    fun toMwlNode(): DicomNode? {
        val dedicatedHost = mwlHost.trim()
        val dedicatedAe = mwlCalledAeTitle.trim()
        if (dedicatedHost.isBlank() && dedicatedAe.isBlank()) return null
        if (!isDimseReady(dedicatedHost, mwlPort, dedicatedAe, callingAeTitle)) return null
        return DicomNode(
            host = dedicatedHost,
            port = mwlPort,
            calledAeTitle = dedicatedAe,
            callingAeTitle = callingAeTitle.trim(),
            useTls = mwlUseTls,
        )
    }

    /** True when the operator started filling a dedicated MWL destination. */
    fun hasDedicatedMwl(): Boolean =
        mwlHost.isNotBlank() || mwlCalledAeTitle.isNotBlank()

    /**
     * Node used for MWL C-FIND: dedicated MWL SCP when configured, otherwise
     * the archive DIMSE node (same calling AE).
     */
    fun resolveMwlNode(): DicomNode? {
        if (hasDedicatedMwl()) return toMwlNode()
        return toNode().takeIf {
            isDimseReady(host, port, calledAeTitle, callingAeTitle)
        }
    }

    fun isMwlConfigured(): Boolean = resolveMwlNode() != null

    fun isConfigured(): Boolean = when (transportMode) {
        TransportMode.DIMSE ->
            isDimseReady(host, port, calledAeTitle, callingAeTitle)
        TransportMode.DICOMWEB ->
            dicomWebBaseUrl.trim().startsWith("http://") ||
                dicomWebBaseUrl.trim().startsWith("https://")
    }

    companion object {
        fun isDimseReady(
            host: String,
            port: Int,
            calledAeTitle: String,
            callingAeTitle: String,
        ): Boolean =
            host.isNotBlank() &&
                calledAeTitle.isNotBlank() &&
                callingAeTitle.isNotBlank() &&
                port in 1..65535
    }
}
