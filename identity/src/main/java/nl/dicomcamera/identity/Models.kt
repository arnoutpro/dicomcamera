package nl.dicomcamera.identity

/**
 * Demographics resolved from MWL, HL7, FHIR, or manual entry.
 * Maps into DICOM patient modules in the dicom layer.
 */
data class PatientDemographics(
    val patientId: String,
    val patientName: String,
    val birthDate: String? = null,
    val sex: String? = null,
    val source: IdentitySource,
)

enum class IdentitySource {
    MANUAL,
    MODALITY_WORKLIST,
    HL7_V2,
    FHIR,
    PACS_QUERY,
}

data class OrderExamRef(
    val accessionNumber: String? = null,
    val studyInstanceUid: String? = null,
    val requestedProcedureId: String? = null,
    val studyDescription: String? = null,
    val patient: PatientDemographics,
)

data class PatientQuery(
    val patientId: String? = null,
    val accessionNumber: String? = null,
    val patientName: String? = null,
)
