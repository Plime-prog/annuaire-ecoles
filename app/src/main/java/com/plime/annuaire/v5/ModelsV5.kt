package com.plime.annuaire.v5

import android.content.Context
import com.plime.annuaire.v3.AnnuaireV3
import com.plime.annuaire.v3.FieldV3
import com.plime.annuaire.v3.TabV3
import com.plime.annuaire.v3.newKeyV3
import org.json.JSONArray
import org.json.JSONObject

/* ==================== MODÈLES V5 ==================== */

data class ContactV5(
    val nom: String,
    val fonction: String = "",
    val tel: String = "",
    val mail: String = "",
    val note: String = ""
)

data class BureauV5(val nom: String, val membres: List<ContactV5>)

data class DsdenV5(
    val coordonnees: ContactV5 = ContactV5(""),
    val direction: List<ContactV5> = emptyList(),
    val secretariat: List<ContactV5> = emptyList(),
    val bureaux: List<BureauV5> = emptyList()
)

data class CircoV5(
    val code: String,
    val nom: String,
    val ien: String = "",
    val adresse: String = "",
    val tel: String = "",
    val mail: String = "",
    val cpc: List<ContactV5> = emptyList(),
    val erun: List<ContactV5> = emptyList(),
    val assistante: String = "",
    val rased: List<ContactV5> = emptyList(),
    val pialEr: List<ContactV5> = emptyList(),
    val colleges: List<String> = emptyList()
)

/* ==================== LECTURE JSON (assets) ==================== */

private fun JSONObject.str(k: String): String =
    if (has(k) && !isNull(k)) getString(k) else ""

private fun JSONArray.contacts(): List<ContactV5> =
    (0 until length()).map { getJSONObject(it) }.map { o ->
        ContactV5(
            nom = o.str("nom"), fonction = o.str("fonction"),
            tel = o.str("tel"), mail = o.str("mail"), note = o.str("note")
        )
    }.filter { it.nom.isNotBlank() }

/** Lit assets/aube-dsden.json. Retourne null si le fichier est absent. */
fun loadDsdenV5(ctx: Context): DsdenV5? = try {
    val s = ctx.assets.open("aube-dsden.json").bufferedReader().use { it.readText() }
    val o = JSONObject(s)
    val co = o.optJSONObject("coordonnees")
    DsdenV5(
        coordonnees = if (co != null) ContactV5(
            co.str("nom"), co.str("adresse"), co.str("tel"), co.str("mail")
        ) else ContactV5(""),
        direction = o.optJSONArray("direction")?.contacts() ?: emptyList(),
        secretariat = o.optJSONArray("secretariat")?.contacts() ?: emptyList(),
        bureaux = (0 until (o.optJSONArray("bureaux")?.length() ?: 0)).map { i ->
            val b = o.getJSONArray("bureaux").getJSONObject(i)
            BureauV5(b.str("nom"), b.optJSONArray("membres")?.contacts() ?: emptyList())
        }
    )
} catch (e: Exception) { null }

/** Lit assets/aube-circonscriptions.json. Retourne une liste vide si absent. */
fun loadCircosV5(ctx: Context): List<CircoV5> = try {
    val s = ctx.assets.open("aube-circonscriptions.json").bufferedReader().use { it.readText() }
    val o = JSONObject(s).optJSONObject("circonscriptions") ?: return emptyList()
    o.keys().asSequence().map { code ->
        val c = o.getJSONObject(code)
        CircoV5(
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

/* ==================== ÉCOLE → CIRCONSCRIPTION ==================== */

private val RE_CIRCO = Regex("1er degré de (.+?) —")

/** Nom de circonscription écrit dans une ligne de l'onglet Communes, sinon "". */
fun nomCircoDeRow(row: Map<String, String>): String =
    row.values.firstNotNullOfOrNull { v ->
        RE_CIRCO.find(v)?.groupValues?.get(1)?.trim()
    } ?: ""

/** Associe le nom trouvé dans l'Excel au code UAI de la circonscription. */
fun codeCirco(nomCirco: String): String {
    val n = nomCirco.lowercase()
    return when {
        "chapelle" in n -> "0100981M"
        "julien" in n -> "0100043T"
        "aube" in n && "bar" in n -> "0100045V"
        "seine" in n && "bar" in n -> "0100820M"
        "romilly" in n -> "0100044U"
        else -> "0100041R"   /* Troyes (et ASH par défaut) */
    }
}

/** Code circonscription d'une ligne d'école, ou "" si non renseigné. */
fun circoDeRow(row: Map<String, String>): String {
    val nom = nomCircoDeRow(row)
    return if (nom.isEmpty()) "" else codeCirco(nom)
}

/* ==================== ONGLETS V5 ÉDITABLES ==================== */

private fun cle(s: String): String =
    s.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

/** Champs d'une fiche personne (le 🧪 permet d'en ajouter : Tél. 3…). */
private fun champsPersonne(): MutableList<FieldV3> = mutableListOf(
    FieldV3("contact", "Contact"),
    FieldV3("fonction", "Fonction"),
    FieldV3("tel1", "Tél. 1"),
    FieldV3("tel2", "Tél. 2"),
    FieldV3("mail", "Mail"),
    FieldV3("obs", "Observations", multiline = true)
)

private fun lignePersonne(
    nom: String, fonction: String = "", tel1: String = "",
    tel2: String = "", mail: String = "", obs: String = ""
): MutableMap<String, String> {
    val m = mutableMapOf<String, String>()
    m["_id"] = newKeyV3()
    m["contact"] = nom; m["fonction"] = fonction; m["tel1"] = tel1
    m["tel2"] = tel2; m["mail"] = mail; m["obs"] = obs
    return m
}

/**
 * Génère les onglets v5 (éditables) depuis les JSON des assets.
 * Identifiants stables : v5_dsden_<bureau> et v5_circo_<code>_<équipe/rased/pial/colleges>.
 */
fun genereTabsV5(ctx: Context): List<TabV3> {
    val out = mutableListOf<TabV3>()
    val d = loadDsdenV5(ctx)
    if (d != null) {
        val direction = TabV3("v5_dsden_direction", "Direction")
        direction.fields.addAll(champsPersonne())
        (d.direction + d.secretariat).forEach { c ->
            direction.rows.add(lignePersonne(c.nom, c.fonction, c.tel, mail = c.mail))
        }
        out.add(direction)
        d.bureaux.forEach { b ->
            val t = TabV3("v5_dsden_" + cle(b.nom), b.nom)
            t.fields.addAll(champsPersonne())
            b.membres.forEach { c ->
                t.rows.add(lignePersonne(c.nom, c.fonction, c.tel, mail = c.mail))
            }
            out.add(t)
        }
    }
    loadCircosV5(ctx).forEach { cir ->
        val equipe = TabV3("v5_circo_" + cir.code + "_equipe", "Équipe")
        equipe.fields.addAll(champsPersonne())
        if (cir.ien.isNotBlank())
            equipe.rows.add(lignePersonne(cir.ien, "Inspecteur de l'Éducation nationale", cir.tel, mail = cir.mail, obs = cir.adresse))
        if (cir.assistante.isNotBlank())
            equipe.rows.add(lignePersonne(cir.assistante, "Assistante de circonscription"))
        cir.cpc.forEach { equipe.rows.add(lignePersonne(it.nom, "CPC", it.tel, mail = it.mail)) }
        cir.erun.forEach { equipe.rows.add(lignePersonne(it.nom, "ERUN", it.tel, mail = it.mail)) }
        out.add(equipe)

        val rased = TabV3("v5_circo_" + cir.code + "_rased", "RASED")
        rased.fields.addAll(champsPersonne())
        cir.rased.forEach { rased.rows.add(lignePersonne(it.nom, it.fonction, it.tel, mail = it.mail, obs = it.note)) }
        out.add(rased)

        val pial = TabV3("v5_circo_" + cir.code + "_pial", "PIAL-ER")
        pial.fields.addAll(champsPersonne())
        cir.pialEr.forEach { pial.rows.add(lignePersonne(it.nom, it.fonction, it.tel, mail = it.mail, obs = it.note)) }
        out.add(pial)

        val col = TabV3("v5_circo_" + cir.code + "_colleges", "Collèges")
        col.fields.addAll(champsPersonne())
        cir.colleges.forEach { col.rows.add(lignePersonne(it, "Collège partenaire")) }
        out.add(col)
    }
    return out
}

/** Ajoute UNE SEULE FOIS les onglets v5 manquants (les éditions sont préservées). */
fun assureTabsV5(a: AnnuaireV3, ctx: Context) {
    val ids = a.tabs.map { it.id }.toHashSet()
    genereTabsV5(ctx).forEach { if (!ids.contains(it.id)) a.tabs.add(it) }
}

/** Onglets du bas pour un filtre : (nom affiché, onglet). null = Écoles. */
fun ongletsDuFiltre(a: AnnuaireV3, filtre: String): List<Pair<String, TabV3?>> =
    if (filtre == "dsden") {
        a.tabs.filter { it.id.startsWith("v5_dsden_") }.map { it.name to it }
    } else {
        val l = mutableListOf<Pair<String, TabV3?>>("Écoles" to null)
        for (sfx in listOf("_equipe", "_rased", "_pial", "_colleges")) {
            a.tabs.firstOrNull { it.id == "v5_circo_" + filtre + sfx }
                ?.let { l.add(it.name to it) }
        }
        l
    }
