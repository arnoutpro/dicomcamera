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
import nl.dicomcamera.app.BuildConfig
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
    Logging,
}

/**
 * Settings hub: Local AE, Remote DICOM (PACS), HL7 demographics, Logging.
 */
@Composable
fun SettingsFlow(
    initial: PacsSettings,
    connectivityStatus: String,
    logSummary: String,
    onSave: (PacsSettings) -> Unit,
    onPing: (PacsSettings) -> Unit,
    onEcho: (PacsSettings) -> Unit,
    onLoggingEnabledChange: (PacsSettings, Boolean) -> Unit,
    onDownloadLog: () -> Unit,
    onClearLog: () -> Unit,
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
                SettingsSection.Logging -> "Logging"
            },
        )
    }

    when (section) {
        SettingsSection.Hub -> SettingsHub(
            draft = draft,
            onOpenLocal = { section = SettingsSection.LocalAe },
            onOpenRemote = { section = SettingsSection.RemoteDicom },
            onOpenHl7 = { section = SettingsSection.Hl7Demographics },
            onOpenLogging = { section = SettingsSection.Logging },
            onSave = { onSave(draft) },
            connectivityStatus = connectivityStatus,
        )
        SettingsSection.LocalAe -> LocalAeSection(
            draft = draft,
            onChange = { draft = it },
            onBack = { section = SettingsSection.Hub },
        )
        SettingsSection.RemoteDicom -> RemoteDicomSection(
            draft = draft,
            connectivityStatus = connectivityStatus,
            onChange = { draft = it },
            onPing = { onPing(draft) },
            onEcho = { onEcho(draft) },
            onBack = { section = SettingsSection.Hub },
        )
        SettingsSection.Hl7Demographics -> Hl7DemographicsSection(
            draft = draft,
            onChange = { draft = it },
            onBack = { section = SettingsSection.Hub },
        )
        SettingsSection.Logging -> LoggingSection(
            draft = draft,
            logSummary = logSummary,
            onEnabledChange = { enabled ->
                draft = draft.copy(loggingEnabled = enabled)
                onLoggingEnabledChange(draft.copy(loggingEnabled = enabled), enabled)
            },
            onDownloadLog = onDownloadLog,
            onClearLog = onClearLog,
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
    onOpenLogging: () -> Unit,
    onSave: () -> Unit,
    connectivityStatus: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
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
            SettingsNavRow(
                title = "Logging",
                subtitle = draft.loggingSummary(),
                onClick = onOpenLogging,
            )
        }

        ForestButton(
            text = "Save all settings",
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
        )

        if (connectivityStatus.isNotBlank()) {
            StatusBanner(text = connectivityStatus, tone = connectivityTone(connectivityStatus))
        }

        SoftPanel {
            SectionLabel("About")
            Text(
                "DICOM Camera",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = DicomColors.Slate700,
            )
            Text(
                BuildConfig.FLAVOR.replaceFirstChar { it.uppercase() } + " build",
                style = MaterialTheme.typography.bodySmall,
                color = DicomColors.Slate500,
            )
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
    connectivityStatus: String,
    onChange: (PacsSettings) -> Unit,
    onPing: () -> Unit,
    onEcho: () -> Unit,
    onBack: () -> Unit,
) {
    val hostReady = draft.host.isNotBlank()
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
            SectionLabel("Connectivity tests")
            Text(
                "${draft.host.ifBlank { "?" }}:${draft.port} → ${draft.calledAeTitle.ifBlank { "?" }}",
                style = MaterialTheme.typography.bodySmall,
                color = DicomColors.Slate700,
            )
            Text(
                "Ping checks host reachability on the network. C-ECHO is DICOM Verification SCU — it only works against a DICOM AE (PACS), not an HL7 façade.",
                style = MaterialTheme.typography.bodySmall,
                color = DicomColors.Slate700,
            )
            QuietOutlinedButton(
                text = "Ping host",
                onClick = onPing,
                modifier = Modifier.fillMaxWidth(),
                enabled = hostReady,
            )
            QuietOutlinedButton(
                text = "C-ECHO (DICOM)",
                onClick = onEcho,
                modifier = Modifier.fillMaxWidth(),
                enabled = draft.isConfigured(),
            )
            if (!hostReady) {
                Text(
                    "Enter Host / IP to enable Ping.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DicomColors.Slate500,
                )
            }
            if (!draft.isConfigured()) {
                Text(
                    "Fill host, port, Calling AE, and Called AE Title to enable C-ECHO.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DicomColors.Slate500,
                )
            }
            if (connectivityStatus.isNotBlank()) {
                StatusBanner(text = connectivityStatus, tone = connectivityTone(connectivityStatus))
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
                "Ping and C-ECHO in Remote DICOM do not apply here — HL7 uses HTTPS, not DIMSE.",
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

@Composable
private fun LoggingSection(
    draft: PacsSettings,
    logSummary: String,
    onEnabledChange: (Boolean) -> Unit,
    onDownloadLog: () -> Unit,
    onClearLog: () -> Unit,
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
            title = "Logging",
            subtitle = "Diagnostic log for support. Off by default — turn on only when troubleshooting.",
        )
        SoftPanel {
            SectionLabel("Activation")
            Text(
                "Logging stays off until you enable it here. No diagnostic file is written while disabled.",
                style = MaterialTheme.typography.bodySmall,
                color = DicomColors.Slate700,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (draft.loggingEnabled) "Logging enabled" else "Logging disabled",
                    style = MaterialTheme.typography.titleSmall,
                )
                Switch(
                    checked = draft.loggingEnabled,
                    onCheckedChange = onEnabledChange,
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
            SectionLabel("Export")
            Text(
                logSummary,
                style = MaterialTheme.typography.bodyMedium,
            )
            ForestButton(
                text = "Download log",
                onClick = onDownloadLog,
                modifier = Modifier.fillMaxWidth(),
            )
            QuietOutlinedButton(
                text = "Clear log",
                onClick = onClearLog,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Download opens the system save dialog (Files / Drive). The log may include hostnames and patient IDs used during troubleshooting.",
                style = MaterialTheme.typography.bodySmall,
                color = DicomColors.Slate500,
            )
        }
    }
}

private fun connectivityTone(status: String): StatusTone = when {
    status.contains("OK", ignoreCase = false) -> StatusTone.Success
    status.contains("failed", ignoreCase = true) ||
        status.contains("timed out", ignoreCase = true) ||
        status.contains("unreachable", ignoreCase = true) -> StatusTone.Error
    else -> StatusTone.Info
}
