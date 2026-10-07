package com.plime.annuaire.v2

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/* ==================== 1. COMMUNES ET ÉCOLES ==================== */

data class CommuneV2(
    val id: String,
    val nom: String,
    var maire: String = "",
    var telMaire: String = "",
    var adresseMairie: String = "",
    var mail: String = "",
    var contactElu: String = "",
    var telContactElu: String = "",
    var contactService: String = "",
    var telContactService: String = "",
    var observations: String = "",
    val ecoles: MutableList<EcoleV2> = mutableListOf()
)

data class EcoleV2(
    val id: String,
    val nom: String,
    var codeUai: String = "",
    var adresse: String = "",
    var tel: String = "",
    var mail: String = "",
    var directeur: String = "",
    var telDirecteur: String = "",
    var nbEleves: String = "",
    var horairesMatin: String = "",
    var horairesApresMidi: String = "",
    var observations: String = "",
    val professeurs: MutableList<ProfesseurV2> = mutableListOf()
)

data class ProfesseurV2(
    val id: String,
    var nom: String,
    var classe: String = "",
    var nbEleves: String = "",
    var tel: String = "",
    var observations: String = ""
)

/* ==================== 2. RASED ==================== */

data class RasedEntry(
    val id: String,
    var contact: String = "",
    var fonction: String = "",
    var adresse: String = "",
    var mail: String = "",
    var tel1: String = "",
    var tel2: String = "",
    var observations: String = ""
)

/* ==================== 3. ÉQUIPE DE CIRCONSCRIPTION ==================== */

data class CircoEntry(
    val id: String,
    var contact: String = "",
    var fonction: String = "",
    var adresse: String = "",
    var mail: String = "",
    var tel1: String = "",
    var tel2: String = "",
    var observations: String = ""
)

/* ==================== 4. PIAL & ER ==================== */

data class PialEntry(
    val id: String,
    var contact: String = "",
    var fonction: String = "",
    var adresse: String = "",
    var mail: String = "",
    var tel1: String = "",
    var tel2: String = "",
    var observations: String = ""
)

/* ==================== 5. COLLÈGES ==================== */

data class CollegeV2(
    val id: String,
    var nom: String = "",
    var adresse: String = "",
    var contact: String = "",
    var fonction: String = "",
    var adresseContact: String = "",
    var mailContact: String = "",
    var tel1: String = "",
    var tel2: String = "",
    var observations: String = ""
)

/* ==================== ANNUAIRE COMPLET ==================== */

data class AnnuaireV2(
    val communes: MutableList<CommuneV2> = mutableListOf(),
    val rased: MutableList<RasedEntry> = mutableListOf(),
    val circo: MutableList<CircoEntry> = mutableListOf(),
    val pial: MutableList<PialEntry> = mutableListOf(),
    val colleges: MutableList<CollegeV2> = mutableListOf()
)

fun newIdV2(): String = UUID.randomUUID().toString()

/* ==================== SÉRIALISATION JSON (org.json) ==================== */

private fun JSONObject.str(key: String): String =
    if (has(key) && !isNull(key)) getString(key) else ""

fun CommuneV2.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("nom", nom); put("maire", maire); put("telMaire", telMaire)
    put("adresseMairie", adresseMairie); put("mail", mail); put("contactElu", contactElu)
    put("telContactElu", telContactElu); put("contactService", contactService)
    put("telContactService", telContactService); put("observations", observations)
    put("ecoles", JSONArray().apply { ecoles.forEach { put(it.toJson()) } })
}

fun communeFromJson(o: JSONObject): CommuneV2 {
    val c = CommuneV2(
        id = o.str("id").ifEmpty { newIdV2() }, nom = o.str("nom"),
        maire = o.str("maire"), telMaire = o.str("telMaire"),
        adresseMairie = o.str("adresseMairie"), mail = o.str("mail"),
        contactElu = o.str("contactElu"), telContactElu = o.str("telContactElu"),
        contactService = o.str("contactService"), telContactService = o.str("telContactService"),
        observations = o.str("observations")
    )
    val arr = o.optJSONArray("ecoles") ?: JSONArray()
    for (i in 0 until arr.length()) c.ecoles.add(ecoleFromJson(arr.getJSONObject(i)))
    return c
}

fun EcoleV2.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("nom", nom); put("codeUai", codeUai); put("adresse", adresse)
    put("tel", tel); put("mail", mail); put("directeur", directeur)
    put("telDirecteur", telDirecteur); put("nbEleves", nbEleves)
    put("horairesMatin", horairesMatin); put("horairesApresMidi", horairesApresMidi)
    put("observations", observations)
    put("professeurs", JSONArray().apply { professeurs.forEach { put(it.toJson()) } })
}

fun ecoleFromJson(o: JSONObject): EcoleV2 {
    val e = EcoleV2(
        id = o.str("id").ifEmpty { newIdV2() }, nom = o.str("nom"),
        codeUai = o.str("codeUai"), adresse = o.str("adresse"), tel = o.str("tel"),
        mail = o.str("mail"), directeur = o.str("directeur"),
        telDirecteur = o.str("telDirecteur"), nbEleves = o.str("nbEleves"),
        horairesMatin = o.str("horairesMatin"), horairesApresMidi = o.str("horairesApresMidi"),
        observations = o.str("observations")
    )
    val arr = o.optJSONArray("professeurs") ?: JSONArray()
    for (i in 0 until arr.length()) e.professeurs.add(professeurFromJson(arr.getJSONObject(i)))
    return e
}

fun ProfesseurV2.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("nom", nom); put("classe", classe); put("nbEleves", nbEleves)
    put("tel", tel); put("observations", observations)
}

fun professeurFromJson(o: JSONObject): ProfesseurV2 = ProfesseurV2(
    id = o.str("id").ifEmpty { newIdV2() }, nom = o.str("nom"), classe = o.str("classe"),
    nbEleves = o.str("nbEleves"), tel = o.str("tel"), observations = o.str("observations")
)

fun RasedEntry.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("contact", contact); put("fonction", fonction)
    put("adresse", adresse); put("mail", mail); put("tel1", tel1); put("tel2", tel2)
    put("observations", observations)
}

fun rasedFromJson(o: JSONObject): RasedEntry = RasedEntry(
    id = o.str("id").ifEmpty { newIdV2() }, contact = o.str("contact"),
    fonction = o.str("fonction"), adresse = o.str("adresse"), mail = o.str("mail"),
    tel1 = o.str("tel1"), tel2 = o.str("tel2"), observations = o.str("observations")
)

fun CircoEntry.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("contact", contact); put("fonction", fonction)
    put("adresse", adresse); put("mail", mail); put("tel1", tel1); put("tel2", tel2)
    put("observations", observations)
}

fun circoFromJson(o: JSONObject): CircoEntry = CircoEntry(
    id = o.str("id").ifEmpty { newIdV2() }, contact = o.str("contact"),
    fonction = o.str("fonction"), adresse = o.str("adresse"), mail = o.str("mail"),
    tel1 = o.str("tel1"), tel2 = o.str("tel2"), observations = o.str("observations")
)

fun PialEntry.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("contact", contact); put("fonction", fonction)
    put("adresse", adresse); put("mail", mail); put("tel1", tel1); put("tel2", tel2)
    put("observations", observations)
}

fun pialFromJson(o: JSONObject): PialEntry = PialEntry(
    id = o.str("id").ifEmpty { newIdV2() }, contact = o.str("contact"),
    fonction = o.str("fonction"), adresse = o.str("adresse"), mail = o.str("mail"),
    tel1 = o.str("tel1"), tel2 = o.str("tel2"), observations = o.str("observations")
)

fun CollegeV2.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("nom", nom); put("adresse", adresse); put("contact", contact)
    put("fonction", fonction); put("adresseContact", adresseContact)
    put("mailContact", mailContact); put("tel1", tel1); put("tel2", tel2)
    put("observations", observations)
}

fun collegeFromJson(o: JSONObject): CollegeV2 = CollegeV2(
    id = o.str("id").ifEmpty { newIdV2() }, nom = o.str("nom"), adresse = o.str("adresse"),
    contact = o.str("contact"), fonction = o.str("fonction"),
    adresseContact = o.str("adresseContact"), mailContact = o.str("mailContact"),
    tel1 = o.str("tel1"), tel2 = o.str("tel2"), observations = o.str("observations")
)

fun AnnuaireV2.toJson(): JSONObject = JSONObject().apply {
    put("communes", JSONArray().apply { communes.forEach { put(it.toJson()) } })
    put("rased", JSONArray().apply { rased.forEach { put(it.toJson()) } })
    put("circo", JSONArray().apply { circo.forEach { put(it.toJson()) } })
    put("pial", JSONArray().apply { pial.forEach { put(it.toJson()) } })
    put("colleges", JSONArray().apply { colleges.forEach { put(it.toJson()) } })
}

fun annuaireFromJson(s: String): AnnuaireV2 {
    val o = JSONObject(s)
    val a = AnnuaireV2()
    o.optJSONArray("communes")?.let { arr -> for (i in 0 until arr.length()) a.communes.add(communeFromJson(arr.getJSONObject(i))) }
    o.optJSONArray("rased")?.let { arr -> for (i in 0 until arr.length()) a.rased.add(rasedFromJson(arr.getJSONObject(i))) }
    o.optJSONArray("circo")?.let { arr -> for (i in 0 until arr.length()) a.circo.add(circoFromJson(arr.getJSONObject(i))) }
    o.optJSONArray("pial")?.let { arr -> for (i in 0 until arr.length()) a.pial.add(pialFromJson(arr.getJSONObject(i))) }
    o.optJSONArray("colleges")?.let { arr -> for (i in 0 until arr.length()) a.colleges.add(collegeFromJson(arr.getJSONObject(i))) }
    return a
}
