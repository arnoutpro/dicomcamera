package nl.dicomcamera.dicom

import java.io.File

/**
 * Unified PACS operations over DIMSE or DICOMweb.
 *
 * MWL C-FIND remains DIMSE-only (IHE SWF) and uses the dedicated MWL
 * destination on [PacsEndpoint] when set. Otherwise it falls back to the
 * archive DIMSE node (lab Orthanc / existing MDM).
 */
class PacsGateway(
    private val endpoint: PacsEndpoint,
) : AutoCloseable {
    fun ping(): EchoResult = when (endpoint.transportMode) {
        TransportMode.DIMSE -> PacsClient(endpoint.toNode()).use { it.echo() }
        TransportMode.DICOMWEB -> DicomWebClient(endpoint.dicomWebBaseUrl).use { it.ping() }
    }

    fun pingMwl(): EchoResult {
        val node = endpoint.resolveMwlNode()
            ?: return EchoResult.Failed(
                "Modality Worklist requires a DIMSE destination. Set MWL host/AE, or fill archive DIMSE as fallback.",
            )
        return PacsClient(node).use { it.echo() }
    }

    fun store(dicomFile: File): StoreResult = when (endpoint.transportMode) {
        TransportMode.DIMSE -> PacsClient(endpoint.toNode()).use { it.store(dicomFile) }
        TransportMode.DICOMWEB -> DicomWebClient(endpoint.dicomWebBaseUrl).use { it.stow(dicomFile) }
    }

    fun findStudies(query: StudyQuery): FindResult<StudyEntry> = when (endpoint.transportMode) {
        TransportMode.DIMSE -> PacsClient(endpoint.toNode()).use { it.findStudies(query) }
        TransportMode.DICOMWEB -> DicomWebClient(endpoint.dicomWebBaseUrl).use { it.qidoStudies(query) }
    }

    fun findWorklist(query: WorklistQuery): FindResult<WorklistEntry> {
        val node = endpoint.resolveMwlNode()
            ?: return FindResult.Failed(
                "Modality Worklist requires DIMSE. Configure the MWL destination " +
                    "(or archive DIMSE as fallback), or use Append (QIDO-RS).",
            )
        return PacsClient(node).use { it.findWorklist(query) }
    }

    override fun close() {
        // Stateless per-call clients; nothing to release.
    }

    companion object {
        fun fromEndpoint(endpoint: PacsEndpoint): PacsGateway = PacsGateway(endpoint)
    }
}
