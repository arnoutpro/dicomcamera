package nl.dicomcamera.app.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * At-rest storage for HL7 / FHIR bearer tokens using EncryptedSharedPreferences.
 * Tokens are never written to the plaintext DataStore preferences file.
 */
internal object EncryptedTokenStore {
    private const val TAG = "EncryptedTokenStore"
    private const val PREFS_NAME = "dicomcamera_secrets"
    private const val KEY_HL7 = "hl7_bearer_token"
    private const val KEY_FHIR = "fhir_bearer_token"

    fun readHl7(context: Context): String = prefs(context).getString(KEY_HL7, "").orEmpty()

    fun readFhir(context: Context): String = prefs(context).getString(KEY_FHIR, "").orEmpty()

    fun write(context: Context, hl7: String, fhir: String) {
        prefs(context).edit()
            .putString(KEY_HL7, hl7.trim())
            .putString(KEY_FHIR, fhir.trim())
            .apply()
    }

    private fun prefs(context: Context): SharedPreferences {
        return try {
            createPrefs(context)
        } catch (e: Exception) {
            // Keystore/keyset mismatch (e.g. AEADBadTagException) would otherwise crash
            // Settings on every launch. Wipe the corrupt secrets file and recreate empty.
            Log.e(TAG, "Encrypted token store unreadable; resetting", e)
            runCatching { context.deleteSharedPreferences(PREFS_NAME) }
            createPrefs(context)
        }
    }

    private fun createPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
