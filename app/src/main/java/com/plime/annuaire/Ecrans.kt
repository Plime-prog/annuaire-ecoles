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
private val VertFonce = Color(0xFF1F7A4A)
private val VertPale = Color(0xFFEAF7F0)
private val VertContour = Color(0xFFD3EBDD)
private val GrisTexte = Color(0xFF6B7B72)

fun nomCourt(nomCirco: String): String =
    nomCirco.removePrefix("Circonscription de ")
        .replace("Saint-Julien-Arcis", "Saint-Julien/Arcis")

@Composable
private fun ChipFiltre(texte: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) VertMenthe else VertPale,
        border = BorderStroke(1.dp, if (selected) VertMenthe else VertContour),
        shadowElevation = if (selected) 3.dp else 0.dp
    ) {
        Text(
            texte,
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = if (selected) Color.White else VertFonce,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}

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
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            item { ChipFiltre("\uD83C\uDFDB DSDEN", filtre == "dsden") { onFiltre("dsden") } }
            items(circos) { c ->
                ChipFiltre(nomCourt(c.nom), filtre == c.code) { onFiltre(c.code) }
            }
        }
        HorizontalDivider(color = VertContour, thickness = 1.dp)
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
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            itemsIndexed(onglets) { i, n ->
                ChipFiltre(n, i == ongletIndex) { onOnglet(i) }
            }
        }
        Box(Modifier.weight(1f)) { bas() }
    }
}

@Composable
fun ListeEcolesParCommune(
    communes: List<Pair<String, List<MutableMap<String, String>>>>,
    titreEcole: (Map<String, String>) -> String,
    onOpenRow: (MutableMap<String, String>) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(communes) { (nomCommune, ecoles) ->
            CarteCommune(nomCommune, ecoles, titreEcole, onOpenRow)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CarteCommune(
    nom: String,
    ecoles: List<MutableMap<String, String>>,
    titreEcole: (Map<String, String>) -> String,
    onOpenRow: (MutableMap<String, String>) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, VertContour)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().background(VertPale)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.LocationCity, null,
                    Modifier.size(22.dp), tint = VertFonce
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    nom,
                    Modifier.weight(1f),
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = VertFonce
                )
                Surface(shape = RoundedCornerShape(50), color = VertMenthe) {
                    Text(
                        if (ecoles.size == 1) "1 \u00E9cole" else "${ecoles.size} \u00E9coles",
                        Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
            ecoles.forEachIndexed { i, e ->
                if (i > 0) Horizont
