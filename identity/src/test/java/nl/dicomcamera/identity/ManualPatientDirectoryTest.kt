package nl.dicomcamera.identity

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualPatientDirectoryTest {
    @Test
    fun findPatients_requires_patient_id() = runBlocking {
        val directory = ManualPatientDirectory()
        assertTrue(directory.findPatients(PatientQuery(patientName = "A^B")).isEmpty())

        val hits = directory.findPatients(
            PatientQuery(patientId = "123", patientName = "DOE^JANE"),
        )
        assertEquals(1, hits.size)
        assertEquals("123", hits[0].patientId)
        assertEquals(IdentitySource.MANUAL, hits[0].source)
    }
}
