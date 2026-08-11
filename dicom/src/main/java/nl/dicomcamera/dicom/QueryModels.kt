package nl.dicomcamera.dicom

/**
 * Filters for Modality Worklist C-FIND.
 */
data class WorklistQuery(
    val patientId: String? = null,
    val patientName: String? = null,
    val accessionNumber: String? = null,
    val modality: String? = "XC",
    val scheduledStationAeTitle: String? = null,
    /** DICOM DA YYYYMMDD; defaults to today when null in client helpers. */
    val scheduledDate: String? = null,
)

/**
 * One scheduled procedure step / worklist item.
 */
data class WorklistEntry(
    val patientId: String,
    val patientName: String,
    val patientBirthDate: String? = null,
    val patientSex: String? = null,
    val accessionNumber: String? = null,
    val studyInstanceUid: String? = null,
    val requestedProcedureId: String? = null,
    val scheduledProcedureStepId: String? = null,
    val modality: String? = null,
    val scheduledStationAeTitle: String? = null,
    val scheduledStartDate: String? = null,
    val scheduledStartTime: String? = null,
    val studyDescription: String? = null,
) {
    fun toPatientStudyContext(seriesDescription: String = "Clinical photograph"): PatientStudyContext =
        PatientStudyContext(
            patientId = patientId,
            patientName = patientName,
            patientBirthDate = patientBirthDate,
            patientSex = patientSex,
            accessionNumber = accessionNumber,
            studyDescription = studyDescription,
            studyInstanceUid = studyInstanceUid,
            seriesDescription = seriesDescription,
            modality = modality?.takeIf { it.isNotBlank() } ?: "XC",
        )
}

data class StudyQuery(
    val patientId: String? = null,
    val accessionNumber: String? = null,
    val studyInstanceUid: String? = null,
    val patientName: String? = null,
)

data class StudyEntry(
    val patientId: String,
    val patientName: String,
    val patientBirthDate: String? = null,
    val patientSex: String? = null,
    val accessionNumber: String? = null,
    val studyInstanceUid: String,
    val studyDate: String? = null,
    val studyDescription: String? = null,
    val modalitiesInStudy: String? = null,
) {
    fun toPatientStudyContext(seriesDescription: String = "Additional clinical photograph"): PatientStudyContext =
        PatientStudyContext(
            patientId = patientId,
            patientName = patientName,
            patientBirthDate = patientBirthDate,
            patientSex = patientSex,
            accessionNumber = accessionNumber,
            studyDescription = studyDescription,
            studyInstanceUid = studyInstanceUid,
            seriesDescription = seriesDescription,
            modality = "XC",
        )
}

sealed interface FindResult<out T> {
    data class Success<T>(val items: List<T>) : FindResult<T>
    data class Failed(val message: String, val cause: Throwable? = null) : FindResult<Nothing>
}
