package com.plime.annuaire.v2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ================================================================
   1. COMMUNES ET ÉCOLES
   ================================================================ */

@Composable
fun CommunesScreen(a: AnnuaireV2, query: String, onOpenCommune: (CommuneV2) -> Unit) {
    val q = query.trim().lowercase()
    val filtered = a.communes.filter {
        q.isEmpty() || it.nom.contains(q, true) ||
            it.ecoles.any { e -> e.nom.contains(q, true) || e.directeur.contains(q, true) } ||
            it.ecoles.any { e -> e.professeurs.any { p -> p.nom.contains(q, true) } }
    }
    if (a.communes.isEmpty()) {
        EmptyHint("Aucune commune. Utilisez l'import Excel ou le modèle dans RÉGLAGES.")
    } else Column(Modifier.fillMaxSize()) {
        Text("${filtered.size} commune(s) · ${a.communes.sumOf { it.ecoles.size }} école(s) · " +
            "${a.communes.sumOf { c -> c.ecoles.sumOf { it.professeurs.size } }} professeur(s)",
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            items(filtered) { c ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.fillMaxWidth().clickable { onOpenCommune(c) }.padding(14.dp)) {
                        Text(c.nom, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("${c.ecoles.size} école(s)", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun CommuneDetailScreen(c: CommuneV2, onBack: () -> Unit, onOpenEcole: (CommuneV2, EcoleV2) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
            Text(c.nom, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            item {
                Card(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        SectionTitle("Mairie")
                        InfoLine(Icons.Filled.Call, c.maire, c.telMaire)
                        InfoLine(Icons.Filled.LocationOn, c.adresseMairie)
                        InfoLine(Icons.Filled.Email, c.mail)
                        if (c.contactElu.isNotBlank()) {
                            SectionTitle("Élu référent")
                            InfoLine(Icons.Filled.Call, c.contactElu, c.telContactElu)
                        }
                        if (c.contactService.isNotBlank()) {
                            SectionTitle("Service")
                            InfoLine(Icons.Filled.Call, c.contactService, c.telContactService)
                        }
                        if (c.observations.isNotBlank()) InfoLine(null, "Observations : ${c.observations}")
                    }
                }
                SectionTitle("Écoles (${c.ecoles.size})")
            }
            items(c.ecoles) { e ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.fillMaxWidth().clickable { onOpenEcole(c, e) }.padding(14.dp)) {
                        Text(e.nom, fontWeight = FontWeight.Bold)
                        Text(listOfNotNull(
                            e.codeUai.takeIf { it.isNotBlank() }?.let { "UAI $it" },
                            e.directeur.takeIf { it.isNotBlank() }
                        ).joinToString(" · "), fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun EcoleDetailScreen(c: CommuneV2, e: EcoleV2, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
            Text(e.nom, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            item {
                Card(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        SectionTitle("École — ${c.nom}")
                        if (e.codeUai.isNotBlank()) InfoLine(null, "Code UAI : ${e.codeUai}")
                        InfoLine(Icons.Filled.LocationOn, e.adresse)
                        InfoLine(Icons.Filled.Call, "Tél.", e.tel)
                        InfoLine(Icons.Filled.Email, e.mail)
                        if (e.nbEleves.isNotBlank()) InfoLine(null, "Élèves : ${e.nbEleves}")
                        if (e.horairesMatin.isNotBlank() || e.horairesApresMidi.isNotBlank()) {
                            InfoLine(null, "Horaires : ${e.horairesMatin} / ${e.horairesApresMidi}")
                        }
                        if (e.directeur.isNotBlank()) {
                            SectionTitle("Direction")
                            InfoLine(Icons.Filled.Call, e.directeur, e.telDirecteur)
                        }
                        if (e.observations.isNotBlank()) InfoLine(null, "Observations : ${e.observations}")
                    }
                }
                SectionTitle("Professeurs (${e.professeurs.size})")
            }
            items(e.professeurs) { p ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(p.nom, fontWeight = FontWeight.Bold)
                        Text(listOfNotNull(
                            p.classe.takeIf { it.isNotBlank() },
                            p.nbEleves.takeIf { it.isNotBlank() }?.let { "$it élèves" },
                            p.tel.takeIf { it.isNotBlank() }
                        ).joinToString(" · "), fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (p.observations.isNotBlank())
                            Text(p.observations, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/* ================================================================
   2, 3, 4. ÉCRANS DE CONTACTS (RASED / CIRCONSCRIPTION / PIAL & ER)
   ================================================================ */

interface ContactRow {
    val contact: String
    val fonction: String
    val adresse: String
    val mail: String
    val tel1: String
    val tel2: String
    val observations: String
}

private val RasedEntry.asRow get() = object : ContactRow {
    override val contact get() = this@asRow.contact
    override val fonction get() = this@asRow.fonction
    override val adresse get() = this@asRow.adresse
    override val mail get() = this@asRow.mail
    override val tel1 get() = this@asRow.tel1
    override val tel2 get() = this@asRow.tel2
    override val observations get() = this@asRow.observations
}
private val CircoEntry.asRow get() = object : ContactRow {
    override val contact get() = this@asRow.contact
    override val fonction get() = this@asRow.fonction
    override val adresse get() = this@asRow.adresse
    override val mail get() = this@asRow.mail
    override val tel1 get() = this@asRow.tel1
    override val tel2 get() = this@asRow.tel2
    override val observations get() = this@asRow.observations
}
private val PialEntry.asRow get() = object : ContactRow {
    override val contact get() = this@asRow.contact
    override val fonction get() = this@asRow.fonction
    override val adresse get() = this@asRow.adresse
    override val mail get() = this@asRow.mail
    override val tel1 get() = this@asRow.tel1
    override val tel2 get() = this@asRow.tel2
    override val observations get() = this@asRow.observations
}

@Composable
fun RasedScreen(a: AnnuaireV2, query: String) =
    ContactsListScreen("RASED", a.rased.map { it.asRow }, query)

@Composable
fun CircoScreen(a: AnnuaireV2, query: String) =
    ContactsListScreen("Équipe de circonscription", a.circo.map { it.asRow }, query)

@Composable
fun PialScreen(a: AnnuaireV2, query: String) =
    ContactsListScreen("PIAL & ER", a.pial.map { it.asRow }, query)

@Composable
private fun ContactsListScreen(title: String, rows: List<ContactRow>, query: String) {
    val q = query.trim().lowercase()
    val filtered = rows.filter {
        q.isEmpty() || it.contact.contains(q, true) || it.fonction.contains(q, true) ||
            it.mail.contains(q, true)
    }
    Column(Modifier.fillMaxSize()) {
        Text("$title — ${filtered.size} contact(s)",
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        if (rows.isEmpty()) {
            EmptyHint("Aucun contact. Importez votre fichier Excel (onglet dédié).")
        } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            items(filtered) { r ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(r.contact, fontWeight = FontWeight.Bold)
                        if (r.fonction.isNotBlank())
                            Text(r.fonction, fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (r.adresse.isNotBlank())
                            InfoLine(Icons.Filled.LocationOn, r.adresse)
                        InfoLine(Icons.Filled.Email, r.mail)
                        InfoLine(Icons.Filled.Call, r.tel1, r.tel2)
                        if (r.observations.isNotBlank())
                            Text(r.observations, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/* ================================================================
   5. COLLÈGES
   ================================================================ */

@Composable
fun CollegesScreen(a: AnnuaireV2, query: String) {
    val q = query.trim().lowercase()
    val filtered = a.colleges.filter {
        q.isEmpty() || it.nom.contains(q, true) || it.contact.contains(q, true) ||
            it.ville().contains(q, true)
    }
    if (a.colleges.isEmpty()) {
        EmptyHint("Aucun collège. Importez votre fichier Excel (onglet Colleges).")
    } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        items(filtered) { col ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(col.nom, fontWeight = FontWeight.Bold)
                    if (col.adresse.isNotBlank())
                        InfoLine(Icons.Filled.LocationOn, col.adresse)
                    if (col.contact.isNotBlank()) {
                        InfoLine(Icons.Filled.Call, "${col.contact} · ${col.fonction}", col.tel1)
                        InfoLine(Icons.Filled.Email, col.mailContact)
                    }
                    if (col.observations.isNotBlank())
                        Text(col.observations, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun CollegeV2.ville(): String =
    adresse.substringAfterLast(", ").ifBlank { adresse }

/* ================================================================
   Composants communs
   ================================================================ */

@Composable
fun SectionTitle(t: String) {
    Text(t, Modifier.padding(top = 8.dp, bottom = 4.dp),
        fontWeight = FontWeight.Bold, fontSize = 15.sp)
}

@Composable
fun InfoLine(icon: Any?, vararg texts: String) {
    val content = texts.filter { it.isNotBlank() }.joinToString(" — ")
    if (content.isBlank()) return
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        when (icon) {
            is androidx.compose.ui.graphics.vector.ImageVector -> Icon(icon, null,
                Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            else -> Spacer(Modifier.width(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(content, fontSize = 14.sp)
    }
}

@Composable
fun EmptyHint(msg: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp))
    }
}
