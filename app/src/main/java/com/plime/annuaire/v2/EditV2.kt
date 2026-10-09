package com.plime.annuaire.v2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Boîte de dialogue générique d'édition : affiche une fiche sous forme de
 * champs texte, avec Enregistrer / Supprimer / Annuler.
 */
@Composable
fun EntityDialog(
    title: String,
    fields: LinkedHashMap<String, String>,
    onSave: (LinkedHashMap<String, String>) -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val values = remember(fields) { mutableStateMapOf<String, String>().apply { fields.forEach { (k, v) -> put(k, v) } } }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                fields.keys.forEach { label ->
                    OutlinedTextField(
                        value = values[label] ?: "",
                        onValueChange = { values[label] = it },
                        label = { Text(label) },
                        singleLine = label != "Observations",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    )
                }
                if (onDelete != null) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Supprimer", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val out = LinkedHashMap<String, String>()
                fields.keys.forEach { out[it] = values[it] ?: "" }
                onSave(out)
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Annuler") } }
    )
}

/* ---------- Champs et application : COMMUNE ---------- */

fun communeFields(c: CommuneV2) = linkedMapOf(
    "Nom de la commune" to c.nom,
    "Maire" to c.maire,
    "Tél. maire" to c.telMaire,
    "Adresse mairie" to c.adresseMairie,
    "Mail" to c.mail,
    "Contact élu" to c.contactElu,
    "Tél. contact élu" to c.telContactElu,
    "Contact service" to c.contactService,
    "Tél. contact service" to c.telContactService,
    "Observations" to c.observations
)

fun applyCommune(c: CommuneV2, m: Map<String, String>) {
    c.nom = m["Nom de la commune"] ?: c.nom
    c.maire = m["Maire"] ?: ""
    c.telMaire = m["Tél. maire"] ?: ""
    c.adresseMairie = m["Adresse mairie"] ?: ""
    c.mail = m["Mail"] ?: ""
    c.contactElu = m["Contact élu"] ?: ""
    c.telContactElu = m["Tél. contact élu"] ?: ""
    c.contactService = m["Contact service"] ?: ""
    c.telContactService = m["Tél. contact service"] ?: ""
    c.observations = m["Observations"] ?: ""
}

/* ---------- Champs et application : ÉCOLE ---------- */

fun ecoleFields(e: EcoleV2) = linkedMapOf(
    "Nom de l'école" to e.nom,
    "Code UAI" to e.codeUai,
    "Adresse" to e.adresse,
    "Téléphone" to e.tel,
    "Mail" to e.mail,
    "Directeur" to e.directeur,
    "Tél. directeur" to e.telDirecteur,
    "Nombre d'élèves" to e.nbEleves,
    "Horaires du matin" to e.horairesMatin,
    "Horaires de l'après-midi" to e.horairesApresMidi,
    "Observations" to e.observations
)

fun applyEcole(e: EcoleV2, m: Map<String, String>) {
    e.nom = m["Nom de l'école"] ?: e.nom
    e.codeUai = m["Code UAI"] ?: ""
    e.adresse = m["Adresse"] ?: ""
    e.tel = m["Téléphone"] ?: ""
    e.mail = m["Mail"] ?: ""
    e.directeur = m["Directeur"] ?: ""
    e.telDirecteur = m["Tél. directeur"] ?: ""
    e.nbEleves = m["Nombre d'élèves"] ?: ""
    e.horairesMatin = m["Horaires du matin"] ?: ""
    e.horairesApresMidi = m["Horaires de l'après-midi"] ?: ""
    e.observations = m["Observations"] ?: ""
}

/* ---------- Champs et application : PROFESSEUR ---------- */

fun profFields(p: ProfesseurV2) = linkedMapOf(
    "Nom" to p.nom,
    "Classe" to p.classe,
    "Nb d'élèves" to p.nbEleves,
    "Téléphone" to p.tel,
    "Observations" to p.observations
)

fun applyProf(p: ProfesseurV2, m: Map<String, String>) {
    p.nom = m["Nom"] ?: p.nom
    p.classe = m["Classe"] ?: ""
    p.nbEleves = m["Nb d'élèves"] ?: ""
    p.tel = m["Téléphone"] ?: ""
    p.observations = m["Observations"] ?: ""
}

/* ---------- Champs et application : CONTACT (RASED / Circo / PIAL) ---------- */

fun contactFields(contact: String, fonction: String, adresse: String,
                  mail: String, tel1: String, tel2: String, obs: String) = linkedMapOf(
    "Contact" to contact,
    "Fonction" to fonction,
    "Adresse" to adresse,
    "Mail" to mail,
    "Tél. 1" to tel1,
    "Tél. 2" to tel2,
    "Observations" to obs
)

fun applyContact(c: RasedEntry, m: Map<String, String>) {
    c.contact = m["Contact"] ?: ""; c.fonction = m["Fonction"] ?: ""
    c.adresse = m["Adresse"] ?: ""; c.mail = m["Mail"] ?: ""
    c.tel1 = m["Tél. 1"] ?: ""; c.tel2 = m["Tél. 2"] ?: ""
    c.observations = m["Observations"] ?: ""
}

fun applyContact(c: CircoEntry, m: Map<String, String>) {
    c.contact = m["Contact"] ?: ""; c.fonction = m["Fonction"] ?: ""
    c.adresse = m["Adresse"] ?: ""; c.mail = m["Mail"] ?: ""
    c.tel1 = m["Tél. 1"] ?: ""; c.tel2 = m["Tél. 2"] ?: ""
    c.observations = m["Observations"] ?: ""
}

fun applyContact(c: PialEntry, m: Map<String, String>) {
    c.contact = m["Contact"] ?: ""; c.fonction = m["Fonction"] ?: ""
    c.adresse = m["Adresse"] ?: ""; c.mail = m["Mail"] ?: ""
    c.tel1 = m["Tél. 1"] ?: ""; c.tel2 = m["Tél. 2"] ?: ""
    c.observations = m["Observations"] ?: ""
}

/* ---------- Champs et application : COLLÈGE ---------- */

fun collegeFields(col: CollegeV2) = linkedMapOf(
    "Nom" to col.nom,
    "Adresse" to col.adresse,
    "Contact" to col.contact,
    "Fonction contact" to col.fonction,
    "Adresse contact" to col.adresseContact,
    "Mail contact" to col.mailContact,
    "Tél. 1" to col.tel1,
    "Tél. 2" to col.tel2,
    "Observations" to col.observations
)

fun applyCollege(col: CollegeV2, m: Map<String, String>) {
    col.nom = m["Nom"] ?: col.nom
    col.adresse = m["Adresse"] ?: ""
    col.contact = m["Contact"] ?: ""
    col.fonction = m["Fonction contact"] ?: ""
    col.adresseContact = m["Adresse contact"] ?: ""
    col.mailContact = m["Mail contact"] ?: ""
    col.tel1 = m["Tél. 1"] ?: ""
    col.tel2 = m["Tél. 2"] ?: ""
    col.observations = m["Observations"] ?: ""
}
