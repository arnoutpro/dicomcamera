package nl.dicomcamera.dicom

import com.google.common.truth.Truth.assertThat
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DicomWebClientTest {
    @get:Rule
    val temp = TemporaryFolder()

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

    private fun baseUrl() = server.url("/dicom-web").toString().trimEnd('/')

    @Test
    fun qido_studies_parses_dicom_json() {
        val json = """
            [{
              "00100020":{"vr":"LO","Value":["P-77"]},
              "00100010":{"vr":"PN","Value":[{"Alphabetic":"DOE^JANE"}]},
              "00080050":{"vr":"SH","Value":["ACC-9"]},
              "0020000D":{"vr":"UI","Value":["2.25.999"]},
              "00080020":{"vr":"DA","Value":["20260101"]},
              "00081030":{"vr":"LO","Value":["Wound"]}
            }]
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(json))

        DicomWebClient(baseUrl()).use { client ->
            val result = client.qidoStudies(StudyQuery(patientId = "P-77"))
            assertThat(result).isInstanceOf(FindResult.Success::class.java)
            val items = (result as FindResult.Success).items
            assertThat(items).hasSize(1)
            assertThat(items[0].patientId).isEqualTo("P-77")
            assertThat(items[0].patientName).isEqualTo("DOE^JANE")
            assertThat(items[0].studyInstanceUid).isEqualTo("2.25.999")
            assertThat(items[0].accessionNumber).isEqualTo("ACC-9")
        }
        val recorded = server.takeRequest()
        assertThat(recorded.path).contains("/dicom-web/studies")
        assertThat(recorded.requestUrl?.queryParameter("PatientID")).isEqualTo("P-77")
    }

    @Test
    fun stow_posts_multipart_related() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val jpeg = javaClass.getResourceAsStream("/sample.jpg")!!.use { it.readBytes() }
        val dicomFile = temp.newFile("vl.dcm")
        PhotographicImageEncoder().encodeJpegToFile(
            jpegBytes = jpeg,
            context = PatientStudyContext(patientId = "W1", patientName = "WEB^ONE"),
            rows = 16,
            columns = 16,
            outputFile = dicomFile,
        )

        DicomWebClient(baseUrl()).use { client ->
            val store = client.stow(dicomFile)
            assertThat(store).isInstanceOf(StoreResult.Success::class.java)
        }
        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.path).isEqualTo("/dicom-web/studies")
        val contentType = recorded.getHeader("Content-Type").orEmpty()
        assertThat(contentType).contains("multipart/related")
        assertThat(contentType).contains("application/dicom")
        assertThat(recorded.body.size).isGreaterThan(dicomFile.length())
    }

    @Test
    fun stow_failure_omits_response_body_from_error() {
        val leakyBody =
            """{"00100020":{"vr":"LO","Value":["SYNTH-LEAK"]},"00100010":{"vr":"PN","Value":[{"Alphabetic":"DOE^JANE"}]}}"""
        server.enqueue(MockResponse().setResponseCode(500).setBody(leakyBody))
        val jpeg = javaClass.getResourceAsStream("/sample.jpg")!!.use { it.readBytes() }
        val dicomFile = temp.newFile("fail.dcm")
        PhotographicImageEncoder().encodeJpegToFile(
            jpegBytes = jpeg,
            context = PatientStudyContext(patientId = "W1", patientName = "WEB^ONE"),
            rows = 16,
            columns = 16,
            outputFile = dicomFile,
        )

        DicomWebClient(baseUrl()).use { client ->
            val store = client.stow(dicomFile)
            assertThat(store).isInstanceOf(StoreResult.Failed::class.java)
            val message = (store as StoreResult.Failed).message
            assertThat(message).contains("STOW-RS HTTP 500")
            assertThat(message).doesNotContain("SYNTH-LEAK")
            assertThat(message).doesNotContain("DOE^JANE")
        }
    }

    @Test
    fun gateway_dicomweb_store_and_qido() {
        val qidoJson = """
            [{"00100020":{"vr":"LO","Value":["G1"]},
              "00100010":{"vr":"PN","Value":[{"Alphabetic":"GATE^WAY"}]},
              "0020000D":{"vr":"UI","Value":["2.25.777"]}}]
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}")) // STOW
        server.enqueue(MockResponse().setResponseCode(200).setBody(qidoJson)) // QIDO

        val jpeg = javaClass.getResourceAsStream("/sample.jpg")!!.use { it.readBytes() }
        val dicomFile = temp.newFile("g.dcm")
        PhotographicImageEncoder().encodeJpegToFile(
            jpegBytes = jpeg,
            context = PatientStudyContext(
                patientId = "G1",
                patientName = "GATE^WAY",
                studyInstanceUid = "2.25.777",
            ),
            rows = 16,
            columns = 16,
            outputFile = dicomFile,
        )

        val endpoint = PacsEndpoint(
            transportMode = TransportMode.DICOMWEB,
            dicomWebBaseUrl = baseUrl(),
        )
        val gateway = PacsGateway.fromEndpoint(endpoint)
        assertThat(gateway.store(dicomFile)).isInstanceOf(StoreResult.Success::class.java)
        val found = gateway.findStudies(StudyQuery(patientId = "G1"))
        assertThat(found).isInstanceOf(FindResult.Success::class.java)
        assertThat((found as FindResult.Success).items[0].studyInstanceUid).isEqualTo("2.25.777")
    }
}
