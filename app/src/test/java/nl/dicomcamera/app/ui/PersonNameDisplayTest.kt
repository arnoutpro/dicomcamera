package nl.dicomcamera.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PersonNameDisplayTest {
    @Test
    fun formats_dicom_pn_as_given_family() {
        assertThat(formatPersonNameForDisplay("JANSEN^ANNE")).isEqualTo("Anne Jansen")
        assertThat(formatPersonNameForDisplay("DE VRIES^PIETER")).isEqualTo("Pieter De Vries")
    }

    @Test
    fun leaves_plain_names_unchanged() {
        assertThat(formatPersonNameForDisplay("Anne Jansen")).isEqualTo("Anne Jansen")
    }

    @Test
    fun handles_family_only() {
        assertThat(formatPersonNameForDisplay("JANSEN^")).isEqualTo("Jansen")
        assertThat(formatPersonNameForDisplay("JANSEN")).isEqualTo("JANSEN")
    }
}
