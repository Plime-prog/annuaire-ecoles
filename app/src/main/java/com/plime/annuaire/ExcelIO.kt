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

/** Lecture/écriture de fichiers Excel (.xlsx) sans bibliothèque externe.
 *  Compatible avec les fichiers créés par Excel (sharedStrings) et par cette app. */
object ExcelIO {

    val HEADERS = listOf(
        "Commune", "Département", "Population", "Maire", "Tél. maire",
        "Adresse mairie", "Tél. mairie", "École", "Adresse école", "Tél. école",
        "Directeur", "Tél. directeur", "Professeur", "Classe", "Tél. professeur"
    )

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

    /* ---------- Lecture du classeur ---------- */
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
                XmlPullParser.START_TAG ->
                    if (p.name == "si") { inSi = true; text.setLength(0) }
                XmlPullParser.TEXT -> if (inSi) text.append(p.text)
                XmlPullParser.END_TAG ->
                    if (p.name == "si") { inSi = false; list.add(text.toString()) }
            }
            ev = p.next()
        }
        return list
    }

    private fun firstSheetPath(files: Map<String, ByteArray>): String {
        val fallback = "xl/worksheets/sheet1.xml"
        val wb = files["xl/workbook.xml"] ?: return fallback
        val p = parser(wb)
        var rid: String? = null
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT && rid == null) {
            if (ev == XmlPullParser.START_TAG && p.name == "sheet") {
                rid = p.getAttributeValue(
                    "http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
            }
            ev = p.next()
        }
        if (rid == null) return fallback
        val rels = files["xl/_rels/workbook.xml.rels"] ?: return fallback
        val pr = parser(rels)
        var ev2 = pr.eventType
        while (ev2 != XmlPullParser.END_DOCUMENT) {
            if (ev2 == XmlPullParser.START_TAG && pr.name == "Relationship" &&
                pr.getAttributeValue(null, "Id") == rid) {
                val target = pr.getAttributeValue(null, "Target") ?: return fallback
                return when {
                    target.startsWith("/") -> target.drop(1)
                    target.startsWith("xl/") -> target
                    else -> "xl/$target"
                }
            }
            ev2 = pr.next()
        }
        return fallback
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
                        cells.add(if (col >= 0) col else cells.size to text)
                    }
                    "row" -> if (inRow) {
                        inRow = false
                        val width = (cells.maxOfOrNull { it.first } ?: -1) + 1
                        val arr = Array(width.coerceAtLeast(0)) { "" }
                        for ((i, v) in cells) if (i < 15) arr[i] = v.trim()
                        rows.add(arr.toList())
                    }
                }
            }
            ev = p.next()
        }
        return rows
    }

    /* ---------- Import ---------- */
    fun import(ctx: Context, uri: Uri): List<Commune> {
        val files = readZip(ctx, uri)
        val shared = files["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        val sheet = files[firstSheetPath(files)] ?: throw IllegalArgumentException("Feuille introuvable")
        val data = parseSheet(sheet, shared).drop(1) // ligne 1 = en-têtes

        class TEcole(val nom: String, val adresse: String, val tel: String,
                     val dirNom: String, val dirTel: String,
                     val profs: MutableList<Professeur>)
        class TCommune(val nom: String, val dep: String, val pop: String,
                       val maire: String, val telMaire: String,
                       val adr: String, val tel: String,
                       val ecoles: LinkedHashMap<String, TEcole>)

        fun c(row: List<String>, i: Int) = row.getOrElse(i) { "" }
        val communes = LinkedHashMap<String, TCommune>()

        for (row in data) {
            if (c(row, 0).isBlank() && c(row, 7).isBlank() && c(row, 12).isBlank()) continue
            val cm = communes.getOrPut(c(row, 0)) {
                TCommune(c(row, 0), c(row, 1), c(row, 2), c(row, 3), c(row, 4),
                    c(row, 5), c(row, 6), LinkedHashMap())
            }
            if (c(row, 7).isBlank()) continue
            val ec = cm.ecoles.getOrPut(c(row, 7)) {
                TEcole(c(row, 7), c(row, 8), c(row, 9), c(row, 10), c(row, 11), mutableListOf())
            }
            if (c(row, 12).isNotBlank()) ec.profs.add(Professeur(c(row, 12), c(row, 13), c(row, 14)))
        }

        return communes.values.map { tc ->
            Commune(newId(), tc.nom, tc.dep, tc.pop, tc.maire, tc.telMaire,
                tc.adr, tc.tel,
                tc.ecoles.values.map { te ->
                    Ecole(newId(), te.nom, te.adresse, te.tel,
                        Directeur(te.dirNom, te.dirTel), te.profs)
                })
        }
    }

    /* ---------- Écriture ---------- */
    private const val CONTENT_TYPES =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
        "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>"
    private const val RELS_ROOT =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>"
    private const val WORKBOOK =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
        "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
        "<sheets><sheet name=\"Annuaire\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"
    private const val WORKBOOK_RELS =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>"

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

    private fun writeZip(ctx: Context, uri: Uri, rows: List<List<String>>) {
        ctx.contentResolver.openOutputStream(uri)?.use { out ->
            ZipOutputStream(out).use { z ->
                fun put(name: String, content: String) {
                    z.putNextEntry(ZipEntry(name))
                    z.write(content.toByteArray(Charsets.UTF_8))
                    z.closeEntry()
                }
                put("[Content_Types].xml", CONTENT_TYPES)
                put("_rels/.rels", RELS_ROOT)
                put("xl/workbook.xml", WORKBOOK)
                put("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
                put("xl/worksheets/sheet1.xml", sheetXml(rows))
            }
        }
    }

    fun export(ctx: Context, uri: Uri, communes: List<Commune>) {
        val rows = mutableListOf(HEADERS)
        for (c in communes) for (e in c.ecoles) {
            if (e.professeurs.isEmpty()) rows.add(fillRow(c, e, null))
            else for (p in e.professeurs) rows.add(fillRow(c, e, p))
        }
        writeZip(ctx, uri, rows)
    }

    fun writeTemplate(ctx: Context, uri: Uri) {
        val ex1 = listOf(
            "Montreuil", "Seine-Saint-Denis (93)", "111 240 hab.", "Mme Nadia Belkacem",
            "01 48 70 51 47", "1 rue de la Mairie, 93100 Montreuil", "01 48 70 51 40",
            "École élémentaire Paul Signac", "12 avenue de la Résistance, 93100 Montreuil",
            "01 48 70 62 11", "M. Julien Marchand", "06 42 18 73 05",
            "Mme Claire Dubois", "CP", "06 11 24 58 90"
        )
        val ex2 = listOf("Montreuil", "", "", "", "", "", "",
            "École élémentaire Paul Signac", "", "", "", "",
            "M. Karim Haddad", "CE1", "06 78 34 21 46")
        writeZip(ctx, uri, mutableListOf(HEADERS, ex1, ex2))
    }

    private fun fillRow(c: Commune, e: Ecole, p: Professeur?): List<String> = listOf(
        c.nom, c.departement, c.population, c.maire, c.telMaire,
        c.adresseMairie, c.telMairie, e.nom, e.adresse, e.tel,
        e.directeur.nom, e.directeur.tel,
        p?.nom ?: "", p?.classe ?: "", p?.tel ?: ""
    )
}
