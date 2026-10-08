package com.plime.annuaire.v2

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
 * Import/Export du classeur Excel V2 (.xlsx), SANS bibliothèque externe.
 *
 * Format : un classeur unique à 5 feuilles :
 *  - "Communes"        : hiérarchie commune / école / professeur (1 ligne = 1 professeur,
 *                        ou 1 ligne = 1 école si elle n'a pas de professeur)
 *  - "RASED"           : 1 ligne = 1 contact
 *  - "Circonscription" : 1 ligne = 1 contact
 *  - "PIAL_ER"         : 1 ligne = 1 contact
 *  - "Colleges"        : 1 ligne = 1 collège
 *
 * Compatible avec les classeurs enregistrés par Excel (mode sharedStrings)
 * et avec ceux produits par cette classe (mode inlineStr).
 */
object ExcelIOV2 {

    /* ---------- En-têtes des 5 feuilles ---------- */

    val H_COMMUNES = listOf(
        "Commune", "Maire", "Tél. maire", "Adresse mairie", "Mail",
        "Contact élu", "Tél. contact élu", "Contact service", "Tél. contact service",
        "Observations",
        "École", "Code UAI", "Adresse école", "Tél. école", "Mail école",
        "Directeur", "Tél. directeur", "Nb élèves", "Horaires matin",
        "Horaires après-midi", "Observations école",
        "Professeur", "Classe", "Nb élèves (classe)", "Tél. professeur", "Observations prof."
    )

    val H_CONTACTS = listOf(
        "Contact", "Fonction", "Adresse", "Mail", "Tél. 1", "Tél. 2", "Observations"
    )

    val H_COLLEGES = listOf(
        "Nom", "Adresse", "Contact", "Fonction contact", "Adresse contact",
        "Mail contact", "Tél. 1", "Tél. 2", "Observations"
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

    /** Associe le nom de chaque feuille à son fichier XML dans le classeur. */
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
                        for ((i, v) in cells) if (i < 30) arr[i] = v.trim()
                        rows.add(arr.toList())
                    }
                }
            }
            ev = p.next()
        }
        return rows
    }

    /* ---------- IMPORT ---------- */

    fun import(ctx: Context, uri: Uri): AnnuaireV2 {
        val files = readZip(ctx, uri)
        val shared = files["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        val sheets = sheetMap(files)

        fun rowsOf(sheetName: String): List<List<String>> {
            val path = sheets.entries.firstOrNull {
                it.key.equals(sheetName, ignoreCase = true)
            }?.value ?: return emptyList()
            val bytes = files[path] ?: return emptyList()
            return parseSheet(bytes, shared)
        }

        fun cell(row: List<String>, i: Int): String = row.getOrElse(i) { "" }

        /* --- Feuille Communes --- */
        val communes = mutableListOf<CommuneV2>()
        val communeIdx = linkedMapOf<String, CommuneV2>()
        val ecoleIdx = linkedMapOf<String, EcoleV2>()
        for (row in rowsOf("Communes").drop(1)) {
            if (cell(row, 0).isBlank() && cell(row, 10).isBlank() && cell(row, 21).isBlank()) continue
            val nomC = cell(row, 0)
            val cm = communeIdx.getOrPut(nomC) {
                val c = CommuneV2(id = newIdV2(), nom = nomC)
                communes.add(c)
                c
            }
            cm.maire = cell(row, 1).ifBlank { cm.maire }
            cm.telMaire = cell(row, 2).ifBlank { cm.telMaire }
            cm.adresseMairie = cell(row, 3).ifBlank { cm.adresseMairie }
            cm.mail = cell(row, 4).ifBlank { cm.mail }
            cm.contactElu = cell(row, 5).ifBlank { cm.contactElu }
            cm.telContactElu = cell(row, 6).ifBlank { cm.telContactElu }
            cm.contactService = cell(row, 7).ifBlank { cm.contactService }
            cm.telContactService = cell(row, 8).ifBlank { cm.telContactService }
            cm.observations = cell(row, 9).ifBlank { cm.observations }
            val nomE = cell(row, 10)
            if (nomE.isBlank()) continue
            val ec = ecoleIdx.getOrPut("$nomC|$nomE") {
                val e = EcoleV2(id = newIdV2(), nom = nomE)
                cm.ecoles.add(e)
                e
            }
            ec.codeUai = cell(row, 11).ifBlank { ec.codeUai }
            ec.adresse = cell(row, 12).ifBlank { ec.adresse }
            ec.tel = cell(row, 13).ifBlank { ec.tel }
            ec.mail = cell(row, 14).ifBlank { ec.mail }
            ec.directeur = cell(row, 15).ifBlank { ec.directeur }
            ec.telDirecteur = cell(row, 16).ifBlank { ec.telDirecteur }
            ec.nbEleves = cell(row, 17).ifBlank { ec.nbEleves }
            ec.horairesMatin = cell(row, 18).ifBlank { ec.horairesMatin }
            ec.horairesApresMidi = cell(row, 19).ifBlank { ec.horairesApresMidi }
            ec.observations = cell(row, 20).ifBlank { ec.observations }
            val nomP = cell(row, 21)
            if (nomP.isNotBlank()) ec.professeurs.add(ProfesseurV2(newIdV2(), nomP, cell(row, 22), cell(row, 23), cell(row, 24), cell(row, 25)))
        }

        /* --- Feuilles contacts (RASED / Circonscription / PIAL_ER) --- */
        fun importContacts(sheetName: String): Pair<Int, List<out Any>> = throw IllegalStateException()

        val rased = rowsOf("RASED").drop(1).mapNotNull {
            if (it.getOrElse(0) { "" }.isBlank() && it.getOrElse(1) { "" }.isBlank()) null
            else RasedEntry(newIdV2(), it.getOrElse(0) { "" }, it.getOrElse(1) { "" },
                it.getOrElse(2) { "" }, it.getOrElse(3) { "" }, it.getOrElse(4) { "" },
                it.getOrElse(5) { "" }, it.getOrElse(6) { "" })
        }
        val circo = rowsOf("Circonscription").drop(1).mapNotNull {
            if (it.getOrElse(0) { "" }.isBlank() && it.getOrElse(1) { "" }.isBlank()) null
            else CircoEntry(newIdV2(), it.getOrElse(0) { "" }, it.getOrElse(1) { "" },
                it.getOrElse(2) { "" }, it.getOrElse(3) { "" }, it.getOrElse(4) { "" },
                it.getOrElse(5) { "" }, it.getOrElse(6) { "" })
        }
        val pial = rowsOf("PIAL_ER").drop(1).mapNotNull {
            if (it.getOrElse(0) { "" }.isBlank() && it.getOrElse(1) { "" }.isBlank()) null
            else PialEntry(newIdV2(), it.getOrElse(0) { "" }, it.getOrElse(1) { "" },
                it.getOrElse(2) { "" }, it.getOrElse(3) { "" }, it.getOrElse(4) { "" },
                it.getOrElse(5) { "" }, it.getOrElse(6) { "" })
        }

        /* --- Feuille Colleges --- */
        val colleges = rowsOf("Colleges").drop(1).mapNotNull {
            if (it.getOrElse(0) { "" }.isBlank()) null
            else CollegeV2(newIdV2(), it.getOrElse(0) { "" }, it.getOrElse(1) { "" },
                it.getOrElse(2) { "" }, it.getOrElse(3) { "" }, it.getOrElse(4) { "" },
                it.getOrElse(5) { "" }, it.getOrElse(6) { "" }, it.getOrElse(7) { "" },
                it.getOrElse(8) { "" })
        }

        return AnnuaireV2(communes, rased, circo, pial, colleges)
    }

    /* ---------- ÉCRITURE ---------- */

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

    fun export(ctx: Context, uri: Uri, a: AnnuaireV2) {
        val feuilleCommunes = mutableListOf(H_COMMUNES)
        for (c in a.communes) for (e in c.ecoles) {
            if (e.professeurs.isEmpty()) {
                feuilleCommunes.add(listOf(
                    c.nom, c.maire, c.telMaire, c.adresseMairie, c.mail,
                    c.contactElu, c.telContactElu, c.contactService, c.telContactService,
                    c.observations,
                    e.nom, e.codeUai, e.adresse, e.tel, e.mail, e.directeur,
                    e.telDirecteur, e.nbEleves, e.horairesMatin, e.horairesApresMidi,
                    e.observations,
                    "", "", "", "", ""))
            } else for (p in e.professeurs) {
                feuilleCommunes.add(listOf(
                    c.nom, c.maire, c.telMaire, c.adresseMairie, c.mail,
                    c.contactElu, c.telContactElu, c.contactService, c.telContactService,
                    c.observations,
                    e.nom, e.codeUai, e.adresse, e.tel, e.mail, e.directeur,
                    e.telDirecteur, e.nbEleves, e.horairesMatin, e.horairesApresMidi,
                    e.observations,
                    p.nom, p.classe, p.nbEleves, p.tel, p.observations))
            }
        }
        val fRased = mutableListOf(H_CONTACTS)
        a.rased.forEach { fRased.add(listOf(it.contact, it.fonction, it.adresse, it.mail, it.tel1, it.tel2, it.observations)) }
        val fCirco = mutableListOf(H_CONTACTS)
        a.circo.forEach { fCirco.add(listOf(it.contact, it.fonction, it.adresse, it.mail, it.tel1, it.tel2, it.observations)) }
        val fPial = mutableListOf(H_CONTACTS)
        a.pial.forEach { fPial.add(listOf(it.contact, it.fonction, it.adresse, it.mail, it.tel1, it.tel2, it.observations)) }
        val fColleges = mutableListOf(H_COLLEGES)
        a.colleges.forEach {
            fColleges.add(listOf(it.nom, it.adresse, it.contact, it.fonction, it.adresseContact, it.mailContact, it.tel1, it.tel2, it.observations))
        }
        writeZip(ctx, uri, listOf(
            "Communes" to feuilleCommunes,
            "RASED" to fRased,
            "Circonscription" to fCirco,
            "PIAL_ER" to fPial,
            "Colleges" to fColleges))
    }

    /** Modèle vierge (5 onglets) avec une ligne d'exemple pour guider la saisie. */
    fun writeTemplate(ctx: Context, uri: Uri) {
        val exC = listOf(
            "Montreuil", "Mme Nadia Belkacem", "01 48 70 51 47",
            "1 rue de la Mairie, 93100 Montreuil", "mairie@montreuil.fr",
            "M. Paul Durant", "06 11 22 33 44", "Mme Anne Roger", "01 48 70 51 48",
            "Mairie en centre-ville",
            "École élémentaire Paul Signac", "0931234X", "12 avenue de la Résistance, 93100 Montreuil",
            "01 48 70 62 11", "ecole-signac@montreuil.fr", "M. Julien Marchand",
            "06 42 18 73 05", "245", "8h30 - 11h45", "13h45 - 16h30",
            "Restauration sur place",
            "Mme Claire Dubois", "CP", "28", "06 11 24 58 90", "Référente CP"
        )
        val exContact = listOf(
            "Mme Sophie Lemaire", "Enseignante spécialisée", "1 rue de la Mairie, 93100 Montreuil",
            "s.lemaire@ac-creteil.fr", "01 48 70 51 49", "06 55 44 33 22", "Permanence le matin"
        )
        val exCollege = listOf(
            "Collège Jean Vilar", "5 rue des Lilas, 93100 Montreuil",
            "Mme Roberta Klein", "Principale adjointe", "5 rue des Lilas, 93100 Montreuil",
            "r.klein@ac-creteil.fr", "01 48 70 51 50", "06 77 88 99 00", "Section sportive"
        )
        writeZip(ctx, uri, listOf(
            "Communes" to mutableListOf(H_COMMUNES, exC),
            "RASED" to mutableListOf(H_CONTACTS, exContact),
            "Circonscription" to mutableListOf(H_CONTACTS, exContact),
            "PIAL_ER" to mutableListOf(H_CONTACTS, exContact),
            "Colleges" to mutableListOf(H_COLLEGES, exCollege)))
    }
}
