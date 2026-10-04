package com.plime.annuaire

val COMMUNES = listOf(

    Commune(
        id = "montreuil", nom = "Montreuil",
        departement = "Seine-Saint-Denis (93)", population = "111 240 hab.",
        maire = "Mme Nadia Belkacem", telMaire = "01 48 70 51 47",
        adresseMairie = "1 rue de la Mairie, 93100 Montreuil",
        telMairie = "01 48 70 51 40",
        ecoles = listOf(
            Ecole(
                id = "e1", nom = "École élémentaire Paul Signac",
                adresse = "12 avenue de la Résistance, 93100 Montreuil",
                tel = "01 48 70 62 11",
                directeur = Directeur("M. Julien Marchand", "06 42 18 73 05"),
                professeurs = listOf(
                    Professeur("Mme Claire Dubois", "CP", "06 11 24 58 90"),
                    Professeur("M. Karim Haddad", "CE1", "06 78 34 21 46"),
                    Professeur("Mme Sophie Renard", "CE2", "06 55 90 12 37"),
                    Professeur("M. Antoine Petit", "CM1", "06 23 47 89 01"),
                    Professeur("Mme Léa Moreau", "CM2", "06 84 65 30 22")
                )
            ),
            Ecole(
                id = "e2", nom = "École maternelle Les Lilas",
                adresse = "3 rue des Lilas, 93100 Montreuil",
                tel = "01 48 70 63 88",
                directeur = Directeur("Mme Isabelle Fontaine", "06 71 02 95 44"),
                professeurs = listOf(
                    Professeur("Mme Anna Costa", "Petite section", "06 12 78 40 66"),
                    Professeur("M. Thomas Girard", "Moyenne section", "06 94 31 27 08"),
                    Professeur("Mme Fatou Ndiaye", "Grande section", "06 60 19 83 52")
                )
            ),
            Ecole(
                id = "e3", nom = "École élémentaire Jean Jaurès",
                adresse = "45 bd Paul Vaillant-Couturier, 93100 Montreuil",
                tel = "01 48 70 64 02",
                directeur = Directeur("M. Bruno Lefèvre", "06 37 84 60 19"),
                professeurs = listOf(
                    Professeur("Mme Julie Lambert", "CP", "06 21 43 76 98"),
                    Professeur("Mme Nadia Cherif", "CE1", "06 87 09 55 31"),
                    Professeur("M. Étienne Blanc", "CM2", "06 45 22 10 77")
                )
            )
        )
    ),

    Commune(
        id = "vincennes", nom = "Vincennes",
        departement = "Val-de-Marne (94)", population = "49 300 hab.",
        maire = "M. François Bayle", telMaire = "01 43 28 71 12",
        adresseMairie = "53 rue de Fontenay, 94300 Vincennes",
        telMairie = "01 43 28 71 00",
        ecoles = listOf(
            Ecole(
                id = "e4", nom = "École élémentaire Saint-Louis",
                adresse = "8 avenue de Paris, 94300 Vincennes",
                tel = "01 43 28 21 45",
                directeur = Directeur("Mme Hélène Aubry", "06 52 77 41 93"),
                professeurs = listOf(
                    Professeur("M. Marc Delacroix", "CP", "06 14 88 23 57"),
                    Professeur("Mme Camille Roux", "CE2", "06 39 60 74 18"),
                    Professeur("Mme Emma Schneider", "CM1", "06 73 05 92 64")
                )
            ),
            Ecole(
                id = "e5", nom = "École maternelle de la Ferme",
                adresse = "22 rue de la Ferme, 94300 Vincennes",
                tel = "01 43 28 22 90",
                directeur = Directeur("Mme Pascale Vidal", "06 28 51 04 76"),
                professeurs = listOf(
                    Professeur("Mme Inès Benali", "Petite section", "06 95 47 38 21"),
                    Professeur("M. Hugo Perrin", "Grande section", "06 61 20 84 39")
                )
            )
        )
    ),

    Commune(
        id = "neuilly", nom = "Neuilly-sur-Seine",
        departement = "Hauts-de-Seine (92)", population = "60 350 hab.",
        maire = "Mme Catherine Barreau", telMaire = "01 40 88 80 12",
        adresseMairie = "96 avenue de Neuilly, 92200 Neuilly-sur-Seine",
        telMairie = "01 40 88 80 00",
        ecoles = listOf(
            Ecole(
                id = "e6", nom = "École élémentaire Les Renardières",
                adresse = "14 rue Louis Philippe, 92200 Neuilly-sur-Seine",
                tel = "01 40 88 82 34",
                directeur = Directeur("M. Olivier Garnier", "06 47 93 15 08"),
                professeurs = listOf(
                    Professeur("Mme Sandra Lemoine", "CP", "06 18 62 44 71"),
                    Professeur("Mme Bérénice Faure", "CE1", "06 80 27 63 15"),
                    Professeur("M. Damien Roy", "CM2", "06 33 71 90 46")
                )
            ),
            Ecole(
                id = "e7", nom = "École élémentaire Saint-James",
                adresse = "5 avenue Saint-James, 92200 Neuilly-sur-Seine",
                tel = "01 40 88 83 61",
                directeur = Directeur("Mme Valérie Noblet", "06 29 84 37 50"),
                professeurs = listOf(
                    Professeur("Mme Chloé Bardin", "CE2", "06 57 13 28 94"),
                    Professeur("M. Yann Tessier", "CM1", "06 92 46 80 23")
                )
            )
        )
    ),

    Commune(
        id = "saint-denis", nom = "Saint-Denis",
        departement = "Seine-Saint-Denis (93)", population = "113 100 hab.",
        maire = "M. Mathieu Girault", telMaire = "01 42 43 61 22",
        adresseMairie = "1 place du 18 Juin 1940, 93200 Saint-Denis",
        telMairie = "01 42 43 60 00",
        ecoles = listOf(
            Ecole(
                id = "e8", nom = "École élémentaire Jean-Moulin",
                adresse = "30 rue Gabriel Péri, 93200 Saint-Denis",
                tel = "01 42 43 62 88",
                directeur = Directeur("Mme Amina Kaci", "06 24 58 90 13"),
                professeurs = listOf(
                    Professeur("Mme Laura Petitjean", "CP", "06 77 34 21 68"),
                    Professeur("M. Sofiane Amrani", "CE1", "06 41 90 52 87"),
                    Professeur("Mme Diane Leroy", "CM1", "06 66 15 78 34")
                )
            ),
            Ecole(
                id = "e9", nom = "École maternelle Victor Hugo",
                adresse = "17 rue de la Boulangerie, 93200 Saint-Denis",
                tel = "01 42 43 63 05",
                directeur = Directeur("M. Pierre Chevalier", "06 35 68 91 07"),
                professeurs = listOf(
                    Professeur("Mme Rosa Mendes", "Petite section", "06 59 83 42 16"),
                    Professeur("Mme Alice Tanguy", "Moyenne section", "06 82 07 65 39")
                )
            )
        )
    )
)
