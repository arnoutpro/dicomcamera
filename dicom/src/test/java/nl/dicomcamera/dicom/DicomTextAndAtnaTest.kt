package nl.dicomcamera.dicom

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.ZoneOffset

class DicomTextAndAtnaTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun charset_switches_to_utf8_for_non_ascii() {
        assertThat(DicomText.specificCharacterSet("DOE^JOHN")).isEqualTo("ISO_IR 100")
        assertThat(DicomText.specificCharacterSet("MÜLLER^HANS")).isEqualTo("ISO_IR 192")
    }

    @Test
    fun normalize_da_and_sex() {
        assertThat(DicomText.normalizeDa("1980-01-02")).isEqualTo("19800102")
        assertThat(DicomText.normalizeDa("bad")).isNull()
        assertThat(DicomText.normalizeSex("female")).isEqualTo("F")
        assertThat(DicomText.normalizeSex("M")).isEqualTo("M")
    }

    @Test
    fun timezone_offset_format() {
        val offset = DicomDateTime.timezoneOffsetFromUtc(ZoneOffset.ofHours(1))
        assertThat(offset).isEqualTo("+0100")
        val neg = DicomDateTime.timezoneOffsetFromUtc(ZoneOffset.ofHours(-5))
        assertThat(neg).isEqualTo("-0500")
    }

    @Test
    fun photo_encoder_sets_timezone_and_utf8() {
        val jpeg = javaClass.getResourceAsStream("/sample.jpg")!!.use { it.readBytes() }
        val out = temp.newFile("utf.dcm")
        PhotographicImageEncoder().encodeJpegToFile(
            jpegBytes = jpeg,
            context = PatientStudyContext(
                patientId = "U1",
                patientName = "SØRENSEN^ANNE",
            ),
            rows = 16,
            columns = 16,
            outputFile = out,
        )
        org.dcm4che3.io.DicomInputStream(out).use { input ->
            input.readFileMetaInformation()
            val ds = input.readDataset()
            assertThat(ds.getString(org.dcm4che3.data.Tag.SpecificCharacterSet)).isEqualTo("ISO_IR 192")
            assertThat(ds.getString(org.dcm4che3.data.Tag.TimezoneOffsetFromUTC)).isNotEmpty()
        }
    }

    @Test
    fun atna_export_writes_syslog_lines() {
        val csv = temp.newFile("audit.csv")
        csv.writeText(
            """
            timestamp,action,patientId,studyUid,sopUid,detail
            2026-01-01T00:00:00Z,c_store_success,P1,2.25.1,2.25.2,DIMSE
            """.trimIndent() + "\n",
        )
        val exporter = AtnaAuditExporter(temp.newFolder("atna"), aet = "DICOMCAM")
        val result = exporter.exportFromCsv(csv)
        assertThat(result.eventCount).isEqualTo(1)
        val text = result.file.readText()
        assertThat(text).contains("DICOM=c_store_success")
        assertThat(text).contains("PatientID=P1")
        assertThat(text).contains("DICOMCAM")
    }
}
