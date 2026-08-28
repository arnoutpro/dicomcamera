package nl.dicomcamera.dicom

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PacsEndpointTest {
    private fun archive(
        mwlHost: String = "",
        mwlPort: Int = 104,
        mwlCalled: String = "",
        mwlTls: Boolean = false,
        transport: TransportMode = TransportMode.DIMSE,
    ) = PacsEndpoint(
        transportMode = transport,
        host = "pacs.hospital.local",
        port = 11112,
        calledAeTitle = "PACS",
        callingAeTitle = "DICOMCAM",
        useTls = true,
        dicomWebBaseUrl = "https://pacs.hospital.local/dicom-web",
        mwlHost = mwlHost,
        mwlPort = mwlPort,
        mwlCalledAeTitle = mwlCalled,
        mwlUseTls = mwlTls,
    )

    @Test
    fun empty_mwl_falls_back_to_archive_dimse() {
        val endpoint = archive()
        val node = endpoint.resolveMwlNode()
        assertThat(endpoint.hasDedicatedMwl()).isFalse()
        assertThat(node).isNotNull()
        assertThat(node!!.host).isEqualTo("pacs.hospital.local")
        assertThat(node.port).isEqualTo(11112)
        assertThat(node.calledAeTitle).isEqualTo("PACS")
        assertThat(node.callingAeTitle).isEqualTo("DICOMCAM")
        assertThat(node.useTls).isTrue()
        assertThat(endpoint.isMwlConfigured()).isTrue()
    }

    @Test
    fun dedicated_mwl_uses_worklist_ae_not_archive() {
        val endpoint = archive(
            mwlHost = "mwl.ris.local",
            mwlPort = 104,
            mwlCalled = "MWLSCP",
            mwlTls = false,
        )
        val node = endpoint.resolveMwlNode()
        assertThat(endpoint.hasDedicatedMwl()).isTrue()
        assertThat(node).isNotNull()
        assertThat(node!!.host).isEqualTo("mwl.ris.local")
        assertThat(node.port).isEqualTo(104)
        assertThat(node.calledAeTitle).isEqualTo("MWLSCP")
        assertThat(node.callingAeTitle).isEqualTo("DICOMCAM")
        assertThat(node.useTls).isFalse()
        assertThat(endpoint.toNode().calledAeTitle).isEqualTo("PACS")
    }

    @Test
    fun incomplete_dedicated_mwl_does_not_fall_back() {
        val endpoint = archive(mwlHost = "mwl.ris.local")
        assertThat(endpoint.hasDedicatedMwl()).isTrue()
        assertThat(endpoint.toMwlNode()).isNull()
        assertThat(endpoint.resolveMwlNode()).isNull()
        assertThat(endpoint.isMwlConfigured()).isFalse()
    }

    @Test
    fun dicomweb_store_still_resolves_dedicated_mwl() {
        val endpoint = archive(
            transport = TransportMode.DICOMWEB,
            mwlHost = "mwl.ris.local",
            mwlPort = 11112,
            mwlCalled = "MWLSCP",
        )
        assertThat(endpoint.isConfigured()).isTrue()
        assertThat(endpoint.resolveMwlNode()!!.calledAeTitle).isEqualTo("MWLSCP")
    }

    @Test
    fun dicomweb_without_dimse_or_mwl_is_not_mwl_ready() {
        val endpoint = PacsEndpoint(
            transportMode = TransportMode.DICOMWEB,
            dicomWebBaseUrl = "https://pacs.example/dicom-web",
            callingAeTitle = "DICOMCAM",
        )
        assertThat(endpoint.isConfigured()).isTrue()
        assertThat(endpoint.resolveMwlNode()).isNull()
        assertThat(endpoint.isMwlConfigured()).isFalse()
    }
}
