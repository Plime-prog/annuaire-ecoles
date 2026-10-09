package com.plime.annuaire.v2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

class MainActivityV2 : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AnnuaireV2App() }
    }
}

private enum class TabV2(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    COMMUNES("Communes", Icons.Filled.LocationCity),
    RASED("RASED", Icons.Filled.Groups),
    CIRCO("Circo.", Icons.Filled.SupervisorAccount),
    PIAL("PIAL & ER", Icons.Filled.Devices),
    COLLEGES("Collèges", Icons.Filled.School)
}

/* Type de fiche en cours d'édition (pour la boîte de dialogue) */
private sealed class EditTarget {
    abstract val isNew: Boolean

    data class Commune(val c: CommuneV2, override val isNew: Boolean) : EditTarget()
    data class Ecole(val c: CommuneV2, val e: EcoleV2, override val isNew: Boolean) : EditTarget()
    data class Prof(val e: EcoleV2, val p: ProfesseurV2, override val isNew: Boolean) : EditTarget()
    data class Contact(val kind: Int, val entry: Any, override val isNew: Boolean) : EditTarget() // kind: 0=RASED 1=Circo 2=PIAL
    data class College(val col: CollegeV2, override val isNew: Boolean) : EditTarget()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnuaireV2App() {
    val ctx = LocalContext.current
    var annuaire by remember { mutableStateOf(AnnuaireV2()) }
    var unlocked by remember { mutableStateOf(false) }
    var hasPin by remember { mutableStateOf(false) }
    var titre by remember { mutableStateOf("Circonscription") }
    var tab by remember { mutableStateOf(TabV2.COMMUNES) }
    var query by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var detailCommune by remember { mutableStateOf<CommuneV2?>(null) }
    var detailEcole by remember { mutableStateOf<Pair<CommuneV2, EcoleV2>?>(null) }
    var message by remember { mutableStateOf("") }
    var editTarget by remember { mutableStateOf<EditTarget?>(null) }

    /* Le Jetpack ne détecte pas les mutations internes (var des objets) :
       on force le rafraîchissement avec un compteur à chaque sauvegarde. */
    var revision by remember { mutableStateOf(0) }
    val refresh: () -> Unit = { revision++ }

    fun saveAll() {
        StorageV2.save(ctx, annuaire)
        refresh()
    }

    LaunchedEffect(Unit) {
        annuaire = StorageV2.load(ctx)
        hasPin = StorageV2.hasPin(ctx)
        titre = StorageV2.getTitre(ctx)
    }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) try {
            annuaire = ExcelIOV2.import(ctx, uri)
            StorageV2.save(ctx, annuaire)
            refresh()
            message = "Import réussi : ${annuaire.communes.size} commune(s), " +
                "${annuaire.communes.sumOf { it.ecoles.size }} école(s), " +
                "${annuaire.rased.size + annuaire.circo.size + annuaire.pial.size} contact(s), " +
                "${annuaire.colleges.size} collège(s)."
        } catch (e: Exception) {
            message = "Échec de l'import : ${e.message ?: "fichier invalide"}"
        }
    }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) try {
            ExcelIOV2.export(ctx, uri, annuaire)
            message = "Export réussi."
        } catch (e: Exception) {
            message = "Échec de l'export : ${e.message ?: "erreur"}"
        }
    }

    val modeleur = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) try {
            ExcelIOV2.writeTemplate(ctx, uri)
            message = "Modèle créé."
        } catch (e: Exception) {
            message = "Échec : ${e.message ?: "erreur"}"
        }
    }

    MaterialTheme {
        if (!hasPin) {
            PinDefineScreen { pin ->
                StorageV2.setPin(ctx, pin)
                StorageV2.setUnlocked(ctx, true)
                unlocked = true
                hasPin = true
            }
        } else if (!unlocked) {
            PinEnterScreen(
                onError = { message = "Code incorrect." }
            ) { pin ->
                if (StorageV2.checkPin(ctx, pin)) {
                    StorageV2.setUnlocked(ctx, true)
                    unlocked = true
                }
            }
        } else {
            /* Affichage de la boîte de dialogue d'édition si ouverte */
            val target = editTarget
            if (target != null) EditDialogFor(
                target = target,
                onSave = { m ->
                    when (target) {
                        is EditTarget.Commune -> {
                            val c = target.c
                            val ancienNom = c.nom
                            applyCommune(c, m)
                            if (target.isNew) annuaire.communes.add(c)
                            if (c.nom != ancienNom) detailCommune = c
                            editTarget = null
                            saveAll()
                        }
                        is EditTarget.Ecole -> {
                            applyEcole(target.e, m)
                            if (target.isNew) target.c.ecoles.add(target.e)
                            editTarget = null
                            saveAll()
                        }
                        is EditTarget.Prof -> {
                            applyProf(target.p, m)
                            if (target.isNew) target.e.professeurs.add(target.p)
                            editTarget = null
                            saveAll()
                        }
                        is EditTarget.Contact -> {
                            when (target.entry) {
                                is RasedEntry -> applyContact(target.entry, m)
                                is CircoEntry -> applyContact(target.entry, m)
                                is PialEntry -> applyContact(target.entry, m)
                            }
                            if (target.isNew) {
                                when (target.kind) {
                                    0 -> annuaire.rased.add(target.entry as RasedEntry)
                                    1 -> annuaire.circo.add(target.entry as CircoEntry)
                                    2 -> annuaire.pial.add(target.entry as PialEntry)
                                }
                            }
                            editTarget = null
                            saveAll()
                        }
                        is EditTarget.College -> {
                            applyCollege(target.col, m)
                            if (target.isNew) annuaire.colleges.add(target.col)
                            editTarget = null
                            saveAll()
                        }
                    }
                    message = "Modifications enregistrées."
                },
                onCancel = { editTarget = null },
                onDelete = if (target.isNew) null else {{
                    when (target) {
                        is EditTarget.Commune -> {
                            annuaire.communes.remove(target.c)
                            if (detailCommune == target.c) detailCommune = null
                            message = "Commune supprimée."
                        }
                        is EditTarget.Ecole -> {
                            target.c.ecoles.remove(target.e)
                            if (detailEcole?.second == target.e) detailEcole = null
                            message = "École supprimée."
                        }
                        is EditTarget.Prof -> {
                            target.e.professeurs.remove(target.p)
                            message = "Professeur supprimé."
                        }
                        is EditTarget.Contact -> {
                            when (target.entry) {
                                is RasedEntry -> annuaire.rased.remove(target.entry)
                                is CircoEntry -> annuaire.circo.remove(target.entry)
                                is PialEntry -> annuaire.pial.remove(target.entry)
                            }
                            message = "Contact supprimé."
                        }
                        is EditTarget.College -> {
                            annuaire.colleges.remove(target.col)
                            message = "Collège supprimé."
                        }
                    }
                    editTarget = null
                    saveAll()
                }}
            )

            Scaffold(
                topBar = {
                    Column {
                        TopAppBar(
                            title = { Text(titre, fontWeight = FontWeight.Bold) },
                            actions = {
                                IconButton(onClick = { showSettings = true }) {
                                    Icon(Icons.Filled.Settings, "Réglages")
                                }
                            }
                        )
                        BandeauFrance()
                    }
                },
                bottomBar = {
                    NavigationBar {
                        TabV2.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = {
                                    tab = t; query = ""
                                    detailCommune = null; detailEcole = null
                                },
                                icon = { Icon(t.icon, null) },
                                label = { Text(t.label, fontSize = 11.sp) }
                            )
                        }
                    }
                },
                floatingActionButton = {
                    FloatingActionButton(onClick = {
                        when (tab) {
                            TabV2.COMMUNES ->
                                editTarget = EditTarget.Commune(CommuneV2(newIdV2(), ""), true)
                            TabV2.RASED -> editTarget = EditTarget.Contact(0, RasedEntry(newIdV2()), true)
                            TabV2.CIRCO -> editTarget = EditTarget.Contact(1, CircoEntry(newIdV2()), true)
                            TabV2.PIAL -> editTarget = EditTarget.Contact(2, PialEntry(newIdV2()), true)
                            TabV2.COLLEGES -> editTarget = EditTarget.College(CollegeV2(newIdV2()), true)
                        }
                    }) { Icon(Icons.Filled.Add, "Ajouter") }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        placeholder = { Text("Rechercher…") },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        singleLine = true
                    )
                    key(revision) {
                        val cm = detailCommune
                        val ec = detailEcole
                        when {
                            ec != null -> EcoleDetailScreen(
                                c = ec.first, e = ec.second,
                                onBack = { detailEcole = null },
                                onEditEcole = { e -> editTarget = EditTarget.Ecole(ec.first, e, false) },
                                onAddProf = {
                                    editTarget = EditTarget.Prof(ec.second, ProfesseurV2(newIdV2(), ""), true)
                                },
                                onEditProf = { p -> editTarget = EditTarget.Prof(ec.second, p, false) },
                                onDeleteProf = { p ->
                                    ec.second.professeurs.remove(p)
                                    message = "Professeur supprimé."
                                    saveAll()
                                }
                            )
                            cm != null -> CommuneDetailScreen(
                                c = cm,
                                onBack = { detailCommune = null },
                                onOpenEcole = { c, e -> detailEcole = c to e },
                                onEditCommune = { c -> editTarget = EditTarget.Commune(c, false) },
                                onAddEcole = {
                                    editTarget = EditTarget.Ecole(cm, EcoleV2(newIdV2(), ""), true)
                                },
                                onEditEcole = { c, e -> editTarget = EditTarget.Ecole(c, e, false) },
                                onDeleteEcole = { c, e ->
                                    c.ecoles.remove(e)
                                    message = "École supprimée."
                                    saveAll()
                                }
                            )
                            tab == TabV2.COMMUNES -> CommunesScreen(
                                a = annuaire, query = query,
                                onOpenCommune = { c -> detailCommune = c },
                                onEditCommune = { c -> editTarget = EditTarget.Commune(c, false) }
                            )
                            tab == TabV2.RASED -> RasedScreen(
                                a = annuaire, query = query,
                                onEdit = { entry -> editTarget = EditTarget.Contact(0, entry, false) }
                            )
                            tab == TabV2.CIRCO -> CircoScreen(
                                a = annuaire, query = query,
                                onEdit = { entry -> editTarget = EditTarget.Contact(1, entry, false) }
                            )
                            tab == TabV2.PIAL -> PialScreen(
                                a = annuaire, query = query,
                                onEdit = { entry -> editTarget = EditTarget.Contact(2, entry, false) }
                            )
                            tab == TabV2.COLLEGES -> CollegesScreen(
                                a = annuaire, query = query,
                                onEditCollege = { col -> editTarget = EditTarget.College(col, false) }
                            )
                        }
                    }
                }
            }
            if (showSettings) SettingsDialogV2(
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
                onExport = { showSettings = false; exporter.launch("annuaire-v2.xlsx") },
                onTemplate = { showSettings = false; modeleur.launch("modele-annuaire-v2.xlsx") },
                onLock = {
                    StorageV2.setUnlocked(ctx, false)
                    showSettings = false; unlocked = false
                },
                onClear = {
                    StorageV2.clearData(ctx)
                    annuaire = AnnuaireV2()
                    refresh()
                    message = "Toutes les données ont été effacées."
                },
                onChangePin = { old, new ->
                    if (StorageV2.checkPin(ctx, old)) {
                        StorageV2.setPin(ctx, new)
                        message = "Code PIN modifié."
                    } else message = "Ancien code incorrect."
                },
                onChangeTitre = { nouveau ->
                    titre = nouveau
                    StorageV2.setTitre(ctx, nouveau)
                    message = "Titre modifié."
                }
            )
        }
    }
}

/* ==================== BOÎTE DE DIALOGUE D'ÉDITION (aiguillage) ==================== */

@Composable
private fun EditDialogFor(
    target: EditTarget,
    onSave: (Map<String, String>) -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    when (target) {
        is EditTarget.Commune ->
            EntityDialog("Commune", communeFields(target.c), onSave, onCancel, onDelete)
        is EditTarget.Ecole ->
            EntityDialog("École", ecoleFields(target.e), onSave, onCancel, onDelete)
        is EditTarget.Prof ->
            EntityDialog("Professeur", profFields(target.p), onSave, onCancel, onDelete)
        is EditTarget.Contact -> {
            val f = when (val entry = target.entry) {
                is RasedEntry -> contactFields(entry.contact, entry.fonction, entry.adresse, entry.mail, entry.tel1, entry.tel2, entry.observations)
                is CircoEntry -> contactFields(entry.contact, entry.fonction, entry.adresse, entry.mail, entry.tel1, entry.tel2, entry.observations)
                is PialEntry -> contactFields(entry.contact, entry.fonction, entry.adresse, entry.mail, entry.tel1, entry.tel2, entry.observations)
                else -> linkedMapOf()
            }
            EntityDialog("Contact", f, onSave, onCancel, onDelete)
        }
        is EditTarget.College ->
            EntityDialog("Collège", collegeFields(target.col), onSave, onCancel, onDelete)
    }
}

/* ==================== ÉCRAN PIN : définition ==================== */

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

/* ==================== ÉCRAN PIN : saisie ==================== */

@Composable
private fun PinEnterScreen(onError: () -> Unit, onSuccess: (String) -> Unit) {
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
private fun SettingsDialogV2(
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
                /* --- Titre de l'application --- */
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

                /* --- Import / Export / Modèle --- */
                Button(onClick = onImport, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(Icons.Filled.UploadFile, null); Spacer(Modifier.width(8.dp)); Text("Importer un fichier Excel")
                }
                Button(onClick = onExport, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(Icons.Filled.Download, null); Spacer(Modifier.width(8.dp)); Text("Exporter vers Excel")
                }
                Button(onClick = onTemplate, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(Icons.Filled.Description, null); Spacer(Modifier.width(8.dp)); Text("Créer le modèle d'import")
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                /* --- Code PIN --- */
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
