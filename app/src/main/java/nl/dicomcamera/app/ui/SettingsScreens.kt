package nl.dicomcamera.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nl.dicomcamera.app.settings.PacsSettings
import nl.dicomcamera.app.ui.components.DicomTextField
import nl.dicomcamera.app.ui.components.ForestButton
import nl.dicomcamera.app.ui.components.QuietOutlinedButton
import nl.dicomcamera.app.ui.components.ScreenTitle
import nl.dicomcamera.app.ui.components.SectionLabel
import nl.dicomcamera.app.ui.components.SoftPanel
import nl.dicomcamera.app.ui.components.StatusBanner
import nl.dicomcamera.app.ui.components.StatusTone
import nl.dicomcamera.app.ui.theme.DicomColors

private enum class SettingsSection {
    Hub,
    LocalAe,
    RemoteDicom,
    Hl7Demographics,
}

/**
 * Settings hub: Local AE, Remote DICOM (PACS), HL7 demographics façade.
 */
@Composable
fun SettingsFlow(
    initial: PacsSettings,
    echoStatus: String,
    onSave: (PacsSettings) -> Unit,
    onEcho: (PacsSettings) -> Unit,
    onTitleChange: (String) -> Unit = {},
) {
    var section by remember { mutableStateOf(SettingsSection.Hub) }
    var draft by remember(initial) { mutableStateOf(initial) }

    SideEffect {
        onTitleChange(
            when (section) {
                SettingsSection.Hub -> "Settings"
                SettingsSection.LocalAe -> "Local AE"
                SettingsSection.RemoteDicom -> "Remote DICOM"
                SettingsSection.Hl7Demographics -> "HL7 demographics"
            },
        )
    }

    when (section) {
        SettingsSection.Hub -> SettingsHub(
            draft = draft,
            onOpenLocal = { section = SettingsSection.LocalAe },
            onOpenRemote = { section = SettingsSection.RemoteDicom },
            onOpenHl7 = { section = SettingsSection.Hl7Demographics },
            onSave = { onSave(draft) },
            echoStatus = echoStatus,
        )
        SettingsSection.LocalAe -> LocalAeSection(
            draft = draft,
            onChange = { draft = it },
            onBack = { section = SettingsSection.Hub },
        )
        SettingsSection.RemoteDicom -> RemoteDicomSection(
            draft = draft,
            echoStatus = echoStatus,
            onChange = { draft = it },
            onEcho = { onEcho(draft) },
            onBack = { section = SettingsSection.Hub },
        )
        SettingsSection.Hl7Demographics -> Hl7DemographicsSection(
            draft = draft,
            onChange = { draft = it },
            onBack = { section = SettingsSection.Hub },
        )
    }
}

@Composable
private fun SettingsHub(
    draft: PacsSettings,
    onOpenLocal: () -> Unit,
    onOpenRemote: () -> Unit,
    onOpenHl7: () -> Unit,
    onSave: () -> Unit,
    echoStatus: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle(
            title = "Settings",
            subtitle = "Modality identity, PACS archive, and EHR demographics lookup.",
        )

        StatusBanner(
            text = if (draft.isConfigured()) {
                "PACS ready — ${draft.remoteSummary()}"
            } else {
                "Remote PACS not fully configured yet"
            },
            tone = if (draft.isConfigured()) StatusTone.Success else StatusTone.Warn,
        )

        SoftPanel {
            SectionLabel("Configuration")
            SettingsNavRow(
                title = "Local AE",
                subtitle = draft.localSummary(),
                onClick = onOpenLocal,
            )
            SettingsNavRow(
                title = "Remote DICOM",
                subtitle = draft.remoteSummary(),
                onClick = onOpenRemote,
            )
            SettingsNavRow(
                title = "HL7 demographics",
                subtitle = draft.hl7Summary(),
                onClick = onOpenHl7,
            )
        }

        ForestButton(
            text = "Save all settings",
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
        )

        if (echoStatus.isNotBlank()) {
            val tone = when {
                echoStatus.contains("OK") -> StatusTone.Success
                echoStatus.contains("failed", ignoreCase = true) -> StatusTone.Error
                else -> StatusTone.Info
            }
            StatusBanner(text = echoStatus, tone = tone)
        }
    }
}

@Composable
private fun SettingsNavRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = DicomColors.Slate700,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = DicomColors.Slate500,
        )
    }
}

@Composable
private fun LocalAeSection(
    draft: PacsSettings,
    onChange: (PacsSettings) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        QuietOutlinedButton(text = "← Back to Settings", onClick = onBack)
        ScreenTitle(
            title = "Local AE",
            subtitle = "How this device identifies itself on the DICOM network.",
        )
        SoftPanel {
            SectionLabel("Application Entity")
            DicomTextField(
                value = draft.callingAeTitle,
                onValueChange = { onChange(draft.copy(callingAeTitle = it)) },
                label = "Calling AE Title",
            )
            Text(
                "Register this AE Title on the PACS (e.g. DICOMCAM).",
                style = MaterialTheme.typography.bodySmall,
                color = DicomColors.Slate700,
            )
            DicomTextField(
                value = draft.modality,
                onValueChange = { onChange(draft.copy(modality = it.uppercase().take(16))) },
                label = "Modality code",
            )
            Text(
                "Usually XC for clinical photography / video.",
                style = MaterialTheme.typography.bodySmall,
                color = DicomColors.Slate700,
            )
            DicomTextField(
                value = draft.stationName,
                onValueChange = { onChange(draft.copy(stationName = it)) },
                label = "Station name (optional)",
            )
        }
        SoftPanel {
            SectionLabel("Summary")
            Text(draft.localSummary(), style = MaterialTheme.typography.bodyMedium)
            Text(
                "Tap Save all settings on the Settings hub to persist.",
                style = MaterialTheme.typography.bodySmall,
                color = DicomColors.Slate500,
            )
        }
    }
}

@Composable
private fun RemoteDicomSection(
    draft: PacsSettings,
    echoStatus: String,
    onChange: (PacsSettings) -> Unit,
    onEcho: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        QuietOutlinedButton(text = "← Back to Settings", onClick = onBack)
        ScreenTitle(
            title = "Remote DICOM",
            subtitle = "Archive / PACS endpoint for worklist, query, and C-STORE.",
        )
        SoftPanel {
            SectionLabel("PACS node")
            DicomTextField(
                value = draft.host,
                onValueChange = { onChange(draft.copy(host = it)) },
                label = "Host / IP",
            )
            DicomTextField(
                value = draft.port.toString(),
                onValueChange = { text ->
                    onChange(draft.copy(port = text.filter { it.isDigit() }.toIntOrNull() ?: draft.port))
                },
                label = "DIMSE port",
            )
            DicomTextField(
                value = draft.calledAeTitle,
                onValueChange = { onChange(draft.copy(calledAeTitle = it)) },
                label = "Called AE Title",
            )
            Text(
                "Remote archive AE (e.g. ORTHANC, PACS).",
                style = MaterialTheme.typography.bodySmall,
                color = DicomColors.Slate700,
            )
        }
        SoftPanel {
            SectionLabel("Security")
            Text(
                "DICOM TLS uses the Android system trust store. Install a hospital CA via MDM when required.",
                style = MaterialTheme.typography.bodySmall,
                color = DicomColors.Slate700,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (draft.useTls) "TLS enabled" else "TLS disabled",
                    style = MaterialTheme.typography.titleSmall,
                )
                Switch(
                    checked = draft.useTls,
                    onCheckedChange = { onChange(draft.copy(useTls = it)) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = DicomColors.Forest,
                        checkedThumbColor = DicomColors.White,
                        uncheckedTrackColor = DicomColors.Hairline,
                        uncheckedThumbColor = DicomColors.Slate500,
                    ),
                )
            }
        }
        SoftPanel {
            SectionLabel("Connectivity")
            Text(
                "${draft.host.ifBlank { "?" }}:${draft.port} → ${draft.calledAeTitle.ifBlank { "?" }}",
                style = MaterialTheme.typography.bodySmall,
                color = DicomColors.Slate700,
            )
            QuietOutlinedButton(
                text = "Test C-ECHO",
                onClick = onEcho,
                modifier = Modifier.fillMaxWidth(),
                enabled = draft.isConfigured(),
            )
            if (!draft.isConfigured()) {
                Text(
                    "Fill host, port, and Called AE Title to enable C-ECHO.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DicomColors.Slate500,
                )
            }
            if (echoStatus.isNotBlank()) {
                val tone = when {
                    echoStatus.contains("OK") -> StatusTone.Success
                    echoStatus.contains("failed", ignoreCase = true) -> StatusTone.Error
                    else -> StatusTone.Info
                }
                StatusBanner(text = echoStatus, tone = tone)
            }
        }
    }
}

@Composable
private fun Hl7DemographicsSection(
    draft: PacsSettings,
    onChange: (PacsSettings) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        QuietOutlinedButton(text = "← Back to Settings", onClick = onBack)
        ScreenTitle(
            title = "HL7 demographics",
            subtitle = "Query patient details via hospital HL7 façade (HTTPS). No raw MLLP on the phone.",
        )
        SoftPanel {
            SectionLabel("Façade")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (draft.hl7Enabled) "HL7 lookup enabled" else "HL7 lookup disabled",
                    style = MaterialTheme.typography.titleSmall,
                )
                Switch(
                    checked = draft.hl7Enabled,
                    onCheckedChange = { onChange(draft.copy(hl7Enabled = it)) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = DicomColors.Forest,
                        checkedThumbColor = DicomColors.White,
                        uncheckedTrackColor = DicomColors.Hairline,
                        uncheckedThumbColor = DicomColors.Slate500,
                    ),
                )
            }
            DicomTextField(
                value = draft.hl7BaseUrl,
                onValueChange = { onChange(draft.copy(hl7BaseUrl = it)) },
                label = "Façade base URL",
            )
            Text(
                "Example: https://ehr-gw.hospital.local/hl7 — app calls GET …/patients?patientId=",
                style = MaterialTheme.typography.bodySmall,
                color = DicomColors.Slate700,
            )
            DicomTextField(
                value = draft.hl7BearerToken,
                onValueChange = { onChange(draft.copy(hl7BearerToken = it)) },
                label = "Bearer token (optional)",
            )
        }
        SoftPanel {
            SectionLabel("Usage")
            Text(
                "On the Worklist tab, enter a Patient ID and tap Query HL7 to fill name, DOB, and sex.",
                style = MaterialTheme.typography.bodySmall,
                color = DicomColors.Slate700,
            )
            Text(
                draft.hl7Summary(),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Tap Save all settings on the Settings hub to persist.",
                style = MaterialTheme.typography.bodySmall,
                color = DicomColors.Slate500,
            )
        }
    }
}
