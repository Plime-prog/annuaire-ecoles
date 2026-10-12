package com.plime.annuaire.v5

import android.content.Context
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

/* ==================== LECTURE JSON ==================== */

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
            co.str("nom"), "", co.str("tel"), co.str("mail")
        ).let { ContactV5(it.nom, co.str("adresse"), it.tel, it.mail) } else ContactV5(""),
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
