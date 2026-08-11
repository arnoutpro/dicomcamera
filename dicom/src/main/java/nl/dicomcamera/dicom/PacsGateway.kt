package nl.dicomcamera.dicom

import java.io.File

/**
 * Unified PACS operations over DIMSE or DICOMweb.
 *
 * MWL C-FIND remains DIMSE-only (IHE SWF); when transport is DICOMweb,
 * [findWorklist] returns a clear failure unless a DIMSE node is also configured
 * via [dimseFallback].
 */
class PacsGateway(
    private val endpoint: PacsEndpoint,
    private val dimseFallback: DicomNode? = null,
) : AutoCloseable {
    fun ping(): EchoResult = when (endpoint.transportMode) {
        TransportMode.DIMSE -> PacsClient(endpoint.toNode()).use { it.echo() }
        TransportMode.DICOMWEB -> DicomWebClient(endpoint.dicomWebBaseUrl).use { it.ping() }
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
        val node = when (endpoint.transportMode) {
            TransportMode.DIMSE -> endpoint.toNode()
            TransportMode.DICOMWEB -> dimseFallback
                ?: return FindResult.Failed(
                    "Modality Worklist requires DIMSE. Configure DIMSE AE or use Append (QIDO-RS).",
                )
        }
        return PacsClient(node).use { it.findWorklist(query) }
    }

    override fun close() {
        // Stateless per-call clients; nothing to release.
    }

    companion object {
        fun fromEndpoint(endpoint: PacsEndpoint): PacsGateway {
            val fallback = endpoint.takeIf {
                it.host.isNotBlank() && it.calledAeTitle.isNotBlank()
            }?.toNode()
            return PacsGateway(endpoint, dimseFallback = fallback)
        }
    }
}
