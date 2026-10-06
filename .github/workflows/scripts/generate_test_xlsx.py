#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Génère test-annuaire-xxl.xlsx : 40 communes, 120 écoles, 350 professeurs."""
import zipfile, random

HEADERS = ["Commune","Département","Population","Maire","Tél. maire","Adresse mairie",
           "Tél. mairie","École","Adresse école","Tél. école","Directeur","Tél. directeur",
           "Professeur","Classe","Tél. professeur"]
PRE = ["Saint","Mont","Ville","Beau","Neuf","Val","Clair","Roch","Fontaine","Pierre",
       "Bois","Lac","Cour","Château","Pont","Vieux","Haut","Basse"]
SUF = ["-neuve","-le-Comte","-sur-Loire","-en-Brie","-les-Bains","-du-Bois","-la-Forêt",
       "-sous-Cergy","-en-Provence","-la-Romaine","-d'Anjou","-le-Château","-lès-Mines",
       "-sur-Mer","-en-Velay"]
DEPS = ["Loire-Atlantique (44)","Ain (01)","Gers (32)","Vendée (85)","Aveyron (12)",
        "Seine-Maritime (76)","Drôme (26)","Jura (39)","Morbihan (56)","Hautes-Alpes (05)",
        "Nièvre (58)","Tarn (81)"]
PREFIX = ["02","03","04","05"]
PRENOMS = ["Marie","Jean","Claire","Pierre","Sophie","Michel","Nadia","Paul","Julie","Marc",
           "Anne","Luc","Céline","Antoine","Élodie","Bruno","Fatima","Hugo","Nathalie","Olivier",
           "Camille","Vincent","Laura","David","Inès","Thomas","Amandine","Kevin","Rosa","Étienne"]
NOMS = ["Martin","Bernard","Dubois","Thomas","Robert","Richard","Petit","Durand","Leroy","Moreau",
        "Simon","Laurent","Lefebvre","Michel","Garcia","Roux","Fournier","Girard","Lambert","Bonnet",
        "Faure","Mercier","Blanc","Henry","Chevalier","Perrin","Morel","Barbier","Dupont","Leroux"]
NOMS_ECOLE = ["des Fleurs","Jean Jaurès","Victor Hugo","du Parc","Jean Moulin","Saint-Exupéry",
              "Marie Curie","des Chênes","Jules Ferry","de la Fontaine","Paul Bert","des Peupliers"]
RUES = ["rue des Écoles","avenue de la République","rue Victor Hugo","place du Marché",
        "boulevard des Fleurs","chemin du Moulin","rue Pasteur","allée des Tilleuls",
        "rue de la Gare","impasse des Jardins"]
CLASSES_ELEM = ["CP","CE1","CE2","CM1","CM2"]
CLASSES_MAT = ["Petite section","Moyenne section","Grande section"]

def esc(s):
    return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")

def col_letter(i):
    s = ""
    i += 1
    while i > 0:
        m = (i - 1) % 26
        s = chr(65 + m) + s
        i = (i - 1) // 26
    return s

def main():
    rnd = random.Random(20261006)
    two = lambda: str(10 + rnd.randrange(90))
    tel_fixe = lambda: "%s %s %s %s %s" % (PREFIX[rnd.randrange(4)], two(), two(), two(), two())
    tel_mob = lambda: "06 %s %s %s %s" % (two(), two(), two(), two())
    personne = lambda: ("Mme " if rnd.random() < 0.5 else "M. ") + \
        PRENOMS[rnd.randrange(len(PRENOMS))] + " " + NOMS[rnd.randrange(len(NOMS))]

    rows = [HEADERS]
    used = set()
    ecole_idx = 0
    for c in range(40):
        nom = PRE[(c * 7) % len(PRE)] + SUF[rnd.randrange(len(SUF))]
        while nom in used:
            nom = PRE[(c * 7) % len(PRE)] + SUF[rnd.randrange(len(SUF))]
        used.add(nom)
        dep = DEPS[c % len(DEPS)]
        cp = str(1000 + (c * 137) % 89000)
        pop = "%d %03d hab." % (5 + (c * 331) % 195, (c * 7) % 1000)
        maire = personne()
        t_maire = tel_fixe()
        adr_mairie = "%d place de la Mairie, %s %s" % (c % 60 + 1, cp, nom)
        t_mairie = tel_fixe()
        types = ["École élémentaire", "École maternelle", "Groupe scolaire"]
        for e in range(3):
            nb_prof = 3 if ecole_idx < 110 else 2
            ecole_idx += 1
            nom_ecole = "%s %s" % (types[e], NOMS_ECOLE[rnd.randrange(len(NOMS_ECOLE))])
            adresse = "%d %s, %s %s" % ((e + 1) * 7, RUES[rnd.randrange(len(RUES))], cp, nom)
            t_ecole = tel_fixe()
            directeur = personne()
            t_dir = tel_mob()
            classes = CLASSES_MAT if e == 1 else CLASSES_ELEM
            for p in range(nb_prof):
                rows.append([nom, dep, pop, maire, t_maire, adr_mairie, t_mairie,
                             nom_ecole, adresse, t_ecole, directeur, t_dir,
                             personne(), classes[p % len(classes)], tel_mob()])

    sheet = ['<?xml version="1.0" encoding="UTF-8" standalone="yes"?>',
             '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>']
    for r, row in enumerate(rows):
        sheet.append('<row r="%d">' % (r + 1))
        for i, v in enumerate(row):
            if v == "":
                continue
            sheet.append('<c r="%s%d" t="inlineStr"><is><t>%s</t></is></c>' % (col_letter(i), r + 1, esc(v)))
        sheet.append('</row>')
    sheet.append('</sheetData></worksheet>')
    sheet_xml = "\n".join(sheet)

    content_types = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>'
    rels_root = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>'
    workbook = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Annuaire" sheetId="1" r:id="rId1"/></sheets></workbook>'
    workbook_rels = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>'

    with zipfile.ZipFile("test-annuaire-xxl.xlsx", "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("[Content_Types].xml", content_types)
        z.writestr("_rels/.rels", rels_root)
        z.writestr("xl/workbook.xml", workbook)
        z.writestr("xl/_rels/workbook.xml.rels", workbook_rels)
        z.writestr("xl/worksheets/sheet1.xml", sheet_xml)

    print("OK : %d lignes de données" % (len(rows) - 1))

if __name__ == "__main__":
    main()
