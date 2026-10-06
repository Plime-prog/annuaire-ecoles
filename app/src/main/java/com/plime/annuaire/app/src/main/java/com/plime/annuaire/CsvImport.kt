package com.plime.annuaire

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

object CsvIO {

    val HEADERS = listOf(
        "Commune", "Département", "Population", "Maire", "Tél. maire",
        "Adresse mairie", "Tél. mairie", "École", "Adresse école", "Tél. école",
        "Directeur", "Tél. directeur", "Professeur", "Classe", "Tél. professeur"
    )

    /** UTF-8 si valide, sinon Windows-1252 (ancien CSV d'Excel). */
    private fun decode(bytes: ByteArray): String {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: Exception) {
            String(bytes, charset("windows-1252"))
        }
        return text.removePrefix("\uFEFF")
    }

    private fun parseRows(text: String): List<List<String>> {
        val firstLine = text.lineSequence().firstOrNull() ?: ""
        val sep = listOf(';', ',', '\t').maxByOrNull { s -> firstLine.count { it == s } } ?: ';'
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    cell.append(ch)
                }
            } else if (ch == '"') {
                inQuotes = true
            } else if (ch == sep) {
                row.add(cell.toString())
                cell.setLength(0)
            } else if (ch == '\n') {
                row.add(cell.toString())
                cell.setLength(0)
                rows.add(row)
                row = mutableListOf()
            } else if (ch != '\r') {
                cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            rows.add(row)
        }
        return rows
    }

    fun parse(bytes: ByteArray): List<Commune> {
        val data = parseRows(decode(bytes)).drop(1) // ligne 1 = en-têtes

        class TEcole(
            val nom: String, val adresse: String, val tel: String,
            val dirNom: String, val dirTel: String,
            val profs: MutableList<Professeur>
        )
        class TCommune(
            val nom: String, val dep: String, val pop: String,
            val maire: String, val telMaire: String,
            val adr: String, val tel: String,
            val ecoles: LinkedHashMap<String, TEcole>
        )

        fun c(row: List<String>, i: Int): String = row.getOrElse(i) { "" }.trim()

        val communes = LinkedHashMap<String, TCommune>()
        for (row in data) {
            if (c(row, 0).isBlank()) continue
            val cm = communes.getOrPut(c(row, 0)) {
                TCommune(c(row, 0), c(row, 1), c(row, 2), c(row, 3), c(row, 4),
                    c(row, 5), c(row, 6), LinkedHashMap())
            }
            if (c(row, 7).isBlank()) continue
            val ec = cm.ecoles.getOrPut(c(row, 7)) {
                TEcole(c(row, 7), c(row, 8), c(row, 9), c(row, 10), c(row, 11), mutableListOf())
            }
            if (c(row, 12).isNotBlank()) {
                ec.profs.add(Professeur(c(row, 12), c(row, 13), c(row, 14)))
            }
        }

        return communes.values.map { tc ->
            Commune(
                newId(), tc.nom, tc.dep, tc.pop, tc.maire, tc.telMaire, tc.adr, tc.tel,
                tc.ecoles.values.map { te ->
                    Ecole(newId(), te.nom, te.adresse, te.tel,
                        Directeur(te.dirNom, te.dirTel), te.profs)
                }
            )
        }
    }

    fun template(): String =
        "\uFEFF" + HEADERS.joinToString(";") + "\n" +
        "Exemple-Ville;Département (00);10 000 hab.;Mme Anne Maire;01 00 00 00 01;" +
        "1 rue de la Mairie;01 00 00 00 00;École Exemple;2 rue des Écoles;01 00 00 00 02;" +
        "M. Paul Directeur;06 00 00 00 01;Mme Claire Prof;CP;06 00 00 00 02\n" +
        "Exemple-Ville;;;;;;;École Exemple;;;;;M. Karim Prof;CE1;06 00 00 00 03\n"
}

@Composable
fun CsvPanel(repo: Repo) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<List<Commune>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)
                    ?.use { it.readBytes() } ?: ByteArray(0)
                val list = CsvIO.parse(bytes)
                if (list.isEmpty()) {
                    message = "Aucune commune trouvée. Vérifiez que la colonne A (Commune) est remplie et que la ligne 1 contient les en-têtes."
                } else {
                    pending = list
                }
            } catch (e: Exception) {
                message = "Import impossible : " + (e.message ?: "fichier illisible")
            }
        }
    }

    val templateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(CsvIO.template().toByteArray(Charsets.UTF_8))
                }
                message = "Modèle enregistré."
            } catch (e: Exception) {
                message = "Échec de l'enregistrement du modèle."
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionButton(
                "Importer un CSV", Icons.Filled.Download, Amber,
                { importLauncher.launch(arrayOf("*/*")) },
                Modifier.weight(1f)
            )
            ActionButton(
                "Modèle CSV", Icons.Filled.Upload, Indigo,
                { templateLauncher.launch("modele-annuaire.csv") },
                Modifier.weight(1f)
            )
        }
    }

    pending?.let { list ->
        val nbEcoles = list.sumOf { it.ecoles.size }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Importer le CSV", fontWeight = FontWeight.Bold) },
            text = {
                Text(list.size.toString() + " commune(s) et " + nbEcoles +
                    " école(s) trouvées. Que faire des données actuelles ?")
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        repo.replaceAll(repo.communes + list)
                        pending = null
                        message = "Données ajoutées."
                    }) { Text("Ajouter", fontWeight = FontWeight.Bold) }
                    TextButton(onClick = {
                        repo.replaceAll(list)
                        pending = null
                        message = "Données remplacées."
                    }) { Text("Remplacer", color = Red, fontWeight = FontWeight.Bold) }
                }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Annuler") } }
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
