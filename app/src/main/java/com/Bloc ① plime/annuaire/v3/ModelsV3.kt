package com.plime.annuaire.v3

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/* ==================== MODÈLE DYNAMIQUE V3 ==================== */

/** Définition d'un champ (colonne). La clé est stable, le libellé est modifiable. */
data class FieldV3(
    val key: String,
    var label: String,
    var multiline: Boolean = false
)

/** Un onglet = une table : liste de champs + lignes (clé -> valeur). */
data class TabV3(
    val id: String,
    var name: String,
    var groupBy: String = "",        // clé du champ de regroupement ("" = pas de groupes)
    val fields: MutableList<FieldV3> = mutableListOf(),
    val rows: MutableList<MutableMap<String, String>> = mutableListOf()
)

data class AnnuaireV3(
    val tabs: MutableList<TabV3> = mutableListOf()
)

fun newKeyV3(): String = UUID.randomUUID().toString().substring(0, 8)

/* ==================== SÉRIALISATION JSON ==================== */

private fun JSONObject.str(key: String): String =
    if (has(key) && !isNull(key)) getString(key) else ""

fun FieldV3.toJson(): JSONObject = JSONObject().apply {
    put("key", key); put("label", label); put("multiline", multiline)
}

fun fieldFromJsonV3(o: JSONObject): FieldV3 = FieldV3(
    key = o.str("key").ifEmpty { newKeyV3() },
    label = o.str("label"),
    multiline = o.optBoolean("multiline", false)
)

fun TabV3.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("name", name); put("groupBy", groupBy)
    put("fields", JSONArray().apply { fields.forEach { put(it.toJson()) } })
    put("rows", JSONArray().apply { rows.forEach { put(JSONObject(it as Map<*, *>)) } })
}

fun tabFromJsonV3(o: JSONObject): TabV3 {
    val t = TabV3(id = o.str("id").ifEmpty { newKeyV3() }, name = o.str("name"), groupBy = o.str("groupBy"))
    o.optJSONArray("fields")?.let { a -> for (i in 0 until a.length()) t.fields.add(fieldFromJsonV3(a.getJSONObject(i))) }
    o.optJSONArray("rows")?.let { a ->
        for (i in 0 until a.length()) {
            val r = a.getJSONObject(i)
            val m = mutableMapOf<String, String>()
            for (k in r.keys()) m[k] = r.str(k)
            if (!m.containsKey("_id")) m["_id"] = newKeyV3()
            t.rows.add(m)
        }
    }
    return t
}

fun AnnuaireV3.toJson(): JSONObject = JSONObject().apply {
    put("tabs", JSONArray().apply { tabs.forEach { put(it.toJson()) } })
}

fun annuaireV3FromJson(s: String): AnnuaireV3 {
    val a = AnnuaireV3()
    val o = JSONObject(s)
    o.optJSONArray("tabs")?.let { arr -> for (i in 0 until arr.length()) a.tabs.add(tabFromJsonV3(arr.getJSONObject(i))) }
    return a
}

/* ==================== UTILITAIRES ==================== */

/** Normalise pour recherche : minuscules sans accents. */
fun normV3(s: String): String =
    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()

/** Titre d'une ligne = première valeur non vide selon l'ordre des champs. */
fun rowTitleV3(tab: TabV3, row: Map<String, String>): String =
    tab.fields.firstNotNullOfOrNull { row[it.key]?.takeIf { v -> v.isNotBlank() } }
        ?: "(sans titre)"

/** Sous-titre = les 2 valeurs non vides suivantes. */
fun rowSubtitleV3(tab: TabV3, row: Map<String, String>): String {
    val vals = tab.fields.mapNotNull { row[it.key]?.takeIf { v -> v.isNotBlank() } }
    return if (vals.size > 1) vals.drop(1).take(2).joinToString(" · ") else ""
}

/* ==================== SCHÉMA PAR DÉFAUT (identique V2) ==================== */

private fun fld(label: String): FieldV3 =
    FieldV3(newKeyV3(), label, multiline = label.contains("Observation") || label.contains("Adresse") || label.contains("Horaires"))

fun defaultSchemaV3(): AnnuaireV3 {
    val communes = TabV3(newKeyV3(), "Communes").apply {
        listOf(
            "Commune", "Maire", "Tél. maire", "Adresse mairie", "Mail",
            "Contact élu", "Tél. contact élu", "Contact service", "Tél. contact service",
            "Observations", "École", "Code UAI", "Adresse école", "Tél. école", "Mail école",
            "Directeur", "Tél. directeur", "Nb élèves", "Horaires matin",
            "Horaires après-midi", "Observations école", "Professeur", "Classe",
            "Nb élèves (classe)", "Tél. professeur", "Observations prof."
        ).forEach { fields.add(fld(it)) }
        groupBy = fields[0].key
    }
    fun contacts(name: String): TabV3 = TabV3(newKeyV3(), name).apply {
        listOf("Contact", "Fonction", "Adresse", "Mail", "Tél. 1", "Tél. 2", "Observations")
            .forEach { fields.add(fld(it)) }
    }
    val colleges = TabV3(newKeyV3(), "Colleges").apply {
        listOf("Nom", "Adresse", "Contact", "Fonction contact", "Adresse contact",
            "Mail contact", "Tél. 1", "Tél. 2", "Observations")
            .forEach { fields.add(fld(it)) }
    }
    return AnnuaireV3(mutableListOf(
        communes, contacts("RASED"), contacts("Circonscription"), contacts("PIAL_ER"), colleges
    ))
}

/* ==================== STOCKAGE CHIFFRÉ ==================== */

object StorageV3 {
    private const val PREFS = "annuaire_v3_secure"

    private fun prefs(ctx: Context) = EncryptedSharedPreferences.create(
        ctx, PREFS,
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun load(ctx: Context): AnnuaireV3 {
        val s = prefs(ctx).getString("data", null) ?: return defaultSchemaV3()
        return try { annuaireV3FromJson(s) } catch (e: Exception) { defaultSchemaV3() }
    }

    fun save(ctx: Context, a: AnnuaireV3) {
        prefs(ctx).edit().putString("data", a.toJson().toString()).apply()
    }

    fun hasPin(ctx: Context): Boolean = prefs(ctx).getBoolean("pinDefined", false)

    fun setPin(ctx: Context, pin: String) {
        prefs(ctx).edit().putString("pin", pin).putBoolean("pinDefined", true).apply()
    }

    fun checkPin(ctx: Context, pin: String): Boolean = prefs(ctx).getString("pin", "") == pin

    fun getTitre(ctx: Context): String = prefs(ctx).getString("titre", "Circonscription") ?: "Circonscription"

    fun setTitre(ctx: Context, t: String) {
        prefs(ctx).edit().putString("titre", t.ifBlank { "Circonscription" }).apply()
    }
}
