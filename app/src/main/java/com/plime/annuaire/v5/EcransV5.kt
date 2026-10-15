package com.plime.annuaire.v5

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
private val GrisClair = Color(0xFFE0E0E0)   /* fond non sélectionné */
private val GrisTexte = Color(0xFF888888)   /* texte non sélectionné */

/** Nom affiché dans le filtre : « Troyes », « Bar-sur-Aube »… */
fun nomCourt(nomCirco: String): String = nomCirco.removePrefix("Circonscription de ")

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
private fun ChipFiltre(texte: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) VertMenthe else GrisClair,
        border = BorderStroke(2.dp, if (selected) VertMenthe else GrisClair)
    ) {
        Text(
            texte,
            Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            color = if (selected) Color.White else GrisTexte,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        )
    }
}

/* ==================== ÉCRAN D'ACCUEIL ==================== */

/**
 * Accueil v6 : plus de logo ni de grand titre dans la page (le logo est
 * dans la barre du haut). Les chips de filtre sont collées tout en haut,
 * la ligne verte, puis les onglets contextuels — le bloc du bas occupe
 * TOUTE la place restante.
 *
 * V6 : dès qu'une circonscription (ou la DSDEN) est sélectionnée, la
 * rangée de boutons défile automatiquement pour montrer le bouton actif.
 * Idem pour la rangée des onglets (Communes, Circonscription, Collèges...).
 */
@Composable
fun EcranAccueilV5(
    filtre: String,
    circos: List<CircoV5>,
    onFiltre: (String) -> Unit,
    onglets: List<String>,
    ongletIndex: Int,
    onOnglet: (Int) -> Unit,
    bas: @Composable () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        /* ---- Filtres collés en haut : DSDEN puis les 7 circonscriptions ---- */
        /* Position du bouton sélectionné : 0 = DSDEN, sinon 1 + rang de la
           circonscription dans la liste. */
        val indexFiltre = if (filtre == "dsden") 0
            else 1 + (circos.indexOfFirst { it.code == filtre }).coerceAtLeast(-1)
        val etatFiltres = rememberLazyListState()
        LaunchedEffect(filtre, circos) {
            if (indexFiltre < 1 + circos.size) {
                etatFiltres.animateScrollToItem(indexFiltre)
            }
        }
        LazyRow(
            state = etatFiltres,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            item { ChipFiltre("🏛 DSDEN", filtre == "dsden") { onFiltre("dsden") } }
            items(circos) { c ->
                ChipFiltre(nomCourt(c.nom), filtre == c.code) { onFiltre(c.code) }
            }
        }
        HorizontalDivider(color = VertMenthe, thickness = 2.dp)
        /* ---- Onglets contextuels : mêmes chips vert/gris que le haut ---- */
        val etatOnglets = rememberLazyListState()
        LaunchedEffect(ongletIndex, onglets) {
            if (ongletIndex < onglets.size) {
                etatOnglets.animateScrollToItem(ongletIndex)
            }
        }
        LazyRow(
            state = etatOnglets,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            itemsIndexed(onglets) { i, n ->
                ChipFiltre(n, i == ongletIndex) { onOnglet(i) }
            }
        }
        Box(Modifier.weight(1f)) { bas() }
    }
}

/* ==================== ÉCOLES DE LA CIRCO (communes dépliantes) ==================== */

@Composable
fun ListeEcolesParCommuneV5(
    communes: List<Pair<String, List<MutableMap<String, String>>>>,
    titreEcole: (Map<String, String>) -> String,
    onOpenRow: (MutableMap<String, String>) -> Unit
) {
    /* Une seule commune dépliée à la fois */
    var communeOuverte by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize()) {
        communes.forEach { (nomCommune, ecoles) ->
            item {
                val open = nomCommune == communeOuverte
                /* Même système : verte si ouverte, grisée sinon */
                Surface(
                    onClick = {
                        communeOuverte = if (open) null else nomCommune
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = if (open) VertMenthe else GrisClair,
                    border = BorderStroke(2.dp, if (open) VertMenthe else GrisClair)
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            nomCommune, fontSize = 15.sp,
                            fontWeight = if (open) FontWeight.Bold else FontWeight.Normal,
                            color = if (open) Color.White else GrisTexte,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${ecoles.size} école(s)", fontSize = 12.sp,
                            color = if (open) Color.White else GrisTexte
                        )
                        Icon(
                            if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            null,
                            tint = if (open) Color.White else GrisTexte
                        )
                    }
                }
            }
            if (nomCommune == communeOuverte) {
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
