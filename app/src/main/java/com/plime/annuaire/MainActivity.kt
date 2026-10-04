package com.plime.annuaire

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ---------- Palette ---------- */
val Indigo = Color(0xFF4F46E5)
val Violet = Color(0xFF7C3AED)
val Slate400 = Color(0xFF94A3B8)
val Slate700 = Color(0xFF334155)
val Slate900 = Color(0xFF0F172A)
val Emerald = Color(0xFF059669)
val Amber = Color(0xFFD97706)
val ScreenBg = Color(0xFFF8FAFC)
val LineGray = Color(0xFFF1F5F9)
val HeaderBrush = Brush.linearGradient(listOf(Indigo, Violet))
val AmberBrush = Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEA580C)))
val VioletBrush = Brush.linearGradient(listOf(Color(0xFF8B5CF6), Indigo))

/* ---------- Activité ---------- */
class MainActivity : ComponentActivity() {

    private val unlocked = mutableStateOf(false)
    private lateinit var store: SecureStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecureStore(this)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(primary = Indigo, secondary = Violet)
            ) {
                Surface(Modifier.fillMaxSize()) {
                    AnnuaireApp(store = store, unlockedState = unlocked)
                }
            }
        }
    }

    /** Verrouillage automatique dès que l'app quitte le premier plan. */
    override fun onPause() {
        unlocked.value = false
        super.onPause()
    }
}

/* ---------- Navigation (pile maison) ---------- */
sealed class Screen {
    data object Home : Screen()
    data class CommuneDetail(val commune: Commune) : Screen()
    data class EcoleDetail(val commune: Commune, val ecole: Ecole) : Screen()
    data class DirecteurDetail(val entry: DirecteurEntry) : Screen()
}

@Composable
fun AnnuaireApp(store: SecureStore, unlockedState: MutableState<Boolean>) {
    val isUnlocked = unlockedState.value
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("ville") }   // "ville" | "directeur"
    var showPin by remember { mutableStateOf(false) }

    val push: (Screen) -> Unit = { stack = stack + it }
    val back: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) }
    BackHandler(enabled = stack.size > 1) { back() }

    Column(Modifier.fillMaxSize().background(ScreenBg)) {
        Box(Modifier.weight(1f)) {
            when (val screen = stack.last()) {
                is Screen.Home -> HomeScreen(
                    query = query, onQuery = { query = it },
                    mode = mode, onMode = { mode = it; query = "" },
                    isUnlocked = isUnlocked,
                    onOpenCommune = { push(Screen.CommuneDetail(it)) },
                    onOpenDirecteur = { push(Screen.DirecteurDetail(it)) }
                )
                is Screen.CommuneDetail -> CommuneDetailScreen(
                    commune = screen.commune, isUnlocked = isUnlocked,
                    onBack = back,
                    onOpenEcole = { push(Screen.EcoleDetail(screen.commune, it)) }
                )
                is Screen.EcoleDetail -> EcoleDetailScreen(
                    commune = screen.commune, ecole = screen.ecole,
                    isUnlocked = isUnlocked,
                    onBack = back,
                    onAskPin = { showPin = true },
                    onLock = { unlockedState.value = false }
                )
                is Screen.DirecteurDetail -> DirecteurDetailScreen(
                    entry = screen.entry, isUnlocked = isUnlocked,
                    onBack = back,
                    onOpenEcole = { push(Screen.EcoleDetail(screen.entry.commune, screen.entry.ecole)) }
                )
            }
        }
        BottomBar(
            isUnlocked = isUnlocked,
            onHome = { stack = listOf(Screen.Home); query = "" },
            onDirecteurs = { stack = listOf(Screen.Home); mode = "directeur"; query = "" },
            onToggleLock = { if (isUnlocked) unlockedState.value = false else showPin = true }
        )
    }

    if (showPin) {
        PinDialog(
            expected = store.pin,
            onSuccess = { unlockedState.value = true; showPin = false },
            onDismiss = { showPin = false }
        )
    }
}

/* ---------- Écran d'accueil / recherche ---------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    query: String, onQuery: (String) -> Unit,
    mode: String, onMode: (String) -> Unit,
    isUnlocked: Boolean,
    onOpenCommune: (Commune) -> Unit,
    onOpenDirecteur: (DirecteurEntry) -> Unit
) {
    val communes = remember(query, mode) {
        if (mode != "ville") emptyList()
        else if (query.isBlank()) COMMUNES
        else COMMUNES.filter { it.nom.normalized().contains(query.normalized()) }
    }
    val directeurs = remember(query, mode) {
        if (mode != "directeur") emptyList()
        else {
            val all = COMMUNES.flatMap { c -> c.ecoles.map { DirecteurEntry(c, it) } }
            if (query.isBlank()) all
            else all.filter {
                it.ecole.directeur.nom.normalized().contains(query.normalized()) ||
                    it.commune.nom.normalized().contains(query.normalized())
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(HeaderBrush).padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(16.dp))
                Text("Bienvenue,", color = Color(0xFFC7D2FE), fontSize = 12.sp)
                Text("Annuaire des Écoles", color = Color.White,
                    fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = query, onValueChange = onQuery,
                    placeholder = {
                        Text(
                            if (mode == "ville") "Rechercher une commune…"
                            else "Rechercher un directeur…",
                            color = Color(0xFFC7D2FE), fontSize = 14.sp
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Color(0xFFC7D2FE)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = Color.White,
                        focusedContainerColor = Color.White,
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row {
                    ModeChip("Par ville", mode == "ville") { onMode("ville") }
                    Spacer(Modifier.width(8.dp))
                    ModeChip("Par directeur", mode == "directeur") { onMode("directeur") }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        item {
            val nbEcoles = COMMUNES.sumOf { it.ecoles.size }
            val nbProf = COMMUNES.sumOf { c -> c.ecoles.sumOf { it.professeurs.size } }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(Icons.Filled.LocationCity, COMMUNES.size.toString(), "Communes", Modifier.weight(1f))
                StatCard(Icons.Filled.School, nbEcoles.toString(), "Écoles", Modifier.weight(1f))
                StatCard(Icons.Filled.Groups, nbProf.toString(), "Professeurs", Modifier.weight(1f))
            }
        }
        item { SectionTitle(if (mode == "ville") "Communes" else "Directeurs") }
        if (mode == "ville") {
            items(communes) { c -> CommuneCard(c) { onOpenCommune(c) } }
        } else {
            items(directeurs) { d -> DirecteurCard(d, isUnlocked) { onOpenDirecteur(d) } }
        }
        if (communes.isEmpty() && directeurs.isEmpty()) {
            item { EmptyState() }
        }
    }
}

/* ---------- Écran commune ---------- */
@Composable
fun CommuneDetailScreen(
    commune: Commune, isUnlocked: Boolean,
    onBack: () -> Unit, onOpenEcole: (Ecole) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(HeaderBrush).padding(20.dp)) {
                HeaderBack("Communes", onBack)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.LocationCity, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(commune.nom, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text("${commune.departement} · ${commune.population}",
                            color = Color(0xFFC7D2FE), fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("MAIRIE", style = SectionStyle)
                Card(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        ContactRow(Icons.Filled.Person, "Maire", commune.maire, isUnlocked)
                        HLine()
                        ContactRow(Icons.Filled.Place, "Adresse", commune.adresseMairie, isUnlocked)
                        HLine()
                        ContactRow(Icons.Filled.Phone, "Téléphone", commune.telMairie, isUnlocked)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("ÉCOLES DE LA COMMUNE", style = SectionStyle)
            }
        }
        items(commune.ecoles) { e ->
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
                    .clickable { onOpenEcole(e) },
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFEEF2FF)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.School, null, tint = Indigo, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.nom, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = Slate900, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${e.directeur.nom} · ${e.professeurs.size} prof.",
                            fontSize = 11.sp, color = Slate400)
                    }
                }
            }
        }
    }
}

/* ---------- Écran école ---------- */
@Composable
fun EcoleDetailScreen(
    commune: Commune, ecole: Ecole, isUnlocked: Boolean,
    onBack: () -> Unit, onAskPin: () -> Unit, onLock: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(HeaderBrush).padding(20.dp)) {
                HeaderBack(commune.nom, onBack)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.School, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(ecole.nom, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("${ecole.professeurs.size} enseignant(s)", color = Color(0xFFC7D2FE), fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                /* Bandeau confidentialité */
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isUnlocked) AmberBrush
                            else Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF0D9488)))
                        )
                        .clickable { if (isUnlocked) onLock() else onAskPin() }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (isUnlocked) Icons.Filled.LockOpen else Icons.Filled.Lock, null, tint = Color.White)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (isUnlocked) "Coordonnées visibles" else "Mode privé actif",
                            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (isUnlocked) "Appuyez pour masquer à nouveau"
                            else "Coordonnées masquées · stockage local uniquement",
                            color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        ContactRow(Icons.Filled.Place, "Adresse de l'école", ecole.adresse, isUnlocked)
                        HLine()
                        ContactRow(Icons.Filled.Phone, "Téléphone de l'école", ecole.tel, isUnlocked)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("DIRECTION", style = SectionStyle)
                Card(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(AmberBrush),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(initials(ecole.directeur.nom), color = Color.White,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(ecole.directeur.nom, fontSize = 15.sp,
                                fontWeight = FontWeight.Bold, color = Slate900)
                            PrivateValue(ecole.directeur.tel, isUnlocked)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("CORPS ENSEIGNANT", style = SectionStyle)
            }
        }
        items(ecole.professeurs) { t -> TeacherRow(t, isUnlocked) }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

/* ---------- Écran directeur ---------- */
@Composable
fun DirecteurDetailScreen(
    entry: DirecteurEntry, isUnlocked: Boolean,
    onBack: () -> Unit, onOpenEcole: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(AmberBrush).padding(20.dp)) {
                HeaderBack("Directeurs", onBack)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(initials(entry.ecole.directeur.nom), color = Color.White,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(entry.ecole.directeur.nom, color = Color.White,
                            fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("${entry.ecole.nom} · ${entry.commune.nom}",
                            color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        ContactRow(Icons.Filled.Phone, "Téléphone (privé)", entry.ecole.directeur.tel, isUnlocked)
                        HLine()
                        ContactRow(Icons.Filled.School, "École", entry.ecole.nom, isUnlocked)
                        HLine()
                        ContactRow(Icons.Filled.Place, "Adresse", entry.ecole.adresse, isUnlocked)
                        HLine()
                        ContactRow(Icons.Filled.Phone, "Tél. école", entry.ecole.tel, isUnlocked)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Voir l'école et les professeurs",
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Indigo)
                        .clickable { onOpenEcole() }
                        .padding(vertical = 14.dp)
                )
            }
        }
    }
}

/* ---------- Composants réutilisables ---------- */

private val SectionStyle = androidx.compose.ui.text.TextStyle(
    fontSize = 11.sp, fontWeight = FontWeight.Bold,
    color = Slate400, letterSpacing = 1.2.sp
)

@Composable
fun SectionTitle(text: String) {
    Text(text.uppercase(), style = SectionStyle,
        modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp))
}

@Composable
fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Aucun résultat", color = Slate400, fontSize = 14.sp)
    }
}

@Composable
fun HeaderBack(label: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onBack).padding(4.dp)
    ) {
        Icon(Icons.Filled.ArrowBack, null, tint = Color(0xFFC7D2FE), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color(0xFFC7D2FE), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = if (selected) Indigo else Color(0xFFC7D2FE))
    }
}

@Composable
fun StatCard(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = Indigo, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Slate900)
            Text(label, fontSize = 10.sp, color = Slate400)
        }
    }
}

@Composable
fun CommuneCard(c: Commune, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(HeaderBrush),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.LocationCity, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.nom, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Slate900)
                Text("${c.departement} · ${c.ecoles.size} écoles", fontSize = 11.sp, color = Slate400)
            }
        }
    }
}

@Composable
fun DirecteurCard(entry: DirecteurEntry, isUnlocked: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(AmberBrush),
                contentAlignment = Alignment.Center
            ) {
                Text(initials(entry.ecole.directeur.nom), color = Color.White,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.ecole.directeur.nom, fontSize = 14.sp,
                    fontWeight = FontWeight.Bold, color = Slate900, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${entry.ecole.nom} · ${entry.commune.nom}", fontSize = 11.sp,
                    color = Slate400, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                if (isUnlocked) entry.ecole.directeur.tel else "•••••",
                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = if (isUnlocked) Emerald else Slate400,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isUnlocked) Color(0xFFECFDF5) else Color(0xFFF1F5F9))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun ContactRow(icon: ImageVector, label: String, value: String, unlocked: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEEF2FF)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Indigo, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, fontSize = 10.sp, color = Slate400, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            PrivateValue(value, unlocked)
        }
    }
}

@Composable
fun PrivateValue(value: String, unlocked: Boolean) {
    if (unlocked) Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Slate700)
    else Text("••••••••", fontSize = 14.sp, color = Slate400, letterSpacing = 2.sp)
}

@Composable
fun TeacherRow(t: Professeur, unlocked: Boolean) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(VioletBrush),
                contentAlignment = Alignment.Center
            ) {
                Text(initials(t.nom), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.nom, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Slate700)
                Row {
                    Text("${t.classe} · ", fontSize = 11.sp, color = Slate400)
                    if (unlocked) Text(t.tel, fontSize = 11.sp, color = Slate400)
                    else Text("••••••••", fontSize = 11.sp, color = Slate400, letterSpacing = 1.5.sp)
                }
            }
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (unlocked) Emerald else Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Phone, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
        }
    }
}

@Composable
fun HLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LineGray))
}

@Composable
fun BottomBar(
    isUnlocked: Boolean,
    onHome: () -> Unit, onDirecteurs: () -> Unit, onToggleLock: () -> Unit
) {
    Surface(shadowElevation = 8.dp, color = Color.White) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BottomItem(Icons.Filled.Home, "Accueil", Indigo, onHome)
            BottomItem(Icons.Filled.LocationCity, "Communes", Indigo, onHome)
            BottomItem(Icons.Filled.Person, "Directeurs", Indigo, onDirecteurs)
            BottomItem(
                if (isUnlocked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                "Privé", if (isUnlocked) Amber else Emerald, onToggleLock
            )
        }
    }
}

@Composable
fun BottomItem(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate400)
    }
}
        title = { Text("Code PIN requis", fontWeight = FontWeight.Bold) },
/* ---------- Dialog PIN ---------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinDialog(expected: String, onSuccess: () -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, tint = Indigo)
                Spacer(Modifier.width(8.dp))
                Text("Code PIN requis", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text("Saisissez le code pour afficher les coordonnées.",
                    fontSize = 13.sp, color = Slate400)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 4 && it.all(Char::isDigit)) { pin = it; error = false }
                    },
                    placeholder = { Text("••••") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error) {
                    Spacer(Modifier.height(4.dp))
                    Text("Code incorrect", color = Color(0xFFDC2626), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (pin == expected) onSuccess() else { error = true; pin = "" } }) {
                Text("Déverrouiller", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
