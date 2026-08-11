package nl.dicomcamera.dicom

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DicomUidTest {
    @Test
    fun newUid_uses_uuid_oid_arc() {
        val uid = DicomUid.newUid()
        assertThat(uid).startsWith("2.25.")
        assertThat(uid.length).isGreaterThan(10)
        assertThat(uid).doesNotContain("..")
    }

    @Test
    fun newUid_is_unique() {
        val a = DicomUid.newUid()
        val b = DicomUid.newUid()
        assertThat(a).isNotEqualTo(b)
    }
}
