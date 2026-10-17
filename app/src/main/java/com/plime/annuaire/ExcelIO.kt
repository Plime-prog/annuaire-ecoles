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
            /* Reconnaissance par LES EN-TETES (pas seulement le nom de la
               feuille) : si Excel a "recupere" le classeur, les feuilles sont
               renommees "Recupere_FeuilN" et leur nom ne dit plus rien.
               - onglet des ecoles = colonnes " Commune " ET " Ecole "
                 -> renomme " Communes ", regroupe par commune ;
               - onglet des colleges = colonnes " Nom " et college/contact. */
            val nomOnglet = norme(tab.name)
            val labels = tab.fields.map { norme(it.label) }
            val estCommunes = nomOnglet.contains("commune") ||
                (labels.any { it == "commune" } && labels.any { it.contains("ecole") })
            val estColleges = !estCommunes && (nomOnglet.contains("college") ||
                (labels.any { it == "nom" } && labels.any { it.contains("college") }))
            if (estCommunes) {
                tab.name = "Communes"
                tab.groupBy =
                    tab.fields.firstOrNull { norme(it.label).contains("commune") }?.key ?: ""
            } else if (estColleges) {
                tab.groupBy = tab.fields.firstOrNull {
                    norme(it.label) == "nom" || norme(it.label).contains("college")
                }?.key ?: ""
            } else {
                /* autre onglet (Circonscription...) : liste a plat */
                tab.groupBy = ""
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

    /* Excel interdit certains caracteres dans le nom d'un onglet
       ( : \ / ? * [ ] ) et limite a 31 caracteres ; un nom non conforme
       declenche la "recuperation" du classeur a l'ouverture.
       On nettoie les noms avant d'ecrire : " Saint-Julien/Arcis "
       devient " Saint-Julien-Arcis ". L'import retablit le rattachement
       par le nom d'en-tete, donc l'aller-retour reste correct. */
    private fun safeSheetName(name: String, used: MutableSet<String>): String {
        var n = name.replace(Regex("[:\\\\/?*\\[\\]]"), "-").trim()
        if (n.length > 31) n = n.take(31)
        if (n.isBlank()) n = "Onglet"
        /* Excel distingue les noms SANS la casse (" Equipe " et " EQUIPE "
           sont un doublon pour lui) : le controle utilise des minuscules. */
        val lower = { s: String -> s.lowercase() }
        val usedLower = used.map { lower(it) }.toMutableSet()
        var base = n; var i = 2
        while (lower(n) in usedLower) { n = (base.take(28) + "_" + i); i++ }
        used.add(n); usedLower.add(lower(n))
        return n
    }

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
    /* Import FUSION : ajoute les donnees du fichier SANS effacer
       l'annuaire existant. Les lignes deja presentes (meme commune +
       meme ecole, ou meme contact) sont completees champ par champ
       (seuls les champs vides sont remplis) ; les nouvelles lignes
       sont ajoutees. Aucune donnee existante n'est ecrasee. */
    fun importFusion(ctx: Context, uri: Uri, base: Annuaire): Annuaire {
        val nouveau = import(ctx, uri)
        nouveau.tabs.forEach { nt ->
            val cible = base.tabs.firstOrNull { norme(it.name) == norme(nt.name) }
            if (cible == null) {
                base.tabs.add(nt)
                return@forEach
            }
            /* Correspondance des champs par libelle (accents/casse
               ignores) ; un champ absent est cree dans la cible. */
            val mapChamps = mutableMapOf<String, String>()
            nt.fields.forEach { nf ->
                val ef = cible.fields.firstOrNull { norme(it.label) == norme(nf.label) }
                if (ef != null) mapChamps[nf.key] = ef.key
                else { cible.fields.add(nf); mapChamps[nf.key] = nf.key }
            }
            /* Cle d'identification d'une ligne : commune + ecole pour
               l'onglet Communes, sinon premiere valeur non vide. */
            val kGrp = cible.groupBy
            val kEcole = cible.fields.firstOrNull { cleTexte(it.label) == "ecole" }?.key
            fun cleLigne(r: Map<String, String>): String =
                if (kGrp.isNotBlank())
                    cleTexte(r[kGrp] ?: "") + "|" + cleTexte(r[kEcole ?: ""] ?: "")
                else cleTexte(r.values.firstOrNull { it.isNotBlank() } ?: "")
            val existantes = cible.rows.associateBy { cleLigne(it) }
            nt.rows.forEach { nr ->
                val clee = cleLigne(nr)
                val ex = existantes[clee]
                if (ex != null) {
                    /* Completer les champs vides de la ligne existante. */
                    nr.forEach { (k, v) ->
                        val kc = mapChamps[k] ?: return@forEach
                        if (v.isNotBlank() && (ex[kc] ?: "").isBlank()) ex[kc] = v
                    }
                } else {
                    /* Nouvelle ligne : remapper ses cles de champs. */
                    val m = mutableMapOf("_id" to nouvelleCle())
                    nr.forEach { (k, v) ->
                        val kc = mapChamps[k] ?: return@forEach
                        if (v.isNotBlank()) m[kc] = v
                    }
                    cible.rows.add(m)
                }
            }
        }
        return base
    }

    fun export(ctx: Context, uri: Uri, a: Annuaire, withRows: Boolean = true) {
        val used = mutableSetOf<String>()
        val sheets = a.tabs.map { tab ->
            val rows = mutableListOf(tab.fields.map { it.label })
            if (withRows) for (row in tab.rows) {
                rows.add(tab.fields.map { row[it.key] ?: "" })
            }
            safeSheetName(tab.name, used) to rows
        }
        if (sheets.isEmpty()) throw IllegalArgumentException("aucun onglet \u00E0 exporter")
        writeZip(ctx, uri, sheets)
    }

    /** Modele vierge : structure en cours, sans les donnees. */
    fun writeTemplate(ctx: Context, uri: Uri, a: Annuaire) = export(ctx, uri, a, withRows = false)
}
