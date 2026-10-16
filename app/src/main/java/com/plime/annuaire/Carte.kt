package com.plime.annuaire

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import kotlin.math.cos

/* ==================== CARTE DE L'AUBE (hors ligne) ==================== */

/* Dessinee a partir de deux assets :
   - aube-geo.json  : contour du departement ;
   - aube-zones.json : contour des communes ayant au moins une ecole,
     groupees par circonscription (open data IGN + annuaire MENJ).
   Chaque circonscription a sa couleur ; celle choisie est en teinte foncee.
   Toucher une commune coloree selectionne sa circonscription.
   La circonscription de Troyes ASH (0100042S) est adaptee : elle n'a pas
   de territoire propre, on la choisit seulement dans la liste. */

private val VertCarte = Color(0xFF3CB371)
private val VertCarteFonce = Color(0xFF2E8B57)
private val FondCarte = Color(0xFFEAF7F0)
private val BordCommune = Color(0x33000000)

/* Couleur de chaque circonscription : pastel au repos, foncee si choisie */
private data class CouleurZone(val pastel: Color, val foncee: Color)

private val CouleursZones = mapOf(
    "0100041R" to CouleurZone(Color(0xFFA8D5A2), Color(0xFF2E7D32)), /* Troyes */
    "0100043T" to CouleurZone(Color(0xFF9FD6DF), Color(0xFF00838F)), /* Saint-Julien/Arcis */
    "0100044U" to CouleurZone(Color(0xFFAECFE8), Color(0xFF1565C0)), /* Romilly */
    "0100045V" to CouleurZone(Color(0xFFF5C08A), Color(0xFFE65100)), /* Bar-sur-Aube */
    "0100820M" to CouleurZone(Color(0xFFD7BDE5), Color(0xFF6A1B9A)), /* Bar-sur-Seine */
    "0100981M" to CouleurZone(Color(0xFFF7DC94), Color(0xFFB7950B))  /* La Chapelle-Saint-Luc */
)

private fun couleurZone(code: String): CouleurZone =
    CouleursZones[code] ?: CouleurZone(VertCarte, VertCarteFonce)

data class PinCirco(
    val code: String, val nom: String,
    val lon: Double, val lat: Double,
    val dx: Int, val dy: Int
)

data class ZoneCirco(
    val code: String,
    val contours: List<List<Pair<Double, Double>>>
)

object GeoAube {

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

    fun circos(ctx: Context): List<PinCirco> {
        val arr = lire(ctx, "aube-geo.json").getJSONArray("circos")
        val out = mutableListOf<PinCirco>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(PinCirco(
                o.getString("code"), o.getString("nom"),
                o.getDouble("lon"), o.getDouble("lat"),
                o.optInt("dx", -55), o.optInt("dy", -46)
            ))
        }
        return out
    }

    /* Contours des communes par circonscription (asset aube-zones.json) */
    fun zones(ctx: Context): List<ZoneCirco> {
        val racine = lire(ctx, "aube-zones.json").getJSONObject("zones")
        val out = mutableListOf<ZoneCirco>()
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
            out.add(ZoneCirco(code, contours))
        }
        return out
    }

    /* Noms des communes, DANS L'ORDRE des contours de chaque circonscription
       (asset aube-communes.json). L'indice du contour touche = le nom. */
    fun communes(ctx: Context): Map<String, List<String>> {
        val racine = lire(ctx, "aube-communes.json").getJSONObject("zones")
        val out = mutableMapOf<String, List<String>>()
        for (code in racine.keys()) {
            val arr = racine.getJSONArray(code)
            val noms = mutableListOf<String>()
            for (i in 0 until arr.length()) noms.add(arr.getString(i))
            out[code] = noms
        }
        return out
    }
}

/* Projection plate carree : la longitude est etiree selon la latitude
   moyenne de l'Aube (~48,3 degres) pour rester fidele. */
private class Projection(pts: List<Pair<Double, Double>>) {
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
private data class Params(val k: Float, val ox: Float, val oy: Float)

private fun params(proj: Projection, w: Float, h: Float): Params {
    val k = proj.echelle(w, h)
    return Params(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranCarte(
    filtreActif: String,
    onCirco: (String) -> Unit,
    onFermer: () -> Unit,
    /* Ouvrir la fiche de la commune (ic\u00F4ne en haut \u00E0 droite). */
    onCommune: (String) -> Unit = {},
    /* Selection de commune sur la carte : m\u00E9moris\u00E9e par l'app
       (sans ouvrir la fiche). */
    onCommuneSel: (String) -> Unit = {},
    /* Commune choisie pr\u00E9c\u00E9demment : la carte se rouvre sur la vue
       circonscription avec cette commune remplie. */
    communeSelectionnee: String = ""
) {
    val ctx = LocalContext.current
    val dept = remember { GeoAube.dept(ctx) }
    val pins = remember { GeoAube.circos(ctx) }
    val zones = remember { GeoAube.zones(ctx) }
    val zonesNoms = remember { GeoAube.communes(ctx) }
    /* Mode circonscription : si une circo est choisie (pas la DSDEN et elle
       possede un territoire), la carte se concentre sur SES communes et
       toucher une commune l'ouvre ; sinon carte complete du departement. */
    val zoneActive =
        if (filtreActif == "dsden") null
        else zones.firstOrNull { it.code == filtreActif }
    /* Vue courante : "dept" = carte de l'Aube, "circo" = carte zoom\u00E9e
       sur la circonscription. Premier toucher : d\u00E9partement ; si une
       commune \u00E9tait d\u00E9j\u00E0 choisie, on rouvre sur la vue circo. */
    var vue by remember {
        mutableStateOf(if (communeSelectionnee.isNotBlank()) "circo" else "dept")
    }
    /* Commune s\u00E9lectionn\u00E9e dans la vue circonscription : remplie
       en teinte fonc\u00E9e, titre = son nom. */
    var communeSel by remember(filtreActif) { mutableStateOf(communeSelectionnee) }
    val vueCirco = vue == "circo" && zoneActive != null
    val proj = remember(filtreActif) {
        if (zoneActive != null) Projection(zoneActive.contours.flatten())
        else Projection(dept)
    }
    var taille by remember { mutableStateOf(IntSize(1, 1)) }

    val params = remember(proj, taille) {
        params(proj, taille.width.toFloat(), taille.height.toFloat())
    }
    /* Zoom a deux doigts + deplacement ; remis a zero quand on change
       de vue. " aff " = parametres reels apres zoom/deplacement. */
    var zoom by remember(filtreActif) { mutableStateOf(1f) }
    var panX by remember(filtreActif) { mutableStateOf(0f) }
    var panY by remember(filtreActif) { mutableStateOf(0f) }
    fun reinitZoom() { zoom = 1f; panX = 0f; panY = 0f }
    val aff = Params(params.k * zoom, params.ox * zoom + panX, params.oy * zoom + panY)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    /* Titre : Aube / nom de la circonscription / nom de
                       la commune selectionnee. */
                    Text(
                        when {
                            vueCirco && communeSel.isNotBlank() -> communeSel
                            vueCirco ->
                                pins.firstOrNull { it.code == filtreActif }?.nom ?: "Circonscription"
                            else -> "Aube"
                        },
                        fontWeight = FontWeight.Bold, color = VertCarte
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onFermer) { Icon(Icons.Filled.ArrowBack, "Retour") }
                },
                /* Icones en haut a droite :
                   - vue departement : aller a la carte de la circonscription ;
                   - vue circonscription : ouvrir la fiche de la commune
                     selectionnee, puis enlever la selection ; un deuxieme
                     appui sur " enlever " revient a la carte de l'Aube
                     (la circonscription reste selectionnee). */
                actions = {
                    if (!vueCirco) {
                        if (zoneActive != null) {
                            IconButton(onClick = { vue = "circo"; reinitZoom() }) {
                                Icon(Icons.Filled.LocationOn, "Carte de la circonscription",
                                    tint = VertCarte)
                            }
                        }
                    } else {
                        if (communeSel.isNotBlank()) {
                            IconButton(onClick = { onCommune(communeSel) }) {
                                Icon(Icons.Filled.Info, "Fiche de la commune",
                                    tint = VertCarte)
                            }
                        }
                        IconButton(onClick = {
                            if (communeSel.isNotBlank()) {
                                communeSel = ""
                                onCommuneSel("")
                            } else {
                                vue = "dept"
                                reinitZoom()
                            }
                        }) {
                            Icon(Icons.Filled.Close, "Enlever la s\u00E9lection",
                                tint = if (communeSel.isNotBlank()) Color(0xFFB3261E) else VertCarte)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            /* --- La carte (sans etiquettes : la plus lisible) --- */
            Box(
                Modifier.fillMaxWidth().weight(1f).onSizeChanged { taille = it }
                    /* Zoom a deux doigts + deplacement a un doigt. */
                    .pointerInput(taille) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            zoom = (zoom * gestureZoom).coerceIn(1f, 8f)
                            val w = taille.width.toFloat()
                            val h = taille.height.toFloat()
                            panX = (panX + pan.x).coerceIn(-(w * (zoom - 1f)), 0f)
                            panY = (panY + pan.y).coerceIn(-(h * (zoom - 1f)), 0f)
                        }
                    }
                    .pointerInput(taille, filtreActif, vue) {
                        detectTapGestures { tap ->
                            /* Position tactile -> coordonnees reelles
                               (tenant compte du zoom/deplacement). */
                            val lon = proj.minLon + (tap.x - aff.ox) / (aff.k * proj.kx)
                            val lat = proj.maxLat - (tap.y - aff.oy) / aff.k
                            if (vueCirco) {
                                /* Toucher une commune : la SELECTIONNER
                                   (remplie en fonce, titre = son nom).
                                   La fiche s'ouvre par l'icone en haut
                                   a droite. */
                                zoneActive?.contours?.forEachIndexed { idx, anneau ->
                                    if (dansPolygone(lon, lat, anneau)) {
                                        zonesNoms[zoneActive.code]?.getOrNull(idx)
                                            ?.let {
                                                communeSel = it
                                                onCommuneSel(it)
                                            }
                                    }
                                }
                            } else {
                                /* Toucher une circonscription : l'affichage
                                   passe a la carte de la circonscription. */
                                for (z in zones) {
                                    if (z.contours.any { dansPolygone(lon, lat, it) }) {
                                        onCirco(z.code)
                                        vue = "circo"
                                        communeSel = ""
                                        reinitZoom()
                                        return@detectTapGestures
                                    }
                                }
                            }
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    if (vueCirco) {
                        /* Vue concentree : communes de la circonscription
                           DETOUREES SANS REMPLISSAGE (comme les
                           circonscriptions sur la carte de l'Aube) ; seule
                           la commune choisie est remplie en teinte foncee. */
                        val c = couleurZone(zoneActive!!.code)
                        val noms = zonesNoms[zoneActive.code] ?: emptyList()
                        zoneActive.contours.forEachIndexed { idx, anneau ->
                            val p = Path()
                            anneau.forEachIndexed { i, (lon, lat) ->
                                val px = aff.ox + (proj.x(lon) * aff.k).toFloat()
                                val py = aff.oy + (proj.y(lat) * aff.k).toFloat()
                                if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
                            }
                            p.close()
                            val choisie = noms.getOrNull(idx) == communeSel
                            if (choisie) drawPath(p, c.foncee)
                            drawPath(p, c.foncee, style = Stroke(width = if (choisie) 4f else 2f))
                        }
                    } else {
                        /* Fond du departement */
                        val path = Path()
                        dept.forEachIndexed { i, (lon, lat) ->
                            val px = aff.ox + (proj.x(lon) * aff.k).toFloat()
                            val py = aff.oy + (proj.y(lat) * aff.k).toFloat()
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
                                    val px = aff.ox + (proj.x(lon) * aff.k).toFloat()
                                    val py = aff.oy + (proj.y(lat) * aff.k).toFloat()
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
                    }
                }
            }
            HorizontalDivider()
            /* --- Sous la carte : liste des communes de la circonscription
               choisie, ou liste des circonscriptions en vue departement. --- */
            Text(
                if (vueCirco) "Toucher une commune pour la s\u00E9lectionner, ou la choisir ici :"
                else "Ou choisir une circonscription :",
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = VertCarte, fontWeight = FontWeight.Bold
            )
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(min = 300.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
            ) {
                if (vueCirco) {
                    /* Communes de la circonscription, par ordre alphabetique ;
                       la commune choisie est mise en evidence. */
                    val c = couleurZone(zoneActive!!.code)
                    (zonesNoms[zoneActive.code] ?: emptyList())
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
                        .forEach { nom ->
                            val actif = nom == communeSel
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (actif) c.foncee else Color.White,
                                border = BorderStroke(2.dp, c.foncee),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    .clickable { communeSel = nom; onCommuneSel(nom) }
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = c.pastel,
                                        modifier = Modifier.size(14.dp)
                                    ) {}
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        nom,
                                        color = if (actif) Color.White else Color.Black,
                                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    Text(
                        "Toutes les communes rattach\u00E9es \u00E0 cette circonscription.",
                        Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                        fontSize = 12.sp, color = Color.Gray
                    )
                } else {
                pins.forEach { pin ->
                    val actif = pin.code == filtreActif
                    val c = couleurZone(pin.code)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (actif) c.foncee else Color.White,
                        border = BorderStroke(2.dp, if (CouleursZones.containsKey(pin.code)) c.foncee else VertCarte),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clickable { onCirco(pin.code); vue = "circo"; communeSel = ""; reinitZoom() }
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (CouleursZones.containsKey(pin.code)) c.pastel else Color.Gray,
                                modifier = Modifier.size(14.dp)
                            ) {}
                            Spacer(Modifier.width(10.dp))
                            Text(
                                pin.nom + if (pin.code == "0100042S") " (adapt\u00E9e, sans territoire)" else "",
                                color = if (actif) Color.White else Color.Black,
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Text(
                    "Seules les communes abritant au moins une \u00E9cole sont color\u00E9es.",
                    Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    fontSize = 12.sp, color = Color.Gray
                )
                Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}
