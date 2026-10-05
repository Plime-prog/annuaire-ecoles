@file:OptIn(ExperimentalMaterial3Api::class)

package com.plime.annuaire

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
val Indigo = Color(0xFF2F6DB5)
val Violet = Color(0xFF4A86C8)
val Slate400 = Color(0xFF94A3B8)
val Slate700 = Color(0xFF334155)
val Slate900 = Color(0xFF0F172A)
val Emerald = Color(0xFF059669)
val Amber = Color(0xFFD97706)
val Red = Color(0xFFDC2626)
val ScreenBg = Color(0xFFF8FAFC)
val LineGray = Color(0xFFF1F5F9)
val HeaderBrush = Brush.linearGradient(listOf(Indigo, Violet))
val AmberBrush = Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEA580C)))
val VioletBrush = Brush.linearGradient(listOf(Color(0xFF5B93D1), Indigo))

/* ---------- Données (stockage local chiffré) ---------- */
class Repo(private val store: SecureStore) {
    var communes by mutableStateOf(
        try { parseCommunes(store.data) } catch (e: Exception) { emptyList<Commune>() }
    )
        private set

    private fun save(list: List<Commune>) {
        communes = list
        store.data = list.toJson()
    }

    fun replaceAll(list: List<Commune>) = save(list)
    fun addCommune(c: Commune) = save(communes + c)
    fun deleteCommune(id: String) = save(communes.filter { it.id != id })
    fun editCommune(id: String, f: (Commune) -> Commune) =
        save(communes.map { if (it.id == id) f(it) else it })
    fun editEcole(cid: String, eid: String, f: (Ecole) -> Ecole) =
        editCommune(cid) { c -> c.copy(ecoles = c.ecoles.map { if (it.id == eid) f(it) else it }) }
}

/* ---------- Formulaires ---------- */
class Form(
    val title: String,
    val labels: List<String>,
    val values: List<String>,
    val onSave: (List<String>) -> Unit
)
class Confirm(val text: String, val onYes: () -> Unit)
class Ui(val form: (Form) -> Unit, val confirm: (Confirm) -> Unit)

val communeLabels = listOf(
    "Nom de la commune", "Département", "Population", "Maire",
    "Tél. du maire", "Adresse de la mairie", "Tél. de la mairie"
)
val ecoleLabels = listOf(
    "Nom de l'école", "Adresse", "Téléphone", "Nom du directeur", "Tél. du directeur"
)
val profLabels = listOf("Nom du professeur", "Classe", "Téléphone")

/* ---------- Activité ---------- */
class MainActivity : ComponentActivity() {

    private val unlocked = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SecureStore(this)
        val repo = Repo(store)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(primary = Indigo, secondary = Violet)
            ) {
                Surface(Modifier.fillMaxSize()) {
                    AnnuaireApp(store = store, repo = repo, unlockedState = unlocked)
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

/* ---------- Navigation ---------- */
sealed class Screen {
    data object Home : Screen()
    data class CommuneDetail(val cid: String) : Screen()
    data class EcoleDetail(val cid: String, val eid: String) : Screen()
    data class DirecteurDetail(val cid: String, val eid: String) : Screen()
}

@Composable
fun AnnuaireApp(store: SecureStore, repo: Repo, unlockedState: MutableState<Boolean>) {
    val isUnlocked = unlockedState.value
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("ville") }
    var showPin by remember { mutableStateOf(false) }
    var form by remember { mutableStateOf<Form?>(null) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val ui = Ui({ form = it }, { confirm = it })

    val push: (Screen) -> Unit = { stack = stack + it }
    val back: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) }
    BackHandler(enabled = stack.size > 1) { back() }

    Column(Modifier.fillMaxSize().background(ScreenBg)) {
        TricoloreBar()
        Box(Modifier.weight(1f)) {
            when (val s = stack.last()) {
                is Screen.Home -> HomeScreen(
                    store = store,
                    repo = repo, ui = ui,
                    query = query, onQuery = { query = it },
                    mode = mode, onMode = { mode = it; query = "" },
                    isUnlocked = isUnlocked,
                    onOpenCommune = { push(Screen.CommuneDetail(it.id)) },
                    onOpenDirecteur = { push(Screen.DirecteurDetail(it.commune.id, it.ecole.id)) }
                )
                is Screen.CommuneDetail -> {
                    val c = repo.communes.find { it.id == s.cid } ?: return@Box
                    CommuneDetailScreen(
                        commune = c, repo = repo, ui = ui, isUnlocked = isUnlocked,
                        onBack = back,
                        onOpenEcole = { push(Screen.EcoleDetail(c.id, it.id)) }
                    )
                }
                is Screen.EcoleDetail -> {
                    val c = repo.communes.find { it.id == s.cid } ?: return@Box
                    val e = c.ecoles.find { it.id == s.eid } ?: return@Box
                    EcoleDetailScreen(
                        commune = c, ecole = e, repo = repo, ui = ui,
                        isUnlocked = isUnlocked, onBack = back,
                        onAskPin = { showPin = true },
                        onLock = { unlockedState.value = false }
                    )
                }
                is Screen.DirecteurDetail -> {
                    val c = repo.communes.find { it.id == s.cid } ?: return@Box
                    val e = c.ecoles.find { it.id == s.eid } ?: return@Box
                    DirecteurDetailScreen(
                        commune = c, ecole = e, isUnlocked = isUnlocked, onBack = back,
                        onOpenEcole = { push(Screen.EcoleDetail(c.id, e.id)) }
                    )
                }
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
    form?.let { f -> FormDialog(f) { form = null } }
    confirm?.let { c -> ConfirmDialog(c) { confirm = null } }
}

/* ---------- Accueil ---------- */
@Composable
fun HomeScreen(
    store: SecureStore,
    repo: Repo, ui: Ui,
    query: String, onQuery: (String) -> Unit,
    mode: String, onMode: (String) -> Unit,
    isUnlocked: Boolean,
    onOpenCommune: (Commune) -> Unit,
    onOpenDirecteur: (DirecteurEntry) -> Unit
) {
    val all = repo.communes.sortedBy { it.nom.normalized() }
    val q = query.normalized()
    val communes: List<Commune> =
        if (mode != "ville") emptyList<Commune>()
        else if (q.isBlank()) all
        else all.filter { it.nom.normalized().contains(q) }
    val directeurs: List<DirecteurEntry> =
        if (mode != "directeur") emptyList<DirecteurEntry>()
        else all.flatMap { c -> c.ecoles.map { DirecteurEntry(c, it) } }
            .filter {
                q.isBlank() ||
                    it.ecole.directeur.nom.normalized().contains(q) ||
                    it.commune.nom.normalized().contains(q)
            }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(HeaderBrush).padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(16.dp))
                Text("Bienvenue,", color = Color(0xFFDCE8F7), fontSize = 12.sp)
                Text("Annuaire des Écoles", color = Color.White,
                    fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = query, onValueChange = onQuery,
                    placeholder = {
                        Text(
                            if (mode == "ville") "Rechercher une commune…"
                            else "Rechercher un directeur…",
                            color = Slate400, fontSize = 14.sp
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Slate400) },
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
            val nbEcoles = all.sumOf { it.ecoles.size }
            val nbProf = all.sumOf { c -> c.ecoles.sumOf { it.professeurs.size } }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(Icons.Filled.LocationCity, all.size.toString(), "Communes", Modifier.weight(1f))
                StatCard(Icons.Filled.School, nbEcoles.toString(), "Écoles", Modifier.weight(1f))
                StatCard(Icons.Filled.Groups, nbProf.toString(), "Professeurs", Modifier.weight(1f))
            }
        }
        if (isUnlocked) {
            item {
                ActionButton(
                    "Ajouter une commune", Icons.Filled.Add, Emerald,
                    {
                        ui.form(Form("Nouvelle commune", communeLabels, List(communeLabels.size) { "" }) { v ->
                            repo.addCommune(
                                Commune(newId(), v[0], v[1], v[2], v[3], v[4], v[5], v[6], emptyList())
                            )
                        })
                    },
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            item { SettingsPanel(store, repo) }
        }
        item { SectionTitle(if (mode == "ville") "Communes" else "Directeurs") }
        if (mode == "ville") {
            items(communes) { c -> CommuneCard(c) { onOpenCommune(c) } }
        } else {
            items(directeurs) { d -> DirecteurCard(d, isUnlocked) { onOpenDirecteur(d) } }
        }
        if (communes.isEmpty() && directeurs.isEmpty()) {
            item {
                EmptyState(
                    if (all.isEmpty())
                        "Aucune donnée pour l'instant.\nAppuyez sur le cadenas « Privé », saisissez le PIN,\npuis « Ajouter une commune »."
                    else "Aucun résultat"
                )
            }
        }
    }
}

/* ---------- Commune ---------- */
@Composable
fun CommuneDetailScreen(
    commune: Commune, repo: Repo, ui: Ui, isUnlocked: Boolean,
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
                            color = Color(0xFFDCE8F7), fontSize = 11.sp)
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
                        ContactRow(Icons.Filled.Phone, "Tél. du maire", commune.telMaire, isUnlocked)
                        HLine()
                        ContactRow(Icons.Filled.Place, "Adresse", commune.adresseMairie, isUnlocked)
                        HLine()
                        ContactRow(Icons.Filled.Phone, "Téléphone", commune.telMairie, isUnlocked)
                    }
                }
                if (isUnlocked) {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton("Modifier", Icons.Filled.Edit, Indigo, {
                            ui.form(Form(
                                "Modifier la commune", communeLabels,
                                listOf(commune.nom, commune.departement, commune.population,
                                    commune.maire, commune.telMaire, commune.adresseMairie, commune.telMairie)
                            ) { v ->
                                repo.editCommune(commune.id) {
                                    it.copy(nom = v[0], departement = v[1], population = v[2], maire = v[3],
                                        telMaire = v[4], adresseMairie = v[5], telMairie = v[6])
                                }
                            })
                        }, Modifier.weight(1f))
                        ActionButton("Supprimer", Icons.Filled.Delete, Red, {
                            ui.confirm(Confirm("Supprimer « ${commune.nom} » et toutes ses écoles ?") {
                                onBack()
                                repo.deleteCommune(commune.id)
                            })
                        }, Modifier.weight(1f))
                    }
                    ActionButton("Ajouter une école", Icons.Filled.Add, Emerald, {
                        ui.form(Form("Nouvelle école", ecoleLabels, List(ecoleLabels.size) { "" }) { v ->
                            repo.editCommune(commune.id) {
                                it.copy(ecoles = it.ecoles + Ecole(newId(), v[0], v[1], v[2],
                                    Directeur(v[3], v[4]), emptyList()))
                            }
                        })
                    }, Modifier.fillMaxWidth().padding(top = 8.dp))
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
                        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE8F0FA)),
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

/* ---------- École ---------- */
@Composable
fun EcoleDetailScreen(
    commune: Commune, ecole: Ecole, repo: Repo, ui: Ui, isUnlocked: Boolean,
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
                        Text("${ecole.professeurs.size} enseignant(s)", color = Color(0xFFDCE8F7), fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
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
                        Text(if (isUnlocked) "Coordonnées visibles · modification active" else "Mode privé actif",
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
                if (isUnlocked) {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton("Modifier", Icons.Filled.Edit, Indigo, {
                            ui.form(Form(
                                "Modifier l'école", ecoleLabels,
                                listOf(ecole.nom, ecole.adresse, ecole.tel,
                                    ecole.directeur.nom, ecole.directeur.tel)
                            ) { v ->
                                repo.editEcole(commune.id, ecole.id) {
                                    it.copy(nom = v[0], adresse = v[1], tel = v[2],
                                        directeur = Directeur(v[3], v[4]))
                                }
                            })
                        }, Modifier.weight(1f))
                        ActionButton("Supprimer", Icons.Filled.Delete, Red, {
                            ui.confirm(Confirm("Supprimer « ${ecole.nom} » ?") {
                                onBack()
                                repo.editCommune(commune.id) { c ->
                                    c.copy(ecoles = c.ecoles.filter { it.id != ecole.id })
                                }
                            })
                        }, Modifier.weight(1f))
                    }
                    ActionButton("Ajouter un professeur", Icons.Filled.Add, Emerald, {
                        ui.form(Form("Nouveau professeur", profLabels, List(profLabels.size) { "" }) { v ->
                            repo.editEcole(commune.id, ecole.id) {
                                it.copy(professeurs = it.professeurs + Professeur(v[0], v[1], v[2]))
                            }
                        })
                    }, Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("CORPS ENSEIGNANT", style = SectionStyle)
            }
        }
        itemsIndexed(ecole.professeurs) { i, t ->
            TeacherRow(
                t, isUnlocked,
                onEdit = {
                    ui.form(Form("Modifier le professeur", profLabels, listOf(t.nom, t.classe, t.tel)) { v ->
                        repo.editEcole(commune.id, ecole.id) { e ->
                            e.copy(professeurs = e.professeurs.mapIndexed { j, p ->
                                if (j == i) Professeur(v[0], v[1], v[2]) else p
                            })
                        }
                    })
                },
                onDelete = {
                    ui.confirm(Confirm("Supprimer « ${t.nom} » ?") {
                        repo.editEcole(commune.id, ecole.id) { e ->
                            e.copy(professeurs = e.professeurs.filterIndexed { j, _ -> j != i })
                        }
                    })
                }
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

/* ---------- Directeur ---------- */
@Composable
fun DirecteurDetailScreen(
    commune: Commune, ecole: Ecole, isUnlocked: Boolean,
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
                        Text(initials(ecole.directeur.nom), color = Color.White,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(ecole.directeur.nom, color = Color.White,
                            fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("${ecole.nom} · ${commune.nom}",
                            color = Color.
