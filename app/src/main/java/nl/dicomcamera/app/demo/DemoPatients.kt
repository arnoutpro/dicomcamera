package nl.dicomcamera.app.demo

import nl.dicomcamera.dicom.WorklistEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Built-in demo worklist so the app can be explored without a live PACS.
 */
object DemoPatients {
    private val today: String
        get() = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)

    val entries: List<WorklistEntry>
        get() = listOf(
            WorklistEntry(
                patientId = "DEMO-1001",
                patientName = "JANSEN^ANNE",
                patientBirthDate = "19800315",
                patientSex = "F",
                accessionNumber = "ACC-DEMO-1001",
                studyInstanceUid = "1.2.826.0.1.3680043.10.474.1001",
                requestedProcedureId = "RP-DEMO-1",
                scheduledProcedureStepId = "SPS-DEMO-1",
                modality = "XC",
                scheduledStationAeTitle = "DICOMCAM",
                scheduledStartDate = today,
                scheduledStartTime = "090000",
                studyDescription = "Demo clinical photography — hand",
            ),
            WorklistEntry(
                patientId = "DEMO-1002",
                patientName = "DE VRIES^PIETER",
                patientBirthDate = "19720602",
                patientSex = "M",
                accessionNumber = "ACC-DEMO-1002",
                studyInstanceUid = "1.2.826.0.1.3680043.10.474.1002",
                requestedProcedureId = "RP-DEMO-2",
                scheduledProcedureStepId = "SPS-DEMO-2",
                modality = "XC",
                scheduledStationAeTitle = "DICOMCAM",
                scheduledStartDate = today,
                scheduledStartTime = "103000",
                studyDescription = "Demo clinical photography — foot",
            ),
        )

    fun isDemoPatientId(patientId: String): Boolean =
        patientId.startsWith("DEMO-", ignoreCase = true)
}
