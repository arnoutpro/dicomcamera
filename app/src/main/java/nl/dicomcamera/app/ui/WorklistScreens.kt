package nl.dicomcamera.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.dicomcamera.app.ui.components.DicomTextField
import nl.dicomcamera.app.ui.components.ForestButton
import nl.dicomcamera.app.ui.components.MetaChip
import nl.dicomcamera.app.ui.components.ResultRow
import nl.dicomcamera.app.ui.components.ScreenTitle
import nl.dicomcamera.app.ui.components.SectionLabel
import nl.dicomcamera.app.ui.components.SoftPanel
import nl.dicomcamera.app.ui.components.StatusBanner
import nl.dicomcamera.app.ui.components.StatusTone
import nl.dicomcamera.app.ui.theme.DicomColors
import nl.dicomcamera.dicom.DicomNode
import nl.dicomcamera.dicom.FindResult
import nl.dicomcamera.dicom.PacsClient
import nl.dicomcamera.dicom.StudyEntry
import nl.dicomcamera.dicom.StudyQuery
import nl.dicomcamera.dicom.WorklistEntry
import nl.dicomcamera.dicom.WorklistQuery

@Composable
fun WorklistScreen(
    node: DicomNode,
    callingAeTitle: String,
    onSelected: (WorklistEntry) -> Unit,
    embedded: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    var patientId by remember { mutableStateOf("") }
    var accession by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<WorklistEntry>>(emptyList()) }
    var status by remember { mutableStateOf("Query today's XC worklist") }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val body: @Composable () -> Unit = {
        if (!embedded) {
            ScreenTitle(
                title = "Modality worklist",
                subtitle = "Select a scheduled XC exam to bind this capture session.",
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!embedded) SectionLabel("Filters")
            DicomTextField(
                value = patientId,
                onValueChange = { patientId = it },
                label = "Patient ID filter",
            )
            DicomTextField(
                value = accession,
                onValueChange = { accession = it },
                label = "Accession filter",
            )
            ForestButton(
                text = "Query worklist",
                onClick = {
                    loading = true
                    failed = false
                    status = "Querying MWL..."
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            PacsClient(node).use {
                                it.findWorklist(
                                    WorklistQuery(
                                        patientId = patientId.trim().ifBlank { null },
                                        accessionNumber = accession.trim().ifBlank { null },
                                        modality = "XC",
                                        scheduledStationAeTitle = callingAeTitle,
                                    ),
                                )
                            }
                        }
                        loading = false
                        when (result) {
                            is FindResult.Success -> {
                                items = result.items
                                status = "${items.size} worklist item(s)"
                                failed = false
                            }
                            is FindResult.Failed -> {
                                items = emptyList()
                                status = "MWL failed: ${result.message}"
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

        items.forEach { entry ->
            ResultRow(
                title = "${entry.patientId} · ${entry.patientName}",
                subtitle = listOfNotNull(
                    entry.accessionNumber?.let { "Acc $it" },
                    entry.modality,
                    entry.scheduledStartDate,
                    entry.studyDescription,
                ).joinToString(" · "),
                onClick = { onSelected(entry) },
                trailing = {
                    MetaChip(text = "Select", foreground = DicomColors.ForestMid)
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
        ) {
            SoftPanel { body() }
        }
    }
}

@Composable
fun AppendStudyScreen(
    node: DicomNode,
    onSelected: (StudyEntry) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var patientId by remember { mutableStateOf("") }
    var accession by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<StudyEntry>>(emptyList()) }
    var status by remember { mutableStateOf("Find an existing study to append photos") }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle(
            title = "Archive",
            subtitle = "Find an existing study, then add clinical photo/video to it.",
        )
        SoftPanel {
            SectionLabel("Query")
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
                            PacsClient(node).use {
                                it.findStudies(
                                    StudyQuery(
                                        patientId = patientId.trim().ifBlank { null },
                                        accessionNumber = accession.trim().ifBlank { null },
                                    ),
                                )
                            }
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
}
