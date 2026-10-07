package com.plime.annuaire.v2

import kotlinx.serialization.Serializable
import java.util.UUID

/* ==================== 1. COMMUNES ET ÉCOLES ==================== */

@Serializable
data class CommuneV2(
    val id: String,
    val nom: String,
    val maire: String = "",
    val telMaire: String = "",
    val adresseMairie: String = "",
    val mail: String = "",
    val contactElu: String = "",
    val telContactElu: String = "",
    val contactService: String = "",
    val telContactService: String = "",
    val observations: String = "",
    val ecoles: List<EcoleV2> = emptyList()
)

@Serializable
data class EcoleV2(
    val id: String,
    val nom: String,
    val codeUai: String = "",
    val adresse: String = "",
    val tel: String = "",
    val mail: String = "",
    val directeur: String = "",
    val telDirecteur: String = "",
    val nbEleves: String = "",
    val horairesMatin: String = "",
    val horairesApresMidi: String = "",
    val observations: String = "",
    val professeurs: List<ProfesseurV2> = emptyList()
)

@Serializable
data class ProfesseurV2(
    val id: String,
    val nom: String,
    val classe: String = "",
    val nbEleves: String = "",
    val tel: String = "",
    val observations: String = ""
)

/* ==================== 2. RASED ==================== */

@Serializable
data class RasedEntry(
    val id: String,
    val contact: String = "",
    val fonction: String = "",
    val adresse: String = "",
    val mail: String = "",
    val tel1: String = "",
    val tel2: String = "",
    val observations: String = ""
)

/* ==================== 3. ÉQUIPE DE CIRCONSCRIPTION ==================== */

@Serializable
data class CircoEntry(
    val id: String,
    val contact: String = "",
    val fonction: String = "",
    val adresse: String = "",
    val mail: String = "",
    val tel1: String = "",
    val tel2: String = "",
    val observations: String = ""
)

/* ==================== 4. PIAL & ER ==================== */

@Serializable
data class PialEntry(
    val id: String,
    val contact: String = "",
    val fonction: String = "",
    val adresse: String = "",
    val mail: String = "",
    val tel1: String = "",
    val tel2: String = "",
    val observations: String = ""
)

/* ==================== 5. COLLÈGES ==================== */

@Serializable
data class CollegeV2(
    val id: String,
    val nom: String = "",
    val adresse: String = "",
    val contact: String = "",
    val fonction: String = "",
    val adresseContact: String = "",
    val mailContact: String = "",
    val tel1: String = "",
    val tel2: String = "",
    val observations: String = ""
)

/* ==================== ANNUAIRE COMPLET ==================== */

@Serializable
data class AnnuaireV2(
    val communes: List<CommuneV2> = emptyList(),
    val rased: List<RasedEntry> = emptyList(),
    val circo: List<CircoEntry> = emptyList(),
    val pial: List<PialEntry> = emptyList(),
    val colleges: List<CollegeV2> = emptyList()
)

fun newIdV2(): String = UUID.randomUUID().toString()
