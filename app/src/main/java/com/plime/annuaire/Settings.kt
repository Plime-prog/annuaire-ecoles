@file:OptIn(ExperimentalMaterial3Api::class)

package com.plime.annuaire

import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/* ---------- Chiffrement des sauvegardes (AES-256-GCM + mot de passe) ---------- */
object Backup {
    private fun key(pass: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(pass.toCharArray(), salt, 200_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
            .generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    fun encrypt(plain: String, pass: String): String {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(pass, salt), GCMParameterSpec(128, iv))
        val body = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(salt + iv + body, Base64.NO_WRAP)
    }

    fun decrypt(text: String, pass: String): String {
        val all = Base64.decode(text.trim(), Base64.DEFAULT)
        val salt = all.copyOfRange(0, 16)
        val iv = all.copyOfRange(16, 28)
        val body = all.copyOfRange(28, all.size)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(pass, salt), GCMParameterSpec(128, iv))
        return String(c.doFinal(body), Charsets.UTF_8)
    }
}

/* ---------- Fine bande bleu-blanc-rouge ---------- */
@Composable
fun TricoloreBar() {
    Row(Modifier.fillMaxWidth().height(4.dp).background(Color(0xFFE2E8F0))) {
        Box(Modifier.weight(1f).fillMaxHeight().background(Indigo))
        Box(Modifier.weight(1f).fillMaxHeight().background(Color.White))
        Box(Modifier.weight(1f).fillMaxHeight().background(Red))
    }
}

/* ---------- Panneau Sécurité et sauvegarde ---------- */
@Composable
fun SettingsPanel(store: SecureStore, repo: Repo) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<List<Commune>?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            try {
                val enc = Backup.encrypt(repo.communes.toJson(), pass)
                context.contentResolver.openOutputStream(uri)?.use { it.write(enc.toByteArray()) }
                message = "Sauvegarde exportée."
            } catch (e: Exception) {
                message = "Échec de l'export."
            }
        }
        pass = ""
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)
                    ?.use { String(it.readBytes()) } ?: ""
                pendingImport = parseCommunes(Backup.decrypt(text, pass))
            } catch (e: Exception) {
                message = "Import impossible : mot de passe incorrect ou fichier invalide."
            }
        }
        pass = ""
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text("SÉCURITÉ ET SAUVEGARDE", fontSize = 11.sp, fontWeight = FontWeight.Bold,
            color = Slate400, letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 6.dp))
        ActionButton("Changer le code PIN", Icons.Filled.Lock, Indigo,
            { dialog = "pin" }, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("Exporter", Icons.Filled.Upload, Emerald,
                { dialog = "export" }, Modifier.weight(1f))
            ActionButton("Importer", Icons.Filled.Download, Amber,
                { dialog = "import" }, Modifier.weight(1f))
        }
    }

    when (dialog) {
        "pin" -> ChangePinDialog(store) { dialog = ""; if (it != null) message = it }
        "export" -> PassDialog(
            "Mot de passe de la sauvegarde",
            "Choisissez-le avec soin : il est indispensable pour réimporter, et impossible à récupérer.",
            onOk = { p -> pass = p; dialog = ""; exportLauncher.launch("annuaire-sauvegarde.bak") },
            onCancel = { dialog = "" }
        )
        "import" -> PassDialog(
            "Mot de passe de la sauvegarde",
            "Saisissez le mot de passe choisi lors de l'export.",
            onOk = { p -> pass = p; dialog = ""; importLauncher.launch(arrayOf("*/*")) },
            onCancel = { dialog = "" }
        )
    }

    pendingImport?.let { list ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Importer la sauvegarde", fontWeight = FontWeight.Bold) },
            text = { Text("Remplacer TOUTES les données actuelles par cette sauvegarde (${list.size} commune(s)) ?") },
            confirmButton = {
                TextButton(onClick = {
                    repo.replaceAll(list)
                    pendingImport = null
                    message = "Données importées."
                }) { Text("Remplacer", color = Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Annuler") } }
        )
    }

    message?.let { m ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(m) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }
        )
    }
}

@Composable
fun PinField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) onChange(it) },
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

@Composable
fun ChangePinDialog(store: SecureStore, onClose: (String?) -> Unit) {
    var ancien by remember { mutableStateOf("") }
    var nouveau by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onClose(null) },
        title = { Text("Changer le code PIN", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                PinField("PIN actuel", ancien) { ancien = it }
                PinField("Nouveau PIN (4 chiffres)", nouveau) { nouveau = it }
                PinField("Confirmer le nouveau PIN", confirmation) { confirmation = it }
                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(error, color = Red, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    ancien != store.pin -> error = "PIN actuel incorrect."
                    nouveau.length != 4 -> error = "Le nouveau PIN doit avoir 4 chiffres."
                    nouveau != confirmation -> error = "Les deux saisies sont différentes."
                    else -> { store.pin = nouveau; onClose("PIN modifié.") }
                }
            }) { Text("Enregistrer", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = { onClose(null) }) { Text("Annuler") } }
    )
}

@Composable
fun PassDialog(title: String, hint: String, onOk: (String) -> Unit, onCancel: () -> Unit) {
    var p by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(hint, fontSize = 13.sp, color = Slate400)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = p, onValueChange = { p = it },
                    label = { Text("Mot de passe (6 caractères minimum)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (p.length >= 6) onOk(p) }) {
                Text("Continuer", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Annuler") } }
    )
}
