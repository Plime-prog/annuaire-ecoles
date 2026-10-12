#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Génère annuaire-aube-2026-10-10.xlsx à partir des données existantes
(data/annuaire-aube-2026-10-10.json) enrichies de la liste des personnels
(data/annuaire-aube-personnels-2026-10-11.json) et des directeurs d'écoles
(data/annuaire-aube-directeurs-2026-10-12.json).
Format V3 : 3 feuilles (Communes, Circonscription, Collèges), inlineStr.
- Communes : colonnes « Directeur » et « Tél. directeur » remplies par UAI.
- Circonscription : personnes par circonscription.
- Collèges : liste enrichie des principaux et adjoints.
Bibliothèque standard uniquement. Usage : python3 scripts/gen_annuaire_xlsx.py
"""
import json
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DATA = REPO / "data" / "annuaire-aube-2026-10-10.json"
PERS = REPO / "data" / "annuaire-aube-personnels-2026-10-11.json"
DIREC = REPO / "data" / "annuaire-aube-directeurs-2026-10-12.json"
OUT = REPO / "annuaire-aube-2026-10-10.xlsx"

H_COMMUNES = [
    "Commune", "Maire", "Tél. maire", "Adresse mairie", "Mail",
    "Contact élu", "Tél. contact élu", "Contact service", "Tél. contact service",
    "Observations",
    "École", "Code UAI", "Adresse école", "Tél. école", "Mail école",
    "Directeur", "Tél. directeur", "Nb élèves", "Horaires matin",
    "Horaires après-midi", "Observations école",
    "Professeur", "Classe", "Nb élèves (classe)", "Tél. professeur", "Observations prof."
]
H_COLLEGES = [
    "Nom", "Adresse", "Contact", "Fonction contact", "Adresse contact",
    "Mail contact", "Tél. 1", "Tél. 2", "Observations"
]
SHEET_ORDER = ["Communes", "Circonscription", "Collèges"]


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def col_letter(i):
    s = ""
    n = i + 1
    while n > 0:
        s = chr(ord("A") + (n - 1) % 26) + s
        n = (n - 1) // 26
    return s


def sheet_xml(rows):
    parts = [
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n',
        '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>'
    ]
    for r, row in enumerate(rows, 1):
        parts.append('<row r="%d">' % r)
        for i, v in enumerate(row):
            if v == "":
                continue
            parts.append('<c r="%s%d" t="inlineStr"><is><t>%s</t></is></c>'
                         % (col_letter(i), r, esc(str(v))))
        parts.append("</row>")
    parts.append("</sheetData></worksheet>")
    return "".join(parts)


def applique_directeurs(communes, directeurs):
    """Remplit les colonnes Directeur (15) et Tél. directeur (16) par UAI."""
    n = 0
    for row in communes:
        uai = str(row[11]).strip().upper()
        if uai in directeurs and not str(row[15]).strip():
            row[15] = directeurs[uai][0]
            row[16] = directeurs[uai][1]
            n += 1
    return n


def enrichit_colleges(colleges, principaux):
    """Ajoute principal / principal adjoint aux lignes des collèges."""
    resultats = []
    ajoutes = 0
    for row in colleges:
        nom = str(row[0])
        nouveau = list(row)
        if not nom.startswith("Section"):
            for p in principaux:
                if p["nom"] not in nom:
                    continue
                if p.get("adresse") and p["adresse"].lower() not in str(row[1]).lower():
                    continue
                if nouveau[2] == "":
                    nouveau[2] = p.get("contact", "")
                if nouveau[3] == "":
                    nouveau[3] = p.get("fonction", "")
                if p.get("tel2") and nouveau[7] == "":
                    nouveau[7] = p["tel2"]
                obs = p.get("observations", "")
                if obs and obs not in str(nouveau[8]):
                    if nouveau[8]:
                        nouveau[8] = str(nouveau[8]) + " — " + obs
                    else:
                        nouveau[8] = obs
                resultats.append(nouveau)
                adj = p.get("adjoint")
                if adj:
                    resultats.append([
                        nom, "", adj.get("contact", ""), adj.get("fonction", ""),
                        "", "", "", adj.get("tel2", ""), adj.get("observations", "")
                    ])
                    ajoutes += 1
                break
            else:
                resultats.append(nouveau)
        else:
            resultats.append(nouveau)
    return resultats, ajoutes


def main():
    data = json.loads(DATA.read_text(encoding="utf-8"))
    sheets = data["sheets"]
    pers = json.loads(PERS.read_text(encoding="utf-8"))

    directeurs = {}
    nb_dir = 0
    if DIREC.exists():
        direc = json.loads(DIREC.read_text(encoding="utf-8"))
        directeurs = direc.get("directeurs", {})
        nb_dir = applique_directeurs(sheets["Communes"], directeurs)
        print("Directeurs renseignés :", nb_dir)

    headers = {"Communes": H_COMMUNES, "Collèges": H_COLLEGES}
    headers["Circonscription"] = pers["headers"]
    sheets["Circonscription"] = pers["Circonscription"]

    colleges = sheets.pop("Colleges")
    sheets["Collèges"], nb_ajoutes = enrichit_colleges(colleges, pers.get("PrincipauxColleges", []))
    print("Principaux adjoints ajoutés :", nb_ajoutes)

    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as z:

        def put(name, content):
            z.writestr(name, content.encode("utf-8"))

        ct = [
            '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n',
            '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">',
            '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>',
            '<Default Extension="xml" ContentType="application/xml"/>',
            '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>',
        ]
        for i in range(1, len(SHEET_ORDER) + 1):
            ct.append('<Override PartName="/xl/worksheets/sheet%d.xml" '
                      'ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>' % i)
        ct.append("</Types>")
        put("[Content_Types].xml", "".join(ct))

        put("_rels/.rels",
            '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'
            '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>'
            '</Relationships>')

        wb = [
            '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n',
            '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
            'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>'
        ]
        for i, name in enumerate(SHEET_ORDER, 1):
            wb.append('<sheet name="%s" sheetId="%d" r:id="rId%d"/>' % (esc(name), i, i))
        wb.append("</sheets></workbook>")
        put("xl/workbook.xml", "".join(wb))

        rels = [
            '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n',
            '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        ]
        for i in range(1, len(SHEET_ORDER) + 1):
            rels.append('<Relationship Id="rId%d" '
                        'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" '
                        'Target="worksheets/sheet%d.xml"/>' % (i, i))
        rels.append("</Relationships>")
        put("xl/_rels/workbook.xml.rels", "".join(rels))

        for i, name in enumerate(SHEET_ORDER, 1):
            rows = [headers[name]] + [
                [str(v) if v is not None else "" for v in row]
                for row in sheets.get(name, [])
            ]
            put("xl/worksheets/sheet%d.xml" % i, sheet_xml(rows))

    counts = {n: len(sheets.get(n, [])) for n in SHEET_ORDER}
    print("OK :", OUT.name, counts)


if __name__ == "__main__":
    main()
