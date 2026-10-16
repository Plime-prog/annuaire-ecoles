package com.plime.annuaire

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

/* Logo officiel " Academie de Reims - DSDEN de l'Aube ", construit comme
   une signature de l'Etat : bloc Marianne (trace officiel du systeme de
   design de l'Etat, dans res/drawable/marianne_officielle.xml), intitule
   ACADEMIE DE REIMS sur deux lignes, devise republicaine en italique,
   trait separateur vertical, puis le bloc des services departementaux.
   Il s'affiche dans la barre du haut tant qu'aucun logo.jpg personnalise
   n'a ete choisi ; toucher le logo ouvre le selecteur d'image.
   Le parametre " echelle " agrandit TOUT le logo nativement (tailles de
   layout incluses) : 1f = hauteur d'origine 42 dp, 2.4f = 100 dp environ.
   Ne pas utiliser Modifier.scale() : il agrandit le dessin sans agrandir
   l'espace reserve, ce qui rogne le logo et le decale. */
@Composable
fun LogoOfficiel(
    modifier: Modifier = Modifier,
    echelle: Float = 1f,
    /* nom de la circonscription ajoute en vert sous " de l'Aube ".
       Chaine vide (ou DSDEN) = rien d'affiche. */
    circonscription: String = ""
) {
    val k = echelle
    Row(modifier.height((42 * k).dp), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Image(
                painterResource(R.drawable.marianne_officielle),
                "Bloc Marianne",
                Modifier.width((22 * k).dp).height((9 * k).dp)
            )
            Text("ACAD\u00C9MIE", fontSize = (6.5 * k).sp, fontWeight = FontWeight.Bold, lineHeight = (7 * k).sp)
            Text("DE REIMS", fontSize = (6.5 * k).sp, fontWeight = FontWeight.Bold, lineHeight = (7 * k).sp)
            Text("Libert\u00E9", fontSize = (2.8 * k).sp, fontStyle = FontStyle.Italic, lineHeight = (3.2 * k).sp)
            Text("\u00C9galit\u00E9", fontSize = (2.8 * k).sp, fontStyle = FontStyle.Italic, lineHeight = (3.2 * k).sp)
            Text("Fraternit\u00E9", fontSize = (2.8 * k).sp, fontStyle = FontStyle.Italic, lineHeight = (3.2 * k).sp)
        }
        Spacer(Modifier.width((6 * k).dp))
        Box(Modifier.width((0.5 * k).dp).height((37 * k).dp).background(Color.Black))
        Spacer(Modifier.width((6 * k).dp))
        Column {
            Text("Direction des services d\u00E9partementaux", fontSize = (3.2 * k).sp, fontWeight = FontWeight.Bold, lineHeight = (3.6 * k).sp)
            Text("de l'\u00E9ducation nationale", fontSize = (3.2 * k).sp, fontWeight = FontWeight.Bold, lineHeight = (3.6 * k).sp)
            Text("de l'Aube", fontSize = (3.2 * k).sp, fontWeight = FontWeight.Bold, lineHeight = (3.6 * k).sp)
            if (circonscription.isNotBlank()) {
                Spacer(Modifier.height((1.2 * k).dp))
                Text(
                    "Circonscription de " + circonscription,
                    fontSize = (2.6 * k).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF3CB371),
                    lineHeight = (3 * k).sp,
                    maxLines = 2
                )
            }
        }
    }
}
