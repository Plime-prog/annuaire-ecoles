package com.plime.annuaire

import java.text.Normalizer
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class Professeur(val nom: String, val classe: String, val tel: String)
data class Directeur(val nom: String, val tel: String)

data class Ecole(
    val id: String,
    val nom: String,
    val adresse: String,
    val tel: String,
    val directeur: Directeur,
    val professeurs: List<Professeur>
)

data class Commune(
    val id: String,
    val nom: String,
    val departement: String,
    val population: String,
    val maire: String,
    val telMaire: String,
    val adresseMairie: String,
    val telMairie: String,
    val ecoles: List<Ecole>
)

data class DirecteurEntry(val commune: Commune, val ecole: Ecole)

fun newId(): String = UUID.randomUUID().toString()

fun String.normalized(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .lowercase()

fun initials(nom: String): String =
    nom.removePrefix("M. ").removePrefix("Mme ")
        .split(" ").filter { it.length > 1 }
        .take(2).map { it.first() }.joinToString("")

fun List<Commune>.toJson(): String {
    val arr = JSONArray()
    for (c in this) {
        val ecoles = JSONArray()
        for (e in c.ecoles) {
            val profs = JSONArray()
            for (p in e.professeurs) {
                profs.put(JSONObject().put("nom", p.nom).put("classe", p.classe).put("tel", p.tel))
            }
            ecoles.put(
                JSONObject().put("id", e.id).put("nom", e.nom).put("adresse", e.adresse)
                    .put("tel", e.tel).put("dirNom", e.directeur.nom).put("dirTel", e.directeur.tel)
                    .put("profs", profs)
            )
        }
        arr.put(
            JSONObject().put("id", c.id).put("nom", c.nom).put("departement", c.departement)
                .put("population", c.population).put("maire", c.maire).put("telMaire", c.telMaire)
                .put("adresseMairie", c.adresseMairie).put("telMairie", c.telMairie)
                .put("ecoles", ecoles)
        )
    }
    return arr.toString()
}

fun parseCommunes(s: String): List<Commune> {
    val arr = JSONArray(s)
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val ea = o.getJSONArray("ecoles")
        Commune(
            o.getString("id"), o.getString("nom"), o.getString("departement"),
            o.getString("population"), o.getString("maire"), o.getString("telMaire"),
            o.getString("adresseMairie"), o.getString("telMairie"),
            (0 until ea.length()).map { j ->
                val e = ea.getJSONObject(j)
                val pa = e.getJSONArray("profs")
                Ecole(
                    e.getString("id"), e.getString("nom"), e.getString("adresse"), e.getString("tel"),
                    Directeur(e.getString("dirNom"), e.getString("dirTel")),
                    (0 until pa.length()).map { k ->
                        val p = pa.getJSONObject(k)
                        Professeur(p.getString("nom"), p.getString("classe"), p.getString("tel"))
                    }
                )
            }
        )
    }
}
