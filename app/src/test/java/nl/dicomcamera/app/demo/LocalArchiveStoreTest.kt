package nl.dicomcamera.app.demo

import com.google.common.truth.Truth.assertThat
import nl.dicomcamera.app.session.CaptureKind
import nl.dicomcamera.app.session.CaptureSession
import nl.dicomcamera.app.session.SessionItem
import nl.dicomcamera.dicom.PatientStudyContext
import nl.dicomcamera.dicom.SecureStaging
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalArchiveStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun purgeOrphans_wipesPartialDirWithoutMeta_leavesReadableStudy() {
        val staging = SecureStaging(tmp.newFolder("staging"))
        val root = tmp.newFolder("local-archive")
        val store = LocalArchiveStore(root, staging)

        val raw = File(staging.directory, "raw.jpg").also { it.writeBytes(ByteArray(32) { 7 }) }
        val saved = store.saveSession(
            session = CaptureSession(
                studyInstanceUid = "1.2.3",
                seriesInstanceUid = "1.2.4",
                items = listOf(
                    SessionItem(
                        kind = CaptureKind.PHOTO,
                        rawFile = raw,
                        rows = 8,
                        columns = 8,
                    ),
                ),
            ),
            context = PatientStudyContext(
                patientId = "DEMO-1",
                patientName = "TEST^USER",
                studyInstanceUid = "1.2.3",
                seriesInstanceUid = "1.2.4",
            ),
        )

        val orphan = File(root, "orphan-partial").also { it.mkdirs() }
        File(orphan, "photo_00.jpg").writeBytes(ByteArray(16) { 9 })
        // No meta.json — unreadable to list(), must be wiped.

        assertThat(store.list()).hasSize(1)
        assertThat(store.purgeOrphans()).isEqualTo(1)
        assertThat(orphan.exists()).isFalse()
        assertThat(store.list().map { it.id }).containsExactly(saved.id)
        assertThat(saved.photoFiles.first().exists()).isTrue()
    }

    @Test
    fun purgeOrphans_wipesCorruptMetaDir() {
        val staging = SecureStaging(tmp.newFolder("staging"))
        val root = tmp.newFolder("local-archive")
        val store = LocalArchiveStore(root, staging)

        val corrupt = File(root, "corrupt").also { it.mkdirs() }
        File(corrupt, "meta.json").writeText("{not-json")
        File(corrupt, "photo_00.jpg").writeBytes(ByteArray(8) { 1 })

        assertThat(store.list()).isEmpty()
        assertThat(store.purgeOrphans()).isEqualTo(1)
        assertThat(corrupt.exists()).isFalse()
    }
}
