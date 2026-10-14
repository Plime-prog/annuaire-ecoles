package com.plime.annuaire.v6

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.pointerInput
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

/* Dessinee a partir de deux assets :
   - aube-geo.json  : contour du departement + une etiquette par circonscription ;
   - aube-zones.json : contour des communes ayant au moins une ecole,
     groupees par circonscription (open data IGN + annuaire MENJ).
   Chaque circonscription a sa couleur ; celle choisie est en teinte foncee.
   Toucher une commune coloree selectionne sa circonscription.
   Les etiquettes de carte ne concernent que les 6 circonscriptions
   territoriales : Troyes ASH (0100042S) est adaptee, sans territoire
   propre, elle se choisit dans la zone basse qui liste les 7. */

private val VertCarte = Color(0xFF3CB371)
private val VertCarteFonce = Color(0xFF2E8B57)
private val FondCarte = Color(0xFFEAF7F0)
private val BordCommune = Color(0x33000000)
private val CodeAsh = "0100042S"

/* Couleur de chaque circonscription : pastel au repos, foncee si choisie */
private data class CouleurZone(val pastel: Color, val foncee: Color)

private val CouleursZones = mapOf(
    "0100041R" to CouleurZone(Color(0xFFA8D5A2), Color(0xFF2E7D32)), /* Troyes */
    "0100043T" to CouleurZone(Color(0xFF9FD6DF), Color(0xFF00838F)), /* Saint-Julien */
    "0100044U" to CouleurZone(Color(0xFFAECFE8), Color(0xFF1565C0)), /* Romilly */
    "0100045V" to CouleurZone(Color(0xFFF5C08A), Color(0xFFE65100)), /* Bar-sur-Aube */
    "0100820M" to CouleurZone(Color(0xFFD7BDE5), Color(0xFF6A1B9A)), /* Bar-sur-Seine */
    "0100981M" to CouleurZone(Color(0xFFF7DC94), Color(0xFFB7950B))  /* La Chapelle-Saint-Luc */
)

private fun couleurZone(code: String): CouleurZone =
    CouleursZones[code] ?: CouleurZone(VertCarte, VertCarteFonce)

data class PinCircoV6(
    val code: String, val nom: String,
    val lon: Double, val lat: Double,
    val dx: Int, val dy: Int
)

data class ZoneCircoV6(
    val code: String,
    val contours: List<List<Pair<Double, Double>>>
)

object GeoAubeV6 {

    private fun lire(ctx: Context, nom: String): JSONObject =
        ctx.assets.open(nom).bufferedReader().use { it.readText() }
            .let { JSONObject(it) }

    fun dept(ctx: Context): List<Pair<Double, Double>> {
        val arr = lire(ctx, "aube-geo.json").getJSONArray("dept")
        val pts = mutableListOf<Pair<Double, Double>>()
        for (i in 0 until arr.length()) {
            val p = arr.getJSONArray(i)
            pts.add(p.getDouble(0) to p.getDouble(1))
        }
        return pts
    }

    fun circos(ctx: Context): List<PinCircoV6> {
        val arr = lire(ctx, "aube-geo.json").getJSONArray("circos")
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

    /* Contours des communes par circonscription (asset aube-zones.json) */
    fun zones(ctx: Context): List<ZoneCircoV6> {
        val racine = lire(ctx, "aube-zones.json").getJSONObject("zones")
        val out = mutableListOf<ZoneCircoV6>()
        for (code in racine.keys()) {
            val communes = racine.getJSONArray(code)
            val contours = mutableListOf<List<Pair<Double, Double>>>()
            for (i in 0 until communes.length()) {
                val anneau = mutableListOf<Pair<Double, Double>>()
                val pts = communes.getJSONArray(i)
                for (j in 0 until pts.length()) {
                    val p = pts.getJSONArray(j)
                    anneau.add(p.getDouble(0) to p.getDouble(1))
                }
                contours.add(anneau)
            }
            out.add(ZoneCircoV6(code, contours))
        }
        return out
    }
}

/* Projection plate carree : la longitude est etiree selon la latitude
   moyenne de l'Aube (~48,3 degres) pour rester fidele. */
private class ProjectionV6(pts: List<Pair<Double, Double>>) {
    val minLon = pts.minOf { it.first }
    val maxLat = pts.maxOf { it.second }
    private val maxLon = pts.maxOf { it.first }
    private val minLat = pts.minOf { it.second }
    val kx = cos(Math.toRadians((minLat + maxLat) / 2.0))
    val spanX = (maxLon - minLon) * kx
    val spanY = maxLat - minLat

    fun x(lon: Double): Double = (lon - minLon) * kx
    fun y(lat: Double): Double = maxLat - lat
    fun echelle(w: Float, h: Float): Float =
        minOf(w / spanX.toFloat(), h / spanY.toFloat()) * 0.86f
}

/* Parametres d'affichage pour une taille donnee */
private data class ParamsV6(val k: Float, val ox: Float, val oy: Float)

private fun paramsV6(proj: ProjectionV6, w: Float, h: Float): ParamsV6 {
    val k = proj.echelle(w, h)
    return ParamsV6(
        k,
        (w - (proj.spanX * k).toFloat()) / 2f,
        (h - (proj.spanY * k).toFloat()) / 2f
    )
}

/* Test point dans un polygone (lancement de rayon) */
private fun dansPolygone(lon: Double, lat: Double, anneau: List<Pair<Double, Double>>): Boolean {
    var dedans = false
    var j = anneau.size - 1
    for (i in anneau.indices) {
        val (xi, yi) = anneau[i]
        val (xj, yj) = anneau[j]
        if ((yi > lat) != (yj > lat) &&
            lon < (xj - xi) * (lat - yi) / (yj - yi) + xi
        ) dedans = !dedans
        j = i
    }
    return dedans
}

/* ==================== ETIQUETTES SANS CHEVAUCHEMENT ==================== */

/* Etiquette : rectangle (x, y, w, h) + point reel relie par un trait.
   Position initiale : point reel + decalage (dx, dy) de l'asset.
   Puis relaxations successives :
   - deux etiquettes qui se touchent sont poussees l'une loin de l'autre ;
   - chaque etiquette est eloignee de son propre point d'au moins 4 px ;
   - tout reste dans le cadre de la carte. */

private data class Etiquette(
    val pin: PinCircoV6,
    val reelle: Offset,
    var x: Float, var y: Float,
    val w: Float, val h: Float
)

private fun largeurEtiquette(nom: String): Float =
    24f + nom.length * 7.5f   /* approximation pour 13 sp */

private fun placerEtiquettes(
    pins: List<PinCircoV6>,
    params: ParamsV6,
    proj: ProjectionV6,
    largeur: Float,
    hauteur: Float
): List<Etiquette> {
    val marge = 6f
    val ecartMin = 14f   /* espace minimal entre deux etiquettes */
    val etqs = pins.filter { it.code != CodeAsh }.map { p ->
        val px = params.ox + (proj.x(p.lon) * params.k).toFloat()
        val py = params.oy + (proj.y(p.lat) * params.k).toFloat()
        val w = largeurEtiquette(p.nom)
        val h = 30f
        Etiquette(p, Offset(px, py), px + p.dx, py + p.dy, w, h)
    }
    repeat(160) {
        var deplacement = false
        /* 1. poussees mutuelles si rectangles gonfles se chevauchent */
        for (a in etqs.indices) {
            for (b in a + 1 until etqs.size) {
                val e1 = etqs[a]; val e2 = etqs[b]
                val g1 = e1.x - ecartMin; val d1 = e1.x + e1.w + ecartMin
                val h1 = e1.y - ecartMin; val b1 = e1.y + e1.h + ecartMin
                val g2 = e2.x; val d2 = e2.x + e2.w
                val h2 = e2.y; val b2 = e2.y + e2.h
                if (d1 > g2 && d2 > g1 && b1 > h2 && b2 > h1) {
                    /* cote le plus court pour se separer */
                    val pousseX = minOf(d1 - g2, d2 - g1)
                    val pousseY = minOf(b1 - h2, b2 - h1)
                    if (pousseX <= pousseY) {
                        val s = if (e1.x + e1.w / 2f <= e2.x + e2.w / 2f) -1f else 1f
                        val pas = (pousseX / 2f).coerceAtLeast(2f)
                        e1.x += s * pas; e2.x -= s * pas
                    } else {
                        val s = if (e1.y + e1.h / 2f <= e2.y + e2.h / 2f) -1f else 1f
                        val pas = (pousseY / 2f).coerceAtLeast(2f)
                        e1.y += s * pas; e2.y -= s * pas
                    }
                    deplacement = true
                }
            }
        }
        /* 2. chaque etiquette reste a distance de son point reel */
        for (e in etqs) {
            val cx = e.x + e.w / 2f
            val cy = e.y + e.h / 2f
            val dx = cx - e.reelle.x
            val dy = cy - e.reelle.y
            val d = kotlin.math.hypot(dx, dy)
            val dMin = 26f
            if (d in 0.01f..dMin) {
                val f = (dMin - d) / d.coerceAtLeast(0.01f)
                e.x += dx * f; e.y += dy * f
                deplacement = true
            }
        }
        /* 3. maintien dans le cadre */
        for (e in etqs) {
            val nx = e.x.coerceIn(marge, largeur - e.w - marge)
            val ny = e.y.coerceIn(marge, hauteur - e.h - marge)
            if (nx != e.x || ny != e.y) { e.x = nx; e.y = ny; deplacement = true }
        }
        if (!deplacement) return etqs
    }
    return etqs
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
    val zones = remember { GeoAubeV6.zones(ctx) }
    val proj = remember { ProjectionV6(dept) }
    var taille by remember { mutableStateOf(IntSize(1, 1)) }

    val params = remember(taille) {
        paramsV6(proj, taille.width.toFloat(), taille.height.toFloat())
    }

    /* Etiquettes separees, recalculees a chaque taille d'ecran */
    val etqs = remember(taille) {
        if (taille.width < 2 || taille.height < 2) emptyList()
        else placerEtiquettes(
            pins, params, proj,
            taille.width.toFloat(), taille.height.toFloat()
        )
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
            Box(
                Modifier.fillMaxWidth().weight(1f).onSizeChanged { taille = it }
                    .pointerInput(taille, filtreActif) {
                        detectTapGestures { tap ->
                            val lon = proj.minLon + (tap.x - params.ox) / (params.k * proj.kx)
                            val lat = proj.maxLat - (tap.y - params.oy) / params.k
                            for (z in zones) {
                                if (z.contours.any { dansPolygone(lon, lat, it) }) {
                                    onCirco(z.code)
                                    return@detectTapGestures
                                }
                            }
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    /* Fond du departement */
                    val path = Path()
                    dept.forEachIndexed { i, (lon, lat) ->
                        val px = params.ox + (proj.x(lon) * params.k).toFloat()
                        val py = params.oy + (proj.y(lat) * params.k).toFloat()
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    path.close()
                    drawPath(path, FondCarte)

                    /* Communes colorees par circonscription */
                    zones.forEach { z ->
                        val c = couleurZone(z.code)
                        val choisie = z.code == filtreActif
                        z.contours.forEach { anneau ->
                            val p = Path()
                            anneau.forEachIndexed { i, (lon, lat) ->
                                val px = params.ox + (proj.x(lon) * params.k).toFloat()
                                val py = params.oy + (proj.y(lat) * params.k).toFloat()
                                if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
                            }
                            p.close()
                            drawPath(p, if (choisie) c.foncee else c.pastel)
                            drawPath(
                                p, if (choisie) c.foncee else BordCommune,
                                style = Stroke(width = if (choisie) 3f else 1f)
                            )
                        }
                    }

                    /* Contour du departement par-dessus */
                    drawPath(path, VertCarte, style = Stroke(width = 4f))

                    /* Point reel + trait vers l'etiquette */
                    etqs.forEach { e ->
                        drawCircle(VertCarteFonce, radius = 6f, center = e.reelle)
                        drawLine(
                            VertCarte,
                            e.reelle,
                            Offset(e.x + e.w / 2f, e.y + e.h / 2f),
                            strokeWidth = 2.5f
                        )
                    }
                }
                etqs.forEach { e ->
                    val pin = e.pin
                    val c = couleurZone(pin.code)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (pin.code == filtreActif) c.foncee
                                else if (CouleursZones.containsKey(pin.code)) c.pastel
                                else VertCarte,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset { IntOffset(e.x.roundToInt(), e.y.roundToInt()) }
                            .clickable { onCirco(pin.code) }
                    ) {
                        Text(
                            pin.nom,
                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = if (pin.code == filtreActif) Color.White else Color.Black,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            HorizontalDivider()
            /* --- Zone basse : les 7 circonscriptions, toutes visibles --- */
            Text(
                "Choisir une circonscription :",
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                color = VertCarte, fontWeight = FontWeight.Bold, fontSize = 14.sp
            )
            Column(
                Modifier.fillMaxWidth().weight(0.52f)
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                pins.forEach { pin ->
                    val actif = pin.code == filtreActif
                    val c = couleurZone(pin.code)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (actif) c.foncee else Color.White,
                        border = BorderStroke(
                            2.dp,
                            if (CouleursZones.containsKey(pin.code)) c.foncee else VertCarte
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .clickable { onCirco(pin.code) }
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (CouleursZones.containsKey(pin.code)) c.pastel else Color.Gray,
                                modifier = Modifier.size(12.dp)
                            ) {}
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (pin.code == CodeAsh) pin.nom + " (adaptee, sans territoire)"
                                     else pin.nom,
                                color = if (actif) Color.White else Color.Black,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
