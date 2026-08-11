package nl.dicomcamera.dicom

/**
 * Connection parameters for a remote DICOM Application Entity.
 */
data class DicomNode(
    val host: String,
    val port: Int,
    val calledAeTitle: String,
    val callingAeTitle: String,
)
