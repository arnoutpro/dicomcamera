package nl.dicomcamera.dicom

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class WorklistAndAppendTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var scp: InProcessDicomScp
    private val today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)

    @Before
    fun setUp() {
        scp = InProcessDicomScp(storageDir = temp.newFolder("pacs"))
        scp.start()
        scp.addWorklistItem(
            WorklistEntry(
                patientId = "WL-100",
                patientName = "WORKLIST^PATIENT",
                patientBirthDate = "19750101",
                patientSex = "F",
                accessionNumber = "ACC-WL-1",
                studyInstanceUid = "2.25.111",
                requestedProcedureId = "RP1",
                scheduledProcedureStepId = "SPS1",
                modality = "XC",
                scheduledStationAeTitle = "DICOMCAM",
                scheduledStartDate = today,
                studyDescription = "Wound photo",
            ),
        )
        scp.addStudy(
            StudyEntry(
                patientId = "ST-200",
                patientName = "STUDY^EXISTING",
                accessionNumber = "ACC-ST-2",
                studyInstanceUid = "2.25.222",
                studyDate = today,
                studyDescription = "Prior exam",
                modalitiesInStudy = "XC",
            ),
        )
    }

    @After
    fun tearDown() {
        scp.close()
    }

    private fun node() = DicomNode("127.0.0.1", scp.boundPort, "TESTPACS", "DICOMCAM")

    @Test
    fun mwl_find_returns_scheduled_item() {
        PacsClient(node()).use { client ->
            val result = client.findWorklist(
                WorklistQuery(patientId = "WL-100", modality = "XC", scheduledDate = today),
            )
            assertThat(result).isInstanceOf(FindResult.Success::class.java)
            val items = (result as FindResult.Success).items
            assertThat(items).hasSize(1)
            assertThat(items[0].accessionNumber).isEqualTo("ACC-WL-1")
            assertThat(items[0].studyInstanceUid).isEqualTo("2.25.111")
        }
    }

    @Test
    fun study_find_and_append_keeps_study_uid() {
        val staging = SecureStaging(temp.newFolder("staging"))
        val jpeg = javaClass.getResourceAsStream("/sample.jpg")!!.use { it.readBytes() }
        val dicomFile = staging.createStagingFile("vl", "dcm")

        val ctx = StudyEntry(
            patientId = "ST-200",
            patientName = "STUDY^EXISTING",
            accessionNumber = "ACC-ST-2",
            studyInstanceUid = "2.25.222",
        ).toPatientStudyContext()

        val encoded = PhotographicImageEncoder().encodeJpegToFile(
            jpegBytes = jpeg,
            context = ctx,
            rows = 16,
            columns = 16,
            outputFile = dicomFile,
        )
        assertThat(encoded.studyInstanceUid).isEqualTo("2.25.222")

        PacsClient(node()).use { client ->
            val found = client.findStudies(StudyQuery(patientId = "ST-200"))
            assertThat(found).isInstanceOf(FindResult.Success::class.java)
            assertThat((found as FindResult.Success).items.map { it.studyInstanceUid })
                .contains("2.25.222")

            val store = client.store(dicomFile)
            assertThat(store).isInstanceOf(StoreResult.Success::class.java)
            val sop = (store as StoreResult.Success).sopInstanceUid
            assertThat(scp.readStudyUid(sop)).isEqualTo("2.25.222")
            assertThat(scp.readPatientId(sop)).isEqualTo("ST-200")
        }
    }
}
