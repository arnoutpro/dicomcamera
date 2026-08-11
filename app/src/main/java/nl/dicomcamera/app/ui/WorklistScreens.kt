package nl.dicomcamera.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
) {
    val scope = rememberCoroutineScope()
    var patientId by remember { mutableStateOf("") }
    var accession by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<WorklistEntry>>(emptyList()) }
    var status by remember { mutableStateOf("Query today's XC worklist") }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = patientId,
            onValueChange = { patientId = it },
            label = { Text("Patient ID filter") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = accession,
            onValueChange = { accession = it },
            label = { Text("Accession filter") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(
            onClick = {
                loading = true
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
                        }
                        is FindResult.Failed -> {
                            items = emptyList()
                            status = "MWL failed: ${result.message}"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
        ) {
            Text("Query worklist")
        }
        if (loading) CircularProgressIndicator()
        Text(status)
        items.forEach { entry ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelected(entry) }
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    "${entry.patientId} · ${entry.patientName}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    listOfNotNull(
                        entry.accessionNumber?.let { "Acc $it" },
                        entry.modality,
                        entry.scheduledStartDate,
                        entry.studyDescription,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = patientId,
            onValueChange = { patientId = it },
            label = { Text("Patient ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = accession,
            onValueChange = { accession = it },
            label = { Text("Accession") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(
            onClick = {
                if (patientId.isBlank() && accession.isBlank()) {
                    status = "Enter Patient ID and/or Accession"
                    return@Button
                }
                loading = true
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
                        }
                        is FindResult.Failed -> {
                            items = emptyList()
                            status = "Query failed: ${result.message}"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
        ) {
            Text("Query studies")
        }
        if (loading) CircularProgressIndicator()
        Text(status)
        items.forEach { entry ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelected(entry) }
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    "${entry.patientId} · ${entry.patientName}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    listOfNotNull(
                        entry.accessionNumber?.let { "Acc $it" },
                        entry.studyDate,
                        entry.studyDescription,
                        entry.studyInstanceUid.take(24) + "…",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
