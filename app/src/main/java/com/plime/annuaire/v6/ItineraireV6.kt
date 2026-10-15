package com.plime.annuaire.v6

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.plime.annuaire.v3.normV3
import java.util.Locale

private val VertItineraire = Color(0xFF3CB371)

/** Champ adresse ? (libellé contenant « adresse ») */
fun estAdresseV6(label: String): Boolean = normV3(label).contains("adresse")

/** Ouvre Google Maps avec l'itinéraire vers la destination, dans le mode
    donné (« driving » ou « walking »). L'itinéraire part de la position
    actuelle, Maps la demande si nécessaire. */
fun ouvrirMapsV6(ctx: Context, destination: String, mode: String) {
    val url = "https://www.google.com/maps/dir/?api=1&destination=" +
        Uri.encode(destination) + "&travelmode=" + mode
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        /* ni Maps ni navigateur : ouvre au moins la vue carte */
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(destination)))
            )
        } catch (_: Exception) {
            /* rien d'installé : on ignore silencieusement */
        }
    }
}

/** Dernière position connue de l'appareil (GPS, réseau, passive),
    sans attente - peut être null. */
private fun dernierePositionV6(ctx: Context): Location? {
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    return try {
        listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).mapNotNull { p ->
            try { lm.getLastKnownLocation(p) } catch (_: SecurityException) { null }
        }.maxByOrNull { it.time }
    } catch (_: Exception) { null }
}

/** Lance l'itinéraire vers l'adresse : à pied si elle est à moins de 5 km,
    en voiture sinon. Sans position connue ou sans géocodage, on ouvre
    en voiture (Maps proposera la position de départ lui-même). */
fun lancerItineraireV6(ctx: Context, adresse: String) {
    if (adresse.isBlank()) return
    val pos = dernierePositionV6(ctx)
    if (pos == null) {
        ouvrirMapsV6(ctx, adresse, "driving")
        return
    }
    Thread {
        val dest = try {
            @Suppress("DEPRECATION")
            Geocoder(ctx, Locale.FRANCE).getFromLocationName(adresse, 1)?.firstOrNull()
        } catch (_: Exception) { null }
        val cible: String
        val mode: String
        if (dest != null) {
            cible = "${dest.latitude},${dest.longitude}"
            val res = FloatArray(3)
            Location.distanceBetween(
                pos.latitude, pos.longitude, dest.latitude, dest.longitude, res
            )
            mode = if (res[0] < 5000) "walking" else "driving"
        } else {
            cible = adresse
            mode = "driving"
        }
        ouvrirMapsV6(ctx, cible, mode)
    }.start()
}

/** Ligne « adresse » cliquable, utilisée partout dans l'annuaire :
    au premier toucher elle demande la permission de localisation,
    puis ouvre l'itinéraire Google Maps. Si la permission est refusée,
    l'itinéraire s'ouvre quand même (en voiture, départ choisi par Maps). */
@Composable
fun LigneAdresseV6(adresse: String) {
    val ctx = LocalContext.current
    var enAttente by remember { mutableStateOf<String?>(null) }
    val perm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { accorde ->
        val a = enAttente
        enAttente = null
        if (!a.isNullOrBlank()) {
            if (accorde) lancerItineraireV6(ctx, a)
            else ouvrirMapsV6(ctx, a, "driving")
        }
    }
    Row(
        Modifier.fillMaxWidth().clickable {
            if (ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                lancerItineraireV6(ctx, adresse)
            } else {
                enAttente = adresse
                perm.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Directions, "Itinéraire", tint = VertItineraire)
        Spacer(Modifier.width(6.dp))
        Text(
            adresse,
            fontSize = 16.sp,
            color = VertItineraire,
            fontWeight = FontWeight.SemiBold
        )
    }
}
