package nl.dicomcamera.identity

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class Hl7PatientDirectoryTest {
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
    fun lookup_by_patient_id_parses_json() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "patientId": "123456789",
                  "patientName": "JANSEN^ANNE",
                  "birthDate": "19800315",
                  "sex": "F"
                }
                """.trimIndent(),
            ),
        )
        val directory = Hl7PatientDirectory(
            configProvider = {
                Hl7FacadeConfig(
                    enabled = true,
                    baseUrl = server.url("/hl7").toString().trimEnd('/'),
                )
            },
        )
        val results = directory.findPatients(PatientQuery(patientId = "123456789"))
        assertThat(results).hasSize(1)
        assertThat(results[0].patientName).isEqualTo("JANSEN^ANNE")
        assertThat(results[0].birthDate).isEqualTo("19800315")
        assertThat(results[0].sex).isEqualTo("F")
        assertThat(results[0].source).isEqualTo(IdentitySource.HL7_V2)

        val recorded = server.takeRequest()
        assertThat(recorded.path).contains("/hl7/patients")
        assertThat(recorded.requestUrl?.queryParameter("patientId")).isEqualTo("123456789")
    }

    @Test
    fun requires_enabled_config() = runBlocking {
        val directory = Hl7PatientDirectory(
            configProvider = { Hl7FacadeConfig(enabled = false, baseUrl = "http://x") },
        )
        try {
            directory.findPatients(PatientQuery(patientId = "1"))
            throw AssertionError("expected failure")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("not configured")
        }
    }

    @Test
    fun http_error_omits_response_body_from_exception() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(502)
                .setBody("""{"patientId":"SYNTH-LEAK","patientName":"DOE^JANE"}"""),
        )
        val directory = Hl7PatientDirectory(
            configProvider = {
                Hl7FacadeConfig(enabled = true, baseUrl = server.url("/hl7").toString().trimEnd('/'))
            },
        )
        try {
            directory.findPatients(PatientQuery(patientId = "1"))
            throw AssertionError("expected failure")
        } catch (e: IllegalStateException) {
            assertThat(e.message).contains("HL7 façade HTTP 502")
            assertThat(e.message).doesNotContain("SYNTH-LEAK")
            assertThat(e.message).doesNotContain("DOE^JANE")
        }
    }
}
