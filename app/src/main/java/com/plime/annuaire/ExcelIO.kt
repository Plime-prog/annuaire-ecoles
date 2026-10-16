package com.plime.annuaire

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
 * Import/Export Excel - TOTALEMENT DYNAMIQUE.
 * L'import lit les noms d'onglets et les en-tetes du fichier :
 *  - un onglet inconnu devient un nouvel onglet ;
 *  - une colonne inconnue devient un nouveau champ ;
 *  - le mapping se fait PAR NOM D'EN-TETE (pas par position).
 * L'export ecrit exactement la structure en cours (onglets + champs de l'app).
 */
object ExcelIO {

    /* ---------- Utilitaires XML ---------- */

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

    fun import(ctx: Context, uri: Uri): Annuaire {
        val files = readZip(ctx, uri)
        val shared = files["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        val sheets = sheetMap(files)
        if (sheets.isEmpty()) throw IllegalArgumentException("aucune feuille trouv\u00E9e")

        val a = Annuaire()
        for ((sheetName, path) in sheets) {
            val bytes = files[path] ?: continue
            val all = parseSheet(bytes, shared)
            if (all.isEmpty()) continue
            val headers = all.first().map { it.trim() }
            val tab = Onglet(nouvelleCle(), sheetName)
            // Colonne -> cle de champ (mapping par nom d'en-tete)
            val colKeys = mutableListOf<Pair<Int, String>>()
            headers.forEachIndexed { i, h ->
                if (h.isBlank()) return@forEachIndexed
                val existing = tab.fields.firstOrNull {
                    it.label.equals(h, ignoreCase = true)
                }
                val f = existing ?: Champ(nouvelleCle(), h).also { tab.fields.add(it) }
                colKeys.add(Pair(i, f.key))
            }
            if (tab.fields.isEmpty()) continue
            for (row in all.drop(1)) {
                if (row.all { it.isBlank() }) continue
                val m = mutableMapOf("_id" to nouvelleCle())
                for ((i, key) in colKeys) m[key] = row.getOrElse(i) { "" }
                tab.rows.add(m)
            }
            // Regroupement automatique selon le NOM du champ (pas la position) :
            //  - Communes  -> le champ " Commune "
            //  - Colleges  -> le champ " Nom " (nom du college)
            //  - Circonscription -> aucun : liste a plat des personnes
            val nomOnglet = norme(tab.name)
            tab.groupBy = when {
                nomOnglet.contains("commune") ->
                    tab.fields.firstOrNull { norme(it.label).contains("commune") }?.key ?: ""
                nomOnglet.contains("college") || nomOnglet.contains("coll\u00E8ge") ->
                    tab.fields.firstOrNull {
                        norme(it.label) == "nom" || norme(it.label).contains("college")
                    }?.key ?: ""
                else -> ""
            }
            a.tabs.add(tab)
        }

        /* PIAL-ER et RASED sont fusionnes dans l'onglet Circonscription :
           leurs personnes recoivent " Circonscription elargie " = Oui. */
        val aFusionner = a.tabs.filter {
            val n = norme(it.name)
            n.contains("pial") || n.contains("rased") || n.contains("er/")
        }
        if (aFusionner.isNotEmpty()) {
            val cible = a.tabs.firstOrNull {
                norme(it.name).contains("circonscription")
            } ?: Onglet(nouvelleCle(), "Circonscription").also { a.tabs.add(it) }
            val fEl = cible.fields.firstOrNull {
                norme(it.label).contains("elargie")
            } ?: Champ(nouvelleCle(), "Circonscription \u00E9largie").also { cible.fields.add(it) }
            aFusionner.forEach { source ->
                source.rows.forEach { r ->
                    val m = mutableMapOf("_id" to nouvelleCle())
                    source.fields.forEach { sf ->
                        val v = r[sf.key] ?: ""
                        if (v.isNotBlank()) {
                            val cf = cible.fields.firstOrNull {
                                it.label.equals(sf.label, ignoreCase = true)
                            } ?: Champ(nouvelleCle(), sf.label).also { cible.fields.add(it) }
                            m[cf.key] = v
                        }
                    }
                    m[fEl.key] = "Oui"
                    cible.rows.add(m)
                }
            }
            a.tabs.removeAll(aFusionner)
        }

        if (a.tabs.isEmpty()) throw IllegalArgumentException("classeur vide")
        return a
    }

    /* ---------- ECRITURE (structure en cours) ---------- */

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

    /** Exporte l'annuaire complet : un onglet par table, en-tetes = libelles des champs. */
    fun export(ctx: Context, uri: Uri, a: Annuaire, withRows: Boolean = true) {
        val sheets = a.tabs.map { tab ->
            val rows = mutableListOf(tab.fields.map { it.label })
            if (withRows) for (row in tab.rows) {
                rows.add(tab.fields.map { row[it.key] ?: "" })
            }
            tab.name to rows
        }
        if (sheets.isEmpty()) throw IllegalArgumentException("aucun onglet \u00E0 exporter")
        writeZip(ctx, uri, sheets)
    }

    /** Modele vierge : structure en cours, sans les donnees. */
    fun writeTemplate(ctx: Context, uri: Uri, a: Annuaire) = export(ctx, uri, a, withRows = false)
}
