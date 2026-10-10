package com.plime.annuaire.v3

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

class MainActivityV3 : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AnnuaireV3App() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnuaireV3App() {
    val ctx = LocalContext.current
    var annuaire by remember { mutableStateOf(AnnuaireV3()) }
    var unlocked by remember { mutableStateOf(false) }
    var hasPin by remember { mutableStateOf(false) }
    var titre by remember { mutableStateOf("Circonscription") }
    var tabIndex by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showSchema by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var revision by remember { mutableStateOf(0) }

    /* Édition d'une ligne : onglet + copie de la ligne (ou ligne neuve) */
    var editTab by remember { mutableStateOf<TabV3?>(null) }
    var editRow by remember { mutableStateOf<MutableMap<String, String>?>(null) }
    var editIsNew by remember { mutableStateOf(false) }
    /* Ligne d'origine pour l'édition (null si nouvelle) */
    var editOriginal by remember { mutableStateOf<MutableMap<String, String>?>(null) }

    fun saveAll() {
        StorageV3.save(ctx, annuaire)
        revision++
    }

    LaunchedEffect(Unit) {
        annuaire = StorageV3.load(ctx)
        hasPin = StorageV3.hasPin(ctx)
        titre = StorageV3.getTitre(ctx)
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

    MaterialTheme {
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

            if (showSchema) {
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
                Scaffold(
                    topBar = {
                        Column {
                            TopAppBar(
                                title = { Text(titre, fontWeight = FontWeight.Bold) },
                                actions = {
                                    IconButton(onClick = { showSchema = true }) {
                                        Icon(Icons.Filled.Science, "Éditer la structure")
                                    }
                                    IconButton(onClick = { showSettings = true }) {
                                        Icon(Icons.Filled.Settings, "Réglages")
                                    }
                                }
                            )
                            BandeauFrance()
                            if (annuaire.tabs.isNotEmpty()) {
                                ScrollableTabRow(
                                    selectedTabIndex = safeIndex,
                                    edgePadding = 8.dp
                                ) {
                                    annuaire.tabs.forEachIndexed { i, tabV ->
                                        Tab(
                                            selected = i == safeIndex,
                                            onClick = { tabIndex = i; query = "" },
                                            text = { Text(tabV.name, maxLines = 1) }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    floatingActionButton = {
                        if (currentTab != null) FloatingActionButton(onClick = {
                            editTab = currentTab
                            editOriginal = null
                            editIsNew = true
                            editRow = mutableMapOf("_id" to newKeyV3())
                        }) { Icon(Icons.Filled.Add, "Ajouter") }
                    }
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            placeholder = { Text("Rechercher…") },
                            leadingIcon = { Icon(Icons.Filled.Search, null) },
                            singleLine = true
                        )
                        key(revision, safeIndex, query) {
                            if (currentTab != null) TabScreenV3(
                                tab = currentTab, query = query,
                                onEdit = { row ->
                                    editTab = currentTab
                                    editOriginal = row
                                    editIsNew = false
                                    editRow = LinkedHashMap(row)
                                }
                            ) else Text(
                                "Aucun onglet. Touchez 🧪 pour créer la structure.",
                                Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }

            if (showSettings) SettingsDialogV3(
                message = message,
                titre = titre,
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
                },
                onChangeTitre = { nouveau ->
                    titre = nouveau
                    StorageV3.setTitre(ctx, nouveau)
                    message = "Titre modifié."
                }
            )
        }
    }
}

/* ==================== ÉCRAN D'UN ONGLET (groupes pliables) ==================== */

@Composable
private fun TabScreenV3(tab: TabV3, query: String, onEdit: (MutableMap<String, String>) -> Unit) {
    val q = normV3(query)
    val filtered = if (q.isBlank()) tab.rows
    else tab.rows.filter { row -> row.values.any { normV3(it).contains(q) } }

    val groups: List<Pair<String, List<MutableMap<String, String>>>> =
        if (tab.groupBy.isNotBlank()) {
            filtered.groupBy { it[tab.groupBy] ?: "" }
                .toSortedMap(compareBy { normV3(it) })
                .map { (k, v) -> (k.ifBlank { "(sans groupe)" }) to v }
        } else listOf(("" to filtered))

    val expanded = remember(tab.id) { mutableStateMapOf<String, Boolean>() }

    LazyColumn(Modifier.fillMaxSize()) {
        groups.forEach { (gName, rows) ->
            if (tab.groupBy.isNotBlank()) {
                item(key = "h_$gName") {
                    val open = expanded[gName] ?: true
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { expanded[gName] = !open }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (open) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                            null, tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "$gName  (${rows.size})",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            val open = expanded[gName] ?: true
            if (open || tab.groupBy.isBlank()) {
                items(rows, key = { it["_id"] ?: it.hashCode().toString() }) { row ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onEdit(row) }.padding(
                            horizontal = 16.dp, vertical = 10.dp
                        )
                    ) {
                        Text(rowTitleV3(tab, row), fontWeight = FontWeight.SemiBold)
                        val sub = rowSubtitleV3(tab, row)
                        if (sub.isNotBlank()) Text(sub, fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        if (groups.all { it.second.isEmpty() }) item {
            Text(
                if (query.isBlank()) "Aucune ligne. Touchez ➕ pour en ajouter."
                else "Aucun résultat.",
                Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (isNew) "Nouvelle ligne — ${tab.name}" else rowTitleV3(tab, values)) },
        text = {
            Column(Modifier.verticalScroll(scroll)) {
                tab.fields.forEach { f ->
                    val v = values[f.key] ?: ""
                    OutlinedTextField(
                        value = v,
                        onValueChange = { values[f.key] = it },
                        label = { Text(f.label) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        minLines = if (f.multiline) 2 else 1,
                        maxLines = if (f.multiline) 4 else 1
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Enregistrer") } },
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
            title = { Text("Structure", fontWeight = FontWeight.Bold) },
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
            Text("ONGLETS", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            annuaire.tabs.forEach { t ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { selected = if (selected == t.id) null else t.id }) {
                        Text(
                            (if (selected == t.id) "▾ " else "▸ ") + t.name +
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
                                        "${i + 1}. ${f.label}" + if (f.multiline) "  ¶" else "",
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
                "¶ = champ multi-lignes. Les suppressions de champs/onglets " +
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
            title = { Text("Regrouper les lignes par") },
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
        title = { Text(title) },
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
        title = { Text("Confirmer") },
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
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Définissez votre code PIN", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = pin, onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
            label = { Text("Code PIN") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = confirm, onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) confirm = it },
            label = { Text("Confirmation") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            isError = confirm.isNotBlank() && confirm != pin
        )
        if (confirm.isNotBlank() && confirm != pin)
            Text("Les codes ne correspondent pas.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { if (pin.length >= 4 && pin == confirm) onDone(pin) },
            enabled = pin.length >= 4 && pin == confirm
        ) { Text("Valider") }
    }
}

@Composable
private fun PinEnterScreen(onSuccess: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Lock, null, Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("Entrez votre code PIN", fontSize = 16.sp)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = pin, onValueChange = { pin = it },
            label = { Text("Code PIN") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { if (pin.isNotBlank()) onSuccess(pin) }) { Text("Déverrouiller") }
    }
}

/* ==================== PANNEAU RÉGLAGES ==================== */

@Composable
private fun SettingsDialogV3(
    message: String,
    titre: String,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onTemplate: () -> Unit,
    onLock: () -> Unit,
    onClear: () -> Unit,
    onChangePin: (String, String) -> Unit,
    onChangeTitre: (String) -> Unit
) {
    var showPinForm by remember { mutableStateOf(false) }
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var titreSaisi by remember { mutableStateOf(titre) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("RÉGLAGES") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = titreSaisi,
                    onValueChange = { titreSaisi = it },
                    label = { Text("Titre de l'application") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
                Button(
                    onClick = { onChangeTitre(titreSaisi) },
                    enabled = titreSaisi.isNotBlank() && titreSaisi != titre,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Enregistrer le titre") }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                Button(onClick = onImport, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(Icons.Filled.UploadFile, null); Spacer(Modifier.width(8.dp)); Text("Importer un fichier Excel")
                }
                Button(onClick = onExport, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(Icons.Filled.Download, null); Spacer(Modifier.width(8.dp)); Text("Exporter vers Excel")
                }
                Button(onClick = onTemplate, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(Icons.Filled.Description, null); Spacer(Modifier.width(8.dp)); Text("Créer le modèle d'import")
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
