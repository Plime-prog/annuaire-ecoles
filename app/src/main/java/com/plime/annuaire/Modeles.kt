package com.plime.annuaire

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/* ==================== MODÃLE DYNAMIQUE ==================== */

/** DÃ©finition d'un champ (colonne). La clÃ© est stable, le libellÃ© est modifiable. */
data class Champ(
    val key: String,
    var label: String,
    var multiline: Boolean = false
)

/** Un onglet = une table : liste de champs + lignes (clÃ© -> valeur). */
data class Onglet(
    val id: String,
    var name: String,
    var groupBy: String = "",        // clÃ© du champ de regroupement ("" = pas de groupes)
    val fields: MutableList<Champ> = mutableListOf(),
    val rows: MutableList<MutableMap<String, String>> = mutableListOf()
)

data class Annuaire(
    val tabs: MutableList<Onglet> = mutableListOf()
)

fun nouvelleCle(): String = UUID.randomUUID().toString().substring(0, 8)

/* ==================== SÃRIALISATION JSON ==================== */

private fun JSONObject.str(key: String): String =
    if (has(key) && !isNull(key)) getString(key) else ""

fun Champ.toJson(): JSONObject = JSONObject().apply {
    put("key", key); put("label", label); put("multiline", multiline)
}

private fun champDepuisJson(o: JSONObject): Champ = Champ(
    key = o.str("key").ifEmpty { nouvelleCle() },
    label = o.str("label"),
    multiline = o.optBoolean("multiline", false)
)

fun Onglet.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("name", name); put("groupBy", groupBy)
    put("fields", JSONArray().apply { fields.forEach { put(it.toJson()) } })
    put("rows", JSONArray().apply { rows.forEach { put(JSONObject(it as Map<*, *>)) } })
}

private fun ongletDepuisJson(o: JSONObject): Onglet {
    val t = Onglet(id = o.str("id").ifEmpty { nouvelleCle() }, name = o.str("name"), groupBy = o.str("groupBy"))
    o.optJSONArray("fields")?.let { a -> for (i in 0 until a.length()) t.fields.add(champDepuisJson(a.getJSONObject(i))) }
    o.optJSONArray("rows")?.let { a ->
        for (i in 0 until a.length()) {
            val r = a.getJSONObject(i)
            val m = mutableMapOf<String, String>()
            for (k in r.keys()) m[k] = r.str(k)
            if (!m.containsKey("_id")) m["_id"] = nouvelleCle()
            t.rows.add(m)
        }
    }
    return t
}

fun Annuaire.toJson(): JSONObject = JSONObject().apply {
    put("tabs", JSONArray().apply { tabs.forEach { put(it.toJson()) } })
}

fun annuaireDepuisJson(s: String): Annuaire {
    val a = Annuaire()
    val o = JSONObject(s)
    o.optJSONArray("tabs")?.let { arr -> for (i in 0 until arr.length()) a.tabs.add(ongletDepuisJson(arr.getJSONObject(i))) }
    return a
}

/* ==================== UTILITAIRES ==================== */

/** Normalise pour recherche : minuscules sans accents. */
fun norme(s: String): String =
    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()

/** Titre d'une ligne = premiÃ¨re valeur non vide selon l'ordre des champs. */
fun titreLigne(tab: Onglet, row: Map<String, String>): String =
    tab.fields.firstNotNullOfOrNull { row[it.key]?.takeIf { v -> v.isNotBlank() } }
        ?: "(sans titre)"

/** Sous-titre = les 2 valeurs non vides suivantes. */
fun sousTitreLigne(tab: Onglet, row: Map<String, String>): String {
    val vals = tab.fields.mapNotNull { row[it.key]?.takeIf { v -> v.isNotBlank() } }
    return if (vals.size > 1) vals.drop(1).take(2).joinToString(" Â· ") else ""
}

/* ==================== SCHÃMA PAR DÃFAUT ==================== */

private fun fld(label: String): Champ =
    Champ(nouvelleCle(), label, multiline = label.contains("Observation") || label.contains("Adresse") || label.contains("Horaires"))

fun schemaParDefaut(): Annuaire {
    val communes = Onglet(nouvelleCle(), "Communes").apply {
        /* Structure en dur de l'onglet Communes :
           mairie et Ã©lu de la commune, puis l'Ã©cole et sa fiche complÃ¨te,
           puis la dÃ©charge et les horaires. */
        listOf(
            "Commune", "Adresse Mairie", "TÃ©l. Mairie",
            "Maire", "TÃ©l. Maire", "Mail Maire",
            "Contact Ãlu", "Fonction Contact Ãlu", "TÃ©l. Ãlu", "Mail Ãlu",
            "Observations",
            "Ãcole", "Code UAI", "Statut", "Adresse Ã©cole", "TÃ©l. Ã©cole", "Mail Ã©cole",
            "Directeur", "TÃ©l. Directeur", "Mail Directeur",
            "DÃ©charge", "Matin", "AprÃ¨s-midi",
            "Observations"
        ).forEach { fields.add(fld(it)) }
        groupBy = fields[0].key
    }
    val circo = Onglet(nouvelleCle(), "Circonscription").apply {
        /* Liste des personnes : IEN, CPC, assistantes, ERUNâ¦ */
        listOf("Contact", "Fonction", "Adresse", "Mail", "TÃ©l. 1", "TÃ©l. 2",
            "Observations", "Circonscription Ã©largie")
            .forEach { fields.add(fld(it)) }
        groupBy = ""   /* liste Ã  plat des personnes, pas de regroupement */
    }
    val colleges = Onglet(nouvelleCle(), "CollÃ¨ges").apply {
        listOf("Nom", "Adresse", "Contact", "Fonction contact", "Adresse contact",
            "Mail contact", "TÃ©l. 1", "TÃ©l. 2", "Observations")
            .forEach { fields.add(fld(it)) }
        groupBy = fields[0].key   /* Â« Nom Â» = nom du collÃ¨ge */
    }
    return Annuaire(mutableListOf(
        communes, circo, colleges
    ))
}

/* ==================== STOCKAGE CHIFFRÃ ==================== */

object Stockage {
    /* Nom du fichier de prÃ©fÃ©rences conservÃ© tel quel : les donnÃ©es
       dÃ©jÃ  enregistrÃ©es sur les appareils restent lisibles. */
    private const val PREFS = "annuaire_v3_secure"

    private fun prefs(ctx: Context) = EncryptedSharedPreferences.create(
        ctx, PREFS,
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun load(ctx: Context): Annuaire {
        val s = prefs(ctx).getString("data", null) ?: return schemaParDefaut()
        return try { annuaireDepuisJson(s) } catch (e: Exception) { schemaParDefaut() }
    }

    fun save(ctx: Context, a: Annuaire) {
        prefs(ctx).edit().putString("data", a.toJson().toString()).apply()
    }

    fun hasPin(ctx: Context): Boolean = prefs(ctx).getBoolean("pinDefined", false)

    fun setPin(ctx: Context, pin: String) {
        prefs(ctx).edit().putString("pin", pin).putBoolean("pinDefined", true).apply()
    }

    fun checkPin(ctx: Context, pin: String): Boolean = prefs(ctx).getString("pin", "") == pin

    /* ==================== FAVORI ==================== */

    /** Fiche favorite : Â« idOnglet|idLigne Â», vide si aucun. */
    fun getFavori(ctx: Context): String = prefs(ctx).getString("favori", "") ?: ""

    fun setFavori(ctx: Context, tabId: String, rowId: String) {
        prefs(ctx).edit().putString("favori", "$tabId|$rowId").apply()
    }

    fun clearFavori(ctx: Context) {
        prefs(ctx).edit().remove("favori").apply()
    }
}

/* ==================== DONNÃES OFFICIELLES (DSDEN, CIRCONSCRIPTIONS) ==================== */

data class Contact(
    val nom: String,
    val fonction: String = "",
    val tel: String = "",
    val mail: String = "",
    val note: String = ""
)

data class Bureau(val nom: String, val membres: List<Contact>)

data class Dsden(
    val coordonnees: Contact = Contact(""),
    val direction: List<Contact> = emptyList(),
    val secretariat: List<Contact> = emptyList(),
    val bureaux: List<Bureau> = emptyList()
)

data class Circo(
    val code: String,
    val nom: String,
    val ien: String = "",
    val adresse: String = "",
    val tel: String = "",
    val mail: String = "",
    val cpc: List<Contact> = emptyList(),
    val erun: List<Contact> = emptyList(),
    val assistante: String = "",
    val rased: List<Contact> = emptyList(),
    val pialEr: List<Contact> = emptyList(),
    val colleges: List<String> = emptyList()
)

private fun JSONArray.contacts(): List<Contact> =
    (0 until length()).map { getJSONObject(it) }.map { o ->
        Contact(
            nom = o.str("nom"), fonction = o.str("fonction"),
            tel = o.str("tel"), mail = o.str("mail"), note = o.str("note")
        )
    }.filter { it.nom.isNotBlank() }

/** Lit assets/aube-dsden.json. Retourne null si le fichier est absent. */
fun chargerDsden(ctx: Context): Dsden? = try {
    val s = ctx.assets.open("aube-dsden.json").bufferedReader().use { it.readText() }
    val o = JSONObject(s)
    val co = o.optJSONObject("coordonnees")
    Dsden(
        coordonnees = if (co != null) Contact(
            co.str("nom"), co.str("adresse"), co.str("tel"), co.str("mail")
        ) else Contact(""),
        direction = o.optJSONArray("direction")?.contacts() ?: emptyList(),
        secretariat = o.optJSONArray("secretariat")?.contacts() ?: emptyList(),
        bureaux = (0 until (o.optJSONArray("bureaux")?.length() ?: 0)).map { i ->
            val b = o.getJSONArray("bureaux").getJSONObject(i)
            Bureau(b.str("nom"), b.optJSONArray("membres")?.contacts() ?: emptyList())
        }
    )
} catch (e: Exception) { null }

/** Lit assets/aube-circonscriptions.json. Retourne une liste vide si absent. */
fun chargerCircos(ctx: Context): List<Circo> = try {
    val s = ctx.assets.open("aube-circonscriptions.json").bufferedReader().use { it.readText() }
    val o = JSONObject(s).optJSONObject("circonscriptions") ?: return emptyList()
    o.keys().asSequence().map { code ->
        val c = o.getJSONObject(code)
        Circo(
            code = code,
            nom = c.str("nom"), ien = c.str("ien"),
            adresse = c.str("adresse"), tel = c.str("tel"), mail = c.str("mail"),
            cpc = c.optJSONArray("cpc")?.contacts() ?: emptyList(),
            erun = c.optJSONArray("erun")?.contacts() ?: emptyList(),
            assistante = c.str("assistante"),
            rased = c.optJSONArray("rased")?.contacts() ?: emptyList(),
            pialEr = c.optJSONArray("pial_er")?.contacts() ?: emptyList(),
            colleges = (0 until (c.optJSONArray("colleges")?.length() ?: 0))
                .map { c.getJSONArray("colleges").getString(it) }
        )
    }.sortedBy { it.nom }.toList()
} catch (e: Exception) { emptyList() }

/* ==================== ÃCOLE â CIRCONSCRIPTION ==================== */

private val RE_CIRCO = Regex("1er degrÃ© de (.+?) â")

/** Nom de circonscription Ã©crit dans une ligne de l'onglet Communes, sinon "". */
fun nomCircoDeLigne(row: Map<String, String>): String =
    row.values.firstNotNullOfOrNull { v ->
        RE_CIRCO.find(v)?.groupValues?.get(1)?.trim()
    } ?: ""

/** Associe le nom trouvÃ© dans l'Excel au code UAI de la circonscription. */
fun codeCirco(nomCirco: String): String {
    val n = nomCirco.lowercase()
    return when {
        "chapelle" in n -> "0100981M"
        "julien" in n -> "0100043T"
        "aube" in n && "bar" in n -> "0100045V"
        "seine" in n && "bar" in n -> "0100820M"
        "romilly" in n -> "0100044U"
        else -> "0100041R"   /* Troyes (et ASH par dÃ©faut) */
    }
}

/** Code circonscription d'une ligne d'Ã©cole, ou "" si non renseignÃ©. */
fun circoDeLigne(row: Map<String, String>): String {
    val nom = nomCircoDeLigne(row)
    return if (nom.isEmpty()) "" else codeCirco(nom)
}

/* ==================== ONGLETS DSDEN / CIRCONSCRIPTIONS (Ã©ditables) ==================== */

/* Les identifiants Â« v5_... Â» sont conservÃ©s tels quels : ils sont dÃ©jÃ 
   enregistrÃ©s dans les donnÃ©es des appareils, les changer casserait la
   correspondance avec les fiches existantes. */

private fun cle(s: String): String =
    s.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

/** Champs d'une fiche personne (le ð§ª permet d'en ajouter : TÃ©l. 3â¦). */
private fun champsPersonne(): MutableList<Champ> = mutableListOf(
    Champ("contact", "Contact"),
    Champ("fonction", "Fonction"),
    Champ("tel1", "TÃ©l. 1"),
    Champ("tel2", "TÃ©l. 2"),
    Champ("mail", "Mail"),
    Champ("obs", "Observations", multiline = true)
)

private fun lignePersonne(
    nom: String, fonction: String = "", tel1: String = "",
    tel2: String = "", mail: String = "", obs: String = ""
): MutableMap<String, String> {
    val m = mutableMapOf<String, String>()
    m["_id"] = nouvelleCle()
    m["contact"] = nom; m["fonction"] = fonction; m["tel1"] = tel1
    m["tel2"] = tel2; m["mail"] = mail; m["obs"] = obs
    return m
}

/**
 * GÃ©nÃ¨re les onglets DSDEN et circonscriptions depuis les JSON des assets.
 * Identifiants stables : v5_dsden_<bureau> et v5_circo_<code>_<Ã©quipe/rased/pial/colleges>.
 */
fun genererOnglets(ctx: Context): List<Onglet> {
    val out = mutableListOf<Onglet>()
    val d = chargerDsden(ctx)
    if (d != null) {
        val direction = Onglet("v5_dsden_direction", "Direction")
        direction.fields.addAll(champsPersonne())
        (d.direction + d.secretariat).forEach { c ->
            direction.rows.add(lignePersonne(c.nom, c.fonction, c.tel, mail = c.mail))
        }
        out.add(direction)
        d.bureaux.forEach { b ->
            val t = Onglet("v5_dsden_" + cle(b.nom), b.nom)
            t.fields.addAll(champsPersonne())
            b.membres.forEach { c ->
                t.rows.add(lignePersonne(c.nom, c.fonction, c.tel, mail = c.mail))
            }
            out.add(t)
        }
    }
    chargerCircos(ctx).forEach { cir ->
        val equipe = Onglet("v5_circo_" + cir.code + "_equipe", "Ãquipe")
        equipe.fields.addAll(champsPersonne())
        if (cir.ien.isNotBlank())
            equipe.rows.add(lignePersonne(cir.ien, "Inspecteur de l'Ãducation nationale", cir.tel, mail = cir.mail, obs = cir.adresse))
        if (cir.assistante.isNotBlank())
            equipe.rows.add(lignePersonne(cir.assistante, "Assistante de circonscription"))
        cir.cpc.forEach { equipe.rows.add(lignePersonne(it.nom, "CPC", it.tel, mail = it.mail)) }
        cir.erun.forEach { equipe.rows.add(lignePersonne(it.nom, "ERUN", it.tel, mail = it.mail)) }
        out.add(equipe)

        val rased = Onglet("v5_circo_" + cir.code + "_rased", "RASED")
        rased.fields.addAll(champsPersonne())
        cir.rased.forEach { rased.rows.add(lignePersonne(it.nom, it.fonction, it.tel, mail = it.mail, obs = it.note)) }
        out.add(rased)

        val pial = Onglet("v5_circo_" + cir.code + "_pial", "PIAL-ER")
        pial.fields.addAll(champsPersonne())
        cir.pialEr.forEach { pial.rows.add(lignePersonne(it.nom, it.fonction, it.tel, mail = it.mail, obs = it.note)) }
        out.add(pial)

        val col = Onglet("v5_circo_" + cir.code + "_colleges", "CollÃ¨ges")
        col.fields.addAll(champsPersonne())
        cir.colleges.forEach { col.rows.add(lignePersonne(it, "CollÃ¨ge partenaire")) }
        out.add(col)
    }
    return out
}

/** Ajoute UNE SEULE FOIS les onglets manquants (les Ã©ditions sont prÃ©servÃ©es). */
fun assurerOnglets(a: Annuaire, ctx: Context) {
    val ids = a.tabs.map { it.id }.toHashSet()
    genererOnglets(ctx).forEach { if (!ids.contains(it.id)) a.tabs.add(it) }
}

/** Onglets du bas pour un filtre : (nom affichÃ©, onglet). null = Ãcoles. */
fun ongletsDuFiltre(a: Annuaire, filtre: String): List<Pair<String, Onglet?>> =
    if (filtre == "dsden") {
        a.tabs.filter { it.id.startsWith("v5_dsden_") }.map { it.name to it }
    } else {
        val l = mutableListOf<Pair<String, Onglet?>>("Ãcoles" to null)
        for (sfx in listOf("_equipe", "_rased", "_pial", "_colleges")) {
            a.tabs.firstOrNull { it.id == "v5_circo_" + filtre + sfx }
                ?.let { l.add(it.name to it) }
        }
        l
    }
