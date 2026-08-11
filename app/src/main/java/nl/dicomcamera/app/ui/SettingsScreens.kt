package nl.dicomcamera.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import nl.dicomcamera.app.settings.PacsSettings
import nl.dicomcamera.dicom.TransportMode

private enum class SettingsSection {
    Hub,
    Transport,
    Modality,
    PacsNode,
    Connectivity,
    Audit,
}

/**
 * Structured settings: hub → section screens. Draft edits stay local until Save.
 */
@Composable
fun SettingsFlow(
    initial: PacsSettings,
    echoStatus: String,
    onSave: (PacsSettings) -> Unit,
    onTestConnectivity: (PacsSettings) -> Unit,
    onExportAtna: () -> Unit,
    onClose: () -> Unit,
    titleOverride: (String) -> Unit = {},
) {
    var section by remember { mutableStateOf(SettingsSection.Hub) }
    var draft by remember(initial) { mutableStateOf(initial) }
    val locked = draft.managedByMdm

    titleOverride(
        when (section) {
            SettingsSection.Hub -> "Settings"
            SettingsSection.Transport -> "Transport"
            SettingsSection.Modality -> "Modality identity"
            SettingsSection.PacsNode -> "PACS node"
            SettingsSection.Connectivity -> "Connectivity test"
            SettingsSection.Audit -> "Audit & export"
        },
    )

    when (section) {
        SettingsSection.Hub -> SettingsHub(
            draft = draft,
            onOpen = { section = it },
            onSave = { onSave(draft) },
            onClose = onClose,
        )
        SettingsSection.Transport -> TransportSection(
            draft = draft,
            locked = locked,
            onChange = { draft = it },
            onBack = { section = SettingsSection.Hub },
        )
        SettingsSection.Modality -> ModalitySection(
            draft = draft,
            locked = locked,
            onChange = { draft = it },
            onBack = { section = SettingsSection.Hub },
        )
        SettingsSection.PacsNode -> PacsNodeSection(
            draft = draft,
            locked = locked,
            onChange = { draft = it },
            onBack = { section = SettingsSection.Hub },
        )
        SettingsSection.Connectivity -> ConnectivitySection(
            draft = draft,
            echoStatus = echoStatus,
            onTest = { onTestConnectivity(draft) },
            onBack = { section = SettingsSection.Hub },
        )
        SettingsSection.Audit -> AuditSection(
            onExportAtna = onExportAtna,
            status = echoStatus,
            onBack = { section = SettingsSection.Hub },
        )
    }
}

@Composable
private fun SettingsHub(
    draft: PacsSettings,
    onOpen: (SettingsSection) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Configure PACS when ready. You can explore capture without a server.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (draft.managedByMdm) {
            Text(
                text = "Managed by MDM — fields may be locked.",
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        StatusChip(draft)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsRow(
            title = "Transport",
            subtitle = when (draft.transportMode) {
                TransportMode.DIMSE -> "DIMSE (C-STORE / MWL)"
                TransportMode.DICOMWEB -> "DICOMweb (STOW / QIDO)"
            },
            onClick = { onOpen(SettingsSection.Transport) },
        )
        SettingsRow(
            title = "Modality identity",
            subtitle = "AE ${draft.callingAeTitle.ifBlank { "—" }} · ${draft.modality}",
            onClick = { onOpen(SettingsSection.Modality) },
        )
        SettingsRow(
            title = "PACS node",
            subtitle = when (draft.transportMode) {
                TransportMode.DIMSE ->
                    listOf(draft.host.ifBlank { "host not set" }, draft.port.toString(), draft.calledAeTitle)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                TransportMode.DICOMWEB ->
                    draft.dicomWebBaseUrl.ifBlank { "DICOMweb URL not set" }
            },
            onClick = { onOpen(SettingsSection.PacsNode) },
        )
        SettingsRow(
            title = "Connectivity test",
            subtitle = if (draft.transportMode == TransportMode.DIMSE) "C-ECHO" else "DICOMweb ping",
            onClick = { onOpen(SettingsSection.Connectivity) },
        )
        SettingsRow(
            title = "Audit & export",
            subtitle = "ATNA-style SIEM export",
            onClick = { onOpen(SettingsSection.Audit) },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = !draft.managedByMdm,
        ) {
            Text("Save all settings")
        }
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("Close")
        }
    }
}

@Composable
private fun StatusChip(draft: PacsSettings) {
    val configured = draft.isConfigured()
    Text(
        text = if (configured) {
            "PACS configured — send to archive enabled"
        } else {
            "Demo mode — capture works; send needs PACS"
        },
        style = MaterialTheme.typography.titleSmall,
        color = if (configured) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.tertiary
        },
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}

@Composable
private fun SectionScaffold(
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(onClick = onBack) { Text("← Back to settings") }
        content()
    }
}

@Composable
private fun TransportSection(
    draft: PacsSettings,
    locked: Boolean,
    onChange: (PacsSettings) -> Unit,
    onBack: () -> Unit,
) {
    SectionScaffold(onBack = onBack) {
        Text(
            "How this device talks to the archive.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf(
            TransportMode.DIMSE to "DIMSE — classic DICOM (C-ECHO, MWL, C-STORE)",
            TransportMode.DICOMWEB to "DICOMweb — QIDO-RS query + STOW-RS store",
        ).forEach { (mode, label) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = draft.transportMode == mode,
                        onClick = { if (!locked) onChange(draft.copy(transportMode = mode)) },
                        role = Role.RadioButton,
                        enabled = !locked,
                    )
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = draft.transportMode == mode,
                    onClick = null,
                    enabled = !locked,
                )
                Text(label, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Text(
            "MWL always uses DIMSE. In DICOMweb mode, keep host/AE filled if you need worklist.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModalitySection(
    draft: PacsSettings,
    locked: Boolean,
    onChange: (PacsSettings) -> Unit,
    onBack: () -> Unit,
) {
    SectionScaffold(onBack = onBack) {
        Text(
            "Identity this device presents as a modality.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = draft.callingAeTitle,
            onValueChange = { if (!locked) onChange(draft.copy(callingAeTitle = it)) },
            label = { Text("Calling AE Title") },
            supportingText = { Text("Registered on the PACS as this device") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !locked,
        )
        OutlinedTextField(
            value = draft.modality,
            onValueChange = { if (!locked) onChange(draft.copy(modality = it.uppercase().take(16))) },
            label = { Text("Modality code") },
            supportingText = { Text("Usually XC for clinical photography") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !locked,
        )
        OutlinedTextField(
            value = draft.stationName,
            onValueChange = { if (!locked) onChange(draft.copy(stationName = it)) },
            label = { Text("Station name (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !locked,
        )
        Text(text = "DICOM TLS (DIMSE associations)", style = MaterialTheme.typography.labelLarge)
        Text(
            text = "Uses the Android system trust store. Install a hospital private CA via MDM.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (draft.useTls) "TLS enabled" else "TLS disabled")
            Switch(
                checked = draft.useTls,
                onCheckedChange = { if (!locked) onChange(draft.copy(useTls = it)) },
                enabled = !locked,
            )
        }
    }
}

@Composable
private fun PacsNodeSection(
    draft: PacsSettings,
    locked: Boolean,
    onChange: (PacsSettings) -> Unit,
    onBack: () -> Unit,
) {
    SectionScaffold(onBack = onBack) {
        Text(
            "Remote archive endpoint. Leave empty to explore the app in demo mode.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = draft.host,
            onValueChange = { if (!locked) onChange(draft.copy(host = it)) },
            label = { Text("PACS host") },
            supportingText = { Text("DIMSE host; also MWL fallback when using DICOMweb") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !locked,
        )
        OutlinedTextField(
            value = draft.port.toString(),
            onValueChange = { text ->
                if (!locked) {
                    onChange(draft.copy(port = text.filter { it.isDigit() }.toIntOrNull() ?: draft.port))
                }
            },
            label = { Text("DIMSE port") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !locked,
        )
        OutlinedTextField(
            value = draft.calledAeTitle,
            onValueChange = { if (!locked) onChange(draft.copy(calledAeTitle = it)) },
            label = { Text("Called AE Title") },
            supportingText = { Text("Remote PACS AE Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !locked,
        )
        OutlinedTextField(
            value = draft.dicomWebBaseUrl,
            onValueChange = { if (!locked) onChange(draft.copy(dicomWebBaseUrl = it)) },
            label = { Text("DICOMweb base URL") },
            supportingText = { Text("e.g. https://pacs.example/dicom-web") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !locked,
        )
    }
}

@Composable
private fun ConnectivitySection(
    draft: PacsSettings,
    echoStatus: String,
    onTest: () -> Unit,
    onBack: () -> Unit,
) {
    SectionScaffold(onBack = onBack) {
        Text(
            when (draft.transportMode) {
                TransportMode.DIMSE ->
                    "Sends a DICOM Verification (C-ECHO) to the configured Called AE."
                TransportMode.DICOMWEB ->
                    "HTTP reachability check against the DICOMweb studies endpoint."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = when (draft.transportMode) {
                TransportMode.DIMSE ->
                    "${draft.host.ifBlank { "?" }}:${draft.port} → ${draft.calledAeTitle.ifBlank { "?" }} " +
                        "(from ${draft.callingAeTitle.ifBlank { "?" }})"
                TransportMode.DICOMWEB ->
                    draft.dicomWebBaseUrl.ifBlank { "DICOMweb URL not set" }
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = onTest,
            modifier = Modifier.fillMaxWidth(),
            enabled = draft.isConfigured(),
        ) {
            Text(
                when (draft.transportMode) {
                    TransportMode.DIMSE -> "Run C-ECHO"
                    TransportMode.DICOMWEB -> "Ping DICOMweb"
                },
            )
        }
        if (!draft.isConfigured()) {
            Text(
                "Fill PACS node / transport settings first.",
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        if (echoStatus.isNotBlank()) {
            Text(echoStatus, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun AuditSection(
    onExportAtna: () -> Unit,
    status: String,
    onBack: () -> Unit,
) {
    SectionScaffold(onBack = onBack) {
        Text(
            "Local audit stays on device. Export creates ATNA-style syslog lines for SIEM hand-off.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onExportAtna, modifier = Modifier.fillMaxWidth()) {
            Text("Export ATNA audit log")
        }
        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}
