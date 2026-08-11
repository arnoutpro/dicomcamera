package nl.dicomcamera.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nl.dicomcamera.app.BuildConfig
import nl.dicomcamera.dicom.DicomNode
import nl.dicomcamera.dicom.PacsEndpoint
import nl.dicomcamera.dicom.TransportMode
import nl.dicomcamera.identity.Hl7FacadeConfig

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dicomcamera_settings")

data class PacsSettings(
    /** Local / calling AE Title for this device. */
    val callingAeTitle: String = BuildConfig.DEFAULT_CALLING_AET,
    /** DICOM modality code stamped on created instances (usually XC). */
    val modality: String = "XC",
    /** Optional station name for display / future tags. */
    val stationName: String = "",
    /** DIMSE vs DICOMweb (Phase 4 dual stack). */
    val transportMode: TransportMode = TransportMode.DIMSE,
    /** Remote PACS host for DIMSE (also MWL fallback when using DICOMweb). */
    val host: String = BuildConfig.DEFAULT_PACS_HOST,
    val port: Int = BuildConfig.DEFAULT_PACS_PORT,
    /** Remote / called AE Title (archive). */
    val calledAeTitle: String = BuildConfig.DEFAULT_CALLED_AET,
    val useTls: Boolean = false,
    /** DICOMweb root URL (QIDO-RS / STOW-RS). */
    val dicomWebBaseUrl: String = BuildConfig.DEFAULT_DICOMWEB_URL,
    /** HL7 demographics façade. */
    val hl7Enabled: Boolean = false,
    val hl7BaseUrl: String = "",
    val hl7BearerToken: String = "",
    /** Opt-in diagnostic logging (off by default). */
    val loggingEnabled: Boolean = false,
    /** True when Managed Configurations overlay is active. */
    val managedByMdm: Boolean = false,
) {
    fun toNode(): DicomNode = DicomNode(
        host = host.trim(),
        port = port,
        calledAeTitle = calledAeTitle.trim(),
        callingAeTitle = callingAeTitle.trim(),
        useTls = useTls,
    )

    fun toEndpoint(): PacsEndpoint = PacsEndpoint(
        transportMode = transportMode,
        host = host.trim(),
        port = port,
        calledAeTitle = calledAeTitle.trim(),
        callingAeTitle = callingAeTitle.trim(),
        useTls = useTls,
        dicomWebBaseUrl = dicomWebBaseUrl.trim(),
    )

    fun toHl7Config(): Hl7FacadeConfig = Hl7FacadeConfig(
        enabled = hl7Enabled,
        baseUrl = hl7BaseUrl.trim(),
        bearerToken = hl7BearerToken.trim(),
    )

    fun isConfigured(): Boolean = toEndpoint().isConfigured()

    fun localSummary(): String =
        listOfNotNull(
            callingAeTitle.trim().ifBlank { null },
            modality.trim().ifBlank { null },
            stationName.trim().ifBlank { null },
        ).joinToString(" · ").ifBlank { "Not set" }

    fun transportSummary(): String = when (transportMode) {
        TransportMode.DIMSE -> "DIMSE (C-STORE / MWL)"
        TransportMode.DICOMWEB -> "DICOMweb (STOW / QIDO)"
    }

    fun remoteSummary(): String =
        when (transportMode) {
            TransportMode.DIMSE -> when {
                host.isBlank() -> "Not configured"
                else -> listOf(
                    host.trim(),
                    port.toString(),
                    calledAeTitle.trim().ifBlank { "AE?" },
                    if (useTls) "TLS" else "plain",
                ).joinToString(" · ")
            }
            TransportMode.DICOMWEB ->
                dicomWebBaseUrl.trim().ifBlank { "DICOMweb URL not set" }
        }

    fun hl7Summary(): String = toHl7Config().summary()

    fun loggingSummary(): String =
        if (loggingEnabled) "Enabled — export from Logging" else "Off (manual activation)"
}

class SettingsRepository(private val context: Context) {
    private object Keys {
        val transport = stringPreferencesKey("pacs_transport")
        val host = stringPreferencesKey("pacs_host")
        val port = intPreferencesKey("pacs_port")
        val called = stringPreferencesKey("pacs_called_aet")
        val calling = stringPreferencesKey("pacs_calling_aet")
        val tls = booleanPreferencesKey("pacs_use_tls")
        val webUrl = stringPreferencesKey("pacs_dicomweb_url")
        val modality = stringPreferencesKey("modality_code")
        val station = stringPreferencesKey("station_name")
        val hl7Enabled = booleanPreferencesKey("hl7_enabled")
        val hl7Url = stringPreferencesKey("hl7_base_url")
        val hl7Token = stringPreferencesKey("hl7_bearer_token")
        val loggingEnabled = booleanPreferencesKey("logging_enabled")
    }

    val settings: Flow<PacsSettings> = context.dataStore.data.map { prefs ->
        val local = PacsSettings(
            callingAeTitle = prefs[Keys.calling] ?: BuildConfig.DEFAULT_CALLING_AET,
            modality = prefs[Keys.modality] ?: "XC",
            stationName = prefs[Keys.station].orEmpty(),
            transportMode = prefs[Keys.transport]?.let {
                runCatching { TransportMode.valueOf(it) }.getOrDefault(TransportMode.DIMSE)
            } ?: TransportMode.DIMSE,
            host = prefs[Keys.host] ?: BuildConfig.DEFAULT_PACS_HOST,
            port = prefs[Keys.port] ?: BuildConfig.DEFAULT_PACS_PORT,
            calledAeTitle = prefs[Keys.called] ?: BuildConfig.DEFAULT_CALLED_AET,
            useTls = prefs[Keys.tls] ?: false,
            dicomWebBaseUrl = prefs[Keys.webUrl] ?: BuildConfig.DEFAULT_DICOMWEB_URL,
            hl7Enabled = prefs[Keys.hl7Enabled] ?: false,
            hl7BaseUrl = prefs[Keys.hl7Url].orEmpty(),
            hl7BearerToken = prefs[Keys.hl7Token].orEmpty(),
            loggingEnabled = prefs[Keys.loggingEnabled] ?: false,
        )
        ManagedConfig.merge(context, local)
    }

    suspend fun save(settings: PacsSettings) {
        if (ManagedConfig.isManaged(context)) {
            return
        }
        context.dataStore.edit { prefs ->
            prefs[Keys.calling] = settings.callingAeTitle.trim()
            prefs[Keys.modality] = settings.modality.trim().ifBlank { "XC" }
            prefs[Keys.station] = settings.stationName.trim()
            prefs[Keys.transport] = settings.transportMode.name
            prefs[Keys.host] = settings.host.trim()
            prefs[Keys.port] = settings.port
            prefs[Keys.called] = settings.calledAeTitle.trim()
            prefs[Keys.tls] = settings.useTls
            prefs[Keys.webUrl] = settings.dicomWebBaseUrl.trim()
            prefs[Keys.hl7Enabled] = settings.hl7Enabled
            prefs[Keys.hl7Url] = settings.hl7BaseUrl.trim()
            prefs[Keys.hl7Token] = settings.hl7BearerToken.trim()
            prefs[Keys.loggingEnabled] = settings.loggingEnabled
        }
    }
}
