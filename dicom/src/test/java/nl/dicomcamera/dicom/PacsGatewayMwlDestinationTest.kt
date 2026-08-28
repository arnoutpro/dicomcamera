package nl.dicomcamera.dicom

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PacsGatewayMwlDestinationTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var archive: InProcessDicomScp
    private lateinit var mwl: InProcessDicomScp
    private val today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)

    @Before
    fun setUp() {
        archive = InProcessDicomScp(aeTitle = "ARCHIVE", storageDir = temp.newFolder("archive"))
        mwl = InProcessDicomScp(aeTitle = "MWLSCP", storageDir = temp.newFolder("mwl"))
        archive.start()
        mwl.start()
        archive.addWorklistItem(
            WorklistEntry(
                patientId = "ARCHIVE-ONLY",
                patientName = "ARCHIVE^ITEM",
                accessionNumber = "ACC-ARCHIVE",
                studyInstanceUid = "2.25.archive",
                modality = "XC",
                scheduledStationAeTitle = "DICOMCAM",
                scheduledStartDate = today,
            ),
        )
        mwl.addWorklistItem(
            WorklistEntry(
                patientId = "MWL-ONLY",
                patientName = "WORKLIST^ITEM",
                accessionNumber = "ACC-MWL",
                studyInstanceUid = "2.25.mwl",
                modality = "XC",
                scheduledStationAeTitle = "DICOMCAM",
                scheduledStartDate = today,
            ),
        )
    }

    @After
    fun tearDown() {
        archive.close()
        mwl.close()
    }

    private fun archiveEndpoint(mwlHost: String = "", mwlCalled: String = "") = PacsEndpoint(
        host = "127.0.0.1",
        port = archive.boundPort,
        calledAeTitle = "ARCHIVE",
        callingAeTitle = "DICOMCAM",
        mwlHost = mwlHost,
        mwlPort = mwl.boundPort,
        mwlCalledAeTitle = mwlCalled,
    )

    @Test
    fun findWorklist_hits_dedicated_mwl_not_archive() {
        val gateway = PacsGateway.fromEndpoint(
            archiveEndpoint(mwlHost = "127.0.0.1", mwlCalled = "MWLSCP"),
        )
        val result = gateway.findWorklist(
            WorklistQuery(modality = "XC", scheduledDate = today),
        )
        assertThat(result).isInstanceOf(FindResult.Success::class.java)
        val items = (result as FindResult.Success).items
        assertThat(items.map { it.patientId }).containsExactly("MWL-ONLY")
    }

    @Test
    fun findWorklist_falls_back_to_archive_when_mwl_empty() {
        val gateway = PacsGateway.fromEndpoint(archiveEndpoint())
        val result = gateway.findWorklist(
            WorklistQuery(modality = "XC", scheduledDate = today),
        )
        assertThat(result).isInstanceOf(FindResult.Success::class.java)
        val items = (result as FindResult.Success).items
        assertThat(items.map { it.patientId }).containsExactly("ARCHIVE-ONLY")
    }

    @Test
    fun store_still_targets_archive_when_mwl_is_dedicated() {
        val jpeg = javaClass.getResourceAsStream("/sample.jpg")!!.use { it.readBytes() }
        val dicomFile = temp.newFile("vl.dcm")
        PhotographicImageEncoder().encodeJpegToFile(
            jpegBytes = jpeg,
            context = PatientStudyContext(patientId = "ST-1", patientName = "STORE^ONE"),
            rows = 16,
            columns = 16,
            outputFile = dicomFile,
        )
        val gateway = PacsGateway.fromEndpoint(
            archiveEndpoint(mwlHost = "127.0.0.1", mwlCalled = "MWLSCP"),
        )
        val store = gateway.store(dicomFile)
        assertThat(store).isInstanceOf(StoreResult.Success::class.java)
        val sop = (store as StoreResult.Success).sopInstanceUid
        assertThat(archive.readPatientId(sop)).isEqualTo("ST-1")
        assertThat(mwl.readPatientId(sop)).isNull()
    }

    @Test
    fun pingMwl_echoes_dedicated_scp() {
        val gateway = PacsGateway.fromEndpoint(
            archiveEndpoint(mwlHost = "127.0.0.1", mwlCalled = "MWLSCP"),
        )
        assertThat(gateway.pingMwl()).isEqualTo(EchoResult.Success)
    }

    @Test
    fun findWorklist_fails_when_dedicated_mwl_is_incomplete() {
        val gateway = PacsGateway.fromEndpoint(
            archiveEndpoint(mwlHost = "127.0.0.1", mwlCalled = ""),
        )
        val result = gateway.findWorklist(WorklistQuery(modality = "XC", scheduledDate = today))
        assertThat(result).isInstanceOf(FindResult.Failed::class.java)
    }
}
