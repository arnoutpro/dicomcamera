package nl.dicomcamera.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import nl.dicomcamera.app.ui.theme.DicomColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * DICOM DA (YYYYMMDD) field backed by a Material date picker.
 * Empty value is allowed (optional demographics).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DicomDateField(
    valueYyyymmdd: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Date",
    allowClear: Boolean = true,
    yearRange: IntRange = 1900..LocalDate.now().year,
) {
    var showPicker by remember { mutableStateOf(false) }
    val displayFormatter = remember {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
    }
    val parsed = remember(valueYyyymmdd) { parseDa(valueYyyymmdd) }
    val buttonLabel = if (parsed != null) {
        "$label: ${parsed.format(displayFormatter)}"
    } else {
        "Select $label"
    }

    QuietOutlinedButton(
        text = buttonLabel,
        onClick = { showPicker = true },
        modifier = modifier.fillMaxWidth(),
    )
    if (parsed == null && valueYyyymmdd.isNotBlank()) {
        Text(
            "Invalid date — pick again",
            style = MaterialTheme.typography.bodySmall,
            color = DicomColors.Rose,
        )
    }
    if (allowClear && valueYyyymmdd.isNotBlank()) {
        TextButton(onClick = { onValueChange("") }) {
            Text("Clear $label")
        }
    }

    if (showPicker) {
        val initial = parsed ?: LocalDate.of(1980, 1, 1)
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initial
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
            yearRange = yearRange,
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            onValueChange(date.format(DateTimeFormatter.BASIC_ISO_DATE))
                        }
                        showPicker = false
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun parseDa(value: String): LocalDate? {
    if (value.length != 8 || value.any { !it.isDigit() }) return null
    return runCatching {
        LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE)
    }.getOrNull()
}
