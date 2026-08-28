package nl.dicomcamera.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.dicomcamera.app.ui.components.DicomTextField
import nl.dicomcamera.app.ui.components.ForestButton
import nl.dicomcamera.app.ui.components.MetaChip
import nl.dicomcamera.app.ui.components.QuietOutlinedButton
import nl.dicomcamera.app.ui.components.ResultRow
import nl.dicomcamera.app.ui.components.ScreenTitle
import nl.dicomcamera.app.ui.components.SectionLabel
import nl.dicomcamera.app.ui.components.SegmentedChoice
import nl.dicomcamera.app.ui.components.SoftPanel
import nl.dicomcamera.app.ui.components.StatusBanner
import nl.dicomcamera.app.ui.components.StatusTone
import nl.dicomcamera.app.ui.theme.DicomColors
import nl.dicomcamera.dicom.FindResult
import nl.dicomcamera.dicom.PacsEndpoint
import nl.dicomcamera.dicom.PacsGateway
import nl.dicomcamera.dicom.StudyEntry
import nl.dicomcamera.dicom.StudyQuery
import nl.dicomcamera.dicom.TransportMode
import nl.dicomcamera.dicom.WorklistEntry
import nl.dicomcamera.dicom.WorklistQuery
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private enum class WorklistDateMode {
    Today,
    Date,
}

private enum class WorklistSort {
    Time,
    Name,
    PatientId,
    Accession,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorklistScreen(
    endpoint: PacsEndpoint,
    callingAeTitle: String,
    onSelected: (WorklistEntry) -> Unit,
    embedded: Boolean = false,
    modality: String = "XC",
) {
    val scope = rememberCoroutineScope()
    var dateMode by remember { mutableStateOf(WorklistDateMode.Today) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(WorklistSort.Time) }
    var patientId by remember { mutableStateOf("") }
    var accession by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<WorklistEntry>>(emptyList()) }
    var status by remember {
        mutableStateOf(
            if (endpoint.hasDedicatedMwl()) {
                "Query today's worklist (dedicated MWL AE)"
            } else if (endpoint.transportMode == TransportMode.DICOMWEB) {
                "MWL is DIMSE-only — uses archive DIMSE if host/AE configured"
            } else {
                "Query today's worklist"
            },
        )
    }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val today = remember { LocalDate.now() }
    val dateDisplayFormatter = remember {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
    }
    val sortedItems = remember(items, sort) { items.sortedWith(worklistComparator(sort)) }

    fun runQuery() {
        val scheduledDate = when (dateMode) {
            WorklistDateMode.Today -> today.format(DateTimeFormatter.BASIC_ISO_DATE)
            WorklistDateMode.Date -> selectedDate.format(DateTimeFormatter.BASIC_ISO_DATE)
        }
        loading = true
        failed = false
        status = "Querying MWL…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                PacsGateway.fromEndpoint(endpoint).findWorklist(
                    WorklistQuery(
                        patientId = patientId.trim().ifBlank { null },
                        accessionNumber = accession.trim().ifBlank { null },
                        modality = modality.ifBlank { "XC" },
                        scheduledStationAeTitle = callingAeTitle,
                        scheduledDate = scheduledDate,
                    ),
                )
            }
            loading = false
            when (result) {
                is FindResult.Success -> {
                    items = result.items
                    val label = if (dateMode == WorklistDateMode.Today) "today" else scheduledDate
                    status = "${result.items.size} worklist item(s) · $label"
                    failed = false
                }
                is FindResult.Failed -> {
                    items = emptyList()
                    status = "MWL failed: ${result.message}"
                    failed = true
                }
            }
        }
    }

    val body: @Composable () -> Unit = {
        if (!embedded) {
            ScreenTitle(
                title = "Modality worklist",
                subtitle = "Select a scheduled exam to bind this capture session.",
            )
        }

        SoftPanel {
            SectionLabel("Date")
            SegmentedChoice(
                leftLabel = "Today",
                rightLabel = "Pick date",
                leftSelected = dateMode == WorklistDateMode.Today,
                onLeft = {
                    dateMode = WorklistDateMode.Today
                    selectedDate = today
                },
                onRight = { dateMode = WorklistDateMode.Date },
            )
            if (dateMode == WorklistDateMode.Date) {
                QuietOutlinedButton(
                    text = "Scheduled: ${selectedDate.format(dateDisplayFormatter)}",
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    "Scheduled date = ${today.format(dateDisplayFormatter)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = DicomColors.Slate700,
                )
            }

            SectionLabel("Optional filters")
            DicomTextField(
                value = patientId,
                onValueChange = { patientId = it },
                label = "Patient ID",
            )
            DicomTextField(
                value = accession,
                onValueChange = { accession = it },
                label = "Accession",
            )

            SectionLabel("Sort by")
            WorklistSortRow(
                selected = sort,
                onSelect = { sort = it },
            )

            ForestButton(
                text = "Query worklist",
                onClick = { runQuery() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
            )
        }

        if (loading) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(color = DicomColors.Forest)
                Text("Querying modality worklist…", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            StatusBanner(
                text = status,
                tone = if (failed) StatusTone.Error else StatusTone.Info,
            )
        }

        if (sortedItems.isEmpty() && !loading && !failed) {
            Text(
                "No patients on the worklist for this filter.",
                style = MaterialTheme.typography.bodySmall,
                color = DicomColors.Slate500,
            )
        }

        sortedItems.forEach { entry ->
            ResultRow(
                title = "${entry.patientId} · ${formatPersonNameForDisplay(entry.patientName)}",
                subtitle = listOfNotNull(
                    entry.accessionNumber?.let { "Acc $it" },
                    entry.modality,
                    listOfNotNull(entry.scheduledStartDate, entry.scheduledStartTime)
                        .joinToString(" ")
                        .ifBlank { null },
                    entry.studyDescription,
                ).joinToString(" · "),
                onClick = { onSelected(entry) },
                trailing = {
                    MetaChip(text = "Select", foreground = DicomColors.ForestMid)
                },
            )
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            selectedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                        }
                        showDatePicker = false
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (embedded) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = { body() })
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            body()
        }
    }
}

@Composable
private fun WorklistSortRow(
    selected: WorklistSort,
    onSelect: (WorklistSort) -> Unit,
) {
    val options = listOf(
        WorklistSort.Time to "Time",
        WorklistSort.Name to "Name",
        WorklistSort.PatientId to "Patient ID",
        WorklistSort.Accession to "Accession",
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { (value, label) ->
                    Row(
                        Modifier
                            .weight(1f)
                            .selectable(
                                selected = selected == value,
                                onClick = { onSelect(value) },
                                role = Role.RadioButton,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == value,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = DicomColors.Forest,
                                unselectedColor = DicomColors.Slate400,
                            ),
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                if (row.size == 1) {
                    // keep alignment when odd count
                }
            }
        }
    }
}

private fun worklistComparator(sort: WorklistSort): Comparator<WorklistEntry> =
    when (sort) {
        WorklistSort.Time -> compareBy<WorklistEntry>(
            { it.scheduledStartDate.orEmpty() },
            { it.scheduledStartTime.orEmpty() },
            { it.patientName },
        )
        WorklistSort.Name -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.patientName }
        WorklistSort.PatientId -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.patientId }
        WorklistSort.Accession -> compareBy(String.CASE_INSENSITIVE_ORDER) {
            it.accessionNumber.orEmpty()
        }
    }

@Composable
fun AppendStudyScreen(
    endpoint: PacsEndpoint,
    onSelected: (StudyEntry) -> Unit,
    embedded: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    var patientId by remember { mutableStateOf("") }
    var accession by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<StudyEntry>>(emptyList()) }
    var status by remember {
        mutableStateOf(
            when (endpoint.transportMode) {
                TransportMode.DICOMWEB -> "Find study via QIDO-RS"
                TransportMode.DIMSE -> "Find an existing study to append photos"
            },
        )
    }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val body: @Composable () -> Unit = {
        SoftPanel {
            SectionLabel(if (embedded) "PACS query" else "Query")
            DicomTextField(
                value = patientId,
                onValueChange = { patientId = it },
                label = "Patient ID",
            )
            DicomTextField(
                value = accession,
                onValueChange = { accession = it },
                label = "Accession",
            )
            ForestButton(
                text = "Query studies",
                onClick = {
                    if (patientId.isBlank() && accession.isBlank()) {
                        status = "Enter Patient ID and/or Accession"
                        failed = true
                        return@ForestButton
                    }
                    loading = true
                    failed = false
                    status = "Querying studies..."
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            PacsGateway.fromEndpoint(endpoint).findStudies(
                                StudyQuery(
                                    patientId = patientId.trim().ifBlank { null },
                                    accessionNumber = accession.trim().ifBlank { null },
                                ),
                            )
                        }
                        loading = false
                        when (result) {
                            is FindResult.Success -> {
                                items = result.items
                                status = "${items.size} study(ies)"
                                failed = false
                            }
                            is FindResult.Failed -> {
                                items = emptyList()
                                status = "Query failed: ${result.message}"
                                failed = true
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
            )
        }

        if (loading) {
            SoftPanel {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(color = DicomColors.Forest)
                    Text("Querying studies…", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            StatusBanner(
                text = status,
                tone = if (failed) StatusTone.Error else StatusTone.Info,
            )
        }

        items.forEach { entry ->
            ResultRow(
                title = "${entry.patientId} · ${entry.patientName}",
                subtitle = listOfNotNull(
                    entry.accessionNumber?.let { "Acc $it" },
                    entry.studyDate,
                    entry.studyDescription,
                    entry.studyInstanceUid.take(24) + "…",
                ).joinToString(" · "),
                onClick = { onSelected(entry) },
                trailing = {
                    MetaChip(text = "Append", foreground = DicomColors.ForestMid)
                },
            )
        }
    }

    if (embedded) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = { body() })
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = { body() },
        )
    }
}
