package nl.dicomcamera.app.diagnostics

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticLogTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun does_not_write_when_disabled() {
        val log = DiagnosticLog(tmp.newFile("diag.log"))
        log.log("ping", "should not appear")
        assertThat(log.exists()).isFalse()
        assertThat(log.enabled).isFalse()
    }

    @Test
    fun writes_only_after_manual_enable() {
        val file = tmp.newFile("diag.log")
        file.delete()
        val log = DiagnosticLog(file)
        log.setEnabled(true)
        log.log("c_echo", "OK")
        assertThat(log.enabled).isTrue()
        assertThat(log.exists()).isTrue()
        val text = file.readText()
        assertThat(text).contains("logging_enabled")
        assertThat(text).contains("c_echo")
        assertThat(text).contains("OK")
    }

    @Test
    fun clear_empties_file_and_can_continue() {
        val log = DiagnosticLog(tmp.newFile("diag.log"))
        log.setEnabled(true)
        log.log("a", "1")
        log.clear()
        assertThat(log.snapshotFile().readText()).contains("logging_cleared")
        assertThat(log.snapshotFile().readText()).doesNotContain("\ta\t1")
    }
}

class HostPingTest {
    @Test
    fun rejects_empty_and_unsafe_host() {
        assertThat(HostPing.ping("").ok).isFalse()
        assertThat(HostPing.ping("evil;rm -rf /").ok).isFalse()
        assertThat(HostPing.ping("host with spaces").ok).isFalse()
    }
}
