package com.plime.annuaire.v3

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Import/Export Excel V3 — TOTALEMENT DYNAMIQUE.
 * L'import lit les noms d'onglets et les en-têtes du fichier :
 *  - un onglet inconnu devient un nouvel onglet ;
 *  - une colonne inconnue devient un nouveau champ ;
 *  - le mapping se fait PAR NOM D'EN-TÊTE (plus par position).
 * L'export écrit exactement la structure en cours (onglets + champs de l'app).
 */
object ExcelIOV3 {

    /* ---------- Utilitaires XML (identiques V2, éprouvés) ---------- */

    private fun parser(bytes: ByteArray): XmlPullParser {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        p.setInput(ByteArrayInputStream(bytes), "UTF-8")
        return p
    }

    private fun esc(s: String) =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun colIndex(ref: String?): Int {
        if (ref == null) return -1
        var n = 0
        for (ch in ref) {
            if (ch !in 'A'..'Z') break
            n = n * 26 + (ch - 'A' + 1)
        }
        return n - 1
    }

    private fun colLetter(i: Int): String {
        var s = ""
        var n = i + 1
        while (n > 0) {
            s = ('A' + (n - 1) % 26) + s
            n = (n - 1) / 26
        }
        return s
    }

    /* ---------- Lecture du ZIP ---------- */

    private fun readZip(ctx: Context, uri: Uri): Map<String, ByteArray> {
        val map = mutableMapOf<String, ByteArray>()
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zin ->
                var entry = zin.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val buf = ByteArrayOutputStream()
                        val b = ByteArray(8192)
                        var n = zin.read(b)
                        while (n > 0) { buf.write(b, 0, n); n = zin.read(b) }
                        map[entry.name] = buf.toByteArray()
                    }
                    entry = zin.nextEntry
                }
            }
        }
        if (map.isEmpty()) throw IllegalArgumentException("Fichier vide ou illisible")
        return map
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val p = parser(bytes)
        val list = mutableListOf<String>()
        val text = StringBuilder()
        var inSi = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> if (p.name == "si") { inSi = true; text.setLength(0) }
                XmlPullParser.TEXT -> if (inSi) text.append(p.text)
                XmlPullParser.END_TAG -> if (p.name == "si") { inSi = false; list.add(text.toString()) }
            }
            ev = p.next()
        }
        return list
    }

    private fun sheetMap(files: Map<String, ByteArray>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val wb = files["xl/workbook.xml"] ?: return result
        val p = parser(wb)
        val relsFile = files["xl/_rels/workbook.xml.rels"]
        val rels: Map<String, String> = if (relsFile != null) {
            val pr = parser(relsFile)
            val m = mutableMapOf<String, String>()
            var e2 = pr.eventType
            while (e2 != XmlPullParser.END_DOCUMENT) {
                if (e2 == XmlPullParser.START_TAG && pr.name == "Relationship") {
                    val id = pr.getAttributeValue(null, "Id")
                    val target = pr.getAttributeValue(null, "Target")
                    if (id != null && target != null) {
                        m[id] = when {
                            target.startsWith("/") -> target.drop(1)
                            target.startsWith("xl/") -> target
                            else -> "xl/$target"
                        }
                    }
                }
                e2 = pr.next()
            }
            m
        } else emptyMap()

        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && p.name == "sheet") {
                val name = p.getAttributeValue(null, "name") ?: continue
                val rid = p.getAttributeValue(
                    "http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                if (rid != null) rels[rid]?.let { result[name] = it }
            }
            ev = p.next()
        }
        return result
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val p = parser(bytes)
        val rows = mutableListOf<List<String>>()
        val cells = mutableListOf<Pair<Int, String>>()
        val value = StringBuilder()
        var inRow = false
        var inCell = false
        var inValue = false
        var col = -1
        var type = ""
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> { inRow = true; cells.clear() }
                    "c" -> if (inRow) {
                        inCell = true; value.setLength(0)
                        col = colIndex(p.getAttributeValue(null, "r"))
                        type = p.getAttributeValue(null, "t") ?: ""
                    }
                    "v", "is" -> inValue = true
                }
                XmlPullParser.TEXT -> if (inCell && inValue) value.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v", "is" -> inValue = false
                    "c" -> if (inCell) {
                        inCell = false
                        var text = value.toString()
                        if (type == "s") {
                            val idx = text.trim().toIntOrNull() ?: -1
                            text = if (idx in shared.indices) shared[idx] else ""
                        }
                        cells.add(Pair(if (col >= 0) col else cells.size, text))
                    }
                    "row" -> if (inRow) {
                        inRow = false
                        val width = (cells.maxOfOrNull { it.first } ?: -1) + 1
                        val arr = Array(width.coerceAtLeast(0)) { "" }
                        for ((i, v) in cells) if (i < 60) arr[i] = v.trim()
                        rows.add(arr.toList())
                    }
                }
            }
            ev = p.next()
        }
        return rows
    }

    /* ---------- IMPORT DYNAMIQUE ---------- */

    fun import(ctx: Context, uri: Uri): AnnuaireV3 {
        val files = readZip(ctx, uri)
        val shared = files["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        val sheets = sheetMap(files)
        if (sheets.isEmpty()) throw IllegalArgumentException("aucune feuille trouvée")

        val a = AnnuaireV3()
        for ((sheetName, path) in sheets) {
            val bytes = files[path] ?: continue
            val all = parseSheet(bytes, shared)
            if (all.isEmpty()) continue
            val headers = all.first().map { it.trim() }
            val tab = TabV3(newKeyV3(), sheetName)
            // Colonne -> clé de champ (mapping par nom d'en-tête)
            val colKeys = mutableListOf<Pair<Int, String>>()
            headers.forEachIndexed { i, h ->
                if (h.isBlank()) return@forEachIndexed
                val existing = tab.fields.firstOrNull {
                    it.label.equals(h, ignoreCase = true)
                }
                val f = existing ?: FieldV3(newKeyV3(), h).also { tab.fields.add(it) }
                colKeys.add(Pair(i, f.key))
            }
            if (tab.fields.isEmpty()) continue
            for (row in all.drop(1)) {
                if (row.all { it.isBlank() }) continue
                val m = mutableMapOf("_id" to newKeyV3())
                for ((i, key) in colKeys) m[key] = row.getOrElse(i) { "" }
                tab.rows.add(m)
            }
            // Par défaut : regroupement sur le 1er champ pour l'onglet Communes
            if (tab.name.equals("Communes", ignoreCase = true) && tab.fields.isNotEmpty())
                tab.groupBy = tab.fields[0].key
            a.tabs.add(tab)
        }
        if (a.tabs.isEmpty()) throw IllegalArgumentException("classeur vide")
        return a
    }

    /* ---------- ÉCRITURE (structure en cours) ---------- */

    private const val CONTENT_TYPES_HEAD =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
    private const val CONTENT_TYPES_TAIL = "</Types>"
    private const val RELS_ROOT =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>"

    private fun workbookXml(names: List<String>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
        append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
        append("<sheets>")
        names.forEachIndexed { i, n ->
            append("<sheet name=\"").append(esc(n)).append("\" sheetId=\"").append(i + 1)
            .append("\" r:id=\"rId").append(i + 1).append("\"/>")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRelsXml(names: List<String>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        names.forEachIndexed { i, _ ->
            append("<Relationship Id=\"rId").append(i + 1)
            .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" ")
            .append("Target=\"worksheets/sheet").append(i + 1).append(".xml\"/>")
        }
        append("</Relationships>")
    }

    private fun contentTypesXml(names: List<String>): String = buildString {
        append(CONTENT_TYPES_HEAD)
        names.forEachIndexed { i, _ ->
            append("<Override PartName=\"/xl/worksheets/sheet").append(i + 1)
            .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        append(CONTENT_TYPES_TAIL)
    }

    private fun sheetXml(rows: List<List<String>>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
        for ((r, row) in rows.withIndex()) {
            append("<row r=\"${r + 1}\">")
            for ((i, v) in row.withIndex()) {
                if (v.isEmpty()) continue
                append("<c r=\"${colLetter(i)}${r + 1}\" t=\"inlineStr\"><is><t>")
                append(esc(v))
                append("</t></is></c>")
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun writeZip(ctx: Context, uri: Uri, sheets: List<Pair<String, List<List<String>>>>) {
        val names = sheets.map { it.first }
        ctx.contentResolver.openOutputStream(uri)?.use { out ->
            ZipOutputStream(out).use { z ->
                fun put(name: String, content: String) {
                    z.putNextEntry(ZipEntry(name))
                    z.write(content.toByteArray(Charsets.UTF_8))
                    z.closeEntry()
                }
                put("[Content_Types].xml", contentTypesXml(names))
                put("_rels/.rels", RELS_ROOT)
                put("xl/workbook.xml", workbookXml(names))
                put("xl/_rels/workbook.xml.rels", workbookRelsXml(names))
                sheets.forEachIndexed { i, (_, rows) ->
                    put("xl/worksheets/sheet${i + 1}.xml", sheetXml(rows))
                }
            }
        }
    }

    /** Exporte l'annuaire complet : un onglet par tab, en-têtes = libellés des champs. */
    fun export(ctx: Context, uri: Uri, a: AnnuaireV3, withRows: Boolean = true) {
        val sheets = a.tabs.map { tab ->
            val rows = mutableListOf(tab.fields.map { it.label })
            if (withRows) for (row in tab.rows) {
                rows.add(tab.fields.map { row[it.key] ?: "" })
            }
            tab.name to rows
        }
        if (sheets.isEmpty()) throw IllegalArgumentException("aucun onglet à exporter")
        writeZip(ctx, uri, sheets)
    }

    /** Modèle vierge : structure en cours, sans les données. */
    fun writeTemplate(ctx: Context, uri: Uri, a: AnnuaireV3) = export(ctx, uri, a, withRows = false)
}
