package nl.dicomcamera.dicom

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

class PendingStoreQueueTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun purgeExpired_removesEntriesOlderThanFourHours() {
        val staging = SecureStaging(tmp.newFolder("staging"))
        val queue = PendingStoreQueue(tmp.newFolder("pending"), staging)
        val dicom = staging.createStagingFile("vl", "dcm").also { it.writeText("dicom") }
        val item = queue.enqueue(
            dicomFile = dicom,
            rawFile = null,
            patientId = "P1",
            patientName = "TEST^USER",
            error = "PACS not configured",
            studyInstanceUid = "1.2.3",
        )
        // Backdate meta to 5 hours ago.
        val old = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(5)
        File(item.directory, "meta.txt").writeText(
            """
            patientId=P1
            patientName=TEST^USER
            studyInstanceUid=1.2.3
            error=PACS not configured
            createdAt=$old
            """.trimIndent() + "\n",
        )
        assertThat(queue.purgeExpired()).isEqualTo(1)
        assertThat(queue.list()).isEmpty()
    }

    @Test
    fun listGroupedByPatient_keepsFreshEntries() {
        val staging = SecureStaging(tmp.newFolder("staging"))
        val queue = PendingStoreQueue(tmp.newFolder("pending"), staging)
        val dicom = staging.createStagingFile("vl", "dcm").also { it.writeText("dicom") }
        queue.enqueue(
            dicomFile = dicom,
            rawFile = null,
            patientId = "DEMO-1001",
            patientName = "JANSEN^ANNE",
            error = "PACS not configured",
            studyInstanceUid = "10.20.30",
        )
        val groups = queue.listGroupedByPatient()
        assertThat(groups).hasSize(1)
        assertThat(groups[0].patientId).isEqualTo("DEMO-1001")
        assertThat(groups[0].instanceCount).isEqualTo(1)
    }
}

// Local File import for test meta rewrite
private typealias File = java.io.File
