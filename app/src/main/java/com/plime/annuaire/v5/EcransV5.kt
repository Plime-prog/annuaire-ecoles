package com.plime.annuaire.v5

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val VertMenthe = Color(0xFF3CB371)

@Composable
private fun EncadreV5(
    onClick: (() -> Unit)? = null,
    contenu: @Composable () -> Unit
) {
    val base = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)
    Surface(
        modifier = if (onClick != null) base.clickable(onClick = onClick) else base,
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(2.dp, VertMenthe)
    ) { Column(Modifier.padding(12.dp)) { contenu() } }
}

@Composable
private fun TitreSection(texte: String) {
    Text(
        texte, color = VertMenthe, fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp)
    )
}

@Composable
private fun CarteContact(c: ContactV5, onClickTel: (() -> Unit)? = null) {
    EncadreV5 {
        Text(c.nom, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        if (c.fonction.isNotBlank()) Text(c.fonction, fontSize = 13.sp, color = Color(0xFF444444))
        if (c.tel.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Call, null, tint = VertMenthe, modifier = Modifier.padding(start = 4.dp))
            Text("  ${c.tel}", fontSize = 13.sp, color = VertMenthe)
        }
        if (c.mail.isNotBlank()) Text("✉ ${c.mail}", fontSize = 12.sp, color = Color(0xFF666666))
        if (c.note.isNotBlank()) Text(c.note, fontSize = 12.sp, color = Color(0xFF888888))
    }
}

/* ==================== ÉCRAN 1 : DSDEN ==================== */

@Composable
fun EcranDsdenV5(
    dsden: DsdenV5?,
    circos: List<CircoV5>,
    onOpenCirco: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = VertMenthe) }
            Text("DSDEN de l'Aube", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = VertMenthe)
        }
        if (dsden == null) {
            Text(
                "Fichier aube-dsden.json introuvable dans les assets.",
                Modifier.padding(16.dp), color = Color(0xFFB3261E)
            )
            return@Column
        }
        TitreSection("Coordonnées")
        CarteContact(ContactV5(dsden.coordonnees.nom, dsden.coordonnees.fonction, dsden.coordonnees.tel, dsden.coordonnees.mail))
        TitreSection("Direction académique")
        dsden.direction.forEach { CarteContact(it) }
        if (dsden.secretariat.isNotEmpty()) {
            TitreSection("Secrétariat")
            dsden.secretariat.forEach { CarteContact(it) }
        }
        dsden.bureaux.forEach { b ->
            TitreSection(b.nom)
            b.membres.forEach { CarteContact(it) }
        }
        TitreSection("Les ${circos.size} circonscriptions du 1er degré")
        circos.forEach { c ->
            EncadreV5(onClick = { onOpenCirco(c.code) }) {
                Text(c.nom, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("IEN : ${c.ien}", fontSize = 13.sp, color = Color(0xFF444444))
                Text(c.tel, fontSize = 12.sp, color = Color(0xFF666666))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/* ==================== ÉCRAN 2 : CIRCONSCRIPTION ==================== */

@Composable
fun EcranCircoV5(
    circo: CircoV5,
        communes: List<Pair<String, List<MutableMap<String, String>>>>,  /* (commune, lignes écoles) */
    titreEcole: (Map<String, String>) -> String,
    onOpenRow: (MutableMap<String, String>) -> Unit,
) {
    var ouvertes by remember { mutableStateOf(setOf<String>()) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = VertMenthe) }
                Column {
                    Text(circo.nom, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = VertMenthe)
                    Text("${communes.sumOf { it.second.size }} école(s) · ${communes.size} commune(s)", fontSize = 12.sp, color = Color(0xFF666666))
                }
            }
        }
        item {
            TitreSection("Inspection")
            EncadreV5 {
                Text(circo.ien, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Inspecteur de l'Éducation nationale", fontSize = 13.sp, color = Color(0xFF444444))
                Text(circo.adresse, fontSize = 12.sp, color = Color(0xFF666666))
                Text(circo.tel, fontSize = 12.sp, color = Color(0xFF666666))
                Text("✉ ${circo.mail}", fontSize = 12.sp, color = Color(0xFF666666))
            }
        }
        if (circo.assistante.isNotBlank()) item {
            TitreSection("Assistante")
            EncadreV5 { Text(circo.assistante, fontWeight = FontWeight.Bold) }
        }
        if (circo.cpc.isNotEmpty()) item { TitreSection("CPC") }
        items(circo.cpc) { CarteContact(it) }
        if (circo.erun.isNotEmpty()) item { TitreSection("ERUN") }
        items(circo.erun) { CarteContact(it) }
        if (circo.rased.isNotEmpty()) item { TitreSection("RASED") }
        items(circo.rased) { CarteContact(it) }
        if (circo.pialEr.isNotEmpty()) item { TitreSection("PIAL-ER") }
        items(circo.pialEr) { CarteContact(it) }
        if (circo.colleges.isNotEmpty()) {
            item {
                TitreSection("Collèges partenaires")
                EncadreV5 {
                    circo.colleges.forEach { Text("• $it", fontSize = 14.sp) }
                }
            }
        }
        item { TitreSection("Écoles de la circonscription") }
        communes.forEach { (nomCommune, ecoles) ->
            item {
                val open = nomCommune in ouvertes
                EncadreV5(onClick = {
                    ouvertes = if (open) ouvertes - nomCommune else ouvertes + nomCommune
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(nomCommune, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text("${ecoles.size} école(s)", fontSize = 12.sp, color = Color(0xFF666666))
                        Icon(
                            if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            null, tint = VertMenthe
                        )
                    }
                }
            }
            if (nomCommune in ouvertes) {
                items(ecoles) { e ->
                    EncadreV5(onClick = { onOpenRow(e) }) {
                        Text(titreEcole(e), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
