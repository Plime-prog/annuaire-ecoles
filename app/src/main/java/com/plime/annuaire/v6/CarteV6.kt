package com.plime.annuaire.v6

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.roundToInt

/* ==================== CARTE DE L'AUBE (hors ligne) ==================== */

/* Dessinee a partir de l'asset aube-geo.json : contour du departement
   + une etiquette par circonscription. Chaque etiquette a sa position
   (dx, dy) fixee dans l'asset : autour de Troyes elles sont placees
   en eventail pour ne jamais se chevaucher. Toucher une etiquette
   (ou un nom dans la liste) choisit la circonscription du filtre. */

private val VertCarte = Color(0xFF3CB371)
private val VertCarteFonce = Color(0xFF2E8B57)
private val FondCarte = Color(0xFFEAF7F0)

data class PinCircoV6(
    val code: String, val nom: String,
    val lon: Double, val lat: Double,
    val dx: Int, val dy: Int
)

object GeoAubeV6 {

    private fun lire(ctx: Context): JSONObject =
        ctx.assets.open("aube-geo.json").bufferedReader().use { it.readText() }
            .let { JSONObject(it) }

    fun dept(ctx: Context): List<Pair<Double, Double>> {
        val arr = lire(ctx).getJSONArray("dept")
        val pts = mutableListOf<Pair<Double, Double>>()
        for (i in 0 until arr.length()) {
            val p = arr.getJSONArray(i)
            pts.add(p.getDouble(0) to p.getDouble(1))
        }
        return pts
    }

    fun circos(ctx: Context): List<PinCircoV6> {
        val arr = lire(ctx).getJSONArray("circos")
        val out = mutableListOf<PinCircoV6>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(PinCircoV6(
                o.getString("code"), o.getString("nom"),
                o.getDouble("lon"), o.getDouble("lat"),
                o.optInt("dx", -55), o.optInt("dy", -46)
            ))
        }
        return out
    }
}

/* Projection plate carree : la longitude est etiree selon la latitude
   moyenne de l'Aube (~48,3 degres) pour rester fidele. */
private class ProjectionV6(pts: List<Pair<Double, Double>>) {
    private val minLon = pts.minOf { it.first }
    private val maxLon = pts.maxOf { it.first }
    private val minLat = pts.minOf { it.second }
    private val maxLat = pts.maxOf { it.second }
    private val kx = cos(Math.toRadians((minLat + maxLat) / 2.0))
    val spanX = (maxLon - minLon) * kx
    val spanY = maxLat - minLat

    fun x(lon: Double): Double = (lon - minLon) * kx
    fun y(lat: Double): Double = maxLat - lat
    fun echelle(w: Float, h: Float): Float =
        minOf(w / spanX.toFloat(), h / spanY.toFloat()) * 0.86f
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarteScreenV6(
    filtreActif: String,
    onCirco: (String) -> Unit,
    onFermer: () -> Unit
) {
    val ctx = LocalContext.current
    val dept = remember { GeoAubeV6.dept(ctx) }
    val pins = remember { GeoAubeV6.circos(ctx) }
    val proj = remember { ProjectionV6(dept) }
    var taille by remember { mutableStateOf(IntSize(1, 1)) }

    /* Etiquette : position du coin haut-gauche (px) + point reel relie par un trait */
    data class Place(val x: Float, val y: Float, val reelle: Offset)
    val places = remember(taille) {
        if (taille.width < 2 || taille.height < 2) emptyList()
        else {
            val k = proj.echelle(taille.width.toFloat(), taille.height.toFloat())
            val ox = (taille.width - proj.spanX * k).toFloat() / 2f
            val oy = (taille.height - proj.spanY * k).toFloat() / 2f
            pins.map { p ->
                val px = ox + (proj.x(p.lon) * k).toFloat()
                val py = oy + (proj.y(p.lat) * k).toFloat()
                Place(px + p.dx, py + p.dy, Offset(px, py))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Carte de l'Aube", fontWeight = FontWeight.Bold, color = VertCarte) },
                navigationIcon = {
                    IconButton(onClick = onFermer) { Icon(Icons.Filled.ArrowBack, "Retour") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            /* --- La carte --- */
            Box(Modifier.fillMaxWidth().weight(1f).onSizeChanged { taille = it }) {
                Canvas(Modifier.fillMaxSize()) {
                    val k = proj.echelle(size.width, size.height)
                    val ox = (size.width - proj.spanX * k).toFloat() / 2f
                    val oy = (size.height - proj.spanY * k).toFloat() / 2f
                    val path = Path()
                    dept.forEachIndexed { i, (lon, lat) ->
                        val px = ox + (proj.x(lon) * k).toFloat()
                        val py = oy + (proj.y(lat) * k).toFloat()
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    path.close()
                    drawPath(path, FondCarte)
                    drawPath(path, VertCarte, style = Stroke(width = 4f))
                    /* Point reel + trait vers l'etiquette */
                    places.forEach { pl ->
                        drawCircle(VertCarte, radius = 7f, center = pl.reelle)
                        drawLine(
                            VertCarte,
                            pl.reelle,
                            Offset(pl.x + 55f, pl.y + 30f),
                            strokeWidth = 3f
                        )
                    }
                }
                places.zip(pins).forEach { (pl, pin) ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (pin.code == filtreActif) VertCarteFonce else VertCarte,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset { IntOffset(pl.x.roundToInt(), pl.y.roundToInt()) }
                            .clickable { onCirco(pin.code) }
                    ) {
                        Text(
                            pin.nom,
                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = Color.White, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            HorizontalDivider()
            /* --- Liste de secours : les 7 circonscriptions --- */
            Text(
                "Ou choisir une circonscription :",
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = VertCarte, fontWeight = FontWeight.Bold
            )
            Column(
                Modifier.fillMaxWidth().weight(0.55f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
            ) {
                pins.forEach { pin ->
                    val actif = pin.code == filtreActif
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (actif) VertCarte else Color.White,
                        border = BorderStroke(2.dp, VertCarte),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clickable { onCirco(pin.code) }
                    ) {
                        Text(
                            pin.nom,
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            color = if (actif) Color.White else Color.Black,
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
