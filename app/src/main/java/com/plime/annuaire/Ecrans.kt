package com.plime.annuaire

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
private val GrisClair = Color(0xFFE0E0E0)   /* fond non selectionne */
private val GrisTexte = Color(0xFF888888)   /* texte non selectionne */
private val VertPale = Color(0xFFEAF7F0)   /* fond vert pale des pastilles ecoles */

/** Nom affiche dans le filtre : " Troyes ", " Bar-sur-Aube "...
    Saint-Julien s'ecrit " Saint-Julien/Arcis ", comme sur la carte. */
fun nomCourt(nomCirco: String): String =
    nomCirco.removePrefix("Circonscription de ")
        .replace("Saint-Julien-Arcis", "Saint-Julien/Arcis")

@Composable
private fun Encadre(
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

/* ==================== ECRAN D'ACCUEIL ==================== */

/**
 * Accueil v6 : plus de logo ni de grand titre dans la page (le logo est
 * dans la barre du haut). Les chips de filtre sont collees tout en haut,
 * la ligne verte, puis les onglets contextuels - le bloc du bas occupe
 * TOUTE la place restante.
 *
 *des qu'une circonscription (ou la DSDEN) est selectionnee, la
 * rangee de boutons defile automatiquement pour montrer le bouton actif.
 * Idem pour la rangee des onglets (Communes, Circonscription, Colleges...).
 */
@Composable
fun EcranAccueil(
    filtre: String,
    circos: List<Circo>,
    onFiltre: (String) -> Unit,
    onglets: List<String>,
    ongletIndex: Int,
    onOnglet: (Int) -> Unit,
    bas: @Composable () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        /* ---- Filtres colles en haut : DSDEN puis les 7 circonscriptions ---- */
        /* Position du bouton selectionne : 0 = DSDEN, sinon 1 + rang de la
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
            item { ChipFiltre("\uD83C\uDFDB DSDEN", filtre == "dsden") { onFiltre("dsden") } }
            items(circos) { c ->
                ChipFiltre(nomCourt(c.nom), filtre == c.code) { onFiltre(c.code) }
            }
        }
        HorizontalDivider(color = VertMenthe, thickness = 2.dp)
        /* ---- Onglets contextuels : memes chips vert/gris que le haut ---- */
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

/* ==================== ECOLES DE LA CIRCO (communes depliantes) ==================== */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListeEcolesParCommune(
    communes: List<Pair<String, List<MutableMap<String, String>>>>,
    titreEcole: (Map<String, String>) -> String,
    onOpenRow: (MutableMap<String, String>) -> Unit
) {
    /* Pastilles compactes : le nom de la commune (gras) suivi
       DIRECTEMENT de ses ecoles en pastilles vert pale ; une fine
       ligne de separation verte isole chaque commune. Une touche
       sur une pastille ecole ouvre sa fiche. */
    LazyColumn(Modifier.fillMaxSize()) {
        communes.forEach { (nomCommune, ecoles) ->
            item {
                FlowRow(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    /* Toutes les pastilles sur deux lignes au maximum. */
                    maxLines = 2
                ) {
                    /* Pastille commune : centree verticalement avec
                       les pastilles ecoles qui l'accompagnent. */
                    Text(
                        nomCommune,
                        Modifier.padding(top = 3.dp),
                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = GrisTexte
                    )
                    /* Pastilles ecoles : vert pale, texte normal et
                       plus petit. Une touche ouvre la fiche. */
                    ecoles.forEach { e ->
                        Surface(
                            onClick = { onOpenRow(e) },
                            shape = RoundedCornerShape(50),
                            color = VertPale,
                            border = BorderStroke(1.dp, VertMenthe)
                        ) {
                            Text(
                                titreEcole(e),
                                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                color = Color.Black
                            )
                        }
                    }
                }
                /* Petite ligne de separation verte entre les communes. */
                HorizontalDivider(
                    Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    thickness = 1.dp, color = VertMenthe
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
