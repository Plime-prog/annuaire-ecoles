#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Génère annuaire-aube-2026-10-10.xlsx à partir de data/annuaire-aube-2026-10-10.json.
Format identique à ExcelIOV2 (5 feuilles, inlineStr). Bibliothèque standard uniquement.
Usage : python3 scripts/gen_annuaire_xlsx.py
"""
import json
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DATA = REPO / "data" / "annuaire-aube-2026-10-10.json"
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
H_CONTACTS = ["Contact", "Fonction", "Adresse", "Mail", "Tél. 1", "Tél. 2", "Observations"]
H_COLLEGES = [
    "Nom", "Adresse", "Contact", "Fonction contact", "Adresse contact",
    "Mail contact", "Tél. 1", "Tél. 2", "Observations"
]
HEADERS = {
    "Communes": H_COMMUNES, "RASED": H_CONTACTS, "Circonscription": H_CONTACTS,
    "PIAL_ER": H_CONTACTS, "Colleges": H_COLLEGES,
}
SHEET_ORDER = ["Communes", "RASED", "Circonscription", "PIAL_ER", "Colleges"]


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


def main():
    data = json.loads(DATA.read_text(encoding="utf-8"))
    sheets = data["sheets"]

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
            rows = [HEADERS[name]] + [
                [str(v) if v is not None else "" for v in row]
                for row in sheets.get(name, [])
            ]
            put("xl/worksheets/sheet%d.xml" % i, sheet_xml(rows))

    counts = {n: len(sheets.get(n, [])) for n in SHEET_ORDER}
    print("OK :", OUT.name, counts)


if __name__ == "__main__":
    main()
