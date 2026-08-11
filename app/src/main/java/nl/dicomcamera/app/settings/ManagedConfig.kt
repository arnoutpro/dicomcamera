package nl.dicomcamera.app.settings

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import nl.dicomcamera.dicom.TransportMode

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

    fun isManaged(context: Context): Boolean {
        val rm = context.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager
            ?: return false
        val bundle = rm.applicationRestrictions ?: return false
        return !bundle.isEmpty
    }

    fun merge(context: Context, local: PacsSettings): PacsSettings {
        val rm = context.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager
            ?: return local
        val bundle = rm.applicationRestrictions ?: return local
        if (bundle.isEmpty) return local
        return applyBundle(local, bundle).copy(managedByMdm = true)
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
        return next
    }
}
