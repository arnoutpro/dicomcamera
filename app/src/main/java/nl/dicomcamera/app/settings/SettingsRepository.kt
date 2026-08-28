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
import nl.dicomcamera.identity.FhirConfig
import nl.dicomcamera.identity.Hl7FacadeConfig
import nl.dicomcamera.identity.IdentityLookupMode

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
    /** Remote archive host for DIMSE C-STORE / C-ECHO / Study FIND. */
    val host: String = BuildConfig.DEFAULT_PACS_HOST,
    val port: Int = BuildConfig.DEFAULT_PACS_PORT,
    /** Remote / called AE Title (archive). */
    val calledAeTitle: String = BuildConfig.DEFAULT_CALLED_AET,
    val useTls: Boolean = false,
    /** DICOMweb root URL (QIDO-RS / STOW-RS). */
    val dicomWebBaseUrl: String = BuildConfig.DEFAULT_DICOMWEB_URL,
    /**
     * Dedicated MWL SCP. Empty host + called AE → fall back to archive DIMSE.
     * Calling AE is always the Local AE ([callingAeTitle]).
     */
    val mwlHost: String = "",
    val mwlPort: Int = 11112,
    val mwlCalledAeTitle: String = "",
    val mwlUseTls: Boolean = false,
    /** HL7 demographics façade. */
    val hl7Enabled: Boolean = false,
    val hl7BaseUrl: String = "",
    val hl7BearerToken: String = "",
    /** FHIR R4 Patient / order gateway. */
    val fhirEnabled: Boolean = false,
    val fhirBaseUrl: String = "",
    val fhirBearerToken: String = "",
    /** Which EHR adapters to try for demographics. */
    val identityLookupMode: IdentityLookupMode = IdentityLookupMode.FHIR_THEN_HL7,
    /**
     * When true (MDM), Settings edits are locked for operators.
     * Local DataStore still holds the values; MDM overlay applies PACS/EHR keys.
     */
    val adminConfigLocked: Boolean = false,
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
        mwlHost = mwlHost.trim(),
        mwlPort = mwlPort,
        mwlCalledAeTitle = mwlCalledAeTitle.trim(),
        mwlUseTls = mwlUseTls,
    )

    fun isMwlConfigured(): Boolean = toEndpoint().isMwlConfigured()

    fun copyArchiveDimseToMwl(): PacsSettings = copy(
        mwlHost = host.trim(),
        mwlPort = port,
        mwlCalledAeTitle = calledAeTitle.trim(),
        mwlUseTls = useTls,
    )

    fun toHl7Config(): Hl7FacadeConfig = Hl7FacadeConfig(
        enabled = hl7Enabled,
        baseUrl = hl7BaseUrl.trim(),
        bearerToken = hl7BearerToken.trim(),
    )

    fun toFhirConfig(): FhirConfig = FhirConfig(
        enabled = fhirEnabled,
        baseUrl = fhirBaseUrl.trim(),
        bearerToken = fhirBearerToken.trim(),
    )

    fun isConfigured(): Boolean = toEndpoint().isConfigured()

    fun settingsEditable(): Boolean = !managedByMdm && !adminConfigLocked

    fun localSummary(): String =
        listOfNotNull(
            callingAeTitle.trim().ifBlank { null },
            modality.trim().ifBlank { null },
            stationName.trim().ifBlank { null },
        ).joinToString(" · ").ifBlank { "Not set" }

    fun transportSummary(): String = when (transportMode) {
        TransportMode.DIMSE -> "DIMSE (C-STORE / C-FIND)"
        TransportMode.DICOMWEB -> "DICOMweb (STOW / QIDO)"
    }

    fun remoteSummary(): String =
        when (transportMode) {
            TransportMode.DIMSE -> formatDimseSummary(host, port, calledAeTitle, useTls)
            TransportMode.DICOMWEB ->
                dicomWebBaseUrl.trim().ifBlank { "DICOMweb URL not set" }
        }

    fun mwlSummary(): String {
        val endpoint = toEndpoint()
        return when {
            endpoint.hasDedicatedMwl() && endpoint.toMwlNode() != null ->
                formatDimseSummary(mwlHost, mwlPort, mwlCalledAeTitle, mwlUseTls)
            endpoint.hasDedicatedMwl() ->
                "MWL incomplete — fill host, port, and called AE"
            endpoint.resolveMwlNode() != null ->
                "Uses archive DIMSE · ${formatDimseSummary(host, port, calledAeTitle, useTls)}"
            else -> "Not configured"
        }
    }

    fun hl7Summary(): String = toHl7Config().summary()

    fun fhirSummary(): String = toFhirConfig().summary()

    /** True when an enabled EHR façade URL is cleartext HTTP (bearer would travel unprotected). */
    fun ehrUsesCleartextHttp(): Boolean =
        (hl7Enabled && hl7BaseUrl.trim().startsWith("http://")) ||
            (fhirEnabled && fhirBaseUrl.trim().startsWith("http://"))

    fun identitySummary(): String {
        val parts = mutableListOf<String>()
        if (toFhirConfig().isConfigured()) parts += "FHIR"
        if (toHl7Config().isConfigured()) parts += "HL7"
        return if (parts.isEmpty()) {
            "No EHR lookup configured"
        } else {
            "${parts.joinToString(" + ")} · ${identityLookupMode.name.replace('_', ' ').lowercase()}"
        }
    }

    fun loggingSummary(): String =
        if (loggingEnabled) "Enabled — export from Logging" else "Off (manual activation)"
}

private fun formatDimseSummary(
    host: String,
    port: Int,
    calledAeTitle: String,
    useTls: Boolean,
): String = when {
    host.isBlank() -> "Not configured"
    else -> listOf(
        host.trim(),
        port.toString(),
        calledAeTitle.trim().ifBlank { "AE?" },
        if (useTls) "TLS" else "plain",
    ).joinToString(" · ")
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
        val mwlHost = stringPreferencesKey("mwl_host")
        val mwlPort = intPreferencesKey("mwl_port")
        val mwlCalled = stringPreferencesKey("mwl_called_aet")
        val mwlTls = booleanPreferencesKey("mwl_use_tls")
        val modality = stringPreferencesKey("modality_code")
        val station = stringPreferencesKey("station_name")
        val hl7Enabled = booleanPreferencesKey("hl7_enabled")
        val hl7Url = stringPreferencesKey("hl7_base_url")
        /** @deprecated Tokens moved to [EncryptedTokenStore]; kept only for one-shot migration. */
        val hl7Token = stringPreferencesKey("hl7_bearer_token")
        val fhirEnabled = booleanPreferencesKey("fhir_enabled")
        val fhirUrl = stringPreferencesKey("fhir_base_url")
        /** @deprecated Tokens moved to [EncryptedTokenStore]; kept only for one-shot migration. */
        val fhirToken = stringPreferencesKey("fhir_bearer_token")
        val identityMode = stringPreferencesKey("identity_lookup_mode")
        val adminLocked = booleanPreferencesKey("admin_config_locked")
        val loggingEnabled = booleanPreferencesKey("logging_enabled")
    }

    val settings: Flow<PacsSettings> = context.dataStore.data.map { prefs ->
        val encryptedHl7 = EncryptedTokenStore.readHl7(context)
        val encryptedFhir = EncryptedTokenStore.readFhir(context)
        // Prefer encrypted store; fall back to legacy plaintext keys until migrateLegacyTokens().
        val hl7Token = encryptedHl7.ifEmpty { prefs[Keys.hl7Token].orEmpty() }
        val fhirToken = encryptedFhir.ifEmpty { prefs[Keys.fhirToken].orEmpty() }
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
            mwlHost = prefs[Keys.mwlHost].orEmpty(),
            mwlPort = prefs[Keys.mwlPort] ?: 11112,
            mwlCalledAeTitle = prefs[Keys.mwlCalled].orEmpty(),
            mwlUseTls = prefs[Keys.mwlTls] ?: false,
            hl7Enabled = prefs[Keys.hl7Enabled] ?: false,
            hl7BaseUrl = prefs[Keys.hl7Url].orEmpty(),
            hl7BearerToken = hl7Token,
            fhirEnabled = prefs[Keys.fhirEnabled] ?: false,
            fhirBaseUrl = prefs[Keys.fhirUrl].orEmpty(),
            fhirBearerToken = fhirToken,
            identityLookupMode = prefs[Keys.identityMode]?.let {
                runCatching { IdentityLookupMode.valueOf(it) }.getOrDefault(IdentityLookupMode.FHIR_THEN_HL7)
            } ?: IdentityLookupMode.FHIR_THEN_HL7,
            adminConfigLocked = prefs[Keys.adminLocked] ?: false,
            loggingEnabled = prefs[Keys.loggingEnabled] ?: false,
        )
        ManagedConfig.merge(context, local)
    }

    /**
     * One-shot: move bearer tokens from plaintext DataStore into EncryptedSharedPreferences
     * and delete the legacy keys. Safe to call on every launch.
     */
    suspend fun migrateLegacyTokens() {
        context.dataStore.edit { prefs ->
            val legacyHl7 = prefs[Keys.hl7Token].orEmpty()
            val legacyFhir = prefs[Keys.fhirToken].orEmpty()
            if (legacyHl7.isEmpty() && legacyFhir.isEmpty()) return@edit
            val hl7 = EncryptedTokenStore.readHl7(context).ifEmpty { legacyHl7 }
            val fhir = EncryptedTokenStore.readFhir(context).ifEmpty { legacyFhir }
            EncryptedTokenStore.write(context, hl7, fhir)
            prefs.remove(Keys.hl7Token)
            prefs.remove(Keys.fhirToken)
        }
    }

    /**
     * Persist settings. Returns false when MDM owns config and local save is skipped.
     */
    suspend fun save(settings: PacsSettings): Boolean {
        if (ManagedConfig.isManaged(context)) {
            return false
        }
        EncryptedTokenStore.write(
            context,
            hl7 = settings.hl7BearerToken,
            fhir = settings.fhirBearerToken,
        )
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
            prefs[Keys.mwlHost] = settings.mwlHost.trim()
            prefs[Keys.mwlPort] = settings.mwlPort
            prefs[Keys.mwlCalled] = settings.mwlCalledAeTitle.trim()
            prefs[Keys.mwlTls] = settings.mwlUseTls
            prefs[Keys.hl7Enabled] = settings.hl7Enabled
            prefs[Keys.hl7Url] = settings.hl7BaseUrl.trim()
            prefs.remove(Keys.hl7Token)
            prefs[Keys.fhirEnabled] = settings.fhirEnabled
            prefs[Keys.fhirUrl] = settings.fhirBaseUrl.trim()
            prefs.remove(Keys.fhirToken)
            prefs[Keys.identityMode] = settings.identityLookupMode.name
            prefs[Keys.adminLocked] = settings.adminConfigLocked
            prefs[Keys.loggingEnabled] = settings.loggingEnabled
        }
        return true
    }
}
