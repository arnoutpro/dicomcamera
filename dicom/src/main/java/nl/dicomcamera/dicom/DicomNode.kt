package nl.dicomcamera.dicom

/**
 * Connection parameters for a remote DICOM Application Entity.
 *
 * @param useTls When true, negotiate DICOM TLS (Phase 1 basic support; hospital CAs via system trust).
 */
data class DicomNode(
    val host: String,
    val port: Int,
    val calledAeTitle: String,
    val callingAeTitle: String,
    val useTls: Boolean = false,
)
