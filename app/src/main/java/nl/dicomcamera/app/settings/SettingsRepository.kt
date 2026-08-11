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
import nl.dicomcamera.identity.Hl7FacadeConfig

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dicomcamera_settings")

data class PacsSettings(
    /** Local / calling AE Title for this device. */
    val callingAeTitle: String = BuildConfig.DEFAULT_CALLING_AET,
    /** DICOM modality code stamped on created instances (usually XC). */
    val modality: String = "XC",
    /** Optional station name for display / future tags. */
    val stationName: String = "",
    /** Remote PACS host for DIMSE. */
    val host: String = BuildConfig.DEFAULT_PACS_HOST,
    val port: Int = BuildConfig.DEFAULT_PACS_PORT,
    /** Remote / called AE Title (archive). */
    val calledAeTitle: String = BuildConfig.DEFAULT_CALLED_AET,
    val useTls: Boolean = false,
    /** HL7 demographics façade. */
    val hl7Enabled: Boolean = false,
    val hl7BaseUrl: String = "",
    val hl7BearerToken: String = "",
    /** Opt-in diagnostic logging (off by default). */
    val loggingEnabled: Boolean = false,
) {
    fun toNode(): DicomNode = DicomNode(
        host = host.trim(),
        port = port,
        calledAeTitle = calledAeTitle.trim(),
        callingAeTitle = callingAeTitle.trim(),
        useTls = useTls,
    )

    fun toHl7Config(): Hl7FacadeConfig = Hl7FacadeConfig(
        enabled = hl7Enabled,
        baseUrl = hl7BaseUrl.trim(),
        bearerToken = hl7BearerToken.trim(),
    )

    fun isConfigured(): Boolean =
        host.isNotBlank() &&
            calledAeTitle.isNotBlank() &&
            callingAeTitle.isNotBlank() &&
            port in 1..65535

    fun localSummary(): String =
        listOfNotNull(
            callingAeTitle.trim().ifBlank { null },
            modality.trim().ifBlank { null },
            stationName.trim().ifBlank { null },
        ).joinToString(" · ").ifBlank { "Not set" }

    fun remoteSummary(): String =
        when {
            host.isBlank() -> "Not configured"
            else -> listOf(
                host.trim(),
                port.toString(),
                calledAeTitle.trim().ifBlank { "AE?" },
                if (useTls) "TLS" else "plain",
            ).joinToString(" · ")
        }

    fun hl7Summary(): String = toHl7Config().summary()

    fun loggingSummary(): String =
        if (loggingEnabled) "Enabled — export from Logging" else "Off (manual activation)"
}

class SettingsRepository(private val context: Context) {
    private object Keys {
        val host = stringPreferencesKey("pacs_host")
        val port = intPreferencesKey("pacs_port")
        val called = stringPreferencesKey("pacs_called_aet")
        val calling = stringPreferencesKey("pacs_calling_aet")
        val tls = booleanPreferencesKey("pacs_use_tls")
        val modality = stringPreferencesKey("modality_code")
        val station = stringPreferencesKey("station_name")
        val hl7Enabled = booleanPreferencesKey("hl7_enabled")
        val hl7Url = stringPreferencesKey("hl7_base_url")
        val hl7Token = stringPreferencesKey("hl7_bearer_token")
        val loggingEnabled = booleanPreferencesKey("logging_enabled")
    }

    val settings: Flow<PacsSettings> = context.dataStore.data.map { prefs ->
        PacsSettings(
            callingAeTitle = prefs[Keys.calling] ?: BuildConfig.DEFAULT_CALLING_AET,
            modality = prefs[Keys.modality] ?: "XC",
            stationName = prefs[Keys.station].orEmpty(),
            host = prefs[Keys.host] ?: BuildConfig.DEFAULT_PACS_HOST,
            port = prefs[Keys.port] ?: BuildConfig.DEFAULT_PACS_PORT,
            calledAeTitle = prefs[Keys.called] ?: BuildConfig.DEFAULT_CALLED_AET,
            useTls = prefs[Keys.tls] ?: false,
            hl7Enabled = prefs[Keys.hl7Enabled] ?: false,
            hl7BaseUrl = prefs[Keys.hl7Url].orEmpty(),
            hl7BearerToken = prefs[Keys.hl7Token].orEmpty(),
            loggingEnabled = prefs[Keys.loggingEnabled] ?: false,
        )
    }

    suspend fun save(settings: PacsSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.calling] = settings.callingAeTitle.trim()
            prefs[Keys.modality] = settings.modality.trim().ifBlank { "XC" }
            prefs[Keys.station] = settings.stationName.trim()
            prefs[Keys.host] = settings.host.trim()
            prefs[Keys.port] = settings.port
            prefs[Keys.called] = settings.calledAeTitle.trim()
            prefs[Keys.tls] = settings.useTls
            prefs[Keys.hl7Enabled] = settings.hl7Enabled
            prefs[Keys.hl7Url] = settings.hl7BaseUrl.trim()
            prefs[Keys.hl7Token] = settings.hl7BearerToken.trim()
            prefs[Keys.loggingEnabled] = settings.loggingEnabled
        }
    }
}
