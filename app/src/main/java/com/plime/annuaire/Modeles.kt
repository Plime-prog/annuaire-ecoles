package com.plime.annuaire

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/* ==================== MODELE DYNAMIQUE ==================== */

/** Definition d'un champ (colonne). La cle est stable, le libelle est modifiable. */
data class Champ(
    val key: String,
    var label: String,
    var multiline: Boolean = false
)

/** Un onglet = une table : liste de champs + lignes (cle -> valeur). */
data class Onglet(
    val id: String,
    var name: String,
    var groupBy: String = "",        // cl\u00E9 du champ de regroupement ("" = pas de groupes)
    val fields: MutableList<Champ> = mutableListOf(),
    val rows: MutableList<MutableMap<String, String>> = mutableListOf()
)

data class Annuaire(
    val tabs: MutableList<Onglet> = mutableListOf()
)

fun nouvelleCle(): String = UUID.randomUUID().toString().substring(0, 8)

/* ==================== SERIALISATION JSON ==================== */

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

/** Titre d'une ligne = premiere valeur non vide selon l'ordre des champs. */
fun titreLigne(tab: Onglet, row: Map<String, String>): String =
    tab.fields.firstNotNullOfOrNull { row[it.key]?.takeIf { v -> v.isNotBlank() } }
        ?: "(sans titre)"

/** Sous-titre = les 2 valeurs non vides suivantes. */
fun sousTitreLigne(tab: Onglet, row: Map<String, String>): String {
    val vals = tab.fields.mapNotNull { row[it.key]?.takeIf { v -> v.isNotBlank() } }
    return if (vals.size > 1) vals.drop(1).take(2).joinToString(" \u00B7 ") else ""
}

/* ==================== SCHEMA PAR DEFAUT ==================== */

private fun fld(label: String): Champ =
    Champ(nouvelleCle(), label, multiline = label.contains("Observation") || label.contains("Adresse") || label.contains("Horaires"))

fun schemaParDefaut(): Annuaire {
    val communes = Onglet(nouvelleCle(), "Communes").apply {
        /* Structure en dur de l'onglet Communes :
           mairie et elu de la commune, puis l'ecole et sa fiche complete,
           puis la decharge et les horaires. */
        listOf(
            "Commune", "Adresse Mairie", "T\u00E9l. Mairie",
            "Maire", "T\u00E9l. Maire", "Mail Maire",
            "Contact \u00C9lu", "Fonction Contact \u00C9lu", "T\u00E9l. \u00C9lu", "Mail \u00C9lu",
            "Observations",
            "\u00C9cole", "Code UAI", "Circonscription", "IPIAL", "Statut", "Adresse \u00E9cole", "T\u00E9l. \u00E9cole", "Mail \u00E9cole",
            "Directeur", "T\u00E9l. Directeur", "Mail Directeur",
            "D\u00E9charge", "Matin", "Apr\u00E8s-midi",
            "Observations"
        ).forEach { fields.add(fld(it)) }
        groupBy = fields[0].key
    }
    val circo = Onglet(nouvelleCle(), "Circonscription").apply {
        /* Liste des personnes : IEN, CPC, assistantes, ERUN... */
        listOf("Contact", "Fonction", "Adresse", "Mail", "T\u00E9l. 1", "T\u00E9l. 2",
            "Observations", "Circonscription \u00E9largie")
            .forEach { fields.add(fld(it)) }
        groupBy = ""   /* liste \u00E0 plat des personnes, pas de regroupement */
    }
    val colleges = Onglet(nouvelleCle(), "Coll\u00E8ges").apply {
        listOf("Nom", "Adresse", "Contact", "Fonction contact", "Adresse contact",
            "Mail contact", "T\u00E9l. 1", "T\u00E9l. 2", "Observations")
            .forEach { fields.add(fld(it)) }
        groupBy = fields[0].key   /* " Nom " = nom du college */
    }
    return Annuaire(mutableListOf(
        communes, circo, colleges
    ))
}

/* ==================== STOCKAGE CHIFFRE ==================== */

object Stockage {
    /* Nom du fichier de preferences conserve tel quel : les donnees
       deja enregistrees sur les appareils restent lisibles. */
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

    /** Fiche favorite : " idOnglet|idLigne ", vide si aucun. */
    fun getFavori(ctx: Context): String = prefs(ctx).getString("favori", "") ?: ""

    fun setFavori(ctx: Context, tabId: String, rowId: String) {
        prefs(ctx).edit().putString("favori", "$tabId|$rowId").apply()
    }

    fun clearFavori(ctx: Context) {
        prefs(ctx).edit().remove("favori").apply()
    }
}

/* ==================== DONNEES OFFICIELLES (DSDEN, CIRCONSCRIPTIONS) ==================== */

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

/* ==================== ECOLE → CIRCONSCRIPTION ==================== */

private val RE_CIRCO = Regex("1er degr\u00E9 de (.+?) \u2014")

/** Nom de circonscription \u00E9crit dans une ligne de l'onglet Communes, sinon "". */
fun nomCircoDeLigne(row: Map<String, String>): String {
    val officiel: String? =
        row.values.firstNotNullOfOrNull { v ->
            RE_CIRCO.find(v)?.groupValues?.get(1)?.trim()
        }
    if (officiel != null) return officiel
    /* Repli : si aucun " 1er degr\u00E9 de ... - " n'apparait, on accepte une
       valeur courte contenant le nom d'une circonscription connue (ex. la
       colonne " Circonscription " remplie " Saint-Julien/Arcis "). */
    val court: String? =
        row.values.firstNotNullOfOrNull { v ->
            val n = norme(v)
            if (n.length <= 40 && ("chapelle" in n || "julien" in n || "romilly" in n ||
                ("aube" in n && "bar" in n) || ("seine" in n && "bar" in n) || "troyes" in n)
            ) v.trim() else null
        }
    return court ?: ""
}

/** Associe le nom trouve dans l'Excel au code UAI de la circonscription. */
fun codeCirco(nomCirco: String): String {
    val n = nomCirco.lowercase()
    return when {
        "chapelle" in n -> "0100981M"
        "julien" in n -> "0100043T"
        "aube" in n && "bar" in n -> "0100045V"
        "seine" in n && "bar" in n -> "0100820M"
        "romilly" in n -> "0100044U"
        else -> "0100041R"   /* Troyes (et ASH par d\u00E9faut) */
    }
}

/** Code circonscription d'une ligne d'\u00E9cole, ou "" si non renseign\u00E9. */
fun circoDeLigne(row: Map<String, String>): String {
    val nom = nomCircoDeLigne(row)
    return if (nom.isEmpty()) "" else codeCirco(nom)
}

/* ==================== ONGLETS DSDEN / CIRCONSCRIPTIONS (editables) ==================== */

/* Les identifiants " v5_... " sont conserves tels quels : ils sont deja
   enregistres dans les donnees des appareils, les changer casserait la
   correspondance avec les fiches existantes. */

private fun cle(s: String): String =
    s.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

/** Champs d'une fiche personne (le 🧪 permet d'en ajouter : Tel. 3...). */
private fun champsPersonne(): MutableList<Champ> = mutableListOf(
    Champ("contact", "Contact"),
    Champ("fonction", "Fonction"),
    Champ("tel1", "T\u00E9l. 1"),
    Champ("tel2", "T\u00E9l. 2"),
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
 * Genere les onglets DSDEN et circonscriptions depuis les JSON des assets.
 * Identifiants stables : v5_dsden_<bureau> et v5_circo_<code>_<equipe/rased/pial/colleges>.
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
        val equipe = Onglet("v5_circo_" + cir.code + "_equipe", "\u00C9quipe")
        equipe.fields.addAll(champsPersonne())
        if (cir.ien.isNotBlank())
            equipe.rows.add(lignePersonne(cir.ien, "Inspecteur de l'\u00C9ducation nationale", cir.tel, mail = cir.mail, obs = cir.adresse))
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

        val col = Onglet("v5_circo_" + cir.code + "_colleges", "Coll\u00E8ges")
        col.fields.addAll(champsPersonne())
        cir.colleges.forEach { col.rows.add(lignePersonne(it, "Coll\u00E8ge partenaire")) }
        out.add(col)
    }
    return out
}

/** Ajoute UNE SEULE FOIS les onglets manquants (les editions sont preservees). */
fun assurerOnglets(a: Annuaire, ctx: Context) {
    val ids = a.tabs.map { it.id }.toHashSet()
    genererOnglets(ctx).forEach { if (!ids.contains(it.id)) a.tabs.add(it) }
}

/** Onglets du bas pour un filtre : (nom affiche, onglet). null = Ecoles. */
fun ongletsDuFiltre(a: Annuaire, filtre: String): List<Pair<String, Onglet?>> =
    if (filtre == "dsden") {
        a.tabs.filter { it.id.startsWith("v5_dsden_") }.map { it.name to it }
    } else {
        val l = mutableListOf<Pair<String, Onglet?>>("\u00C9coles" to null)
        for (sfx in listOf("_equipe", "_rased", "_pial", "_colleges")) {
            a.tabs.firstOrNull { it.id == "v5_circo_" + filtre + sfx }
                ?.let { l.add(it.name to it) }
        }
        l
    }
