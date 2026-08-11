package nl.dicomcamera.app.settings

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import nl.dicomcamera.dicom.TransportMode
import nl.dicomcamera.identity.IdentityLookupMode

/**
 * Android Managed Configurations (MDM) overlay. When the device is managed,
 * restriction keys override local DataStore settings.
 *
 * Keys match `res/xml/app_restrictions.xml`.
 */
object ManagedConfig {
    const val KEY_TRANSPORT = "pacs_transport"
    const val KEY_HOST = "pacs_host"
    const val KEY_PORT = "pacs_port"
    const val KEY_CALLED_AET = "pacs_called_aet"
    const val KEY_CALLING_AET = "pacs_calling_aet"
    const val KEY_USE_TLS = "pacs_use_tls"
    const val KEY_DICOMWEB_URL = "pacs_dicomweb_url"
    const val KEY_MODALITY = "modality_code"
    const val KEY_STATION = "station_name"
    const val KEY_HL7_ENABLED = "hl7_enabled"
    const val KEY_HL7_URL = "hl7_base_url"
    const val KEY_HL7_TOKEN = "hl7_bearer_token"
    const val KEY_FHIR_ENABLED = "fhir_enabled"
    const val KEY_FHIR_URL = "fhir_base_url"
    const val KEY_FHIR_TOKEN = "fhir_bearer_token"
    const val KEY_IDENTITY_MODE = "identity_lookup_mode"
    const val KEY_ADMIN_LOCKED = "admin_config_locked"

    private val KNOWN_KEYS = setOf(
        KEY_TRANSPORT,
        KEY_HOST,
        KEY_PORT,
        KEY_CALLED_AET,
        KEY_CALLING_AET,
        KEY_USE_TLS,
        KEY_DICOMWEB_URL,
        KEY_MODALITY,
        KEY_STATION,
        KEY_HL7_ENABLED,
        KEY_HL7_URL,
        KEY_HL7_TOKEN,
        KEY_FHIR_ENABLED,
        KEY_FHIR_URL,
        KEY_FHIR_TOKEN,
        KEY_IDENTITY_MODE,
        KEY_ADMIN_LOCKED,
    )

    /**
     * True only when MDM actually pushed one of our restriction keys.
     * An empty/OEM junk restrictions bundle must not block local saves.
     */
    fun isManaged(context: Context): Boolean {
        val bundle = restrictionsBundle(context) ?: return false
        return hasKnownRestriction(bundle)
    }

    fun merge(context: Context, local: PacsSettings): PacsSettings {
        val bundle = restrictionsBundle(context) ?: return local
        if (!hasKnownRestriction(bundle)) return local
        return applyBundle(local, bundle).copy(managedByMdm = true)
    }

    private fun restrictionsBundle(context: Context): Bundle? {
        val rm = context.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager
            ?: return null
        return rm.applicationRestrictions
    }

    internal fun hasKnownRestriction(bundle: Bundle): Boolean {
        if (bundle.isEmpty) return false
        return KNOWN_KEYS.any { key ->
            if (!bundle.containsKey(key)) return@any false
            when (val value = bundle.get(key)) {
                null -> false
                is String -> value.isNotBlank()
                is Boolean, is Int, is Long -> true
                else -> true
            }
        }
    }

    internal fun applyBundle(local: PacsSettings, bundle: Bundle): PacsSettings {
        var next = local
        bundle.getString(KEY_TRANSPORT)?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { TransportMode.valueOf(raw.uppercase()) }.getOrNull()?.let {
                next = next.copy(transportMode = it)
            }
        }
        bundle.getString(KEY_HOST)?.takeIf { it.isNotBlank() }?.let {
            next = next.copy(host = it)
        }
        if (bundle.containsKey(KEY_PORT)) {
            val port = bundle.getInt(KEY_PORT, local.port)
            if (port in 1..65535) next = next.copy(port = port)
        }
        bundle.getString(KEY_CALLED_AET)?.takeIf { it.isNotBlank() }?.let {
            next = next.copy(calledAeTitle = it)
        }
        bundle.getString(KEY_CALLING_AET)?.takeIf { it.isNotBlank() }?.let {
            next = next.copy(callingAeTitle = it)
        }
        if (bundle.containsKey(KEY_USE_TLS)) {
            next = next.copy(useTls = bundle.getBoolean(KEY_USE_TLS))
        }
        bundle.getString(KEY_DICOMWEB_URL)?.takeIf { it.isNotBlank() }?.let {
            next = next.copy(dicomWebBaseUrl = it)
        }
        bundle.getString(KEY_MODALITY)?.takeIf { it.isNotBlank() }?.let {
            next = next.copy(modality = it.trim().uppercase())
        }
        bundle.getString(KEY_STATION)?.let {
            next = next.copy(stationName = it)
        }
        if (bundle.containsKey(KEY_HL7_ENABLED)) {
            next = next.copy(hl7Enabled = bundle.getBoolean(KEY_HL7_ENABLED))
        }
        bundle.getString(KEY_HL7_URL)?.takeIf { it.isNotBlank() }?.let {
            next = next.copy(hl7BaseUrl = it)
        }
        bundle.getString(KEY_HL7_TOKEN)?.let {
            next = next.copy(hl7BearerToken = it)
        }
        if (bundle.containsKey(KEY_FHIR_ENABLED)) {
            next = next.copy(fhirEnabled = bundle.getBoolean(KEY_FHIR_ENABLED))
        }
        bundle.getString(KEY_FHIR_URL)?.takeIf { it.isNotBlank() }?.let {
            next = next.copy(fhirBaseUrl = it)
        }
        bundle.getString(KEY_FHIR_TOKEN)?.let {
            next = next.copy(fhirBearerToken = it)
        }
        bundle.getString(KEY_IDENTITY_MODE)?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { IdentityLookupMode.valueOf(raw.uppercase()) }.getOrNull()?.let {
                next = next.copy(identityLookupMode = it)
            }
        }
        if (bundle.containsKey(KEY_ADMIN_LOCKED)) {
            next = next.copy(adminConfigLocked = bundle.getBoolean(KEY_ADMIN_LOCKED))
        }
        return next
    }
}
