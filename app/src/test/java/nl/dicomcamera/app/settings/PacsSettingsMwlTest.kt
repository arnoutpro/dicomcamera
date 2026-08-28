package nl.dicomcamera.app.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PacsSettingsMwlTest {
    @Test
    fun mwlSummary_reports_fallback_when_empty() {
        val settings = PacsSettings(
            callingAeTitle = "DICOMCAM",
            host = "10.0.2.2",
            port = 4242,
            calledAeTitle = "ORTHANC",
        )
        assertThat(settings.isMwlConfigured()).isTrue()
        assertThat(settings.mwlSummary()).contains("Uses archive DIMSE")
        assertThat(settings.mwlSummary()).contains("10.0.2.2")
        assertThat(settings.mwlSummary()).contains("ORTHANC")
    }

    @Test
    fun mwlSummary_reports_dedicated_destination() {
        val settings = PacsSettings(
            callingAeTitle = "DICOMCAM",
            host = "pacs.local",
            port = 11112,
            calledAeTitle = "PACS",
            mwlHost = "mwl.local",
            mwlPort = 104,
            mwlCalledAeTitle = "MWLSCP",
        )
        assertThat(settings.mwlSummary()).isEqualTo("mwl.local · 104 · MWLSCP · plain")
        assertThat(settings.remoteSummary()).contains("pacs.local")
        assertThat(settings.toEndpoint().resolveMwlNode()!!.calledAeTitle).isEqualTo("MWLSCP")
    }

    @Test
    fun copyArchiveDimseToMwl_copies_host_port_ae_and_tls() {
        val settings = PacsSettings(
            host = "pacs.local",
            port = 4242,
            calledAeTitle = "ORTHANC",
            useTls = true,
        )
        val copied = settings.copyArchiveDimseToMwl()
        assertThat(copied.mwlHost).isEqualTo("pacs.local")
        assertThat(copied.mwlPort).isEqualTo(4242)
        assertThat(copied.mwlCalledAeTitle).isEqualTo("ORTHANC")
        assertThat(copied.mwlUseTls).isTrue()
        assertThat(copied.toEndpoint().hasDedicatedMwl()).isTrue()
    }

    @Test
    fun incomplete_mwl_summary_does_not_claim_fallback() {
        val settings = PacsSettings(
            callingAeTitle = "DICOMCAM",
            host = "pacs.local",
            port = 11112,
            calledAeTitle = "PACS",
            mwlHost = "mwl.local",
        )
        assertThat(settings.isMwlConfigured()).isFalse()
        assertThat(settings.mwlSummary()).contains("incomplete")
    }
}
