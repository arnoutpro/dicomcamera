package nl.dicomcamera.identity

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class FhirPatientDirectoryTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun lookup_parses_fhir_bundle() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "resourceType": "Bundle",
                  "type": "searchset",
                  "entry": [{
                    "resource": {
                      "resourceType": "Patient",
                      "id": "p1",
                      "identifier": [{ "value": "999888777" }],
                      "name": [{ "family": "de Vries", "given": ["Jan"] }],
                      "birthDate": "1975-06-01",
                      "gender": "male"
                    }
                  }]
                }
                """.trimIndent(),
            ),
        )
        val directory = FhirPatientDirectory(
            configProvider = {
                FhirConfig(enabled = true, baseUrl = server.url("/fhir").toString().trimEnd('/'))
            },
        )
        val results = directory.findPatients(PatientQuery(patientId = "999888777"))
        assertThat(results).hasSize(1)
        assertThat(results[0].patientId).isEqualTo("999888777")
        assertThat(results[0].patientName).isEqualTo("de Vries^Jan")
        assertThat(results[0].birthDate).isEqualTo("19750601")
        assertThat(results[0].sex).isEqualTo("M")
        assertThat(results[0].source).isEqualTo(IdentitySource.FHIR)

        val recorded = server.takeRequest()
        assertThat(recorded.path).contains("/fhir/Patient")
        assertThat(recorded.requestUrl?.queryParameter("identifier")).isEqualTo("999888777")
        assertThat(recorded.getHeader("Accept")).contains("fhir+json")
    }

    @Test
    fun requires_enabled_config() = runBlocking {
        val directory = FhirPatientDirectory(
            configProvider = { FhirConfig(enabled = false, baseUrl = "http://x") },
        )
        try {
            directory.findPatients(PatientQuery(patientId = "1"))
            throw AssertionError("expected failure")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("not configured")
        }
    }

    @Test
    fun composite_falls_back_to_hl7() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        server.enqueue(
            MockResponse().setBody(
                """{"patientId":"1","patientName":"A^B","birthDate":"20000101","sex":"F"}""",
            ),
        )
        val base = server.url("/").toString().trimEnd('/')
        val composite = CompositePatientDirectory(
            modeProvider = { IdentityLookupMode.FHIR_THEN_HL7 },
            fhir = FhirPatientDirectory {
                FhirConfig(enabled = true, baseUrl = "$base/fhir")
            },
            hl7 = Hl7PatientDirectory {
                Hl7FacadeConfig(enabled = true, baseUrl = "$base/hl7")
            },
        )
        val results = composite.findPatients(PatientQuery(patientId = "1"))
        assertThat(results).hasSize(1)
        assertThat(results[0].source).isEqualTo(IdentitySource.HL7_V2)
        assertThat(results[0].patientName).isEqualTo("A^B")
    }
}
