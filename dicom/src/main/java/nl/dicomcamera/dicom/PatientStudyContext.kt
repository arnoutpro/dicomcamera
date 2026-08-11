package nl.dicomcamera.dicom

/**
 * Patient / study demographics stamped into created SOP instances.
 * Source of truth should be MWL / EHR lookup in later phases — not free text.
 */
data class PatientStudyContext(
    val patientId: String,
    val patientName: String,
    val patientBirthDate: String? = null, // DICOM DA: YYYYMMDD
    val patientSex: String? = null, // M | F | O
    val accessionNumber: String? = null,
    val studyDescription: String? = null,
    val studyInstanceUid: String? = null,
    val seriesDescription: String? = null,
    val modality: String = "XC",
)
