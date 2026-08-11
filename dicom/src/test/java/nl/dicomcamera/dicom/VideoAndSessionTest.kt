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

class VideoAndSessionTest {
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

    private fun node() = DicomNode("127.0.0.1", scp.boundPort, "TESTPACS", "DICOMCAM")

    @Test
    fun video_photographic_encode_and_store() {
        val staging = SecureStaging(temp.newFolder("staging"))
        val mp4 = ByteArray(256) { (it % 251).toByte() }
        val dicomFile = staging.createStagingFile("vid", "dcm")
        val ctx = PatientStudyContext(
            patientId = "V-1",
            patientName = "VIDEO^TEST",
            bodyPartExamined = "hand",
            laterality = "l",
            studyInstanceUid = "2.25.301",
            seriesInstanceUid = "2.25.302",
        )

        val encoded = VideoPhotographicEncoder().encodeMp4ToFile(
            mp4Bytes = mp4,
            context = ctx,
            rows = 240,
            columns = 320,
            frameCount = 15,
            framesPerSecond = 30,
            outputFile = dicomFile,
        )
        assertThat(encoded.sopClassUid).isEqualTo(UID.VideoPhotographicImageStorage)
        assertThat(encoded.studyInstanceUid).isEqualTo("2.25.301")

        DicomInputStream(dicomFile).use { input ->
            val fmi = input.readFileMetaInformation()
            assertThat(fmi.getString(Tag.TransferSyntaxUID)).isEqualTo(UID.MPEG4HP41)
            val ds = input.readDataset()
            assertThat(ds.getString(Tag.SOPClassUID)).isEqualTo(UID.VideoPhotographicImageStorage)
            assertThat(ds.getString(Tag.BodyPartExamined)).isEqualTo("HAND")
            assertThat(ds.getString(Tag.Laterality)).isEqualTo("L")
            assertThat(ds.getInt(Tag.NumberOfFrames, -1)).isEqualTo(15)
        }

        PacsClient(node()).use { client ->
            val store = client.store(dicomFile)
            assertThat(store).isInstanceOf(StoreResult.Success::class.java)
        }
        assertThat(scp.readPatientId(encoded.sopInstanceUid)).isEqualTo("V-1")
    }

    @Test
    fun mixed_photo_video_same_study_with_batch_retry() {
        val staging = SecureStaging(temp.newFolder("staging"))
        val jpeg = javaClass.getResourceAsStream("/sample.jpg")!!.use { it.readBytes() }
        val mp4 = ByteArray(128) { 7 }

        val studyUid = "2.25.401"
        val seriesUid = "2.25.402"
        val ctx = PatientStudyContext(
            patientId = "MIX-9",
            patientName = "MIXED^SESSION",
            bodyPartExamined = "FOOT",
            laterality = "R",
            studyInstanceUid = studyUid,
            seriesInstanceUid = seriesUid,
            seriesDescription = "Clinical photo/video session",
        )

        val photoFile = staging.createStagingFile("vl", "dcm")
        val videoFile = staging.createStagingFile("vid", "dcm")

        val photo = PhotographicImageEncoder().encodeJpegToFile(
            jpegBytes = jpeg,
            context = ctx,
            rows = 16,
            columns = 16,
            outputFile = photoFile,
        )
        val video = VideoPhotographicEncoder().encodeMp4ToFile(
            mp4Bytes = mp4,
            context = ctx,
            rows = 240,
            columns = 320,
            frameCount = 10,
            framesPerSecond = 25,
            outputFile = videoFile,
        )
        assertThat(photo.studyInstanceUid).isEqualTo(studyUid)
        assertThat(video.studyInstanceUid).isEqualTo(studyUid)
        assertThat(photo.seriesInstanceUid).isEqualTo(seriesUid)
        assertThat(video.seriesInstanceUid).isEqualTo(seriesUid)

        DicomInputStream(photoFile).use { input ->
            input.readFileMetaInformation()
            val ds = input.readDataset()
            assertThat(ds.getString(Tag.BodyPartExamined)).isEqualTo("FOOT")
            assertThat(ds.getString(Tag.Laterality)).isEqualTo("R")
        }

        val batch = BatchStore(
            clientFactory = { PacsClient(node()) },
            maxAttempts = 2,
            initialBackoffMs = 10,
        )
        val outcomes = batch.storeAll(listOf(photoFile, videoFile))
        assertThat(outcomes).hasSize(2)
        assertThat(outcomes.all { it.result is StoreResult.Success }).isTrue()
        assertThat(scp.readPatientId(photo.sopInstanceUid)).isEqualTo("MIX-9")
        assertThat(scp.readPatientId(video.sopInstanceUid)).isEqualTo("MIX-9")

        val wipePhoto = staging.wipe(photoFile)
        val wipeVideo = staging.wipe(videoFile)
        assertThat(wipePhoto).isInstanceOf(WipeResult.Wiped::class.java)
        assertThat(wipeVideo).isInstanceOf(WipeResult.Wiped::class.java)
        assertThat(photoFile.exists()).isFalse()
        assertThat(videoFile.exists()).isFalse()
    }
}
