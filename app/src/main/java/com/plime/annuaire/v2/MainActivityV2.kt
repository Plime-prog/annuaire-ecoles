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

class MainActivityV2 : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AnnuaireV2App() }
    }
}

private enum class TabV2(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    COMMUNES("Communes", Icons.Filled.LocationCity),
    RASED("RASED", Icons.Filled.Groups),
    CIRCO("Circonscription", Icons.Filled.SupervisorAccount),
    PIAL("PIAL & ER", Icons.Filled.Devices),
    COLLEGES("Collèges", Icons.Filled.School)
}

@Composable
fun AnnuaireV2App() {
    val ctx = LocalContext.current
    var annuaire by remember { mutableStateOf(AnnuaireV2()) }
    var unlocked by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(TabV2.COMMUNES) }
    var query by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var detailCommune by remember { mutableStateOf<CommuneV2?>(null) }
    var detailEcole by remember { mutableStateOf<Pair<CommuneV2, EcoleV2>?>(null) }
    var message by remember { mutableStateOf("") }
    var pinSetup by remember { mutableStateOf(false) }

    // Chargement initial
    LaunchedEffect(Unit) { annuaire = StorageV2.load(ctx) }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) try {
            annuaire = ExcelIOV2.import(ctx, uri)
            StorageV2.save(ctx, annuaire)
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
        if (!StorageV2.hasPin(ctx)) {
            // Premier lancement : définir un PIN
            PinDefineScreen { pin ->
                StorageV2.setPin(ctx, pin)
                StorageV2.setUnlocked(ctx, true)
                unlocked = true
            }
        } else if (!StorageV2.isUnlocked(ctx) && !unlocked) {
            PinEnterScreen(
                onError = { message = "Code incorrect." }
            ) { pin ->
                if (StorageV2.checkPin(ctx, pin)) {
                    StorageV2.setUnlocked(ctx, true)
                    unlocked = true
                }
            }
        } else {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Annuaire V2", fontWeight = FontWeight.Bold) },
                        actions = {
                            IconButton(onClick = { showSettings = true }) {
                                Icon(Icons.Filled.Settings, "Réglages")
                            }
                        }
                    )
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
                    val cm = detailCommune
                    val ec = detailEcole
                    when {
                        ec != null -> EcoleDetailScreen(ec.first, ec.second) {
                            detailEcole = null
                        }
                        cm != null -> CommuneDetailScreen(cm, { detailCommune = null }) { c, e ->
                            detailEcole = c to e
                        }
                        tab == TabV2.COMMUNES -> CommunesScreen(annuaire, query) { c ->
                            detailCommune = c
                        }
                        tab == TabV2.RASED -> RasedScreen(annuaire, query)
                        tab == TabV2.CIRCO -> CircoScreen(annuaire, query)
                        tab == TabV2.PIAL -> PialScreen(annuaire, query)
                        tab == TabV2.COLLEGES -> CollegesScreen(annuaire, query)
                    }
                }
            }
            if (showSettings) SettingsDialogV2(
                message = message,
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
                    message = "Toutes les données ont été effacées."
                },
                onChangePin = { old, new ->
                    if (StorageV2.checkPin(ctx, old)) {
                        StorageV2.setPin(ctx, new)
                        message = "Code PIN modifié."
                    } else message = "Ancien code incorrect."
                }
            )
        }
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
        Button(onClick = {
            if (pin.isNotBlank()) onSuccess(pin).also { }
        }) { Text("Déverrouiller") }
    }
}

/* ==================== PANNEAU RÉGLAGES ==================== */

@Composable
private fun SettingsDialogV2(
    message: String,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onTemplate: () -> Unit,
    onLock: () -> Unit,
    onClear: () -> Unit,
    onChangePin: (String, String) -> Unit
) {
    var showPinForm by remember { mutableStateOf(false) }
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("RÉGLAGES") },
        text = Column(Modifier.verticalScroll(rememberScrollState())) {
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Icon(Icons.Filled.UploadFile, null); Spacer(Modifier.width(8.dp)); Text("Importer un fichier Excel")
            }
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Icon(Icons.Filled.Download, null); Spacer(Modifier.width(8.dp)); Text("Exporter vers Excel")
            }
            Button(onClick = onTemplate, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Icon(Icons.Filled.Description, null); Spacer(Modifier.width(8.dp)); Text("Créer le modèle d'import")
            }
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
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
}
