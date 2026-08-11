package nl.dicomcamera.identity

import nl.dicomcamera.dicom.DicomNode
import nl.dicomcamera.dicom.FindResult
import nl.dicomcamera.dicom.PacsClient
import nl.dicomcamera.dicom.WorklistQuery

/**
 * Pluggable patient demographics directory.
 */
interface PatientDirectory {
    val source: IdentitySource
    suspend fun findPatients(query: PatientQuery): List<PatientDemographics>
}

/**
 * Pluggable order / exam directory (MWL + later FHIR ServiceRequest/ImagingStudy).
 */
interface OrderDirectory {
    val source: IdentitySource
    suspend fun findOrders(query: PatientQuery): List<OrderExamRef>
}

class ManualPatientDirectory : PatientDirectory {
    override val source: IdentitySource = IdentitySource.MANUAL

    override suspend fun findPatients(query: PatientQuery): List<PatientDemographics> {
        val id = query.patientId?.takeIf { it.isNotBlank() } ?: return emptyList()
        val name = query.patientName?.takeIf { it.isNotBlank() } ?: "UNKNOWN^UNKNOWN"
        return listOf(
            PatientDemographics(
                patientId = id,
                patientName = name,
                source = IdentitySource.MANUAL,
            ),
        )
    }
}

/**
 * Phase 2: DICOM Modality Worklist C-FIND backed order directory.
 */
class ModalityWorklistDirectory(
    private val nodeProvider: () -> DicomNode,
) : OrderDirectory {
    override val source: IdentitySource = IdentitySource.MODALITY_WORKLIST

    override suspend fun findOrders(query: PatientQuery): List<OrderExamRef> {
        val result = PacsClient(nodeProvider()).use { client ->
            client.findWorklist(
                WorklistQuery(
                    patientId = query.patientId,
                    patientName = query.patientName,
                    accessionNumber = query.accessionNumber,
                    modality = "XC",
                ),
            )
        }
        return when (result) {
            is FindResult.Failed -> throw IllegalStateException(result.message, result.cause)
            is FindResult.Success -> result.items.map { entry ->
                OrderExamRef(
                    accessionNumber = entry.accessionNumber,
                    studyInstanceUid = entry.studyInstanceUid,
                    requestedProcedureId = entry.requestedProcedureId,
                    studyDescription = entry.studyDescription,
                    patient = PatientDemographics(
                        patientId = entry.patientId,
                        patientName = entry.patientName,
                        birthDate = entry.patientBirthDate,
                        sex = entry.patientSex,
                        source = IdentitySource.MODALITY_WORKLIST,
                    ),
                )
            }
        }
    }
}
