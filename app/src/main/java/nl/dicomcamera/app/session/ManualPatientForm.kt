package nl.dicomcamera.app.session

data class ManualPatientForm(
    val patientId: String = "",
    val patientName: String = "",
    val birthDate: String = "", // YYYYMMDD or empty
    val sex: String = "", // M / F / O / empty
    val accessionNumber: String = "",
    val studyDescription: String = "",
) {
    fun isValid(): Boolean = patientId.isNotBlank() && patientName.isNotBlank()

    /** DICOM PN hint: FAMILY^GIVEN */
    fun normalizedName(): String = patientName.trim()
}
