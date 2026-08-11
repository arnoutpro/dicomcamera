package nl.dicomcamera.dicom

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PacsEchoStoreSpikeTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var scp: InProcessStoreScp

    @Before
    fun setUp() {
        scp = InProcessStoreScp(storageDir = temp.newFolder("pacs"))
        scp.start()
    }

    @After
    fun tearDown() {
        scp.close()
    }

    @Test
    fun echo_and_store_jpeg_secondary_capture_then_wipe() {
        val staging = SecureStaging(temp.newFolder("staging"))
        val jpeg = minimalJpegBytes()
        val dicomFile = staging.createStagingFile("sc", "photo.dcm")

        val encoded = SecondaryCaptureEncoder().encodeJpegToFile(
            jpegBytes = jpeg,
            context = PatientStudyContext(
                patientId = "NL-12345",
                patientName = "TEST^PATIENT",
                patientSex = "O",
                accessionNumber = "ACC001",
                studyDescription = "Phase0 spike",
            ),
            rows = 16,
            columns = 16,
            outputFile = dicomFile,
        )

        assertThat(dicomFile.exists()).isTrue()
        assertThat(dicomFile.length()).isGreaterThan(0)

        val node = DicomNode(
            host = "127.0.0.1",
            port = scp.boundPort,
            calledAeTitle = "TESTPACS",
            callingAeTitle = "DICOMCAM",
        )

        PacsClient(node).use { client ->
            val echo = client.echo()
            assertThat(echo).isEqualTo(EchoResult.Success)

            val store = client.store(dicomFile)
            assertThat(store).isInstanceOf(StoreResult.Success::class.java)
            val sopUid = (store as StoreResult.Success).sopInstanceUid
            assertThat(sopUid).isEqualTo(encoded.sopInstanceUid)
            assertThat(scp.readPatientId(sopUid)).isEqualTo("NL-12345")
        }

        val wipe = staging.wipe(dicomFile)
        assertThat(wipe).isEqualTo(WipeResult.Wiped)
        assertThat(dicomFile.exists()).isFalse()
        assertThat(staging.listStagingFiles()).isEmpty()
    }

    @Test
    fun wipeAll_clears_staging_directory() {
        val staging = SecureStaging(temp.newFolder("staging2"))
        val a = staging.createStagingFile("a", "bin")
        val b = staging.createStagingFile("b", "bin")
        a.writeBytes(byteArrayOf(1, 2, 3, 4))
        b.writeBytes(byteArrayOf(5, 6, 7, 8))

        val results = staging.wipeAll()
        assertThat(results).hasSize(2)
        assertThat(results.all { it.second == WipeResult.Wiped }).isTrue()
        assertThat(staging.listStagingFiles()).isEmpty()
    }

    /** Tiny valid 16×16 JPEG fixture (no AWT — Android unit tests lack java.desktop). */
    private fun minimalJpegBytes(): ByteArray {
        val stream = javaClass.getResourceAsStream("/sample.jpg")
            ?: error("Missing test resource /sample.jpg")
        return stream.use { it.readBytes() }
    }
}
