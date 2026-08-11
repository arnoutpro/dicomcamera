package nl.dicomcamera.identity

/**
 * Pluggable patient demographics directory.
 *
 * Phase 0: [ManualPatientDirectory] only.
 * Later: MWL, HL7 v2 façade, FHIR Patient search — same interface.
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
 * Placeholder — implemented in Phase 2 (DICOM MWL C-FIND).
 */
class ModalityWorklistDirectory : OrderDirectory {
    override val source: IdentitySource = IdentitySource.MODALITY_WORKLIST

    override suspend fun findOrders(query: PatientQuery): List<OrderExamRef> {
        throw NotImplementedError("MWL C-FIND lands in Phase 2")
    }
}

/**
 * Placeholder — Phase 5 HL7 v2 demographics query via HTTPS façade.
 */
class Hl7PatientDirectory : PatientDirectory {
    override val source: IdentitySource = IdentitySource.HL7_V2

    override suspend fun findPatients(query: PatientQuery): List<PatientDemographics> {
        throw NotImplementedError("HL7 v2 patient lookup lands in Phase 5")
    }
}

/**
 * Placeholder — Phase 5 FHIR R4 Patient search / SMART launch.
 */
class FhirPatientDirectory : PatientDirectory {
    override val source: IdentitySource = IdentitySource.FHIR

    override suspend fun findPatients(query: PatientQuery): List<PatientDemographics> {
        throw NotImplementedError("FHIR Patient lookup lands in Phase 5")
    }
}
