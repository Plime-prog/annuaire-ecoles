package com.plime.annuaire


import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppAnnuaire() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppAnnuaire() {
    val ctx = LocalContext.current
    var annuaire by remember { mutableStateOf(Annuaire()) }
    var unlocked by remember { mutableStateOf(false) }
    var hasPin by remember { mutableStateOf(false) }
    var tabIndex by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showSchema by remember { mutableStateOf(false) }
    /* carte de l'Aube (bouton carte en haut) */
    var showCarte by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var revision by remember { mutableStateOf(0) }

    /* Edition d'une ligne : onglet + copie de la ligne (ou ligne neuve) */
    var editTab by remember { mutableStateOf<Onglet?>(null) }
    var editRow by remember { mutableStateOf<MutableMap<String, String>?>(null) }
    var editIsNew by remember { mutableStateOf(false) }
    /* Ligne d'origine pour l'edition (null si nouvelle) */
    var editOriginal by remember { mutableStateOf<MutableMap<String, String>?>(null) }

    /* Navigation en escalier : groupe ouvert (ex. commune), puis fiche ouverte (ex. ecole) */
    var openGroup by remember { mutableStateOf<String?>(null) }
    /* Commune choisie via la carte : memorisee pour la reouvrir en fonce
       sur la carte au retour. */
    var communeCarte by remember { mutableStateOf("") }
    var openRow by remember { mutableStateOf<MutableMap<String, String>?>(null) }

    /* Logo .jpg personnalise (remplace le logo officiel) */
    var logoVersion by remember { mutableStateOf(0) }
    /* par defaut, la circonscription de Saint-Julien-Arcis est
       selectionnee ; la fiche favorite (etoile) s'ouvre automatiquement. */
    var filtre by remember { mutableStateOf("0100043T") }
    var favoriVersion by remember { mutableStateOf(0) }
    var onglet by remember { mutableStateOf(0) }
    var ongletFiche by remember { mutableStateOf<Onglet?>(null) }

    /* donnees des circonscriptions (chips du filtre) */
    var circos by remember { mutableStateOf(listOf<Circo>()) }

    fun saveAll() {
        Stockage.save(ctx, annuaire)
        revision++
    }

    LaunchedEffect(Unit) {
        annuaire = Stockage.load(ctx)
        hasPin = Stockage.hasPin(ctx)
        circos = chargerCircos(ctx)
        /* Ouvrir la fiche favorite (etoile) au demarrage si elle existe */
        val fav = Stockage.getFavori(ctx)
        if (fav.isNotBlank()) {
            val parties = fav.split("|")
            if (parties.size == 2) {
                val tb = annuaire.tabs.firstOrNull { it.id == parties[0] }
                val rw = tb?.rows?.firstOrNull { it["_id"] == parties[1] }
                if (tb != null && rw != null) {
                    ongletFiche = tb
                    openRow = rw
                }
            }
        }
    }

    /* les onglets DSDEN/circonscriptions sont recrees des qu'ils manquent - y compris
       apres un import Excel, " Tout effacer " ou " Defauts ". */
    LaunchedEffect(annuaire) {
        if (annuaire.tabs.none { it.id.startsWith("v5_") }) {
            assurerOnglets(annuaire, ctx)
            Stockage.save(ctx, annuaire)
            revision++
        }
    }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) try {
            annuaire = ExcelIO.import(ctx, uri)
            Stockage.save(ctx, annuaire)
            revision++
            val n = annuaire.tabs.sumOf { it.rows.size }
            message = "Import r\u00E9ussi : ${annuaire.tabs.size} onglet(s), $n ligne(s). " +
                "(Onglets et champs reconstruits d'apr\u00E8s le fichier.)"
        } catch (e: Exception) {
            message = "\u00C9chec de l'import : ${e.message ?: "fichier invalide"}"
        }
    }

    /* Import FUSION : ajoute les donnees du fichier sans rien effacer
       (champs vides completes, nouvelles lignes ajoutees). */
    val fusionneur = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) try {
            annuaire = ExcelIO.importFusion(ctx, uri, annuaire)
            Stockage.save(ctx, annuaire)
            revision++
            val n = annuaire.tabs.sumOf { it.rows.size }
            message = "Fusion r\u00E9ussie : $n ligne(s) au total. Donn\u00E9es existantes conserv\u00E9es."
        } catch (e: Exception) {
            message = "\u00C9chec de la fusion : ${e.message ?: "fichier invalide"}"
        }
    }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) try {
            ExcelIO.export(ctx, uri, annuaire)
            message = "Export r\u00E9ussi."
        } catch (e: Exception) {
            message = "\u00C9chec de l'export : ${e.message ?: "erreur"}"
        }
    }

    val modeleur = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) try {
            ExcelIO.writeTemplate(ctx, uri, annuaire)
            message = "Mod\u00E8le cr\u00E9\u00E9 (en-t\u00EAtes actuels, sans donn\u00E9es)."
        } catch (e: Exception) {
            message = "\u00C9chec : ${e.message ?: "erreur"}"
        }
    }

    val logoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) try {
            val dest = File(ctx.filesDir, "logo.jpg")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
            logoVersion++
            message = "Logo ajout\u00E9."
        } catch (e: Exception) {
            message = "\u00C9chec du logo : ${e.message ?: "erreur"}"
        }
    }

    /* Theme : fond blanc partout, ecriture noire, accents vert menthe.
       TOUTES les cases de couleur sont remplies : plus aucune couleur
       par defaut (rose ou violet) de Material 3 ne peut apparaitre,
       y compris dans les boites de dialogue (Reglages, PIN...). */
    MaterialTheme(
        colorScheme = lightColorScheme(
            background = Color.White,
            onBackground = Color.Black,
            surface = Color.White,
            onSurface = Color.Black,
            surfaceVariant = Color.White,
            onSurfaceVariant = Color(0xFF333333),
            surfaceTint = Color.White,
            surfaceDim = Color.White,
            surfaceBright = Color.White,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color.White,
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color.White,
            surfaceContainerHighest = Color.White,
            inverseSurface = Color(0xFF222222),
            inverseOnSurface = Color.White,
            inversePrimary = VertMenthe,
            primary = VertMenthe,
            onPrimary = Color.White,
            primaryContainer = Color.White,
            onPrimaryContainer = Color.Black,
            secondary = VertMenthe,
            onSecondary = Color.White,
            secondaryContainer = Color.White,
            onSecondaryContainer = Color.Black,
            tertiary = VertMenthe,
            onTertiary = Color.White,
            tertiaryContainer = Color.White,
            onTertiaryContainer = Color.Black,
            error = Color(0xFFB3261E),
            onError = Color.White,
            errorContainer = Color.White,
            onErrorContainer = Color(0xFFB3261E),
            outline = Color(0xFF777777),
            outlineVariant = Color(0xFFDDDDDD)
        )
    ) {
        if (!hasPin) {
            PinDefineScreen { pin ->
                Stockage.setPin(ctx, pin)
                unlocked = true
                hasPin = true
            }
        } else if (!unlocked) {
            PinEnterScreen { pin ->
                if (Stockage.checkPin(ctx, pin)) unlocked = true
                else message = "Code incorrect."
            }
        } else {
            val t = editTab
            val r = editRow
            if (t != null && r != null) EntityDialog(
                tab = t, values = r,
                isNew = editIsNew,
                onSave = {
                    if (editIsNew) {
                        r["_id"] = nouvelleCle()
                        t.rows.add(r)
                    } else {
                        editOriginal?.clear()
                        editOriginal?.putAll(r)
                    }
                    editTab = null; editRow = null
                    saveAll()
                    message = "Modifications enregistr\u00E9es."
                },
                onCancel = { editTab = null; editRow = null },
                onDelete = if (editIsNew) null else {{
                    editOriginal?.let { orig -> t.rows.removeIf { it === orig } }
                    editTab = null; editRow = null
                    saveAll()
                    message = "Ligne supprim\u00E9e."
                }}
            )

            if (showCarte) {
                /* Commune choisie via la carte : memorisee pour qu'elle
                   apparaisse en fonce a la reouverture de la carte. */
                EcranCarte(
                    filtreActif = filtre,
                    communeSelectionnee = communeCarte,
                    onCirco = { code ->
                        filtre = code
                        communeCarte = ""
                        onglet = 0
                        openGroup = null
                        openRow = null
                        ongletFiche = null
                    },
                    /* Selection d'une commune sur la carte (sans ouvrir
                       la fiche) : simplement memorisee ; la carte reste
                       affichee sur la vue circonscription. */
                    onCommuneSel = { nom -> communeCarte = nom },
                    /* Une commune touchee sur la carte : retrouver le groupe
                       du meme nom dans l'onglet Communes (accents et casse
                       ignores) et l'ouvrir. */
                    onCommune = { nom ->
                        showCarte = false
                        communeCarte = nom
                        val tb = annuaire.tabs.firstOrNull {
                            it.name.equals("Communes", ignoreCase = true)
                        }
                        val cle = tb?.groupBy
                        val cible = if (tb != null && cle != null)
                            tb.rows.firstOrNull { norme(it[cle] ?: "") == norme(nom) }?.get(cle)
                        else null
                        if (cible != null) {
                            openGroup = cible
                            openRow = null
                            ongletFiche = null
                        } else {
                            message = "Commune introuvable dans l'annuaire : " + nom
                        }
                    },
                    onFermer = { showCarte = false }
                )
            } else if (showSchema) {
                SchemaEditorScreen(
                    annuaire = annuaire,
                    onDone = { saveAll(); showSchema = false; message = "Structure enregistr\u00E9e." },
                    onRestore = {
                        annuaire = schemaParDefaut()
                        saveAll()
                        tabIndex = 0
                        message = "Structure par d\u00E9faut restaur\u00E9e (donn\u00E9es effac\u00E9es)."
                    }
                )
            } else {
                val safeIndex =
                    if (annuaire.tabs.isEmpty()) 0
                    else tabIndex.coerceIn(0, annuaire.tabs.size - 1)
                val currentTab = annuaire.tabs.getOrNull(safeIndex)

                val logoImage = remember(logoVersion) {
                    File(ctx.filesDir, "logo.jpg").takeIf { it.exists() }
                        ?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
                }
                Scaffold(
                    topBar = {
                        /* En-tete sur mesure, plus haut qu'une TopAppBar,
                           pour afficher le logo en grand.
                           Le bandeau tricolore historique a ete retire
                           au profit du logo officiel.
                           */
                        Surface(color = Color.White) {
                            Row(
                                Modifier.fillMaxWidth().height(148.dp)
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                /* Le logo remplace le titre : toucher le logo
                                   pour en choisir un autre. */
                                Row(
                                    Modifier.weight(1f).clickable {
                                        logoPicker.launch(arrayOf("image/*"))
                                    },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (logoImage != null) {
                                        Image(
                                            logoImage, "Logo - toucher pour changer",
                                            Modifier.fillMaxWidth().heightIn(max = 144.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        /* Logo officiel agrandi nativement (echelle 2,4),
                                           sans Modifier.scale qui rogne et decale le logo.
                                           le nom de la circonscription choisie est
                                           inscrit EN VERT dans le logo, 4e ligne du bloc ;
                                           rien n'est ajoute pour la DSDEN. */
                                        Box(
                                            Modifier.fillMaxWidth().height(144.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            LogoOfficiel(
                                                echelle = 3.2f,
                                                circonscription =
                                                    if (filtre == "dsden") ""
                                                    else circos.firstOrNull { it.code == filtre }
                                                        ?.let { nomCourt(it.nom) } ?: ""
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    floatingActionButton = {
                        val og5 = ongletsDuFiltre(annuaire, filtre)
                        val t5 = if (og5.isEmpty()) null else og5[onglet.coerceIn(0, og5.size - 1)].second
                        /* Quand une fiche est ouverte (notamment apres une
                           recherche globale), le bouton + doit ajouter dans
                           l'onglet de CETTE fiche, pas dans l'onglet actif. */
                        val tabAjout = (openRow?.let { ongletFiche }) ?: t5
                        if (tabAjout != null) FloatingActionButton(onClick = {
                            editTab = tabAjout
                            editOriginal = null
                            editIsNew = true
                            editRow = mutableMapOf("_id" to nouvelleCle())
                        }) { Icon(Icons.Filled.Add, "Ajouter") }
                    }
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        /* barre de recherche raccourcie, avec les icones
                           (carte / structure / reglages) resserrees et collees
                           a sa droite - le logo occupe ainsi sa ligne entiere. */
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            /* Champ de recherche compact : hauteur reduite
                               (le cadre par defaut est trop haut), le texte
                               reste centre verticalement. */
                            OutlinedTextField(
                                value = query,
                                onValueChange = {
                                    query = it
                                    /* Toute saisie relance une recherche globale dans
                                       TOUT l'annuaire : on ferme la fiche ou le groupe
                                       ouvert pour afficher les resultats. */
                                    if (it.isNotBlank()) {
                                        openRow = null
                                        ongletFiche = null
                                        openGroup = null
                                    }
                                },
                                modifier = Modifier.weight(1f).height(56.dp),
                                placeholder = { Text("Rechercher...", fontSize = 15.sp) },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Filled.Search, null, tint = VertMenthe) },
                                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                                    fontSize = 15.sp
                                )
                            )
                            Spacer(Modifier.width(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        /* Fiche d'une \u00E9cole ouverte : afficher
                                           sur la carte la commune de cette \u00E9cole
                                           (la carte s'ouvre en vue circonscription,
                                           commune remplie). Sinon, ouverture
                                           normale de la carte. */
                                        val tbF = annuaire.tabs.firstOrNull {
                                            it.name.equals("Communes", ignoreCase = true)
                                        }
                                        val cleF = tbF?.groupBy
                                        if (tbF != null && cleF != null && openRow != null) {
                                            val nomC = openRow?.get(cleF) ?: ""
                                            if (nomC.isNotBlank()) {
                                                communeCarte = nomC
                                                /* L'\u00E9cole peut appartenir \u00E0 une autre
                                                   circonscription que le filtre actif : la
                                                   carte doit montrer SA circonscription. */
                                                val codeC = openRow?.let { circoDeLigne(it) } ?: ""
                                                if (codeC.isNotBlank() && codeC != filtre) {
                                                    filtre = codeC
                                                }
                                            }
                                        }
                                        showCarte = true
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Filled.Map, "Carte de l'Aube",
                                        tint = VertMenthe, modifier = Modifier.size(20.dp))
                                }
                                IconButton(
                                    onClick = { showSchema = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Filled.Science, "\u00C9diter la structure",
                                        tint = VertMenthe, modifier = Modifier.size(20.dp))
                                }
                                IconButton(
                                    onClick = { showSettings = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Filled.Settings, "R\u00E9glages",
                                        tint = VertMenthe, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        key(revision, filtre, onglet, query, favoriVersion) {
                            val og5 = ongletsDuFiltre(annuaire, filtre)
                            val t5 = if (og5.isEmpty()) null else og5[onglet.coerceIn(0, og5.size - 1)].second
                            val circoOuverte = circos.firstOrNull { it.code == filtre }
                            val tabCommunes = annuaire.tabs.firstOrNull { it.name.equals("Communes", ignoreCase = true) }
                            val fiche = openRow
                            val tabFiche = ongletFiche ?: tabCommunes
                            val grp = openGroup
                            if (fiche != null && tabFiche != null) {
                                val nomFiltre = if (filtre == "dsden") "DSDEN"
                                    else circos.firstOrNull { it.code == filtre }
                                        ?.let { nomCourt(it.nom) } ?: filtre
                                val groupeFiche = tabFiche.groupBy.takeIf { it.isNotBlank() }
                                    ?.let { fiche[it] ?: "" }
                                RowDetailScreen(
                                    tab = tabFiche,
                                    row = fiche,
                                    onBack = { openRow = null; ongletFiche = null },
                                    filAriane = listOfNotNull(
                                        nomFiltre,
                                        tabFiche.name,
                                        groupeFiche?.takeIf { it.isNotBlank() }
                                    ),
                                    favori = Stockage.getFavori(ctx) ==
                                        tabFiche.id + "|" + (fiche["_id"] ?: ""),
                                    onToggleFavori = {
                                        if (Stockage.getFavori(ctx) ==
                                            tabFiche.id + "|" + (fiche["_id"] ?: "")
                                        ) Stockage.clearFavori(ctx)
                                        else Stockage.setFavori(
                                            ctx, tabFiche.id, fiche["_id"] ?: ""
                                        )
                                        favoriVersion++
                                    },
                                    onEdit = {
                                        editTab = tabFiche
                                        editOriginal = fiche
                                        editIsNew = false
                                        editRow = LinkedHashMap(fiche)
                                    },
                                    onDelete = {
                                        tabFiche.rows.removeIf { it === fiche }
                                        openRow = null; ongletFiche = null
                                        saveAll()
                                        message = "Fiche supprim\u00E9e."
                                    }
                                )
                            } else if (grp != null) {
                                /* Groupe ouvert : onglet actif, ou l'onglet
                                   Communes quand la carte a ouvert une
                                   commune depuis la vue Ecoles. */
                                val tabGrp = t5 ?: tabCommunes
                                if (tabGrp != null) {
                                    GroupDetailScreen(
                                        tab = tabGrp,
                                        groupName = grp,
                                        rows = tabGrp.rows.filter { (it[tabGrp.groupBy] ?: "") == grp },
                                        onBack = { openGroup = null },
                                        onOpenRow = { ongletFiche = tabGrp; openRow = it }
                                    )
                                } else {
                                    Text("Aucun onglet \u00E0 afficher.", Modifier.padding(16.dp))
                                }
                            } else {
                                EcranAccueil(
                                    filtre = filtre,
                                    circos = circos,
                                    onFiltre = { filtre = it; onglet = 0; openGroup = null; openRow = null; ongletFiche = null },
                                    onglets = og5.map { it.first },
                                    ongletIndex = onglet.coerceIn(0, (og5.size - 1).coerceAtLeast(0)),
                                    onOnglet = { onglet = it; openGroup = null; openRow = null; ongletFiche = null },
                                    bas = {
                                        if (query.isNotBlank()) {
                                            /* Recherche globale : resultats dans TOUS les onglets.
                                               ouvrir un resultat bascule le filtre sur la
                                               circonscription (ou la DSDEN) d'ou vient la fiche -
                                               le fil d'Ariane vert et la barre de boutons
                                               retrouvent ainsi le chemin qui y a mene. */
                                            RechercheGlobale(annuaire, query) { tb, r ->
                                                ongletFiche = tb
                                                openRow = r
                                                filtre = when {
                                                    tb.id.startsWith("v5_dsden_") -> "dsden"
                                                    tb.id.startsWith("v5_circo_") ->
                                                        tb.id.split("_").getOrNull(2) ?: filtre
                                                    else -> circoDeLigne(r).ifBlank { filtre }
                                                }
                                                onglet = 0
                                            }
                                        } else when {
                                            t5 != null -> EcranOnglet(
                                                tab = t5, query = query,
                                                onOpenGroup = { name ->
                                                    val rows5 = t5.rows.filter { (it[t5.groupBy] ?: "") == name }
                                                    if (rows5.size == 1) openRow = rows5[0] else openGroup = name
                                                },
                                                onOpenRow = { ongletFiche = t5; openRow = it }
                                            )
                                            filtre == "dsden" -> Text("Aucun onglet DSDEN g\u00E9n\u00E9r\u00E9. Videz les donn\u00E9es de l'application puis relancez.", Modifier.padding(16.dp), color = Color(0xFFB3261E))
                                            tabCommunes != null -> {
                                                val lignes = tabCommunes.rows.filter { circoDeLigne(it) == filtre }
                                                /* Ecoles sans rattachement : elles restent
                                                   visibles, en fin de liste de chaque
                                                   circonscription, pour ne jamais perdre
                                                   une ecole importee. */
                                                val sansRattach = tabCommunes.rows.filter { circoDeLigne(it).isEmpty() }
                                                if (lignes.isEmpty() && sansRattach.isEmpty()) {
                                                    Text("Aucune \u00E9cole rattach\u00E9e \u00E0 cette circonscription.", Modifier.padding(16.dp))
                                                } else {
                                                    val parCommune = lignes.groupBy { it[tabCommunes.groupBy] ?: "(sans commune)" }.toSortedMap()
                                                    val communes = parCommune.map { it.key to it.value }.toMutableList()
                                                    if (sansRattach.isNotEmpty())
                                                        communes.add("\u00C9coles sans rattachement" to sansRattach)
                                                    ListeEcolesParCommune(
                                                        communes = communes,
                                                        titreEcole = { row -> titreDetail(tabCommunes, row) },
                                                        onOpenRow = { ongletFiche = tabCommunes; openRow = it }
                                                    )
                                                }
                                            }
                                            else -> Text("Aucune \u00E9cole rattach\u00E9e \u00E0 cette circonscription.", Modifier.padding(16.dp))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (showSettings) SettingsDialog(
                message = message,
                onDismiss = { showSettings = false; message = "" },
                onImport = {
                    showSettings = false
                    importer.launch(arrayOf(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.ms-excel",
                        "application/octet-stream"))
                },
                onImportFusion = {
                    showSettings = false
                    fusionneur.launch(arrayOf(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.ms-excel",
                        "application/octet-stream"))
                },
                /* Restauration : relit les donnees officielles embarquees
                   dans l'application et complete les onglets vides. */
                onRestaurer = {
                    showSettings = false
                    val n = restaurerContactsOfficiels(ctx, annuaire)
                    saveAll()
                    message = if (n > 0)
                        "Contacts officiels restaur\u00E9s : $n ligne(s) ajout\u00E9e(s)."
                    else
                        "Rien a restaurer : les contacts officiels sont d\u00E9j\u00E0 pr\u00E9sents."
                },
                onExport = { showSettings = false; exporter.launch("annuaire-v3.xlsx") },
                onTemplate = { showSettings = false; modeleur.launch("modele-annuaire-v3.xlsx") },
                onLogo = {
                    showSettings = false
                    logoPicker.launch(arrayOf("image/*"))
                },
                onLock = { showSettings = false; unlocked = false },
                onClear = {
                    annuaire = schemaParDefaut()
                    saveAll()
                    tabIndex = 0
                    message = "Donn\u00E9es effac\u00E9es (structure par d\u00E9faut)."
                },
                onChangePin = { old, new ->
                    if (Stockage.checkPin(ctx, old)) {
                        Stockage.setPin(ctx, new)
                        message = "Code PIN modifi\u00E9."
                    } else message = "Ancien code incorrect."
                }
            )
        }
    }
}

/* ==================== APPEL TELEPHONE (ACTION_DIAL, sans permission) ==================== */

/** Champ telephone ? (libelle contenant tel, phone, portable, mobile...) */
private fun estTel(label: String): Boolean {
    val l = norme(label)
    return l.contains("tel") || l.contains("phone") || l.contains("portable") || l.contains("mobile")
}

/** Ouvre le composeur avec le numero pre-rempli (aucune permission requise). */
private fun appelTelephone(ctx: android.content.Context, numero: String) {
    val nettoye = numero.filter { it.isDigit() || it == '+' }
    if (nettoye.isBlank()) return
    try {
        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + nettoye)))
    } catch (_: Exception) {
        /* pas de composeur disponible : on ignore silencieusement */
    }
}

/* ==================== STYLE : encadre vert menthe sur fond blanc ==================== */

private val VertMenthe = Color(0xFF3CB371)

/** Encadre vert menthe, fond blanc, texte taille 16 - utilise sur toutes les pages. */
@Composable
private fun Encadre(
    onClick: (() -> Unit)? = null,
    contenu: @Composable () -> Unit
) {
    val base = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)
    Surface(
        modifier = if (onClick != null) base.clickable(onClick = onClick) else base,
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(2.dp, VertMenthe)
    ) {
        Column(Modifier.padding(12.dp)) { contenu() }
    }
}

/* ==================== TITRES DE FICHES ==================== */

/** Titre d'une fiche : " Ecole " pour les communes, " Nom " pour les colleges,
    " Contact " pour la circonscription - sinon 1ere valeur du groupe. */
private fun titreDetail(tab: Onglet, row: Map<String, String>): String {
    for (p in listOf("\u00E9cole", "ecole", "nom")) {
        tab.fields.firstOrNull {
            it.key != tab.groupBy && norme(it.label).contains(p) &&
                !(row[it.key] ?: "").isBlank()
        }?.let { return row[it.key] ?: "" }
    }
    if (norme(tab.name).contains("circonscription")) {
        tab.fields.firstOrNull {
            norme(it.label).contains("contact") && !(row[it.key] ?: "").isBlank()
        }?.let { return row[it.key] ?: "" }
    }
    (row[tab.groupBy] ?: "").takeIf { it.isNotBlank() }?.let { return it }
    return titreLigne(tab, row)
}

/* ==================== NIVEAU 1 : LISTE D'UN ONGLET ==================== */

/** Champs identiques dans toutes les fiches du groupe (ex. maire, mairie).
    Seulement la section COMMUNE (champs places AVANT la colonne
    "\u00E9cole") : les champs d'\u00E9cole restent dans les blocs \u00E9coles
    en dessous, sans redondance. */
private fun champsConstants(tab: Onglet, rows: List<Map<String, String>>): List<Pair<String, String>> {
    val idxEcole = tab.fields.indexOfFirst { cleTexte(it.label) == "ecole" }
    val section = if (idxEcole >= 0) tab.fields.subList(0, idxEcole).toList() else tab.fields
    return section.mapNotNull { f ->
        if (f.key == tab.groupBy) null
        else {
            val v = rows.first()[f.key] ?: ""
            if (v.isNotBlank() && rows.all { (it[f.key] ?: "") == v }) f.label to v else null
        }
    }
}

/** Valeurs qui changent d'une fiche a l'autre dans le groupe (ex. ecole). */
private fun valeursVariables(tab: Onglet, rows: List<Map<String, String>>, row: Map<String, String>): List<String> =
    tab.fields.mapNotNull { f ->
        if (f.key == tab.groupBy) null
        else {
            val v = row[f.key] ?: ""
            val varie = rows.map { it[f.key] ?: "" }.distinct().size > 1
            if (v.isNotBlank() && varie) v else null
        }
    }

/** Titre d'une fiche du groupe : le nom de l'\u00E9cole si la colonne
    \u00E9cole existe (jamais l'adresse), sinon la 1re valeur variable. */
private fun titreDansGroupe(tab: Onglet, rows: List<Map<String, String>>, row: Map<String, String>): String {
    /* Colonne \u00E9cole retrouvee par son libell\u00E9, comparaison tol\u00E9rante. */
    val keyEcole = tab.fields.firstOrNull { cleTexte(it.label) == "ecole" }?.key
    if (keyEcole != null) {
        val e = row[keyEcole] ?: ""
        if (e.isNotBlank()) return e
    }
    return valeursVariables(tab, rows, row).firstOrNull()
        ?: tab.fields.firstNotNullOfOrNull { f ->
            if (f.key != tab.groupBy) (row[f.key] ?: "").takeIf { it.isNotBlank() } else null
        }
        ?: "(d\u00E9tails)"
}

/** Sous-titre d'une fiche du groupe : les 2 valeurs variables suivantes,
    SANS l'adresse ni le nom d'\u00E9cole (d\u00E9j\u00E0 le titre). */
private fun sousTitreDansGroupe(tab: Onglet, rows: List<Map<String, String>>, row: Map<String, String>): String {
    val keyEcole = tab.fields.firstOrNull { cleTexte(it.label) == "ecole" }?.key
    val vals = valeursVariables(tab, rows, row)
        .filterIndexed { i, _ -> true }
        .toMutableList()
    /* Retirer le titre deja affiche au-dessus : le nom d'ecole. */
    if (keyEcole != null) {
        val e = row[keyEcole] ?: ""
        if (e.isNotBlank()) vals.remove(e)
    }
    return if (vals.size > 1) vals.drop(1).take(2).joinToString(" \u00B7 ") else ""
}

/** Liste : noms seulement (communes, colleges) ou personnes (circonscription). */
@Composable
private fun EcranOnglet(
    tab: Onglet,
    query: String,
    onOpenGroup: (String) -> Unit,
    onOpenRow: (MutableMap<String, String>) -> Unit
) {
    val q = norme(query)
    val filtered = if (q.isBlank()) tab.rows
    else tab.rows.filter { row -> row.values.any { norme(it).contains(q) } }

    /* Champ " Circonscription elargie " eventuel */
    val keyEl = tab.fields.firstOrNull { norme(it.label).contains("elargie") }?.key
    val normales = if (keyEl != null) filtered.filter { norme(it[keyEl] ?: "") != "oui" } else filtered
    val elargies = if (keyEl != null) filtered.filter { norme(it[keyEl] ?: "") == "oui" } else emptyList()
    var elargieOuvert by remember(tab.id) { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().padding(vertical = 4.dp)) {
        /* Bascule " Circonscription elargie (PIAL-ER) " */
        if (keyEl != null && elargies.isNotEmpty()) {
            item(key = "elargie_titre") {
                Encadre(onClick = { elargieOuvert = !elargieOuvert }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (elargieOuvert) Icons.Filled.ExpandMore else Icons.Filled.Add,
                            null, tint = VertMenthe
                        )
                        Text(
                            "Circonscription \u00E9largie  (${elargies.size})",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = VertMenthe
                        )
                    }
                }
            }
            if (elargieOuvert) items(elargies, key = { it["_id"] ?: it.hashCode().toString() }) { row ->
                Encadre(onClick = { onOpenRow(row) }) {
                    Text(titreDetail(tab, row), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    val sub = sousTitreLigne(tab, row)
                    if (sub.isNotBlank()) Text(sub, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (tab.groupBy.isNotBlank() && normales.isNotEmpty()) {
            /* Liste des noms de groupes : communes, colleges... */
            val groups = normales.groupBy { it[tab.groupBy] ?: "" }
                .map { (k, v) -> k.ifBlank { "(sans titre)" } to v.size }
                .sortedBy { norme(it.first) }
            items(groups, key = { it.first }) { (name, count) ->
                Encadre(onClick = { onOpenGroup(name) }) {
                    Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("$count fiche(s)", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            /* Liste a plat : les personnes de la circonscription... */
            items(normales, key = { it["_id"] ?: it.hashCode().toString() }) { row ->
                Encadre(onClick = { onOpenRow(row) }) {
                    Text(titreDetail(tab, row), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    val sub = sousTitreLigne(tab, row)
                    if (sub.isNotBlank()) Text(sub, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (filtered.isEmpty()) item {
            Text(
                if (query.isBlank()) "Aucune fiche. Touchez \u2795 pour en ajouter."
                else "Aucun r\u00E9sultat.",
                Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/* ==================== NIVEAU 2 : DETAIL D'UN GROUPE (ex. une commune) ==================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupDetailScreen(
    tab: Onglet,
    groupName: String,
    rows: List<MutableMap<String, String>>,
    onBack: () -> Unit,
    onOpenRow: (MutableMap<String, String>) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        groupName,
                        fontWeight = FontWeight.Bold,
                        color = VertMenthe
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Retour") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(vertical = 4.dp)
        ) {
            /* Infos du groupe : maire, adresse mairie... */
            if (rows.isNotEmpty()) item(key = "infos") {
                Encadre {
                    champsConstants(tab, rows).forEach { (label, value) ->
                        Text(label, fontSize = 11.sp, color = VertMenthe)
                        if (estAdresse(label)) {
                            /* Adresse du groupe (mairie...) : itineraire Maps */
                            LigneAdresse(value)
                        } else Text(value, fontSize = 15.sp)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
            /* Les fiches du groupe : les ecoles de CETTE commune... */
            items(rows, key = { it["_id"] ?: it.hashCode().toString() }) { row ->
                Encadre(onClick = { onOpenRow(row) }) {
                    Text(titreDansGroupe(tab, rows, row), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    val sub = sousTitreDansGroupe(tab, rows, row)
                    if (sub.isNotBlank()) Text(sub, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/* ==================== NIVEAU 3 : DETAIL D'UNE FICHE (ecole, college, personne) ==================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowDetailScreen(
    tab: Onglet,
    row: MutableMap<String, String>,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    /* fil d'Ariane - les boutons verts qui ont mene a cette fiche */
    filAriane: List<String> = emptyList(),
    /* favori - l'etoile affiche cette fiche par defaut au demarrage */
    favori: Boolean = false,
    onToggleFavori: (() -> Unit)? = null
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        titreDetail(tab, row),
                        fontWeight = FontWeight.Bold,
                        color = VertMenthe
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Retour") }
                },
                actions = {
                    if (onToggleFavori != null) IconButton(onClick = onToggleFavori) {
                        Icon(
                            if (favori) Icons.Filled.Star else Icons.Filled.StarBorder,
                            if (favori) "Retirer des favoris" else "Ajouter aux favoris",
                            tint = if (favori) Color(0xFFFFB300) else VertMenthe
                        )
                    }
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Modifier") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(12.dp)
        ) {
            /* Fil d'Ariane : rappel du chemin en boutons verts */
            if (filAriane.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filAriane.forEach { n ->
                        Text(
                            n,
                            Modifier
                                .background(VertMenthe, RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            color = Color.White, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            /* Deux blocs separes : d'abord la section COMMUNE, c'est-a-dire
               les champs places avant la colonne ecole ; puis la section
               ECOLE, de la colonne ecole jusqu'a la fin. Sans colonne
               ecole, un seul bloc comme avant. */
            val idxEcole = tab.fields.indexOfFirst { cleTexte(it.label) == "ecole" }
            @Composable
            fun BlocChamps(champs: List<Champ>, titreBloc: String = "") {
                Encadre {
                    if (titreBloc.isNotBlank()) {
                        Text(titreBloc, fontSize = 13.sp, color = VertMenthe,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                    }
                    var auMoinsUn = false
                    champs.forEach { f ->
                        /* Colonne " Circonscription " : technique (rattachement
                           a une circo), jamais affichee dans la fiche. */
                        if (norme(f.label) == "circonscription") return@forEach
                        val v = row[f.key] ?: ""
                        if (v.isNotBlank()) {
                            auMoinsUn = true
                            Text(f.label, fontSize = 11.sp, color = VertMenthe)
                            if (estAdresse(f.label)) {
                                /* Ligne adresse : toucher = itineraire Google Maps
                                   depuis la position actuelle (voiture, ou a pied
                                   si moins de 5 km) */
                                LigneAdresse(v)
                            } else if (estTel(f.label)) {
                                /* Ligne telephone : toucher = composeur pre-rempli */
                                val ctxF = LocalContext.current
                                Row(
                                    Modifier.clickable { appelTelephone(ctxF, v) },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Call, "Appeler", tint = VertMenthe)
                                    Spacer(Modifier.width(6.dp))
                                    Text(v, fontSize = 16.sp, color = VertMenthe,
                                        fontWeight = FontWeight.SemiBold)
                                }
                            } else Text(v, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    if (!auMoinsUn) Text("(fiche vide)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (idxEcole >= 0) {
                BlocChamps(tab.fields.subList(0, idxEcole), "Commune")
                Spacer(Modifier.height(12.dp))
                BlocChamps(tab.fields.subList(idxEcole, tab.fields.size), "\u00C9cole")
            } else {
                BlocChamps(tab.fields)
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { confirmDelete = true }) {
                Text("Supprimer cette fiche", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmDelete) ConfirmDialog(
        "Supprimer cette fiche ?",
        onDismiss = { confirmDelete = false }
    ) {
        confirmDelete = false
        onDelete()
    }
}

/* ==================== RECHERCHE GLOBALE (tout l'annuaire) ==================== */

/** Des qu'on tape dans le champ du haut : resultats tires de TOUS les
    onglets (DSDEN, circonscriptions, communes, ecoles, colleges...). */
@Composable
private fun RechercheGlobale(
    annuaire: Annuaire,
    query: String,
    onOpen: (Onglet, MutableMap<String, String>) -> Unit
) {
    val q = norme(query)
    val resultats = remember(annuaire, q) {
        annuaire.tabs.flatMap { tb ->
            tb.rows.filter { r -> r.values.any { norme(it).contains(q) } }
                .map { tb to it }
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(vertical = 4.dp)) {
        if (resultats.isEmpty()) {
            item {
                Text(
                    "Aucun r\u00E9sultat pour \u00AB ${query} \u00BB.",
                    Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            item {
                Text(
                    "${resultats.size} r\u00E9sultat(s)",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = VertMenthe, fontWeight = FontWeight.Bold
                )
            }
            items(resultats, key = { it.second["_id"] ?: it.second.hashCode().toString() }) { (tb, r) ->
                Encadre(onClick = { onOpen(tb, r) }) {
                    Text(titreDetail(tb, r), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(tb.name, fontSize = 12.sp, color = Color(0xFF666666))
                }
            }
        }
    }
}

/* ==================== BOITE DE DIALOGUE : editer une ligne ==================== */

@Composable
private fun EntityDialog(
    tab: Onglet,
    values: MutableMap<String, String>,
    isNew: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val scroll = rememberScrollState()

    /* la saisie passe par des etats Compose - sans cela, l'ecriture
       directe dans la Map ne declenche aucun rafraichissement et le
       clavier semble ne rien saisir. */
    val saisies = remember(tab, values) {
        tab.fields.associate { f -> f.key to mutableStateOf(values[f.key] ?: "") }
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                if (isNew) "Nouvelle ligne - ${tab.name}" else titreLigne(tab, values),
                color = VertMenthe
            )
        },
        text = {
            Column(Modifier.verticalScroll(scroll)) {
                tab.fields.forEach { f ->
                    val saisie = saisies[f.key] ?: return@forEach
                    OutlinedTextField(
                        value = saisie.value,
                        onValueChange = { saisie.value = it },
                        label = { Text(f.label) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        minLines = if (f.multiline) 2 else 1,
                        maxLines = if (f.multiline) 4 else 1
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                /* Recopier la saisie dans la fiche, puis enregistrer */
                tab.fields.forEach { f -> values[f.key] = saisies[f.key]?.value ?: "" }
                onSave()
            }) { Text("Enregistrer") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onCancel) { Text("Annuler") }
            }
        }
    )
}

/* ==================== EDITEUR DE STRUCTURE (onglets + champs) ==================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchemaEditorScreen(
    annuaire: Annuaire,
    onDone: () -> Unit,
    onRestore: () -> Unit
) {
    var selected by remember { mutableStateOf<String?>(null) }
    var renameTab by remember { mutableStateOf<Onglet?>(null) }
    var addTab by remember { mutableStateOf(false) }
    var delTab by remember { mutableStateOf<Onglet?>(null) }
    var renameField by remember { mutableStateOf<Pair<Onglet, Champ>?>(null) }
    var addField by remember { mutableStateOf<Onglet?>(null) }
    var delField by remember { mutableStateOf<Pair<Onglet, Champ>?>(null) }
    var pickGroup by remember { mutableStateOf<Onglet?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }

    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    "Structure",
                    fontWeight = FontWeight.Bold,
                    color = VertMenthe
                )
            },
            navigationIcon = {
                IconButton(onClick = onDone) { Icon(Icons.Filled.Check, "Termin\u00E9") }
            },
            actions = {
                TextButton(onClick = { confirmRestore = true }) {
                    Text("D\u00E9fauts", color = MaterialTheme.colorScheme.error)
                }
            }
        )
        HorizontalDivider()
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp)) {
            Text("Onglets", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            annuaire.tabs.forEach { t ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { selected = if (selected == t.id) null else t.id }) {
                        Text(
                            (if (selected == t.id) "\u25BE " else "\u25B8 ") + t.name +
                                "  (${t.rows.size} lignes, ${t.fields.size} champs)",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { renameTab = t }) { Icon(Icons.Filled.Edit, "Renommer") }
                    IconButton(onClick = { delTab = t }) {
                        Icon(Icons.Filled.Delete, "Supprimer",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
                if (selected == t.id) {
                    /* --- Editeur des champs de l'onglet --- */
                    Surface(
                        tonalElevation = 1.dp,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 8.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Champs de \u00AB ${t.name} \u00BB", fontWeight = FontWeight.SemiBold)
                            /* groupBy */
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("Regrouper par : ", fontSize = 13.sp)
                                TextButton(onClick = { pickGroup = t }) {
                                    Text(t.groupBy.ifBlank {
                                        "(aucun)"
                                    }.let { key ->
                                        t.fields.firstOrNull { f -> f.key == key }?.label ?: "(aucun)"
                                    })
                                }
                            }
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            t.fields.forEachIndexed { i, f ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${i + 1}. ${f.label}" + if (f.multiline) "  \u00B6" else "",
                                        fontSize = 14.sp, modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = {
                                        if (i > 0) {
                                            val m = t.fields[i - 1]; t.fields[i - 1] = f; t.fields[i] = m
                                        }
                                    }) { Icon(Icons.Filled.KeyboardArrowUp, "Monter") }
                                    IconButton(onClick = {
                                        if (i < t.fields.size - 1) {
                                            val m = t.fields[i + 1]; t.fields[i + 1] = f; t.fields[i] = m
                                        }
                                    }) { Icon(Icons.Filled.KeyboardArrowDown, "Descendre") }
                                    IconButton(onClick = { f.multiline = !f.multiline }) {
                                        Icon(
                                            if (f.multiline) Icons.Filled.Notes else Icons.Filled.ShortText,
                                            "Multi-ligne"
                                        )
                                    }
                                    IconButton(onClick = { renameField = t to f }) {
                                        Icon(Icons.Filled.Edit, "Renommer")
                                    }
                                    IconButton(onClick = { delField = t to f }) {
                                        Icon(Icons.Filled.Delete, "Supprimer",
                                            tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            OutlinedButton(onClick = { addField = t }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Ajouter un champ")
                            }
                        }
                    }
                }
            }
            Button(onClick = { addTab = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Ajouter un onglet")
            }
            Text(
                "\u00B6 = champ multi-lignes. Les suppressions de champs/onglets " +
                    "n'effacent que la structure : les valeurs concern\u00E9es sont perdues.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }

    /* --- Petites boites de saisie --- */
    if (renameTab != null) TextPromptDialog(
        title = "Renommer l'onglet", initial = renameTab!!.name
    ) { s ->
        if (s.isNotBlank()) renameTab!!.name = s
        renameTab = null
    }
    if (addTab) TextPromptDialog(title = "Nom du nouvel onglet", initial = "") { s ->
        if (s.isNotBlank()) {
            val t = Onglet(nouvelleCle(), s)
            annuaire.tabs.add(t)
            selected = t.id
        }
        addTab = false
    }
    if (delTab != null) ConfirmDialog(
        "Supprimer l'onglet \u00AB ${delTab!!.name} \u00BB et ses ${delTab!!.rows.size} ligne(s) ?",
        onDismiss = { delTab = null }
    ) {
        annuaire.tabs.remove(delTab!!)
        if (selected == delTab!!.id) selected = null
        delTab = null
    }
    if (renameField != null) TextPromptDialog(
        title = "Renommer le champ", initial = renameField!!.second.label
    ) { s ->
        if (s.isNotBlank()) renameField!!.second.label = s
        renameField = null
    }
    if (addField != null) TextPromptDialog(
        title = "Nom du nouveau champ", initial = ""
    ) { s ->
        if (s.isNotBlank()) addField!!.fields.add(
            Champ(nouvelleCle(), s, multiline = s.contains("Observation")
                || s.contains("Adresse") || s.contains("Horaires"))
        )
        addField = null
    }
    if (delField != null) ConfirmDialog(
        "Supprimer le champ \u00AB ${delField!!.second.label} \u00BB ?\n" +
            "Les valeurs de ce colonne seront perdues.",
        onDismiss = { delField = null }
    ) {
        val (t, f) = delField!!
        t.fields.remove(f)
        if (t.groupBy == f.key) t.groupBy = ""
        delField = null
    }
    if (pickGroup != null) {
        val t = pickGroup!!
        AlertDialog(
            onDismissRequest = { pickGroup = null },
            title = { Text("Regrouper les lignes par", color = VertMenthe) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { t.groupBy = ""; pickGroup = null }) {
                        Text("(aucun regroupement)")
                    }
                    t.fields.forEach { f ->
                        TextButton(onClick = { t.groupBy = f.key; pickGroup = null }) {
                            Text(f.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickGroup = null }) { Text("Fermer") } }
        )
    }
    if (confirmRestore) ConfirmDialog(
        "Restaurer les 5 onglets par d\u00E9faut ?\nTOUTES les donn\u00E9es et la structure actuelles seront effac\u00E9es.",
        onDismiss = { confirmRestore = false }
    ) {
        confirmRestore = false
        onRestore()
    }
}

@Composable
private fun TextPromptDialog(title: String, initial: String, onOk: (String) -> Unit) {
    var s by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onOk("") },
        title = { Text(title, color = VertMenthe) },
        text = {
            OutlinedTextField(
                value = s, onValueChange = { s = it },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onOk(s) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = { onOk("") }) { Text("Annuler") } }
    )
}

/** Boite de confirmation : le 2e parametre ferme la boite (annuler),
 *  le 3e (trailing lambda) execute l'action puis ferme. */
@Composable
private fun ConfirmDialog(
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirmer", color = VertMenthe) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Confirmer", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

/* ==================== ECRANS PIN ==================== */

@Composable
private fun PinDefineScreen(onDone: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
) {
        Text(
            "D\u00E9finissez votre code PIN",
            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = VertMenthe
        )
        Spacer(Modifier.height(16.dp))
        Encadre {
            OutlinedTextField(
                value = pin, onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
                label = { Text("Code PIN") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = confirm, onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) confirm = it },
                label = { Text("Confirmation") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                isError = confirm.isNotBlank() && confirm != pin,
                modifier = Modifier.fillMaxWidth()
            )
            if (confirm.isNotBlank() && confirm != pin)
                Text("Les codes ne correspondent pas.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { if (pin.length >= 4 && pin == confirm) onDone(pin) },
                enabled = pin.length >= 4 && pin == confirm,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Valider") }
        }
    }
}

@Composable
private fun PinEnterScreen(onSuccess: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
) {
        Icon(Icons.Filled.Lock, null, Modifier.size(48.dp), tint = VertMenthe)
        Spacer(Modifier.height(8.dp))
        Text("Entrez votre code PIN", fontSize = 16.sp, color = VertMenthe)
        Spacer(Modifier.height(16.dp))
        Encadre {
            OutlinedTextField(
                value = pin, onValueChange = { pin = it },
                label = { Text("Code PIN") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { if (pin.isNotBlank()) onSuccess(pin) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("D\u00E9verrouiller") }
        }
    }
}

/* ==================== PANNEAU REGLAGES ==================== */

@Composable
private fun SettingsDialog(
    message: String,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
    onImportFusion: () -> Unit,
    onRestaurer: () -> Unit,
    onExport: () -> Unit,
    onTemplate: () -> Unit,
    onLogo: () -> Unit,
    onLock: () -> Unit,
    onClear: () -> Unit,
    onChangePin: (String, String) -> Unit
) {
    var showPinForm by remember { mutableStateOf(false) }
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("R\u00E9glages", color = VertMenthe) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Button(onClick = onImport, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(Icons.Filled.UploadFile, null); Spacer(Modifier.width(8.dp)); Text("Importer un fichier Excel")
                }
                Button(onClick = onImportFusion, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(Icons.Filled.MergeType, null); Spacer(Modifier.width(8.dp)); Text("Importer et fusionner (sans effacer)")
                }
                Button(onClick = onRestaurer, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(Icons.Filled.Restore, null); Spacer(Modifier.width(8.dp)); Text("Restaurer les contacts officiels")
                }
                Button(onClick = onExport, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(Icons.Filled.Download, null); Spacer(Modifier.width(8.dp)); Text("Exporter vers Excel")
                }
                Button(onClick = onTemplate, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(Icons.Filled.Description, null); Spacer(Modifier.width(8.dp)); Text("Cr\u00E9er le mod\u00E8le d'import")
                }
                OutlinedButton(onClick = onLogo, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(Icons.Filled.Image, null); Spacer(Modifier.width(8.dp)); Text("Choisir le logo (image)")
                }
                Text(
                    "Astuce : l'import reconstruit onglets et champs d'apr\u00E8s les en-t\u00EAtes du fichier.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                OutlinedButton(onClick = { showPinForm = !showPinForm }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(Icons.Filled.Password, null); Spacer(Modifier.width(8.dp)); Text("Changer le code PIN")
                }
                if (showPinForm) {
                    OutlinedTextField(
                        value = oldPin, onValueChange = { oldPin = it },
                        label = { Text("Ancien code") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                    OutlinedTextField(
                        value = newPin, onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) newPin = it },
                        label = { Text("Nouveau code (4 chiffres min.)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                    Button(
                        onClick = { onChangePin(oldPin, newPin); oldPin = ""; newPin = "" },
                        enabled = newPin.length >= 4,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Enregistrer le nouveau code") }
                }

                OutlinedButton(onClick = onLock, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(Icons.Filled.Lock, null); Spacer(Modifier.width(8.dp)); Text("Verrouiller maintenant")
                }
                TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                    Text("Tout effacer", color = MaterialTheme.colorScheme.error)
                }
                if (message.isNotBlank())
                    Text(message, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
}
