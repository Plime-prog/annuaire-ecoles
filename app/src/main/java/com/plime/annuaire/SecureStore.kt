package com.plime.annuaire

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Stockage chiffré (AES-256) pour le code PIN. */
class SecureStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        "annuaire_secure",
        masterKey,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var pin: String
        get() = prefs.getString("pin", DEFAULT_PIN) ?: DEFAULT_PIN
        set(value) = prefs.edit().putString("pin", value).apply()

    companion object { const val DEFAULT_PIN = "1234" }
}
