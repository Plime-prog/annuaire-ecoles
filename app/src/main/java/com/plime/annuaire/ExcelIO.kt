package com.plime.annuaire

import android.content.Context
import android.net.Uri
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook

/** Lecture/écriture de fichiers Excel (.xlsx). */
object ExcelIO {

    val HEADERS = listOf(
        "Commune", "Département", "Population", "Maire", "Tél. maire",
        "Adresse mairie", "Tél. mairie", "École", "Adresse école", "Tél. école",
        "Directeur", "Tél. directeur", "Professeur", "Classe", "Tél. professeur"
    )

    /** Importe un classeur .xlsx et reconstruit la liste des communes. */
    fun import(ctx: Context, uri: Uri): List<Commune> {
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            val wb = XSSFWorkbook(input)
            val sheet = wb.getSheetAt(0)
            val fmt = DataFormatter()

            class TEcole(val nom: String, val adresse: String, val tel: String,
                         val dirNom: String, val dirTel: String,
                         val profs: MutableList<Professeur>)
            class TCommune(val nom: String, val dep: String, val pop: String,
                           val maire: String, val telMaire: String,
                           val adr: String, val tel: String,
                           val ecoles: LinkedHashMap<String, TEcole>)

            val communes = LinkedHashMap<String, TCommune>()

            for (r in 1..sheet.lastRowNum) {
                val row: Row = sheet.getRow(r) ?: continue
                fun c(i: Int): String =
                    row.getCell(i)?.let { fmt.formatCellValue(it).trim() } ?: ""
                if (c(0).isBlank() && c(7).isBlank() && c(12).isBlank()) continue

                val cm = communes.getOrPut(c(0)) {
                    TCommune(c(0), c(1), c(2), c(3), c(4), c(5), c(6), LinkedHashMap())
                }
                if (c(7).isBlank()) continue
                val ec = cm.ecoles.getOrPut(c(7)) {
                    TEcole(c(7), c(8), c(9), c(10), c(11), mutableListOf())
                }
                if (c(12).isNotBlank()) ec.profs.add(Professeur(c(12), c(13), c(14)))
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
        return emptyList()
    }

    /** Exporte tout l'annuaire vers un fichier .xlsx. */
    fun export(ctx: Context, uri: Uri, communes: List<Commune>) {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Annuaire")
        val header = sheet.createRow(0)
        HEADERS.forEachIndexed { i, h -> header.createCell(i).setCellValue(h) }
        var r = 1
        for (c in communes) for (e in c.ecoles) {
            if (e.professeurs.isEmpty()) {
                fillRow(sheet.createRow(r++), c, e, null)
            } else {
                for (p in e.professeurs) fillRow(sheet.createRow(r++), c, e, p)
            }
        }
        ctx.contentResolver.openOutputStream(uri)?.use { wb.write(it) }
    }

    /** Génère un modèle d'import : en-têtes + 2 lignes d'exemple. */
    fun writeTemplate(ctx: Context, uri: Uri) {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Annuaire")
        val header = sheet.createRow(0)
        HEADERS.forEachIndexed { i, h -> header.createCell(i).setCellValue(h) }

        val ex1 = sheet.createRow(1)
        listOf(
            "Montreuil", "Seine-Saint-Denis (93)", "111 240 hab.", "Mme Nadia Belkacem",
            "01 48 70 51 47", "1 rue de la Mairie, 93100 Montreuil", "01 48 70 51 40",
            "École élémentaire Paul Signac", "12 avenue de la Résistance, 93100 Montreuil",
            "01 48 70 62 11", "M. Julien Marchand", "06 42 18 73 05",
            "Mme Claire Dubois", "CP", "06 11 24 58 90"
        ).forEachIndexed { i, s -> ex1.createCell(i).setCellValue(s) }

        /* 2e ligne d'exemple : on ne répète PAS les infos déjà données
           pour la commune et l'école, seulement le nouveau professeur. */
        val ex2 = sheet.createRow(2)
        listOf("Montreuil", "", "", "", "", "", "",
            "École élémentaire Paul Signac", "", "", "", "",
            "M. Karim Haddad", "CE1", "06 78 34 21 46"
        ).forEachIndexed { i, s -> ex2.createCell(i).setCellValue(s) }

        ctx.contentResolver.openOutputStream(uri)?.use { wb.write(it) }
    }

    private fun fillRow(row: Row, c: Commune, e: Ecole, p: Professeur?) {
        val v = listOf(
            c.nom, c.departement, c.population, c.maire, c.telMaire,
            c.adresseMairie, c.telMairie, e.nom, e.adresse, e.tel,
            e.directeur.nom, e.directeur.tel,
            p?.nom ?: "", p?.classe ?: "", p?.tel ?: ""
        )
        v.forEachIndexed { i, s -> row.createCell(i).setCellValue(s) }
    }
}
