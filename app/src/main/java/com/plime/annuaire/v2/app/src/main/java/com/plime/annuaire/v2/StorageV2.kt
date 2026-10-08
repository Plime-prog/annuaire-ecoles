package com.plime.annuaire.v2

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stockage privé et chiffré de l'annuaire V2.
 * Les données sont conservées dans un fichier de préférences chiffré,
 * séparé de celui de la V1 (aucune interférence entre les deux versions).
 */
object StorageV2 {

    private const val PREFS_FILE = "annuaire_v2_secure"
    private const val KEY_DATA = "annuaire_json"
    private const val KEY_PIN_HASH = "pin_hash_v2"
    private const val KEY_UNLOCKED = "unlocked_v2"

    private var prefs: SharedPreferences? = null

    private fun getPrefs(ctx: Context): SharedPreferences {
        if (prefs == null) {
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            prefs = EncryptedSharedPreferences.create(
                ctx,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
        return prefs!!
    }

    /* ---------- Annuaire ---------- */

    fun save(ctx: Context, a: AnnuaireV2) {
        getPrefs(ctx).edit().putString(KEY_DATA, a.toJson().toString()).apply()
    }

    fun load(ctx: Context): AnnuaireV2 {
        val s = getPrefs(ctx).getString(KEY_DATA, null) ?: return AnnuaireV2()
        return try {
            annuaireFromJson(s)
        } catch (e: Exception) {
            AnnuaireV2()
        }
    }

    fun clearData(ctx: Context) {
        getPrefs(ctx).edit().remove(KEY_DATA).apply()
    }

    /* ---------- Code PIN ---------- */

    private fun hash(pin: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256")
            .digest((pin + "annuaire-v2-sel").toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun setPin(ctx: Context, pin: String) {
        getPrefs(ctx).edit().putString(KEY_PIN_HASH, hash(pin)).apply()
    }

    fun hasPin(ctx: Context): Boolean =
        !getPrefs(ctx).getString(KEY_PIN_HASH, null).isNullOrBlank()

    fun checkPin(ctx: Context, pin: String): Boolean =
        getPrefs(ctx).getString(KEY_PIN_HASH, null) == hash(pin)

    /* ---------- Verrouillage de session ---------- */

    fun setUnlocked(ctx: Context, unlocked: Boolean) {
        getPrefs(ctx).edit().putBoolean(KEY_UNLOCKED, unlocked).apply()
    }

    fun isUnlocked(ctx: Context): Boolean =
        getPrefs(ctx).getBoolean(KEY_UNLOCKED, false)
}
