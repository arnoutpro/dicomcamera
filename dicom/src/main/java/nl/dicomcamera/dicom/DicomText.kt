package nl.dicomcamera.dicom

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Character set + date/time helpers for multi-vendor encoding.
 */
object DicomText {
    /** Latin-1 default; UTF-8 (ISO_IR 192) when any value needs it. */
    fun specificCharacterSet(vararg texts: String?): String {
        val needsUtf8 = texts.any { text ->
            !text.isNullOrEmpty() && text.any { it.code > 0x7F }
        }
        return if (needsUtf8) "ISO_IR 192" else "ISO_IR 100"
    }

    fun normalizeDa(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val digits = raw.filter { it.isDigit() }
        return digits.takeIf { it.length == 8 }
    }

    fun normalizeSex(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return when (raw.trim().uppercase()) {
            "M", "MALE" -> "M"
            "F", "FEMALE" -> "F"
            "O", "OTHER" -> "O"
            else -> raw.trim().uppercase().take(1)
        }
    }
}

object DicomDateTime {
    private val da = DateTimeFormatter.BASIC_ISO_DATE
    private val tm = DateTimeFormatter.ofPattern("HHmmss")

    fun todayDa(zone: ZoneId = ZoneId.systemDefault()): String =
        LocalDate.now(zone).format(da)

    fun nowTm(zone: ZoneId = ZoneId.systemDefault()): String =
        LocalTime.now(zone).format(tm)

    /**
     * DICOM Timezone Offset From UTC (0008,0201), e.g. `+0100` / `-0500`.
     */
    fun timezoneOffsetFromUtc(zone: ZoneId = ZoneId.systemDefault(), at: Instant = Instant.now()): String {
        val offset = zone.rules.getOffset(at)
        val total = offset.totalSeconds
        val sign = if (total >= 0) '+' else '-'
        val absSec = abs(total)
        val hours = absSec / 3600
        val minutes = (absSec % 3600) / 60
        return "%c%02d%02d".format(sign, hours, minutes)
    }
}
