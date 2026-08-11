package nl.dicomcamera.app.demo

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DemoPatientsTest {
    @Test
    fun provides_two_demo_patients() {
        assertThat(DemoPatients.entries).hasSize(2)
        assertThat(DemoPatients.entries.map { it.patientId }).containsExactly(
            "DEMO-1001",
            "DEMO-1002",
        ).inOrder()
        assertThat(DemoPatients.isDemoPatientId("DEMO-1001")).isTrue()
        assertThat(DemoPatients.isDemoPatientId("WL-9")).isFalse()
    }
}
