package nl.dicomcamera.app.demo

import com.google.common.truth.Truth.assertThat
import nl.dicomcamera.dicom.PatientStudyContext
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArchivedPatientStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun records_metadata_only_and_purges_after_ttl() {
        val store = ArchivedPatientStore(tmp.newFolder("archived"))
        val record = store.recordSuccessfulArchive(
            context = PatientStudyContext(
                patientId = "DEMO-1",
                patientName = "TEST^USER",
                bodyPartExamined = "HAND",
            ),
            studyInstanceUid = "1.2.3",
            seriesInstanceUid = "1.2.4",
            instanceCount = 2,
        )
        assertThat(store.list()).hasSize(1)
        assertThat(record.patientId).isEqualTo("DEMO-1")
        // Simulate expiry
        val meta = tmp.root.resolve("archived/${record.id}.json")
        // rewrite archivedAt far in the past via store purge with now far future
        val removed = store.purgeExpired(now = record.archivedAtEpochMs + ArchivedPatientStore.TTL_MS + 1)
        assertThat(removed).isEqualTo(1)
        assertThat(store.list()).isEmpty()
        assertThat(meta.exists()).isFalse()
    }
}
