package nl.dicomcamera.app.ui

/**
 * Formats DICOM Person Name (`FAMILY^GIVEN[^MIDDLE…]`) for on-screen display.
 * Storage / queries keep the caret form; only the UI uses this.
 */
fun formatPersonNameForDisplay(dicomPn: String): String {
    val trimmed = dicomPn.trim()
    if (trimmed.isEmpty()) return trimmed
    if (!trimmed.contains('^')) return trimmed

    val parts = trimmed.split('^')
    val family = parts.getOrNull(0)?.trim().orEmpty()
    val given = parts.getOrNull(1)?.trim().orEmpty()
    return when {
        given.isNotEmpty() && family.isNotEmpty() ->
            "${toDisplayCase(given)} ${toDisplayCase(family)}"
        family.isNotEmpty() -> toDisplayCase(family)
        given.isNotEmpty() -> toDisplayCase(given)
        else -> trimmed
    }
}

private fun toDisplayCase(value: String): String =
    value.lowercase().split(Regex("\\s+")).joinToString(" ") { word ->
        word.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() }
    }
