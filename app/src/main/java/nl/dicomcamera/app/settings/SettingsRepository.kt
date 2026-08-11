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

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dicomcamera_settings")

data class PacsSettings(
    val transportMode: TransportMode = TransportMode.DIMSE,
    val host: String = BuildConfig.DEFAULT_PACS_HOST,
    val port: Int = BuildConfig.DEFAULT_PACS_PORT,
    val calledAeTitle: String = BuildConfig.DEFAULT_CALLED_AET,
    val callingAeTitle: String = BuildConfig.DEFAULT_CALLING_AET,
    val useTls: Boolean = false,
    val dicomWebBaseUrl: String = BuildConfig.DEFAULT_DICOMWEB_URL,
    val modality: String = "XC",
    val stationName: String = "",
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

    fun isConfigured(): Boolean = toEndpoint().isConfigured()
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
    }

    val settings: Flow<PacsSettings> = context.dataStore.data.map { prefs ->
        val local = PacsSettings(
            transportMode = prefs[Keys.transport]?.let {
                runCatching { TransportMode.valueOf(it) }.getOrDefault(TransportMode.DIMSE)
            } ?: TransportMode.DIMSE,
            host = prefs[Keys.host] ?: BuildConfig.DEFAULT_PACS_HOST,
            port = prefs[Keys.port] ?: BuildConfig.DEFAULT_PACS_PORT,
            calledAeTitle = prefs[Keys.called] ?: BuildConfig.DEFAULT_CALLED_AET,
            callingAeTitle = prefs[Keys.calling] ?: BuildConfig.DEFAULT_CALLING_AET,
            useTls = prefs[Keys.tls] ?: false,
            dicomWebBaseUrl = prefs[Keys.webUrl] ?: BuildConfig.DEFAULT_DICOMWEB_URL,
            modality = prefs[Keys.modality] ?: "XC",
            stationName = prefs[Keys.station].orEmpty(),
        )
        ManagedConfig.merge(context, local)
    }

    suspend fun save(settings: PacsSettings) {
        if (ManagedConfig.isManaged(context)) {
            return
        }
        context.dataStore.edit { prefs ->
            prefs[Keys.transport] = settings.transportMode.name
            prefs[Keys.host] = settings.host.trim()
            prefs[Keys.port] = settings.port
            prefs[Keys.called] = settings.calledAeTitle.trim()
            prefs[Keys.calling] = settings.callingAeTitle.trim()
            prefs[Keys.tls] = settings.useTls
            prefs[Keys.webUrl] = settings.dicomWebBaseUrl.trim()
            prefs[Keys.modality] = settings.modality.trim().ifBlank { "XC" }
            prefs[Keys.station] = settings.stationName.trim()
        }
    }
}
