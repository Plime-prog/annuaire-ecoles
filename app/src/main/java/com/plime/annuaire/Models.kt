package com.plime.annuaire

import java.text.Normalizer

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

/** Recherche insensible aux accents et à la casse. */
fun String.normalized(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .lowercase()

/** Initiales pour les avatars ("Mme Claire Dubois" -> "CD"). */
fun initials(nom: String): String =
    nom.removePrefix("M. ").removePrefix("Mme ")
        .split(" ").filter { it.length > 1 }
        .take(2).map { it.first() }.joinToString("")
