package com.plime.annuaire.v6

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plime.annuaire.R

/* Logo officiel « Académie de Reims – DSDEN de l'Aube », construit comme
   une signature de l'État : bloc Marianne (tracé officiel du système de
   design de l'État, dans res/drawable/marianne_officielle.xml), intitulé
   ACADÉMIE DE REIMS sur deux lignes, devise républicaine en italique,
   trait séparateur vertical, puis le bloc des services départementaux.
   Il s'affiche dans la barre du haut tant qu'aucun logo.jpg personnalisé
   n'a été choisi ; toucher le logo ouvre le sélecteur d'image.
   Le paramètre « echelle » agrandit TOUT le logo nativement (tailles de
   layout incluses) : 1f = hauteur d'origine 42 dp, 2.4f = 100 dp environ.
   Ne pas utiliser Modifier.scale() : il agrandit le dessin sans agrandir
   l'espace réservé, ce qui rogne le logo et le décale. */
@Composable
fun LogoOfficielV6(modifier: Modifier = Modifier, echelle: Float = 1f) {
    val k = echelle
    Row(modifier.height((42 * k).dp), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Image(
                painterResource(R.drawable.marianne_officielle),
                "Bloc Marianne",
                Modifier.width((22 * k).dp).height((9 * k).dp)
            )
            Text("ACADÉMIE", fontSize = (6.5 * k).sp, fontWeight = FontWeight.Bold, lineHeight = (7 * k).sp)
            Text("DE REIMS", fontSize = (6.5 * k).sp, fontWeight = FontWeight.Bold, lineHeight = (7 * k).sp)
            Text("Liberté", fontSize = (2.8 * k).sp, fontStyle = FontStyle.Italic, lineHeight = (3.2 * k).sp)
            Text("Égalité", fontSize = (2.8 * k).sp, fontStyle = FontStyle.Italic, lineHeight = (3.2 * k).sp)
            Text("Fraternité", fontSize = (2.8 * k).sp, fontStyle = FontStyle.Italic, lineHeight = (3.2 * k).sp)
        }
        Spacer(Modifier.width((6 * k).dp))
        Box(Modifier.width((0.5 * k).dp).height((37 * k).dp).background(Color.Black))
        Spacer(Modifier.width((6 * k).dp))
        Column {
            Text("Direction des services départementaux", fontSize = (3.2 * k).sp, fontWeight = FontWeight.Bold, lineHeight = (3.6 * k).sp)
            Text("de l'éducation nationale", fontSize = (3.2 * k).sp, fontWeight = FontWeight.Bold, lineHeight = (3.6 * k).sp)
            Text("de l'Aube", fontSize = (3.2 * k).sp, fontWeight = FontWeight.Bold, lineHeight = (3.6 * k).sp)
        }
    }
}
