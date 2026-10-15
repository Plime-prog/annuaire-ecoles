package com.plime.annuaire.v3

import com.plime.annuaire.v5.*
import com.plime.annuaire.v6.CarteScreenV6
import com.plime.annuaire.v6.LogoOfficielV6

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plime.annuaire.BandeauFrance
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.scale
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll

class MainActivityV5 : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AnnuaireV5App() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnuaireV5App() {
    val ctx = LocalContext.current
    var annuaire by remember { mutableStateOf(AnnuaireV3()) }
    var unlocked by remember { mutableStateOf(false) }
    var hasPin by remember { mutableStateOf(false) }
    var tabIndex by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showSchema by remember { mutableStateOf(false) }
    /* V6 : carte de l'Aube (bouton carte en haut) */
    var showCarte by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var revision by remember { mutableStateOf(0) }

    /* Édition d'une ligne : onglet + copie de la ligne (ou ligne neuve) */
    var editTab by remember { mutableStateOf<TabV3?>(null) }
    var editRow by remember { mutableStateOf<MutableMap<String, String>?>(null) }
    var editIsNew by remember { mutableStateOf(false) }
    /* Ligne d'origine pour l'édition (null si nouvelle) */
    var editOriginal by remember { mutableStateOf<MutableMap<String, String>?>(null) }

    /* Navigation en escalier : groupe ouvert (ex. commune), puis fiche ouverte (ex. école) */
    var openGroup by remember { mutableStateOf<String?>(null) }
    var openRow by remember { mutableStateOf<MutableMap<String, String>?>(null) }

    /* Logo .jpg affiché au-dessus du bandeau tricolore */
    var logoVersion by remember { mutableStateOf(0) }
    var filtreV5 by remember { mutableStateOf("dsden") }
    var ongletV5 by remember { mutableStateOf(0) }
    var openTabV5 by remember { mutableStateOf<TabV3?>(null) }

    /* V6 : données des circonscriptions (chips du filtre) */
    var circosV5 by remember { mutableStateOf(listOf<CircoV5>()) }

    fun saveAll() {
        StorageV3.save(ctx, annuaire)
        revision++
    }

    LaunchedEffect(Unit) {
        annuaire = StorageV3.load(ctx)
        hasPin = StorageV3.hasPin(ctx)
        circosV5 = loadCircosV5(ctx)
    }

    /* V6 : les onglets v5 sont régénérés dès qu'ils manquent - y compris
       après un import Excel, « Tout effacer » ou « Défauts ». */
    LaunchedEffect(annuaire) {
        if (annuaire.tabs.none { it.id.startsWith("v5_") }) {
            assureTabsV5(annuaire, ctx)
            StorageV3.save(ctx, annuaire)
            revision++
        }
    }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) try {
            annuaire = ExcelIOV3.import(ctx, uri)
            StorageV3.save(ctx, annuaire)
            revision++
            val n = annuaire.tabs.sumOf { it.rows.size }
            message = "Import réussi : ${annuaire.tabs.size} onglet(s), $n ligne(s). " +
                "(Onglets et champs reconstruits d'après le fichier.)"
        } catch (e: Exception) {
            message = "Échec de l'import : ${e.message ?: "fichier invalide"}"
        }
    }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) try {
            ExcelIOV3.export(ctx, uri, annuaire)
            message = "Export réussi."
        } catch (e: Exception) {
            message = "Échec de l'export : ${e.message ?: "erreur"}"
        }
    }

    val modeleur = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) try {
            ExcelIOV3.writeTemplate(ctx, uri, annuaire)
            message = "Modèle créé (en-têtes actuels, sans données)."
        } catch (e: Exception) {
            message = "Échec : ${e.message ?: "erreur"}"
        }
    }

    val logoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) try {
            val dest = File(ctx.filesDir, "logo.jpg")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
            logoVersion++
            message = "Logo ajouté."
        } catch (e: Exception) {
            message = "Échec du logo : ${e.message ?: "erreur"}"
        }
    }

    /* Thème : fond blanc partout, écriture noire, accents vert menthe.
       TOUTES les cases de couleur sont remplies : plus aucune couleur
       par défaut (rose ou violet) de Material 3 ne peut apparaître,
       y compris dans les boîtes de dialogue (Réglages, PIN...). */
    MaterialTheme(
        colorScheme = lightColorScheme(
            background = Color.White,
            onBackground = Color.Black,
            surface = Color.White,
            onSurface = Color.Black,
            surfaceVariant = Color.White,
            onSurfaceVariant = Color(0xFF333333),
            surfaceTint = Color.White,
            surfaceDim = Color.White,
            surfaceBright = Color.White,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color.White,
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color.White,
            surfaceContainerHighest = Color.White,
            inverseSurface = Color(0xFF222222),
            inverseOnSurface = Color.White,
            inversePrimary = VertMenthe,
            primary = VertMenthe,
            onPrimary = Color.White,
            primaryContainer = Color.White,
            onPrimaryContainer = Color.Black,
            secondary = VertMenthe,
            onSecondary = Color.White,
            secondaryContainer = Color.White,
            onSecondaryContainer = Color.Black,
            tertiary = VertMenthe,
            onTertiary = Color.White,
            tertiaryContainer = Color.White,
            onTertiaryContainer = Color.Black,
            error = Color(0xFFB3261E),
            onError = Color.White,
            errorContainer = Color.White,
            onErrorContainer = Color(0xFFB3261E),
            outline = Color(0xFF777777),
            outlineVariant = Color(0xFFDDDDDD)
        )
    ) {
        if (!hasPin) {
            PinDefineScreen { pin ->
                StorageV3.setPin(ctx, pin)
                unlocked = true
                hasPin = true
            }
        } else if (!unlocked) {
            PinEnterScreen { pin ->
                if (StorageV3.checkPin(ctx, pin)) unlocked = true
                else message = "Code incorrect."
            }
        } else {
            val t = editTab
            val r = editRow
            if (t != null && r != null) EntityDialogV3(
                tab = t, values = r,
                isNew = editIsNew,
                onSave = {
                    if (editIsNew) {
                        r["_id"] = newKeyV3()
                        t.rows.add(r)
                    } else {
                        editOriginal?.clear()
                        editOriginal?.putAll(r)
                    }
                    editTab = null; editRow = null
                    saveAll()
                    message = "Modifications enregistrées."
                },
                onCancel = { editTab = null; editRow = null },
                onDelete = if (editIsNew) null else {{
                    editOriginal?.let { orig -> t.rows.removeIf { it === orig } }
                    editTab = null; editRow = null
                    saveAll()
                    message = "Ligne supprimée."
                }}
            )

            if (showCarte) {
                CarteScreenV6(
                    filtreActif = filtreV5,
                    onCirco = { code ->
                        filtreV5 = code
                        ongletV5 = 0
                        openGroup = null
                        openRow = null
                        openTabV5 = null
                        showCarte = false
                    },
                    onFermer = { showCarte = false }
                )
            } else if (showSchema) {
                SchemaEditorScreen(
                    annuaire = annuaire,
                    onDone = { saveAll(); showSchema = false; message = "Structure enregistrée." },
                    onRestore = {
                        annuaire = defaultSchemaV3()
                        saveAll()
                        tabIndex = 0
                        message = "Structure par défaut restaurée (données effacées)."
                    }
                )
            } else {
                val safeIndex =
                    if (annuaire.tabs.isEmpty()) 0
                    else tabIndex.coerceIn(0, annuaire.tabs.size - 1)
                val currentTab = annuaire.tabs.getOrNull(safeIndex)

                val logoV5 = remember(logoVersion) {
                    File(ctx.filesDir, "logo.jpg").takeIf { it.exists() }
                        ?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
                }
                Scaffold(
                    topBar = {
                        /* V6 : en-tête sur mesure, plus haut qu'une TopAppBar,
                           pour afficher le logo en grand.
                           Le bandeau tricolore n'est plus affiché ici ; le
                           composant BandeauFrance() est conservé dans
                           BandeauFrance.kt pour une réutilisation future. */
                        Surface(color = Color.White) {
                            Row(
                                Modifier.fillMaxWidth().height(112.dp)
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                /* Le logo remplace le titre : toucher le logo
                                   pour en choisir un autre. */
                                Row(
                                    Modifier.weight(1f).clickable {
                                        logoPicker.launch(arrayOf("image/*"))
                                    },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (logoV5 != null) {
                                        Image(
                                            logoV5, "Logo - toucher pour changer",
                                            Modifier.fillMaxWidth().heightIn(max = 100.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        /* Logo officiel agrandi : échelle 2,4 */
                                        Box(
                                            Modifier.fillMaxWidth().height(100.dp),
                                            contentAlignment = Alignment.Center
                                        ) { LogoOfficielV6(Modifier.scale(2.4f)) }
                                    }
                                }
                                IconButton(onClick = { showCarte = true }) {
                                    Icon(Icons.Filled.Map, "Carte de l'Aube", tint = VertMenthe)
                                }
                                IconButton(onClick = { showSchema = true }) {
                                    Icon(Icons.Filled.Science, "Éditer la structure", tint = VertMenthe)
                                }
                                IconButton(onClick = { showSettings = true }) {
                                    Icon(Icons.Filled.Settings, "Réglages", tint = VertMenthe)
                                }
                            }
                        }
                    },
                    floatingActionButton = {
                        val og5 = ongletsDuFiltre(annuaire, filtreV5)
                        val t5 = if (og5.isEmpty()) null else og5[ongletV5.coerceIn(0, og5.size - 1)].second
                        if (t5 != null) FloatingActionButton(onClick = {
                            editTab = t5
                            editOriginal = null
                            editIsNew = true
                            editRow = mutableMapOf("_id" to newKeyV3())
                        }) { Icon(Icons.Filled.Add, "Ajouter") }
                    }
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        /* Champ de recherche compact (réduit) */
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            OutlinedTextField(
                                value = query, onValueChange = { query = it },
                                modifier = Modifier.fillMaxWidth(0.86f)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                placeholder = { Text("Rechercher...", fontSize = 13.sp) },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Filled.Search, null, tint = VertMenthe) },
                                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                                    fontSize = 13.sp
                                )
                            )
                        }
                        key(revision, filtreV5, ongletV5, query) {
                            val og5 = ongletsDuFiltre(annuaire, filtreV5)
                            val t5 = if (og5.isEmpty()) null else og5[ongletV5.coerceIn(0, og5.size - 1)].second
                            val circoOuverte = circosV5.firstOrNull { it.code == filtreV5 }
                            val tabCommunes = annuaire.tabs.firstOrNull { it.name.equals("Communes", ignoreCase = true) }
                            val fiche = openRow
                            val tabFiche = openTabV5 ?: tabCommunes
                            val grp = openGroup
                            if (fiche != null && tabFiche != null) {
                                val nomFiltreV5 = if (filtreV5 == "dsden") "DSDEN"
                                    else circosV5.firstOrNull { it.code == filtreV5 }
                                        ?.let { nomCourt(it.nom) } ?: filtreV5
                                val groupeFiche = tabFiche.groupBy.takeIf { it.isNotBlank() }
                                    ?.let { fiche[it] ?: "" }
                                RowDetailScreen(
                                    tab = tabFiche,
                                    row = fiche,
                                    onBack = { openRow = null; openTabV5 = null },
                                    filAriane = listOfNotNull(
                                        nomFiltreV5,
                                        tabFiche.name,
                                        groupeFiche?.takeIf { it.isNotBlank() }
                                    ),
                                    onEdit = {
                                        editTab = tabFiche
                                        editOriginal = fiche
                                        editIsNew = false
                                        editRow = LinkedHashMap(fiche)
                                    },
                                    onDelete = {
                                        tabFiche.rows.removeIf { it === fiche }
                                        openRow = null; openTabV5 = null
                                        saveAll()
                                        message = "Fiche supprimée."
                                    }
                                )
                            } else if (grp != null && t5 != null) {
                                GroupDetailScreen(
                                    tab = t5,
                                    groupName = grp,
                                    rows = t5.rows.filter { (it[t5.groupBy] ?: "") == grp },
                                    onBack = { openGroup = null },
                                    onOpenRow = { openTabV5 = t5; openRow = it }
                                )
                            } else {
                                EcranAccueilV5(
                                    filtre = filtreV5,
                                    circos = circosV5,
                                    onFiltre = { filtreV5 = it; ongletV5 = 0; openGroup = null; openRow = null; openTabV5 = null },
                                    onglets = og5.map { it.first },
                                    ongletIndex = ongletV5.coerceIn(0, (og5.size - 1).coerceAtLeast(0)),
                                    onOnglet = { ongletV5 = it; openGroup = null; openRow = null; openTabV5 = null },
                                    bas = {
                                        if (query.isNotBlank()) {
                                            /* Recherche globale : résultats dans TOUS les onglets */
                                            RechercheGlobaleV5(annuaire, query) { tb, r ->
                                                openTabV5 = tb
                                                openRow = r
                                            }
                                        } else when {
                                            t5 != null -> TabScreenV3(
                                                tab = t5, query = query,
                                                onOpenGroup = { name ->
                                                    val rows5 = t5.rows.filter { (it[t5.groupBy] ?: "") == name }
                                                    if (rows5.size == 1) openRow = rows5[0] else openGroup = name
                                                },
                                                onOpenRow = { openTabV5 = t5; openRow = it }
                                            )
                                            filtreV5 == "dsden" -> Text("Aucun onglet DSDEN généré. Videz les données de l'application puis relancez.", Modifier.padding(16.dp), color = Color(0xFFB3261E))
                                            tabCommunes != null -> {
                                                val lignes = tabCommunes.rows.filter { circoDeRow(it) == filtreV5 }
                                                if (lignes.isEmpty()) {
                                                    Text("Aucune école rattachée à cette circonscription.", Modifier.padding(16.dp))
                                                } else {
                                                    val parCommune = lignes.groupBy { it[tabCommunes.groupBy] ?: "(sans commune)" }.toSortedMap()
                                                    ListeEcolesParCommuneV5(
                                                        communes = parCommune.map { it.key to it.value },
                                                        titreEcole = { row -> titreDetail(tabCommunes, row) },
                                                        onOpenRow = { openTabV5 = tabCommunes; openRow = it }
                                                    )
                                                }
                                            }
                                            else -> Text("Aucune école rattachée à cette circonscription.", Modifier.padding(16.dp))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (showSettings) SettingsDialogV3(
                message = message,
                onDismiss = { showSettings = false; message = "" },
                onImport = {
                    showSettings = false
                    importer.launch(arrayOf(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.ms-excel",
                        "application/octet-stream"))
                },
                onExport = { showSettings = false; exporter.launch("annuaire-v3.xlsx") },
                onTemplate = { showSettings = false; modeleur.launch("modele-annuaire-v3.xlsx") },
                onLogo = {
                    showSettings = false
                    logoPicker.launch(arrayOf("image/*"))
                },
                onLock = { showSettings = false; unlocked = false },
                onClear = {
                    annuaire = defaultSchemaV3()
                    saveAll()
                    tabIndex = 0
                    message = "Données effacées (structure par défaut)."
                },
                onChangePin = { old, new ->
                    if (StorageV3.checkPin(ctx, old)) {
                        StorageV3.setPin(ctx, new)
                        message = "Code PIN modifié."
                    } else message = "Ancien code incorrect."
                }
            )
        }
    }
}

/* ==================== APPEL TÉLÉPHONE (ACTION_DIAL, sans permission) ==================== */

/** Champ téléphone ? (libellé contenant tel, phone, portable, mobile...) */
private fun estTelV5(label: String): Boolean {
    val l = normV3(label)
    return l.contains("tel") || l.contains("phone") || l.contains("portable") || l.contains("mobile")
}

/** Ouvre le composeur avec le numéro pré-rempli (aucune permission requise). */
private fun appelTelephone(ctx: android.content.Context, numero: String) {
    val nettoye = numero.filter { it.isDigit() || it == '+' }
    if (nettoye.isBlank()) return
    try {
        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + nettoye)))
    } catch (_: Exception) {
        /* pas de composeur disponible : on ignore silencieusement */
    }
}

/* ==================== STYLE : encadré vert menthe sur fond blanc ==================== */

private val VertMenthe = Color(0xFF3CB371)

/** Encadré vert menthe, fond blanc, texte taille 16 - utilisé sur toutes les pages. */
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
    ) {
        Column(Modifier.padding(12.dp)) { contenu() }
    }
}

/* ==================== TITRES DE FICHES ==================== */

/** Titre d'une fiche : « École » pour les communes, « Nom » pour les collèges,
    « Contact » pour la circonscription - sinon 1ère valeur du groupe. */
private fun titreDetail(tab: TabV3, row: Map<String, String>): String {
    for (p in listOf("\u00E9cole", "ecole", "nom")) {
        tab.fields.firstOrNull {
            it.key != tab.groupBy && normV3(it.label).contains(p) &&
                !(row[it.key] ?: "").isBlank()
        }?.let { return row[it.key] ?: "" }
    }
    if (normV3(tab.name).contains("circonscription")) {
        tab.fields.firstOrNull {
            normV3(it.label).contains("contact") && !(row[it.key] ?: "").isBlank()
        }?.let { return row[it.key] ?: "" }
    }
    (row[tab.groupBy] ?: "").takeIf { it.isNotBlank() }?.let { return it }
    return rowTitleV3(tab, row)
}

/* ==================== NIVEAU 1 : LISTE D'UN ONGLET ==================== */

/** Champs identiques dans toutes les fiches du groupe (ex. maire, mairie). */
private fun champsConstants(tab: TabV3, rows: List<Map<String, String>>): List<Pair<String, String>> =
    tab.fields.mapNotNull { f ->
        if (f.key == tab.groupBy) null
        else {
            val v = rows.first()[f.key] ?: ""
            if (v.isNotBlank() && rows.all { (it[f.key] ?: "") == v }) f.label to v else null
        }
    }

/** Valeurs qui changent d'une fiche à l'autre dans le groupe (ex. école). */
private fun valeursVariables(tab: TabV3, rows: List<Map<String, String>>, row: Map<String, String>): List<String> =
    tab.fields.mapNotNull { f ->
        if (f.key == tab.groupBy) null
        else {
            val v = row[f.key] ?: ""
            val varie = rows.map { it[f.key] ?: "" }.distinct().size > 1
            if (v.isNotBlank() && varie) v else null
        }
    }

/** Titre d'une fiche du groupe : 1ère valeur variable (ex. nom de l'école). */
private fun titreDansGroupe(tab: TabV3, rows: List<Map<String, String>>, row: Map<String, String>): String =
    valeursVariables(tab, rows, row).firstOrNull()
        ?: tab.fields.firstNotNullOfOrNull { f ->
            if (f.key != tab.groupBy) (row[f.key] ?: "").takeIf { it.isNotBlank() } else null
        }
        ?: "(détails)"

/** Sous-titre d'une fiche du groupe : les 2 valeurs variables suivantes. */
private fun sousTitreDansGroupe(tab: TabV3, rows: List<Map<String, String>>, row: Map<String, String>): String {
    val vals = valeursVariables(tab, rows, row)
    return if (vals.size > 1) vals.drop(1).take(2).joinToString(" · ") else ""
}

/** Liste : noms seulement (communes, collèges) ou personnes (circonscription). */
@Composable
private fun TabScreenV3(
    tab: TabV3,
    query: String,
    onOpenGroup: (String) -> Unit,
    onOpenRow: (MutableMap<String, String>) -> Unit
) {
    val q = normV3(query)
    val filtered = if (q.isBlank()) tab.rows
    else tab.rows.filter { row -> row.values.any { normV3(it).contains(q) } }

    /* Champ « Circonscription élargie » éventuel */
    val keyEl = tab.fields.firstOrNull { normV3(it.label).contains("elargie") }?.key
    val normales = if (keyEl != null) filtered.filter { normV3(it[keyEl] ?: "") != "oui" } else filtered
    val elargies = if (keyEl != null) filtered.filter { normV3(it[keyEl] ?: "") == "oui" } else emptyList()
    var elargieOuvert by remember(tab.id) { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().padding(vertical = 4.dp)) {
        /* Bascule « Circonscription élargie (PIAL-ER) » */
        if (keyEl != null && elargies.isNotEmpty()) {
            item(key = "elargie_titre") {
                Encadre(onClick = { elargieOuvert = !elargieOuvert }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (elargieOuvert) Icons.Filled.ExpandMore else Icons.Filled.Add,
                            null, tint = VertMenthe
                        )
                        Text(
                            "Circonscription élargie  (${elargies.size})",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = VertMenthe
                        )
                    }
                }
            }
            if (elargieOuvert) items(elargies, key = { it["_id"] ?: it.hashCode().toString() }) { row ->
                Encadre(onClick = { onOpenRow(row) }) {
                    Text(titreDetail(tab, row), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    val sub = rowSubtitleV3(tab, row)
                    if (sub.isNotBlank()) Text(sub, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (tab.groupBy.isNotBlank() && normales.isNotEmpty()) {
            /* Liste des noms de groupes : communes, collèges... */
            val groups = normales.groupBy { it[tab.groupBy] ?: "" }
                .map { (k, v) -> k.ifBlank { "(sans titre)" } to v.size }
                .sortedBy { normV3(it.first) }
            items(groups, key = { it.first }) { (name, count) ->
                Encadre(onClick = { onOpenGroup(name) }) {
                    Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("$count fiche(s)", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            /* Liste à plat : les personnes de la circonscription... */
            items(normales, key = { it["_id"] ?: it.hashCode().toString() }) { row ->
                Encadre(onClick = { onOpenRow(row) }) {
                    Text(titreDetail(tab, row), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    val sub = rowSubtitleV3(tab, row)
                    if (sub.isNotBlank()) Text(sub, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (filtered.isEmpty()) item {
            Text(
                if (query.isBlank()) "Aucune fiche. Touchez \u2795 pour en ajouter."
                else "Aucun résultat.",
                Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/* ==================== NIVEAU 2 : DÉTAIL D'UN GROUPE (ex. une commune) ==================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupDetailScreen(
    tab: TabV3,
    groupName: String,
    rows: List<MutableMap<String, String>>,
    onBack: () -> Unit,
    onOpenRow: (MutableMap<String, String>) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        groupName,
                        fontWeight = FontWeight.Bold,
                        color = VertMenthe
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Retour") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(vertical = 4.dp)
        ) {
            /* Infos du groupe : maire, adresse mairie... */
            if (rows.isNotEmpty()) item(key = "infos") {
                Encadre {
                    champsConstants(tab, rows).forEach { (label, value) ->
                        Text(label, fontSize = 11.sp, color = VertMenthe)
                        Text(value, fontSize = 15.sp)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
            /* Les fiches du groupe : les écoles de CETTE commune... */
            items(rows, key = { it["_id"] ?: it.hashCode().toString() }) { row ->
                Encadre(onClick = { onOpenRow(row) }) {
                    Text(titreDansGroupe(tab, rows, row), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    val sub = sousTitreDansGroupe(tab, rows, row)
                    if (sub.isNotBlank()) Text(sub, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/* ==================== NIVEAU 3 : DÉTAIL D'UNE FICHE (école, collège, personne) ==================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowDetailScreen(
    tab: TabV3,
    row: MutableMap<String, String>,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    /* V6 : fil d'Ariane - les boutons verts qui ont mené à cette fiche */
    filAriane: List<String> = emptyList()
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        titreDetail(tab, row),
                        fontWeight = FontWeight.Bold,
                        color = VertMenthe
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Retour") }
                },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Modifier") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(12.dp)
        ) {
            /* Fil d'Ariane : rappel du chemin en boutons verts */
            if (filAriane.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filAriane.forEach { n ->
                        Text(
                            n,
                            Modifier
                                .background(VertMenthe, RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            color = Color.White, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Encadre {
                var auMoinsUn = false
                tab.fields.forEach { f ->
                    val v = row[f.key] ?: ""
                    if (v.isNotBlank()) {
                        auMoinsUn = true
                        Text(f.label, fontSize = 11.sp, color = VertMenthe)
                        if (estTelV5(f.label)) {
                            /* Ligne téléphone : toucher = composeur pré-rempli */
                            val ctxF = LocalContext.current
                            Row(
                                Modifier.clickable { appelTelephone(ctxF, v) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Call, "Appeler", tint = VertMenthe)
                                Spacer(Modifier.width(6.dp))
                                Text(v, fontSize = 16.sp, color = VertMenthe,
                                    fontWeight = FontWeight.SemiBold)
                            }
                        } else Text(v, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (!auMoinsUn) Text("(fiche vide)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { confirmDelete = true }) {
                Text("Supprimer cette fiche", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmDelete) ConfirmDialog(
        "Supprimer cette fiche ?",
        onDismiss = { confirmDelete = false }
    ) {
        confirmDelete = false
        onDelete()
    }
}

/* ==================== RECHERCHE GLOBALE (tout l'annuaire) ==================== */

/** Dès qu'on tape dans le champ du haut : résultats tirés de TOUS les
    onglets (DSDEN, circonscriptions, communes, écoles, collèges...). */
@Composable
private fun RechercheGlobaleV5(
    annuaire: AnnuaireV3,
    query: String,
    onOpen: (TabV3, MutableMap<String, String>) -> Unit
) {
    val q = normV3(query)
    val resultats = remember(annuaire, q) {
        annuaire.tabs.flatMap { tb ->
            tb.rows.filter { r -> r.values.any { normV3(it).contains(q) } }
                .map { tb to it }
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(vertical = 4.dp)) {
        if (resultats.isEmpty()) {
            item {
                Text(
                    "Aucun résultat pour « ${query} ».",
                    Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            item {
                Text(
                    "${resultats.size} résultat(s)",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = VertMenthe, fontWeight = FontWeight.Bold
                )
            }
            items(resultats, key = { it.second["_id"] ?: it.second.hashCode().toString() }) { (tb, r) ->
                Encadre(onClick = { onOpen(tb, r) }) {
                    Text(titreDetail(tb, r), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(tb.name, fontSize = 12.sp, color = Color(0xFF666666))
                }
            }
        }
    }
}

/* ==================== BOÎTE DE DIALOGUE : éditer une ligne ==================== */

@Composable
private fun EntityDialogV3(
    tab: TabV3,
    values: MutableMap<String, String>,
    isNew: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val scroll = rememberScrollState()

    /* V6 : la saisie passe par des états Compose - sans cela, l'écriture
       directe dans la Map ne déclenche aucun rafraîchissement et le
       clavier semble ne rien saisir. */
    val saisies = remember(tab, values) {
        tab.fields.associate { f -> f.key to mutableStateOf(values[f.key] ?: "") }
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                if (isNew) "Nouvelle ligne - ${tab.name}" else rowTitleV3(tab, values),
                color = VertMenthe
            )
        },
        text = {
            Column(Modifier.verticalScroll(scroll)) {
                tab.fields.forEach { f ->
                    val saisie = saisies[f.key] ?: return@forEach
                    OutlinedTextField(
                        value = saisie.value,
                        onValueChange = { saisie.value = it },
                        label = { Text(f.label) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        minLines = if (f.multiline) 2 else 1,
                        maxLines = if (f.multiline) 4 else 1
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                /* Recopier la saisie dans la fiche, puis enregistrer */
                tab.fields.forEach { f -> values[f.key] = saisies[f.key]?.value ?: "" }
                onSave()
            }) { Text("Enregistrer") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onCancel) { Text("Annuler") }
            }
        }
    )
}

/* ==================== ÉDITEUR DE STRUCTURE (onglets + champs) ==================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchemaEditorScreen(
    annuaire: AnnuaireV3,
    onDone: () -> Unit,
    onRestore: () -> Unit
) {
    var selected by remember { mutableStateOf<String?>(null) }
    var renameTab by remember { mutableStateOf<TabV3?>(null) }
    var addTab by remember { mutableStateOf(false) }
    var delTab by remember { mutableStateOf<TabV3?>(null) }
    var renameField by remember { mutableStateOf<Pair<TabV3, FieldV3>?>(null) }
    var addField by remember { mutableStateOf<TabV3?>(null) }
    var delField by remember { mutableStateOf<Pair<TabV3, FieldV3>?>(null) }
    var pickGroup by remember { mutableStateOf<TabV3?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }

    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    "Structure",
                    fontWeight = FontWeight.Bold,
                    color = VertMenthe
                )
            },
            navigationIcon = {
                IconButton(onClick = onDone) { Icon(Icons.Filled.Check, "Terminé") }
            },
            actions = {
                TextButton(onClick = { confirmRestore = true }) {
                    Text("Défauts", color = MaterialTheme.colorScheme.error)
                }
            }
        )
        HorizontalDivider()
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp)) {
            Text("Onglets", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            annuaire.tabs.forEach { t ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { selected = if (selected == t.id) null else t.id }) {
                        Text(
                            (if (selected == t.id) "\u25BE " else "\u25B8 ") + t.name +
                                "  (${t.rows.size} lignes, ${t.fields.size} champs)",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { renameTab = t }) { Icon(Icons.Filled.Edit, "Renommer") }
                    IconButton(onClick = { delTab = t }) {
                        Icon(Icons.Filled.Delete, "Supprimer",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
                if (selected == t.id) {
                    /* --- Éditeur des champs de l'onglet --- */
                    Surface(
                        tonalElevation = 1.dp,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 8.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Champs de « ${t.name} »", fontWeight = FontWeight.SemiBold)
                            /* groupBy */
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("Regrouper par : ", fontSize = 13.sp)
                                TextButton(onClick = { pickGroup = t }) {
                                    Text(t.groupBy.ifBlank {
                                        "(aucun)"
                                    }.let { key ->
                                        t.fields.firstOrNull { f -> f.key == key }?.label ?: "(aucun)"
                                    })
                                }
                            }
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            t.fields.forEachIndexed { i, f ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${i + 1}. ${f.label}" + if (f.multiline) "  \u00B6" else "",
                                        fontSize = 14.sp, modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = {
                                        if (i > 0) {
                                            val m = t.fields[i - 1]; t.fields[i - 1] = f; t.fields[i] = m
                                        }
                                    }) { Icon(Icons.Filled.KeyboardArrowUp, "Monter") }
                                    IconButton(onClick = {
                                        if (i < t.fields.size - 1) {
                                            val m = t.fields[i + 1]; t.fields[i + 1] = f; t.fields[i] = m
                                        }
                                    }) { Icon(Icons.Filled.KeyboardArrowDown, "Descendre") }
                                    IconButton(onClick = { f.multiline = !f.multiline }) {
                                        Icon(
                                            if (f.multiline) Icons.Filled.Notes else Icons.Filled.ShortText,
                                            "Multi-ligne"
                                        )
                                    }
                                    IconButton(onClick = { renameField = t to f }) {
                                        Icon(Icons.Filled.Edit, "Renommer")
                                    }
                                    IconButton(onClick = { delField = t to f }) {
                                        Icon(Icons.Filled.Delete, "Supprimer",
                                            tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            OutlinedButton(onClick = { addField = t }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Ajouter un champ")
                            }
                        }
                    }
                }
            }
            Button(onClick = { addTab = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Ajouter un onglet")
            }
            Text(
                "\u00B6 = champ multi-lignes. Les suppressions de champs/onglets " +
                    "n'effacent que la structure : les valeurs concernées sont perdues.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }

    /* --- Petites boîtes de saisie --- */
    if (renameTab != null) TextPromptDialog(
        title = "Renommer l'onglet", initial = renameTab!!.name
    ) { s ->
        if (s.isNotBlank()) renameTab!!.name = s
        renameTab = null
    }
    if (addTab) TextPromptDialog(title = "Nom du nouvel onglet", initial = "") { s ->
        if (s.isNotBlank()) {
            val t = TabV3(newKeyV3(), s)
            annuaire.tabs.add(t)
            selected = t.id
        }
        addTab = false
    }
    if (delTab != null) ConfirmDialog(
        "Supprimer l'onglet « ${delTab!!.name} » et ses ${delTab!!.rows.size} ligne(s) ?",
        onDismiss = { delTab = null }
    ) {
        annuaire.tabs.remove(delTab!!)
        if (selected == delTab!!.id) selected = null
        delTab = null
    }
    if (renameField != null) TextPromptDialog(
        title = "Renommer le champ", initial = renameField!!.second.label
    ) { s ->
        if (s.isNotBlank()) renameField!!.second.label = s
        renameField = null
    }
    if (addField != null) TextPromptDialog(
        title = "Nom du nouveau champ", initial = ""
    ) { s ->
        if (s.isNotBlank()) addField!!.fields.add(
            FieldV3(newKeyV3(), s, multiline = s.contains("Observation")
                || s.contains("Adresse") || s.contains("Horaires"))
        )
        addField = null
    }
    if (delField != null) ConfirmDialog(
        "Supprimer le champ « ${delField!!.second.label} » ?\n" +
            "Les valeurs de ce colonne seront perdues.",
        onDismiss = { delField = null }
    ) {
        val (t, f) = delField!!
        t.fields.remove(f)
        if (t.groupBy == f.key) t.groupBy = ""
        delField = null
    }
    if (pickGroup != null) {
        val t = pickGroup!!
        AlertDialog(
            onDismissRequest = { pickGroup = null },
            title = { Text("Regrouper les lignes par", color = VertMenthe) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { t.groupBy = ""; pickGroup = null }) {
                        Text("(aucun regroupement)")
                    }
                    t.fields.forEach { f ->
                        TextButton(onClick = { t.groupBy = f.key; pickGroup = null }) {
                            Text(f.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickGroup = null }) { Text("Fermer") } }
        )
    }
    if (confirmRestore) ConfirmDialog(
        "Restaurer les 5 onglets par défaut ?\nTOUTES les données et la structure actuelles seront effacées.",
        onDismiss = { confirmRestore = false }
    ) {
        confirmRestore = false
        onRestore()
    }
}

@Composable
private fun TextPromptDialog(title: String, initial: String, onOk: (String) -> Unit) {
    var s by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onOk("") },
        title = { Text(title, color = VertMenthe) },
        text = {
            OutlinedTextField(
                value = s, onValueChange = { s = it },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onOk(s) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = { onOk("") }) { Text("Annuler") } }
    )
}

/** Boîte de confirmation : le 2e paramètre ferme la boîte (annuler),
 *  le 3e (trailing lambda) exécute l'action puis ferme. */
@Composable
private fun ConfirmDialog(
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirmer", color = VertMenthe) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Confirmer", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

/* ==================== ÉCRANS PIN ==================== */

@Composable
private fun PinDefineScreen(onDone: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
) {
        Text(
            "Définissez votre code PIN",
            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = VertMenthe
        )
        Spacer(Modifier.height(16.dp))
        Encadre {
            OutlinedTextField(
                value = pin, onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
                label = { Text("Code PIN") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = confirm, onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) confirm = it },
                label = { Text("Confirmation") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                isError = confirm.isNotBlank() && confirm != pin,
                modifier = Modifier.fillMaxWidth()
            )
            if (confirm.isNotBlank() && confirm != pin)
                Text("Les codes ne correspondent pas.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { if (pin.length >= 4 && pin == confirm) onDone(pin) },
                enabled = pin.length >= 4 && pin == confirm,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Valider") }
        }
    }
}

@Composable
private fun PinEnterScreen(onSuccess: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
) {
        Icon(Icons.Filled.Lock, null, Modifier.size(48.dp), tint = VertMenthe)
        Spacer(Modifier.height(8.dp))
        Text("Entrez votre code PIN", fontSize = 16.sp, color = VertMenthe)
        Spacer(Modifier.height(16.dp))
        Encadre {
            OutlinedTextField(
                value = pin, onValueChange = { pin = it },
                label = { Text("Code PIN") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { if (pin.isNotBlank()) onSuccess(pin) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Déverrouiller") }
        }
    }
}

/* ==================== PANNEAU RÉGLAGES ==================== */

@Composable
private fun SettingsDialogV3(
    message: String,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onTemplate: () -> Unit,
    onLogo: () -> Unit,
    onLock: () -> Unit,
    onClear: () -> Unit,
    onChangePin: (String, String) -> Unit
) {
    var showPinForm by remember { mutableStateOf(false) }
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Réglages", color = VertMenthe) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Button(onClick = onImport, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(Icons.Filled.UploadFile, null); Spacer(Modifier.width(8.dp)); Text("Importer un fichier Excel")
                }
                Button(onClick = onExport, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(Icons.Filled.Download, null); Spacer(Modifier.width(8.dp)); Text("Exporter vers Excel")
                }
                Button(onClick = onTemplate, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(Icons.Filled.Description, null); Spacer(Modifier.width(8.dp)); Text("Créer le modèle d'import")
                }
                OutlinedButton(onClick = onLogo, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(Icons.Filled.Image, null); Spacer(Modifier.width(8.dp)); Text("Choisir le logo (image)")
                }
                Text(
                    "Astuce : l'import reconstruit onglets et champs d'après les en-têtes du fichier.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                OutlinedButton(onClick = { showPinForm = !showPinForm }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(Icons.Filled.Password, null); Spacer(Modifier.width(8.dp)); Text("Changer le code PIN")
                }
                if (showPinForm) {
                    OutlinedTextField(
                        value = oldPin, onValueChange = { oldPin = it },
                        label = { Text("Ancien code") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                    OutlinedTextField(
                        value = newPin, onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) newPin = it },
                        label = { Text("Nouveau code (4 chiffres min.)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                    Button(
                        onClick = { onChangePin(oldPin, newPin); oldPin = ""; newPin = "" },
                        enabled = newPin.length >= 4,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Enregistrer le nouveau code") }
                }

                OutlinedButton(onClick = onLock, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(Icons.Filled.Lock, null); Spacer(Modifier.width(8.dp)); Text("Verrouiller maintenant")
                }
                TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                    Text("Tout effacer", color = MaterialTheme.colorScheme.error)
                }
                if (message.isNotBlank())
                    Text(message, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
}
