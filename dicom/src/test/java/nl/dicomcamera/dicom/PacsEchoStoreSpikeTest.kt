package nl.dicomcamera.dicom

import com.google.common.truth.Truth.assertThat
import org.dcm4che3.data.Tag
import org.dcm4che3.data.UID
import org.dcm4che3.io.DicomInputStream
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PacsEchoStoreSpikeTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var scp: InProcessDicomScp

    @Before
    fun setUp() {
        scp = InProcessDicomScp(storageDir = temp.newFolder("pacs"))
        scp.start()
    }

    @After
    fun tearDown() {
        scp.close()
    }

    @Test
    fun echo_and_store_vl_photographic_then_wipe() {
        val staging = SecureStaging(temp.newFolder("staging"))
        val jpeg = minimalJpegBytes()
        val dicomFile = staging.createStagingFile("vl", "photo.dcm")

        val encoded = PhotographicImageEncoder().encodeJpegToFile(
            jpegBytes = jpeg,
            context = PatientStudyContext(
                patientId = "NL-12345",
                patientName = "TEST^PATIENT",
                patientBirthDate = "19800101",
                patientSex = "O",
                accessionNumber = "ACC001",
                studyDescription = "Phase1 store path",
            ),
            rows = 16,
            columns = 16,
            outputFile = dicomFile,
        )

        assertThat(encoded.sopClassUid).isEqualTo(UID.VLPhotographicImageStorage)
        assertThat(dicomFile.exists()).isTrue()

        DicomInputStream(dicomFile).use { input ->
            input.readFileMetaInformation()
            val ds = input.readDataset()
            assertThat(ds.getString(Tag.PatientID)).isEqualTo("NL-12345")
            assertThat(ds.getString(Tag.PatientBirthDate)).isEqualTo("19800101")
            assertThat(ds.getString(Tag.PatientSex)).isEqualTo("O")
            assertThat(ds.getString(Tag.AccessionNumber)).isEqualTo("ACC001")
            assertThat(ds.getString(Tag.Modality)).isEqualTo("XC")
        }

        val node = DicomNode(
            host = "127.0.0.1",
            port = scp.boundPort,
            calledAeTitle = "TESTPACS",
            callingAeTitle = "DICOMCAM",
        )

        PacsClient(node).use { client ->
            assertThat(client.echo()).isEqualTo(EchoResult.Success)
            val store = client.store(dicomFile)
            assertThat(store).isInstanceOf(StoreResult.Success::class.java)
            val sopUid = (store as StoreResult.Success).sopInstanceUid
            assertThat(sopUid).isEqualTo(encoded.sopInstanceUid)
            assertThat(scp.readPatientId(sopUid)).isEqualTo("NL-12345")
        }

        assertThat(staging.wipe(dicomFile)).isEqualTo(WipeResult.Wiped)
        assertThat(dicomFile.exists()).isFalse()
    }

    @Test
    fun pending_queue_retry_and_discard() {
        val staging = SecureStaging(temp.newFolder("staging"))
        val pending = PendingStoreQueue(temp.newFolder("pending"), staging)
        val jpeg = minimalJpegBytes()
        val dicomFile = staging.createStagingFile("vl", "photo.dcm")
        PhotographicImageEncoder().encodeJpegToFile(
            jpegBytes = jpeg,
            context = PatientStudyContext(patientId = "P1", patientName = "A^B"),
            rows = 16,
            columns = 16,
            outputFile = dicomFile,
        )

        val item = pending.enqueue(
            dicomFile = dicomFile,
            rawFile = null,
            patientId = "P1",
            patientName = "A^B",
            error = "C-STORE failed: offline",
        )
        assertThat(dicomFile.exists()).isFalse()
        assertThat(pending.list()).hasSize(1)

        PacsClient(
            DicomNode("127.0.0.1", scp.boundPort, "TESTPACS", "DICOMCAM"),
        ).use { client ->
            val store = client.store(item.dicomFile)
            assertThat(store).isInstanceOf(StoreResult.Success::class.java)
        }
        pending.markStoredAndWipe(item.id)
        assertThat(pending.list()).isEmpty()
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

    private fun minimalJpegBytes(): ByteArray {
        val stream = javaClass.getResourceAsStream("/sample.jpg")
            ?: error("Missing test resource /sample.jpg")
        return stream.use { it.readBytes() }
    }
}
