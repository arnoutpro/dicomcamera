package nl.dicomcamera.app.session

import nl.dicomcamera.app.ui.formatPersonNameForDisplay
import nl.dicomcamera.dicom.PatientStudyContext

enum class ExamSource {
    MANUAL,
    WORKLIST,
    APPEND_EXISTING,
}

data class ExamSelection(
    val context: PatientStudyContext,
    val source: ExamSource,
) {
    val banner: String
        get() {
            val src = when (source) {
                ExamSource.MANUAL -> "Manual"
                ExamSource.WORKLIST -> "Worklist"
                ExamSource.APPEND_EXISTING -> "Append"
            }
            val acc = context.accessionNumber?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            return "$src · ${context.patientId} · ${formatPersonNameForDisplay(context.patientName)}$acc"
        }
}
