package com.minelab.game

import kotlin.random.Random

/**
 * LE DONJON (organise comme un vrai donjon, pour la future carte) :
 *
 *  ETAGE 0
 *   [1] GRANDE SALLE : champ de mines (demineur). Coeurs caches.
 *   [2] SALLE DU COFFRE : 3 dalles a mini-demineur + 3 blocs -> coffre -> CLE
 *        --- PORTE A CLE --->
 *   [3] SALLE DE RANGEMENT : sokoban 1 (4 caisses) -> TRAPPE + coffre du JOYSTICK
 *
 *  SOUS-SOL (par la trappe)
 *   [4] COULOIR
 *   [5] SALLE DES COULEURS : enigme Simon -> coffre-fort (EPEE) + 2 portes
 *        - PORTE SUD : scellee (une cle a trouver plus tard)
 *        - PORTE EST --->
 *   [6] SALLE DES TORCHES : prendre le BRIQUET au centre, allumer les 4 torches,
 *        des caisses apparaissent -> sokoban 2 (5 caisses, plus dur)
 *        -> une PORTE apparait, gardee par 2 MONSTRES
 *   [7] SALLE DE L'ETOILE : victoire
 */
class World(
    val hallW: Int = 16,
    val hallH: Int = 16,
    val seed: Long = System.currentTimeMillis(),
    val density: Double = 0.15
) {

    companion object {
        const val WALL = 0
        const val FLOOR = 1
        const val DOOR = 2

        // Terrains de la surface (0 = donjon)
        const val TER_NONE = 0
        const val TER_GRASS = 1
        const val TER_EARTH = 2
        const val TER_DIRT = 3
        const val TER_SAND = 4
        const val TER_SHORE = 5
        const val TER_SHALLOW = 6
        const val TER_WATER = 7
        const val TER_WOOD = 8      // parquet des maisons
    }

    private val rnd = Random(seed)

    val wid = maxOf(hallW + 24, 40)
    val uy0 = maxOf(hallH, 11) + 3
    val iy0 = uy0 + 24          // premiere ligne de l'ile
    val hy0 = iy0 + 58          // premiere ligne des interieurs de maison (sous la mer du sud)
    val hei = hy0 + 40

    val grid = IntArray(wid * hei) { WALL }
    /** Terrain de surface (0 = donjon, sinon herbe/sable/mer...). */
    val terrain = IntArray(wid * hei) { TER_NONE }

    /** L'ile : maisons (case -> numero de sprite) et portail d'arrivee. */
    val houses = HashMap<Int, Int>()
    /** Le paillasson devant chaque maison : case -> numero de maison (1..10). */
    val houseMats = HashMap<Int, Int>()
    /** Toutes les cases occupees par chaque batiment. */
    val houseBody = HashMap<Int, Int>()
    /** Interieurs : numero de maison -> case d'arrivee ; et porte de sortie -> maison. */
    val houseEntry = HashMap<Int, Int>()
    val houseExit = HashMap<Int, Int>()
    /** Meubles poses : case -> numero de prop (1..90). */
    val props = HashMap<Int, Int>()
    /** Decor de l'ile : case -> (type, numero). type 0=arbre 1=plante 2=roche 3=bateau */
    val decor = HashMap<Int, Pair<Int, Int>>()
    /** Sol de chaque maison (1..12). */
    val houseFloor = HashMap<Int, Int>()
    /** Objets fixes d'interieur : case -> code (1 fenetre, 2 cheminee, 3 scene, 4 tapis rouge, 5 tapis punk). */
    val fixtures = HashMap<Int, Int>()
    /** Graffitis sur les murs : case -> numero de tag (1..15). */
    val tags = HashMap<Int, Int>()
    /** Distributeurs de boisson : case -> 1 (dore) ou 2 (bleu). */
    val vendors = HashMap<Int, Int>()
    /** La bombe de peinture, cachee dans le squat. */
    var sprayCell = -1
    var sprayTaken = false
    /** Les champignons de Kaos, dans le squat aussi. */
    var shroomCell = -1
    var shroomTaken = false
    /** L'entree cachee du prochain donjon (revelee par le champignon). */
    var dungeon2Cell = -1
    var dungeon2Revealed = false
    /** Villageois et animaux : case de depart -> numero de sprite. */
    val npcSpawns = ArrayList<Pair<Int, Int>>()
    val punkSpawns = ArrayList<Pair<Int, Int>>()
    /** Les 10 heros de la GUILDE, dans le grand hall. */
    val guildSpawns = ArrayList<Pair<Int, Int>>()
    /** Maitre Zephyrin, le magicien du Grand Arbre. */
    var mageCell = -1
    /** Le tavernier (1) et ses clients (2..7), dans la taverne. */
    val tavernCells = ArrayList<Pair<Int, Int>>()
    /** Les 2 marchands ambulants (1=nomade, 2=jolie) et leur stand. */
    val merchantCells = ArrayList<Pair<Int, Int>>()
    val stallCells = HashMap<Int, Int>()   // case du stand -> id marchand (rendu facade)
    var pierreCell = -1
    var frankiCell = -1
    /** La case de mer ou flotte le slip porte-bonheur de Pierre. */
    var slipCell = -1
    /** La case de mer ou la barque attend, pres de la plage sud. */
    var boatCell = -1
    val petSpawns = ArrayList<Pair<Int, Int>>()
    var islandPortal = -1
    var islandVisited = false
    private val villageCx = wid / 2
    private var villageCy = 0

    val layout = HashSet<Int>()
    val mines = HashSet<Int>()
    val revealed = HashSet<Int>()
    val flagged = HashSet<Int>()
    val exploded = HashSet<Int>()
    val defused = HashSet<Int>()
    val hearts = HashSet<Int>()

    val blocks = HashSet<Int>()
    val blocksInit = HashSet<Int>()
    private val blocksInit0 = HashSet<Int>()
    val plates = HashSet<Int>()
    val plateSolved = HashSet<Int>()
    val targets = HashSet<Int>()        // sokoban 1
    val targets2 = HashSet<Int>()       // sokoban 2
    private val crates2 = ArrayList<Int>()
    private val roomB = ArrayList<Int>()
    private val roomTorch = ArrayList<Int>()

    var chest = -1
    var chestOpen = false
    var hasKey = false
    var door = -1
    var trapOpen = false
    // --- LES CACHES SECRETES : trappe cachee -> antichambre -> coffre d'or ---
    /** cellule declencheur (dalle un peu suspecte) -> premiere case de l'antichambre. */
    val secretTraps = HashMap<Int, Int>()
    /** antichambre -> (coffre, liste de cases-monstres). */
    val secretChests = HashMap<Int, Int>()
    val secretMonsters = HashMap<Int, ArrayList<Int>>()
    /** toutes les cases d'antichambre (rendu / passage). */
    val secretRooms = HashMap<Int, Int>()   // case -> id de la cache
    var chest2 = -1
    var chest2Spawned = false
    var chest2Open = false

    // Sous-sol
    var altar = -1
    val simonTiles = IntArray(4)
    val simonSeq = ArrayList<Int>()
    var simonSolved = false
    var chest3 = -1
    var chest3Spawned = false
    var chest3Open = false
    var door1 = -1                      // salle des couleurs -> salle des torches
    var door2 = -1                      // scellee (cle future)
    var door3 = -1                      // salle des torches -> etoile
    var door3Spawned = false
    var door3Open = false

    // Salle des torches
    // Coffre-fort : sudoku 4x4
    val sudokuGiven = IntArray(16)
    val sudokuSol = IntArray(16)
    val sudokuUser = IntArray(16)
    var sudokuSolved = false

    // Porte a runes : lights out 3x3
    var runeDoor = -1
    val lights = BooleanArray(9)
    var lightsSolved = false

    // Salle du boss
    var wave = 0
    var bossDefeated = false
    val bossSpawns = ArrayList<Int>()
    private val roomBoss = ArrayList<Int>()
    private val corridor2 = ArrayList<Int>()

    // Teleportation (apparait apres le boss)
    var teleportActive = false

    var lighter = -1
    var lighterTaken = false
    val torches = IntArray(4)
    val torchLit = HashSet<Int>()
    var sokoban2Spawned = false
    var mobsSpawned = false
    var mobsDead = false
    val mobSpawn = IntArray(2)

    var startX = 1
    var startY = 1
    var undergroundStartX = 0
    var undergroundStartY = 0
    var trapX = 0
    var trapY = 0
    var exitX = 0
    var exitY = 0
    var totalMines = 0

    private val ax = hallW + 2
    private val bx = hallW + 12
    private val tx0 = 22
    private val ty0 = uy0 + 1

    fun idx(x: Int, y: Int) = y * wid + x
    fun cx(i: Int) = i % wid
    fun cy(i: Int) = i / wid
    fun inside(x: Int, y: Int) = x in 0 until wid && y in 0 until hei
    fun isFloor(x: Int, y: Int) = inside(x, y) && grid[idx(x, y)] == FLOOR
    fun isDoor(x: Int, y: Int) = inside(x, y) && grid[idx(x, y)] == DOOR

    init {
        buildHall()
        buildChestRoom()
        buildStorageRoom()
        buildUnderground()
        buildTorchRoom()
        buildCorridor2()
        buildSudoku()
        buildLights()
        buildSecretCaches()
        buildIsland()
        placeMines()
        placeHearts()
        totalMines = mines.size
        blocksInit.addAll(blocks)
        blocksInit0.addAll(blocks)
        revealCascade(startX, startY)
    }

    // ------------------------------------------------------------ etage 0

    private fun buildHall() {
        for (y in 1..hallH) for (x in 1..hallW) grid[idx(x, y)] = FLOOR
        grid[idx(hallW + 1, 5)] = FLOOR
    }

    private fun buildChestRoom() {
        for (y in 1..11) for (x in ax..ax + 8) grid[idx(x, y)] = FLOOR
        plates.add(idx(ax + 1, 2))
        plates.add(idx(ax + 7, 2))
        plates.add(idx(ax + 1, 9))
        blocks.add(idx(ax + 3, 5))
        blocks.add(idx(ax + 5, 5))
        blocks.add(idx(ax + 4, 7))
        chest = idx(ax + 7, 9)
        door = idx(ax + 9, 6)
        grid[door] = DOOR
        for (y in 1..11) for (x in ax..ax + 8) revealed.add(idx(x, y))
        revealed.add(idx(hallW + 1, 5))
    }

    private fun buildStorageRoom() {
        for (y in 1..11) for (x in bx..bx + 10) grid[idx(x, y)] = FLOOR
        grid[idx(bx, 1)] = WALL
        grid[idx(bx + 9, 1)] = WALL
        grid[idx(bx + 10, 1)] = WALL
        grid[idx(bx, 2)] = WALL
        for (x in bx + 1..bx + 4) grid[idx(x, 2)] = WALL
        grid[idx(bx + 6, 2)] = WALL
        grid[idx(bx + 7, 2)] = WALL
        grid[idx(bx + 9, 2)] = WALL
        grid[idx(bx + 10, 2)] = WALL
        grid[idx(bx + 4, 5)] = WALL
        grid[idx(bx + 6, 5)] = WALL
        grid[idx(bx + 2, 7)] = WALL
        grid[idx(bx + 8, 8)] = WALL

        for (k in 1..4) targets.add(idx(bx + k, 1))
        blocks.add(idx(bx + 5, 4))
        blocks.add(idx(bx + 3, 6))
        blocks.add(idx(bx + 7, 7))
        blocks.add(idx(bx + 5, 10))

        trapX = bx + 10
        trapY = 11
        chest2 = idx(bx + 1, 10)
        for (y in 1..11) for (x in bx..bx + 10) if (isFloor(x, y)) roomB.add(idx(x, y))
    }

    fun revealRoomB() = revealed.addAll(roomB)

    // ------------------------------------------------------------ sous-sol

    private fun buildUnderground() {
        val cy0 = uy0 + 5
        for (x in 3..11) grid[idx(x, cy0)] = FLOOR
        undergroundStartX = 3
        undergroundStartY = cy0

        for (y in uy0 + 1..uy0 + 9) for (x in 12..20) grid[idx(x, y)] = FLOOR

        altar = idx(16, uy0 + 5)
        simonTiles[0] = idx(16, uy0 + 2)
        simonTiles[1] = idx(13, uy0 + 5)
        simonTiles[2] = idx(19, uy0 + 5)
        simonTiles[3] = idx(16, uy0 + 8)
        repeat(4) { simonSeq.add(rnd.nextInt(4)) }

        chest3 = idx(14, uy0 + 2)
        door1 = idx(21, uy0 + 5)
        door2 = idx(16, uy0 + 10)       // scellee, pour plus tard
        grid[door2] = DOOR

        // Le sous-sol est eclaire : couloir + salle des couleurs deja decouverts
        for (x in 3..11) revealed.add(idx(x, cy0))
        for (y in uy0 + 1..uy0 + 9) for (x in 12..20) revealed.add(idx(x, y))
        revealed.add(door2)
    }

    private fun buildTorchRoom() {
        for (y in ty0..ty0 + 10) for (x in tx0..tx0 + 12) grid[idx(x, y)] = FLOOR
        // Alcove en cul-de-sac (rangee du haut)
        grid[idx(tx0, ty0)] = WALL
        grid[idx(tx0 + 11, ty0)] = WALL
        grid[idx(tx0 + 12, ty0)] = WALL
        for (x in tx0..tx0 + 12) {
            if (x != tx0 + 6 && x != tx0 + 9) grid[idx(x, ty0 + 1)] = WALL
        }
        // 5 emplacements au fond de l'alcove
        for (k in 1..5) targets2.add(idx(tx0 + k, ty0))
        // 1 emplacement DEVANT l'alcove (bouche du puits)
        targets2.add(idx(tx0 + 6, ty0))
        // 1 emplacement TOUT A DROITE : le piege !
        // (si l'alcove est remplie avant, on ne peut plus pousser vers la droite)
        targets2.add(idx(tx0 + 10, ty0))

        lighter = idx(tx0 + 6, ty0 + 5)
        torches[0] = idx(tx0 + 1, ty0 + 2)
        torches[1] = idx(tx0 + 11, ty0 + 2)
        torches[2] = idx(tx0 + 1, ty0 + 9)
        torches[3] = idx(tx0 + 11, ty0 + 9)

        // 4 caisses dans le puits + 3 caisses laterales a ramener dans le puits
        crates2.add(idx(tx0 + 6, ty0 + 3))
        crates2.add(idx(tx0 + 6, ty0 + 5))
        crates2.add(idx(tx0 + 6, ty0 + 7))
        crates2.add(idx(tx0 + 6, ty0 + 9))
        crates2.add(idx(tx0 + 2, ty0 + 4))
        crates2.add(idx(tx0 + 2, ty0 + 8))
        crates2.add(idx(tx0 + 10, ty0 + 6))

        mobSpawn[0] = idx(tx0 + 9, ty0 + 7)
        mobSpawn[1] = idx(tx0 + 4, ty0 + 8)

        door3 = idx(tx0 + 13, ty0 + 5)

        for (y in ty0..ty0 + 10) for (x in tx0..tx0 + 12) if (isFloor(x, y)) roomTorch.add(idx(x, y))
    }

    /**
     * [8] LE GRAND COULOIR : part de la porte 3, longe le donjon et redescend
     *     jusqu'a une PORTE A RUNES (enigme "lights out") qui ouvre la salle du boss.
     */
    private fun buildCorridor2() {
        val cy = ty0 + 5
        val by0 = uy0 + 11          // haut de la salle du boss
        val bcy = by0 + 5           // ligne d'entree de la salle du boss

        // vers la droite
        for (x in tx0 + 14..tx0 + 16) corridor2.add(idx(x, cy))
        // descente
        for (y in cy..bcy) corridor2.add(idx(tx0 + 16, y))
        // vers la gauche jusqu'a la porte a runes
        for (x in 22..tx0 + 16) corridor2.add(idx(x, bcy))
        for (c in corridor2) grid[c] = FLOOR

        runeDoor = idx(21, bcy)
        grid[runeDoor] = DOOR

        // Salle du boss (9 x 9), sous la salle des couleurs
        for (y in by0..by0 + 8) for (x in 12..20) grid[idx(x, y)] = FLOOR
        for (y in by0..by0 + 8) for (x in 12..20) roomBoss.add(idx(x, y))

        bossSpawns.add(idx(14, by0 + 2))
        bossSpawns.add(idx(18, by0 + 2))
        bossSpawns.add(idx(16, by0 + 1))
        bossSpawns.add(idx(13, by0 + 6))
        bossSpawns.add(idx(19, by0 + 6))

        // La porte scellee (au nord) redonne sur la salle des couleurs
        // -> elle s'ouvre quand le boss est vaincu.

        // Le point de teleportation apparait au centre de la salle des couleurs
        exitX = 16
        exitY = uy0 + 6
    }

    /**
     * [9] L'ILE : on sort du donjon par le portail et on arrive a la surface.
     * Mer -> haut-fond -> rivage -> plage -> herbe, chemins de terre et village.
     */
    /**
     * Trois CACHES SECRETES : une dalle d'apparence anodine, posee sur un sol
     * deja revele, dissimule une trappe. Dessous : une petite antichambre 5x4
     * (creusee dans la roche) gardee par des monstres, avec un coffre d'or.
     * L'antichambre est placee dans une bande de mur vierge, loin de tout.
     */
    private fun buildSecretCaches() {
        // Emplacements candidats pour les dalles-declencheurs : des cases de sol
        // deja revelees, dans les salles du haut du donjon.
        val triggers = listOf(
            idx(ax + 4, 5), idx(bx + 6, 6), idx(16, uy0 + 7)
        ).filter { it >= 0 && isFloor(cx(it), cy(it)) }

        // Bandes de roche ou creuser (colonnes de droite, hors salles connues)
        val caveSlots = listOf(
            Triple(wid - 8, 2, 1), Triple(wid - 8, 8, 2), Triple(wid - 14, 14, 3)
        )

        for ((k, trig) in triggers.withIndex()) {
            if (k >= caveSlots.size) break
            val (rx, ry, id) = caveSlots[k]
            if (!inside(rx + 4, ry + 3)) continue
            // creuser l'antichambre 5x4
            val cells = ArrayList<Int>()
            for (yy in ry until ry + 4) for (xx in rx until rx + 5) {
                if (!inside(xx, yy)) continue
                val c = idx(xx, yy)
                grid[c] = FLOOR
                terrain[c] = TER_NONE
                cells.add(c)
                secretRooms[c] = id
            }
            if (cells.isEmpty()) continue
            val entry = cells.first()            // ou l'on tombe
            val chestC = cells.last()            // le coffre, au fond
            secretTraps[trig] = entry
            secretChests[entry] = chestC
            // 2 monstres gardiens (indices de sprite varies : la serie rigolote)
            val guards = ArrayList<Int>()
            val mid = cells[cells.size / 2]
            guards.add(mid)
            guards.add(cells[cells.size / 2 - 1])
            secretMonsters[entry] = guards
            // la dalle-piege reste dans revealed (elle a l'air normale)
            revealed.add(trig)
        }
    }

    private fun buildIsland() {
        val h = 34
        val top = iy0
        val cxI = wid / 2f
        val cyI = top + h / 2f
        val rx = wid / 2f - 1.5f
        val ry = h / 2f - 1.5f
        // L'ILE LOINTAINE, petite, au sud, accessible en barque
        val cx2 = wid * 0.42f
        val cy2 = top + 45f
        val rx2 = 8.5f
        val ry2 = 7.5f
        villageCy = (cyI + 5).toInt()

        for (y in top until top + 56) {
            for (x in 0 until wid) {
                if (!inside(x, y)) continue
                val i = idx(x, y)
                val dx = (x + 0.5f - cxI) / rx
                val dy = (y + 0.5f - cyI) / ry
                var d = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                // Cote irreguliere
                val ang = kotlin.math.atan2(dy.toDouble(), dx.toDouble()).toFloat()
                d *= 1f + 0.10f * kotlin.math.sin(ang * 3f + seed.toFloat() % 6.28f) +
                        0.06f * kotlin.math.sin(ang * 5f + 1.3f)
                // L'ile lointaine : on garde la cote la plus proche des deux
                val dx2 = (x + 0.5f - cx2) / rx2
                val dy2 = (y + 0.5f - cy2) / ry2
                var d2 = kotlin.math.sqrt((dx2 * dx2 + dy2 * dy2).toDouble()).toFloat()
                val ang2 = kotlin.math.atan2(dy2.toDouble(), dx2.toDouble()).toFloat()
                d2 *= 1f + 0.12f * kotlin.math.sin(ang2 * 4f + seed.toFloat() % 6.28f) +
                        0.05f * kotlin.math.sin(ang2 * 7f + 0.7f)
                d = kotlin.math.min(d, d2)
                when {
                    d < 0.62f -> { terrain[i] = TER_GRASS; grid[i] = FLOOR }
                    d < 0.76f -> { terrain[i] = TER_SAND; grid[i] = FLOOR }
                    d < 0.84f -> { terrain[i] = TER_SHORE; grid[i] = FLOOR }
                    d < 0.93f -> { terrain[i] = TER_SHALLOW; grid[i] = WALL }
                    else -> { terrain[i] = TER_WATER; grid[i] = WALL }
                }
                revealed.add(i)
            }
        }

        // Portail d'arrivee, au centre de l'ile
        islandPortal = idx(cxI.toInt(), (cyI - 4).toInt())
        grid[islandPortal] = FLOOR
        terrain[islandPortal] = TER_DIRT

        // Chemins de terre : du portail vers la place du village, puis vers la plage
        val px = cx(islandPortal)
        val py = cy(islandPortal)
        for (y in py..villageCy) paveDirt(px, y)
        for (x in px - 6..px + 6) paveDirt(x, villageCy)
        for (y in villageCy..villageCy + 6) paveDirt(px, y)
        for (x in px - 4..px + 4) paveDirt(x, villageCy + 6)

        placeTwoHouses()
        placeVillagers()
        placeSecretEntrance()
        placeGiantTree(cx2.toInt())
        placeDecor()

        // La barque, sur un haut-fond au sud du village, contre la plage
        outer3@ for (y in top + 24..top + 33) {
            for (x in px - 8..px + 8) {
                if (!inside(x, y) || !inside(x, y - 1)) continue
                val c = idx(x, y)
                val up = idx(x, y - 1)
                if (c != slipCell && terrain[c] == TER_SHALLOW &&
                    (terrain[up] == TER_SHORE || terrain[up] == TER_SAND) && grid[up] == FLOOR
                ) {
                    boatCell = c
                    break@outer3
                }
            }
        }

        // Un DISTRIBUTEUR 8.6, seul au monde, sur l'ile lointaine
        outer4@ for (y in top + 42..top + 50) {
            for (x in (cx2 - 4).toInt()..(cx2 + 4).toInt()) {
                if (!inside(x, y)) continue
                val c = idx(x, y)
                if (terrain[c] == TER_GRASS && grid[c] == FLOOR && !decor.containsKey(c)) {
                    vendors[c] = 3
                    grid[c] = WALL
                    break@outer4
                }
            }
        }
    }

    /** Cherche une case d'herbe au nord-ouest pour l'entree du prochain donjon. */
    private fun placeSecretEntrance() {
        val top = iy0
        outer@ for (y in top + 6..top + 12) {
            for (x in 6..14) {
                val i = idx(x, y)
                if (isFloor(x, y) && terrain[i] == TER_GRASS) {
                    dungeon2Cell = i
                    break@outer
                }
            }
        }
    }

    /** Arbres, buissons, rochers sur l'ile ; barques echouees sur la plage. */
    private fun placeDecor() {
        plantForests()
        val top = iy0
        for (y in top until top + 56) {
            for (x in 0 until wid) {
                if (!inside(x, y)) continue
                val i = idx(x, y)
                if (grid[i] != FLOOR) continue
                if (houses.containsKey(i) || houseMats.containsKey(i) || i == islandPortal) continue
                if (i == dungeon2Cell) continue
                if (terrain[i] == TER_DIRT || terrain[i] == TER_EARTH) continue   // pas sur les chemins
                if (inVillage(x, y)) continue          // on laisse la place libre
                val t = terrain[i]
                val roll = rnd.nextInt(100)
                when (t) {
                    TER_GRASS -> when {
                        roll < 6 -> decor[i] = Pair(1, 1 + rnd.nextInt(11))
                        roll < 8 -> { decor[i] = Pair(2, 1 + rnd.nextInt(9)); grid[i] = WALL }
                    }
                    TER_SAND -> when {
                        roll < 3 -> { decor[i] = Pair(2, 1 + rnd.nextInt(9)); grid[i] = WALL }
                        roll < 5 -> decor[i] = Pair(1, 1 + rnd.nextInt(11))
                    }
                    TER_SHORE -> if (roll < 2) { decor[i] = Pair(3, 1 + rnd.nextInt(5)); grid[i] = WALL }
                }
            }
        }
    }

    private fun paveDirt(x: Int, y: Int) {
        if (!inside(x, y)) return
        val i = idx(x, y)
        if (terrain[i] == TER_GRASS || terrain[i] == TER_SAND) {
            terrain[i] = TER_DIRT
            grid[i] = FLOOR
        }
    }

    /** Les 10 maisons du village, le long des chemins. */
    /**
     * Les 2 premiers batiments du village : la CHAUMIERE (1) et la FORGE (2).
     * Chaque maison occupe 3x2 cases au sol, avec sa porte (paillasson) devant,
     * et possede un grand interieur vide de 12x8.
     */
    private fun placeTwoHouses() {
        val px = cx(islandPortal)
        val vy = cy(islandPortal) + 7        // juste au nord de la place (le chemin est a +9)
        buildHouse(1, px - 4, vy)            // chaumiere
        buildHouse(2, px + 4, vy)            // forge
        buildHouse(3, px + 7, vy + 5)        // la maison anarchiste, a l'ecart
        buildHouse(4, px - 7, vy + 5)        // la cabane d'alchimiste, de l'autre cote
        buildHouse(5, px + 9, vy)            // le PUNK CLUB, en bordure nord-est
        buildHouse(6, px - 9, vy)            // LA GUILDE, en bordure nord-ouest
        buildHouse(8, px - 4, vy + 5)        // LA TAVERNE, sous la chaumiere

        // Le tavernier + ses 6 clients, dans la salle
        run {
            val rx0 = 1 + ((8 - 1) % 3) * 13
            val ry0 = hy0 + 1 + ((8 - 1) / 3) * 9
            tavernCells.clear()
            val spots = listOf(
                Pair(6, 1),                              // le tavernier, derriere le comptoir
                Pair(1, 3), Pair(3, 5), Pair(9, 3),
                Pair(10, 5), Pair(2, 5), Pair(8, 2)      // les 6 clients attables
            )
            for ((k, sp) in spots.withIndex()) {
                val c = idx(rx0 + sp.first, ry0 + sp.second)
                if (inside(rx0 + sp.first, ry0 + sp.second) && grid[c] == FLOOR) {
                    tavernCells.add(Pair(c, k + 1))
                }
            }
        }

        // Les 10 heros de la guilde, repartis dans le grand hall
        run {
            val rx0 = 1 + ((6 - 1) % 3) * 13
            val ry0 = hy0 + 1 + ((6 - 1) / 3) * 9
            val spots = listOf(
                Pair(1, 2), Pair(3, 2), Pair(5, 2), Pair(7, 2), Pair(9, 2),
                Pair(2, 4), Pair(4, 4), Pair(6, 4), Pair(8, 4), Pair(10, 4)
            )
            for ((k, sp) in spots.withIndex()) {
                val c = idx(rx0 + sp.first, ry0 + sp.second)
                if (inside(rx0 + sp.first, ry0 + sp.second) && grid[c] == FLOOR) {
                    guildSpawns.add(Pair(c, k + 1))
                }
            }
        }

        // Les punks trainent devant le club... et dedans
        for ((k, sp) in listOf(
            Pair(px + 7, vy + 2), Pair(px + 11, vy + 2),
            Pair(px + 10, vy + 3), Pair(px + 8, vy - 2)
        ).withIndex()) {
            val (x, y) = sp
            if (inside(x, y) && grid[idx(x, y)] == FLOOR && !houseMats.containsKey(idx(x, y))) {
                punkSpawns.add(Pair(idx(x, y), k + 1))
            }
        }
        for (k in 0..2) {
            val c = interiorSpot(5, k)
            if (c >= 0) punkSpawns.add(Pair(c, k + 5))
        }

        // Pierre et Franki, les pecheurs, sur la plage au sud-ouest
        outer@ for (y in iy0 + 27 downTo iy0 + 22) {
            for (x in 7..16) {
                val i = idx(x, y)
                if (grid[i] == FLOOR && terrain[i] == TER_SAND) {
                    if (pierreCell < 0) { pierreCell = i }
                    else if (frankiCell < 0 && kotlin.math.abs(cx(i) - cx(pierreCell)) >= 2) {
                        frankiCell = i
                        break@outer
                    }
                }
            }
        }

        // Le slip flotte dans la mer, au large de Pierre (2-3 cases dans l'eau)
        if (pierreCell >= 0) {
            val bx = cx(pierreCell)
            outer2@ for (y in cy(pierreCell) + 2..iy0 + 33) {
                for (dx in intArrayOf(0, -1, 1, -2, 2)) {
                    val x = bx + dx
                    if (!inside(x, y)) continue
                    val t = terrain[idx(x, y)]
                    if (t == TER_WATER || t == TER_SHALLOW) {
                        // on s'avance d'une case de plus vers le large si possible
                        val deeper = if (inside(x, y + 1) &&
                            (terrain[idx(x, y + 1)] == TER_WATER || terrain[idx(x, y + 1)] == TER_SHALLOW)
                        ) idx(x, y + 1) else idx(x, y)
                        slipCell = deeper
                        break@outer2
                    }
                }
            }
        }

        // Les deux distributeurs de 8.6, de part et d'autre du portail
        val py = cy(islandPortal)
        for ((k, dx) in listOf(-2, 2).withIndex()) {
            val c = idx(px + dx, py)
            if (inside(px + dx, py) && grid[c] == FLOOR && terrain[c] == TER_GRASS) {
                vendors[c] = k + 1
                grid[c] = WALL
            }
        }
    }

    /** LE GRAND ARBRE : batiment 7, plante au coeur de l'ile lointaine. */
    private fun placeGiantTree(cx2i: Int) {
        val top = iy0
        for (y in top + 41..top + 49) {
            for (x in cx2i - 5..cx2i + 6) {
                if (!inside(x - 1, y - 1) || !inside(x + 1, y + 1)) continue
                var ok = true
                for (dy in -1..0) for (dx in -1..1) {
                    val c = idx(x + dx, y + dy)
                    if (terrain[c] != TER_GRASS || grid[c] != FLOOR) ok = false
                }
                val mat = idx(x, y + 1)
                if (grid[mat] != FLOOR || terrain[mat] == TER_SHALLOW || terrain[mat] == TER_WATER) ok = false
                if (!ok) continue
                buildHouse(7, x, y)
                mageCell = interiorSpot(7, 1)
                return
            }
        }
    }

    private fun buildHouse(n: Int, x: Int, y: Int) {
        if (!inside(x - 1, y - 1) || !inside(x + 1, y + 1)) return
        // Empreinte au sol : 3 x 2 cases infranchissables
        for (dy in -1..0) for (dx in -1..1) {
            val c = idx(x + dx, y + dy)
            grid[c] = WALL
            houseBody[c] = n
        }
        houses[idx(x, y)] = n
        // La porte : le paillasson juste devant
        val mat = idx(x, y + 1)
        grid[mat] = FLOOR
        if (terrain[mat] != TER_NONE) terrain[mat] = TER_EARTH
        houseMats[mat] = n
        buildInterior(n)
    }

    /** L'interieur : une grande piece vide de 12x8, sol au choix de la maison. */
    private fun buildInterior(n: Int) {
        val col = (n - 1) % 3
        val row = (n - 1) / 3
        val rx0 = 1 + col * 13
        val ry0 = hy0 + 1 + row * 9
        if (ry0 + 8 >= hei || rx0 + 12 >= wid) return
        houseFloor[n] = when (n) {
            2 -> 14       // forge : grandes dalles de pierre claire (SOL_CLAIR_02)
            3 -> 23       // squat anarchiste : beton clair tache, crade mais lumineux (SOL_CLAIR_11)
            4 -> 32       // cabane d'alchimiste : carreaux hexagonaux, tres alchimique (SOL_CLAIR_20)
            5 -> 31       // le punk club : plancher clair (contraste avec les punks !)
            6 -> 27       // LA GUILDE : carrelage clair en diagonale, noble (SOL_CLAIR_15)
            7 -> 13       // LE GRAND ARBRE : terre claire, touffes d'herbe (SOL_CLAIR_01)
            8 -> 22       // LA TAVERNE : plancher de bois blond, chaleureux (SOL_CLAIR_10)
                          // (hfloor6 etait une texture de MUR a colombages - bug corrige)
            else -> 22    // chaumiere : planches de bois blond (SOL_CLAIR_10)
        }
        for (y in ry0..ry0 + 7) for (x in rx0..rx0 + 11) {
            val i = idx(x, y)
            grid[i] = FLOOR
            terrain[i] = TER_WOOD
            revealed.add(i)
        }
        val exitCell = idx(rx0 + 6, ry0 + 7)
        houseExit[exitCell] = n
        houseEntry[n] = exitCell

        // Un DISTRIBUTEUR 8.6 bavard, au fond de la taverne
        if (n == 8) {
            val dc = idx(rx0 + 8, ry0)
            if (grid[dc] == FLOOR) { vendors[dc] = 4; grid[dc] = WALL }
        }

        // Les objets fixes, poses sur les murs / le sol de la piece
        when (n) {
            1 -> {                                  // chaumiere : cheminee + fenetres + tapis
                fixtures[idx(rx0 + 2, ry0 - 1)] = 2
                fixtures[idx(rx0 + 8, ry0 - 1)] = 1
                fixtures[idx(rx0 + 10, ry0 - 1)] = 1
                fixtures[idx(rx0 + 6, ry0 + 3)] = 4
            }
            2 -> {                                  // forge : fenetre
                fixtures[idx(rx0 + 3, ry0 - 1)] = 1
                fixtures[idx(rx0 + 9, ry0 - 1)] = 1
            }
            3 -> {                                  // squat : tapis punk + drapeau antifa
                fixtures[idx(rx0 + 6, ry0 + 3)] = 5
                fixtures[idx(rx0 + 2, ry0 - 1)] = 11
            }
            4 -> {                                  // alchimiste : fenetre + tapis
                fixtures[idx(rx0 + 9, ry0 - 1)] = 1
                fixtures[idx(rx0 + 6, ry0 + 4)] = 5
            }
            7 -> fixtures[idx(rx0 + 6, ry0 + 3)] = 4   // l'antre de l'arbre : le tapis
            8 -> {                                  // taverne : cheminee, fenetres + drapeau a l'entree
                fixtures[idx(rx0 + 2, ry0 - 1)] = 1
                fixtures[idx(rx0 + 9, ry0 - 1)] = 1
                fixtures[idx(rx0 + 4, ry0 + 7)] = 11   // a cote de la porte d'entree
            }
            6 -> {                                  // guilde : cheminee, fenetres, grand tapis
                fixtures[idx(rx0 + 2, ry0 - 1)] = 1
                fixtures[idx(rx0 + 6, ry0 - 1)] = 2
                fixtures[idx(rx0 + 10, ry0 - 1)] = 1
                fixtures[idx(rx0 + 6, ry0 + 3)] = 4
            }
            5 -> {                                  // club : la scene et le matos + drapeau
                fixtures[idx(rx0 + 6, ry0)] = 3
                fixtures[idx(rx0 + 9, ry0)] = 11        // drapeau antifa pres de la scene
                fixtures[idx(rx0 + 4, ry0 + 1)] = 10    // micro devant la scene
                fixtures[idx(rx0 + 0, ry0)] = 6         // amplis
                fixtures[idx(rx0 + 11, ry0)] = 6
                fixtures[idx(rx0 + 0, ry0 + 6)] = 7     // guitare posee
                fixtures[idx(rx0 + 11, ry0 + 6)] = 8    // basse posee
                fixtures[idx(rx0 + 10, ry0 + 1)] = 9    // batterie de rechange
            }
        }

        // Le squat anarchiste : des graffitis partout sur les murs, et la bombe !
        if (n == 3) {
            sprayCell = idx(rx0 + 9, ry0 + 2)
            shroomCell = idx(rx0 + 2, ry0 + 4)
            val wallTags = listOf(
                Pair(rx0 + 1, ry0 - 1), Pair(rx0 + 4, ry0 - 1), Pair(rx0 + 7, ry0 - 1),
                Pair(rx0 + 10, ry0 - 1), Pair(rx0 - 1, ry0 + 2), Pair(rx0 + 12, ry0 + 2),
                Pair(rx0 - 1, ry0 + 5), Pair(rx0 + 12, ry0 + 5)
            )
            for ((k, sp) in wallTags.withIndex()) {
                val (x, y) = sp
                if (inside(x, y)) tags[idx(x, y)] = 1 + (k * 2 + seed.toInt().and(7)) % 15
            }
        }

        // Les meubles en dernier : ils evitent la bombe, les champignons,
        // la porte, les objets fixes et les places des habitants.
        furnish(n, rx0, ry0)
    }

    /** Plante un arbre (1..6) si la case est de l'herbe libre. */
    private fun tryTree(x: Int, y: Int) {
        if (!inside(x, y)) return
        val i = idx(x, y)
        if (grid[i] != FLOOR || terrain[i] != TER_GRASS) return
        if (houses.containsKey(i) || houseMats.containsKey(i) || decor.containsKey(i)) return
        if (i == islandPortal || i == dungeon2Cell || vendors.containsKey(i)) return
        // jamais coller un arbre a une porte
        for (dy in -1..1) for (dx in -1..1) {
            if (inside(x + dx, y + dy) && houseMats.containsKey(idx(x + dx, y + dy))) return
        }
        decor[i] = Pair(0, 1 + rnd.nextInt(6))
        grid[i] = WALL
    }

    /**
     * Les arbres poussent en BOSQUETS (5 clairieres d'arbres groupes),
     * et le squat de Kaos est niche au coeur d'une petite foret.
     */
    private fun plantForests() {
        val top = iy0
        // La foret du squat (maison 3, ancre px+7 / vy+5)
        val ax = cx(islandPortal) + 7
        val ay = cy(islandPortal) + 7 + 5
        for (dy in -3..3) for (dx in -3..3) {
            val ring = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
            if (ring < 2) continue                               // la maison
            if (dx in -1..1 && dy in 2..3) continue              // le passage vers la porte
            val p = if (ring == 2) 70 else 45
            if (rnd.nextInt(100) < p) tryTree(ax + dx, ay + dy)
        }
        // Les bosquets
        repeat(5) {
            var gx = -1
            var gy = -1
            for (attempt in 0 until 24) {
                val tx = 3 + rnd.nextInt(wid - 6)
                val ty = top + 3 + rnd.nextInt(28)
                if (!inside(tx, ty)) continue
                val i = idx(tx, ty)
                if (terrain[i] != TER_GRASS || grid[i] != FLOOR) continue
                if (inVillage(tx, ty)) continue
                gx = tx; gy = ty
                break
            }
            if (gx < 0) return@repeat
            for (dy in -2..2) for (dx in -2..2) {
                val ring = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                val p = when (ring) { 0 -> 90; 1 -> 55; else -> 25 }
                if (rnd.nextInt(100) < p && !inVillage(gx + dx, gy + dy)) tryTree(gx + dx, gy + dy)
            }
        }
    }

    /**
     * Meuble un interieur. Les meubles bloquent le passage : on laisse
     * toujours libres la porte, les allees centrales, les objets a ramasser
     * et les cases ou apparaissent les habitants.
     */
    private fun furnish(n: Int, rx0: Int, ry0: Int) {
        val plan: List<Triple<Int, Int, Int>> = when (n) {
            // CHAUMIERE : cuisine a gauche, chambre a droite, salon en bas
            1 -> listOf(
                Triple(0, 0, 14), Triple(1, 0, 15), Triple(3, 0, 18), Triple(4, 0, 10),
                Triple(8, 0, 20), Triple(9, 0, 21), Triple(10, 0, 22), Triple(5, 0, 64),
                Triple(0, 2, 11), Triple(1, 2, 12), Triple(10, 2, 23), Triple(11, 2, 24),
                Triple(0, 5, 7), Triple(1, 5, 8), Triple(2, 5, 6),
                Triple(9, 5, 19), Triple(10, 5, 25), Triple(11, 5, 77)
            )
            // FORGE : le feu et l'enclume
            2 -> listOf(
                Triple(0, 0, 41), Triple(1, 0, 43), Triple(2, 0, 42),
                Triple(4, 0, 37), Triple(5, 0, 39),
                Triple(8, 0, 38), Triple(9, 0, 45), Triple(10, 0, 44),
                Triple(0, 2, 40), Triple(11, 2, 68),
                Triple(0, 5, 70), Triple(1, 5, 67), Triple(2, 5, 69),
                Triple(10, 5, 72), Triple(11, 5, 16)
            )
            // SQUAT : recup, bazar et vieux canape
            3 -> listOf(
                Triple(0, 0, 70), Triple(1, 0, 68), Triple(3, 0, 65), Triple(4, 0, 66),
                Triple(8, 0, 67), Triple(10, 0, 89), Triple(11, 0, 71),
                Triple(0, 2, 16), Triple(11, 2, 85),
                Triple(0, 5, 7), Triple(1, 5, 8), Triple(2, 5, 83),
                Triple(10, 5, 72), Triple(11, 5, 69)
            )
            // ALCHIMISTE : alambics, grimoires et cristaux
            4 -> listOf(
                Triple(0, 0, 52), Triple(1, 0, 47), Triple(2, 0, 48),
                Triple(4, 0, 46), Triple(5, 0, 54),
                Triple(7, 0, 49), Triple(8, 0, 53),
                Triple(10, 0, 28), Triple(11, 0, 35),
                Triple(0, 2, 51), Triple(11, 2, 31),
                Triple(0, 5, 29), Triple(1, 5, 30),
                Triple(10, 5, 50), Triple(11, 5, 32)
            )
            // PUNK CLUB : le bar et les bancs
            5 -> listOf(
                Triple(0, 2, 13), Triple(1, 2, 68), Triple(0, 3, 2), Triple(1, 3, 60),
                Triple(11, 2, 61), Triple(10, 3, 2),
                Triple(0, 5, 5), Triple(1, 5, 5), Triple(2, 5, 2),
                Triple(9, 5, 5), Triple(10, 5, 2)
            )
            // LA TAVERNE : comptoir, tables, tonneaux, ratelier, buffet
            8 -> listOf(
                Triple(4, 0, 96), Triple(5, 0, 99), Triple(0, 0, 98), Triple(1, 0, 97),
                Triple(10, 0, 100), Triple(11, 0, 99),
                Triple(2, 3, 91), Triple(9, 3, 91),
                Triple(0, 5, 92), Triple(4, 5, 92)
            )
            // LE GRAND ARBRE : un antre druidique, grimoires et cristaux
            7 -> listOf(
                Triple(0, 0, 52), Triple(1, 0, 47), Triple(10, 0, 48), Triple(11, 0, 46),
                Triple(0, 5, 7), Triple(1, 5, 8), Triple(10, 5, 54), Triple(11, 5, 64)
            )
            // GUILDE : rateliers, coffres et tables le long des murs, hall ouvert
            6 -> listOf(
                Triple(0, 0, 37), Triple(1, 0, 39), Triple(3, 0, 20), Triple(4, 0, 21),
                Triple(8, 0, 10), Triple(9, 0, 38), Triple(11, 0, 45),
                Triple(0, 5, 16), Triple(11, 5, 72)
            )
            else -> emptyList()
        }
        for ((dx, dy, pn) in plan) {
            val x = rx0 + dx
            val y = ry0 + dy
            if (!inside(x, y)) continue
            val i = idx(x, y)
            if (grid[i] != FLOOR) continue
            if (fixtures.containsKey(i)) continue
            if (i == houseEntry[n]) continue
            if (i == sprayCell || i == shroomCell) continue
            if (i == interiorSpot(n, 0) || i == interiorSpot(n, 1) || i == interiorSpot(n, 2)) continue
            props[i] = pn
            grid[i] = WALL
        }
    }

    /** Une case de l'interieur n pour y placer un habitant. */
    private fun interiorSpot(n: Int, k: Int): Int {
        val rx0 = 1 + ((n - 1) % 3) * 13
        val ry0 = hy0 + 1 + ((n - 1) / 3) * 9
        val x = rx0 + 3 + k * 4
        val y = ry0 + 3
        if (!inside(x, y)) return -1
        val c = idx(x, y)
        return if (grid[c] == FLOOR) c else -1
    }

    /** Villageois et animaux de la place du village. */
    private fun placeVillagers() {
        val px = cx(islandPortal)
        // (Les batiments seront ajoutes un par un, plus tard.)
        // Villageois et animaux dans le village
        // Qui habite ou : chaumiere -> Rosa (7) + Milo (6) + mamie Agathe (5) ;
        // forge -> Bran (2) ; squat -> Pip le reveur (10) y squatte ;
        // cabane d'alchimiste -> Lila (3). Kaos (11) traine devant son squat.
        val residents = mapOf(1 to listOf(7, 6, 5), 2 to listOf(2), 3 to listOf(10), 4 to listOf(3))
        val indoorIds = residents.values.flatten().toSet()
        for ((hn, ids) in residents) {
            for ((k, id) in ids.withIndex()) {
                val c = interiorSpot(hn, k)
                if (c >= 0) npcSpawns.add(Pair(c, id))
            }
        }
        val outdoorIds = (1..10).filter { it !in indoorIds }
        val vSpots = listOf(
            Pair(px - 4, villageCy), Pair(px + 4, villageCy), Pair(px, villageCy + 3),
            Pair(px - 6, villageCy + 3), Pair(px + 6, villageCy + 3), Pair(px - 1, villageCy - 4),
            Pair(px + 3, villageCy + 7), Pair(px - 3, villageCy - 5), Pair(px + 6, villageCy - 4),
            Pair(px - 6, villageCy - 3)
        )
        for ((n, sp) in vSpots.withIndex()) {
            if (n >= outdoorIds.size) break
            val (x, y) = sp
            val c = if (inside(x, y)) idx(x, y) else -1
            if (c >= 0 && grid[c] == FLOOR && !houseMats.containsKey(c)) {
                npcSpawns.add(Pair(c, outdoorIds[n]))
            }
        }
        // Les 2 MARCHANDS AMBULANTS, sur la place du village, avec leur stand
        run {
            // stand = 1 case-mur (facade) ; le marchand se tient juste devant
            val spots = listOf(
                Triple(px - 5, villageCy - 3, 1),   // nomade a gauche
                Triple(px + 5, villageCy - 3, 2)    // jolie a droite
            )
            for ((sx, sy, id) in spots) {
                if (!inside(sx, sy) || !inside(sx, sy + 1)) continue
                val stall = idx(sx, sy)
                val front = idx(sx, sy + 1)
                if (grid[stall] == FLOOR && grid[front] == FLOOR &&
                    !houseMats.containsKey(stall) && !vendors.containsKey(stall)
                ) {
                    stallCells[stall] = id
                    grid[stall] = WALL
                    merchantCells.add(Pair(front, id))
                }
            }
        }

        // Kaos, le punk, traine devant son squat
        run {
            val kx = px + 6
            val ky = cy(islandPortal) + 13
            if (inside(kx, ky) && grid[idx(kx, ky)] == FLOOR && !houseMats.containsKey(idx(kx, ky))) {
                npcSpawns.add(Pair(idx(kx, ky), 11))
            }
        }
        val aSpots = listOf(
            Pair(px - 7, villageCy + 1), Pair(px + 7, villageCy + 1), Pair(px + 1, villageCy + 8),
            Pair(px - 2, villageCy + 8), Pair(px + 8, villageCy - 2), Pair(px - 8, villageCy - 2),
            Pair(px + 2, villageCy - 6), Pair(px - 4, villageCy + 6), Pair(px + 5, villageCy + 5),
            Pair(px - 5, villageCy - 6)
        )
        for ((n, sp) in aSpots.withIndex()) {
            val (x, y) = sp
            if (inside(x, y) && grid[idx(x, y)] == FLOOR) petSpawns.add(Pair(idx(x, y), n + 1))
        }
    }

    /**
     * L'interieur d'une maison : une piece 9x7 en parquet, meublee avec les 9 objets
     * de sa planche (maison 1 = salon, 2 = cuisine, 3 = chambre, etc.).
     */
    fun isIsland(x: Int, y: Int) =
        inside(x, y) && terrain[idx(x, y)] != TER_NONE && terrain[idx(x, y)] != TER_WOOD
    fun isInterior(x: Int, y: Int) = inside(x, y) && terrain[idx(x, y)] == TER_WOOD

    /** Quel interieur (1..10) contient cette case ? */
    fun interiorOf(x: Int, y: Int): Int {
        if (!isInterior(x, y)) return 0
        val col = (x - 1) / 13
        val row = (y - hy0 - 1) / 9
        val n = row * 3 + col + 1
        return if (n in 1..10) n else 1
    }
    fun inVillage(x: Int, y: Int) =
        isIsland(x, y) && kotlin.math.abs(x - villageCx) <= 8 &&
                kotlin.math.abs(y - villageCy) <= 8

    fun inBossRoom(x: Int, y: Int) = x in 12..20 && y in uy0 + 11..uy0 + 19

    fun openRuneDoor() {
        lightsSolved = true
        grid[runeDoor] = FLOOR
        revealed.add(runeDoor)
        revealed.addAll(roomBoss)
    }

    /** Boss vaincu : la porte scellee s'ouvre, la teleportation apparait. */
    fun bossVictory() {
        bossDefeated = true
        grid[door2] = FLOOR
        revealed.add(door2)
        teleportActive = true
    }

    fun spawnAfterSimon() {
        simonSolved = true
        chest3Spawned = true
        grid[door1] = DOOR
        revealed.add(door1)
    }

    fun openDoor1() {
        grid[door1] = FLOOR
        revealed.add(door1)
        revealed.addAll(roomTorch)
    }

    /** Les 4 torches sont allumees : les caisses apparaissent. */
    fun spawnSokoban2() {
        if (sokoban2Spawned) return
        sokoban2Spawned = true
        blocks.addAll(crates2)
        blocksInit.addAll(crates2)
        revealed.addAll(crates2)
        revealed.addAll(targets2)
    }

    fun spawnDoor3AndMobs() {
        door3Spawned = true
        mobsSpawned = true
        grid[door3] = DOOR
        revealed.add(door3)
    }

    fun openDoor3() {
        door3Open = true
        grid[door3] = FLOOR
        revealed.add(door3)
        revealed.addAll(corridor2)
        revealed.add(runeDoor)
    }

    fun sokoban2Solved(): Boolean = sokoban2Spawned && targets2.all { it in blocks }

    // ------------------------------------------------------------ enigmes

    /** Sudoku 4x4 (chiffres 1-4, blocs 2x2), reproductible via la graine. */
    private fun buildSudoku() {
        // Grille valide de base
        val base = intArrayOf(
            1, 2, 3, 4,
            3, 4, 1, 2,
            2, 1, 4, 3,
            4, 3, 2, 1
        )
        // Permutation des chiffres
        val digits = intArrayOf(1, 2, 3, 4).toMutableList()
        digits.shuffle(rnd)
        for (i in 0 until 16) sudokuSol[i] = digits[base[i] - 1]
        // Echange des lignes dans une bande, et des colonnes dans une pile
        if (rnd.nextBoolean()) swapRows(0, 1)
        if (rnd.nextBoolean()) swapRows(2, 3)
        if (rnd.nextBoolean()) swapCols(0, 1)
        if (rnd.nextBoolean()) swapCols(2, 3)

        // On laisse 7 indices
        val cells = (0 until 16).toMutableList()
        cells.shuffle(rnd)
        for (i in 0 until 16) sudokuGiven[i] = 0
        for (k in 0 until 7) sudokuGiven[cells[k]] = sudokuSol[cells[k]]
        for (i in 0 until 16) sudokuUser[i] = sudokuGiven[i]
    }

    private fun swapRows(a: Int, b: Int) {
        for (c in 0 until 4) {
            val t = sudokuSol[a * 4 + c]
            sudokuSol[a * 4 + c] = sudokuSol[b * 4 + c]
            sudokuSol[b * 4 + c] = t
        }
    }

    private fun swapCols(a: Int, b: Int) {
        for (r in 0 until 4) {
            val t = sudokuSol[r * 4 + a]
            sudokuSol[r * 4 + a] = sudokuSol[r * 4 + b]
            sudokuSol[r * 4 + b] = t
        }
    }

    fun sudokuOk(): Boolean {
        for (i in 0 until 16) if (sudokuUser[i] != sudokuSol[i]) return false
        return true
    }

    /** Lights out 3x3 : toutes les runes doivent briller. */
    private fun buildLights() {
        for (i in 0 until 9) lights[i] = true
        // On melange en appliquant des coups valides : la grille reste soluble
        repeat(6 + rnd.nextInt(4)) { toggleLight(rnd.nextInt(9)) }
        if (lightsAllOn()) toggleLight(rnd.nextInt(9))
    }

    fun toggleLight(i: Int) {
        val x = i % 3
        val y = i / 3
        lights[i] = !lights[i]
        if (x > 0) lights[i - 1] = !lights[i - 1]
        if (x < 2) lights[i + 1] = !lights[i + 1]
        if (y > 0) lights[i - 3] = !lights[i - 3]
        if (y < 2) lights[i + 3] = !lights[i + 3]
    }

    fun lightsAllOn(): Boolean = lights.all { it }

    // ------------------------------------------------------------ mines

    private fun placeMines() {
        val cells = ArrayList<Int>()
        for (y in 1..hallH) for (x in 1..hallW) cells.add(idx(x, y))
        val safe = HashSet<Int>()
        for (dy in -1..1) for (dx in -1..1) safe.add(idx(startX + dx, startY + dy))
        val target = (cells.size * density).toInt()
        var placed = 0
        var guard = 0
        while (placed < target && guard < 80000) {
            guard++
            val c = cells[rnd.nextInt(cells.size)]
            if (c in layout || c in safe) continue
            layout.add(c)
            placed++
        }
        mines.addAll(layout)
    }

    private fun placeHearts() {
        val cells = ArrayList<Int>()
        for (y in 1..hallH) for (x in 1..hallW) {
            val i = idx(x, y)
            if (i !in layout && (x > 3 || y > 3)) cells.add(i)
        }
        var placed = 0
        var guard = 0
        while (placed < 5 && guard < 20000) {
            guard++
            val c = cells[rnd.nextInt(cells.size)]
            if (c in hearts) continue
            hearts.add(c)
            placed++
        }
    }

    // ------------------------------------------------------------ regles

    fun countAround(x: Int, y: Int): Int {
        var c = 0
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = x + dx
            val ny = y + dy
            if (inside(nx, ny) && idx(nx, ny) in layout) c++
        }
        return c
    }

    fun isTorch(i: Int) = torches.any { it == i }

    fun isWalkable(x: Int, y: Int): Boolean {
        if (!isFloor(x, y)) return false
        val i = idx(x, y)
        if (i in mines || i in flagged || i in blocks) return false
        if (i == chest) return false
        if (chest2Spawned && i == chest2) return false
        if (chest3Spawned && i == chest3) return false
        if (i == altar) return false
        if (isTorch(i)) return false
        if (props.containsKey(i)) return false
        if (x == trapX && y == trapY && !trapOpen) return false
        return i in revealed
    }

    fun isTeleport(x: Int, y: Int) = teleportActive && x == exitX && y == exitY
    fun isIslandPortal(x: Int, y: Int) = islandPortal >= 0 && idx(x, y) == islandPortal

    fun canPushInto(x: Int, y: Int): Boolean {
        if (!isFloor(x, y)) return false
        val i = idx(x, y)
        if (i in blocks || i == chest || i in mines || i in flagged) return false
        if (chest2Spawned && i == chest2) return false
        if (chest3Spawned && i == chest3) return false
        if (i == altar || isTorch(i)) return false
        if (i == door || i == door1 || i == door2 || i == door3 || i == runeDoor) return false
        if (x == trapX && y == trapY) return false
        if (i in plates && i !in plateSolved) return false
        return true
    }

    fun platesSolved(): Boolean = plates.all { it in blocks && it in plateSolved }
    fun trapSolved(): Boolean = targets.all { it in blocks }

    fun inTorchRoom(x: Int, y: Int) = x in tx0..tx0 + 12 && y in ty0..ty0 + 10

    /** Remet les caisses de la salle des torches a leur place. */
    fun resetSokoban2() {
        if (!sokoban2Spawned) return
        blocks.removeAll(blocks.filter { inTorchRoom(cx(it), cy(it)) }.toSet())
        blocks.addAll(crates2)
    }

    /** Remet les blocs de l'etage 0 (salle du coffre + salle de rangement). */
    fun resetSokoban1() {
        blocks.removeAll(blocks.filter { cy(it) <= 11 }.toSet())
        blocks.addAll(blocksInit0)
    }

    fun revealCascade(sx: Int, sy: Int) {
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(Pair(sx, sy))
        while (stack.isNotEmpty()) {
            val (x, y) = stack.removeLast()
            if (!isFloor(x, y)) continue
            val i = idx(x, y)
            if (i in revealed || i in mines || i in flagged) continue
            revealed.add(i)
            if (countAround(x, y) == 0) {
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    stack.addLast(Pair(x + dx, y + dy))
                }
            }
        }
    }

    /** Une case de mer praticable en barque. */
    fun isNavigable(x: Int, y: Int): Boolean {
        if (!inside(x, y)) return false
        val t = terrain[idx(x, y)]
        return t == TER_WATER || t == TER_SHALLOW
    }

    fun findPath(fx: Int, fy: Int, tx: Int, ty: Int, boat: Boolean = false): List<Pair<Int, Int>>? {
        fun ok(x: Int, y: Int) = if (boat) isNavigable(x, y) else isWalkable(x, y)
        if (!ok(tx, ty)) return null
        if (fx == tx && fy == ty) return emptyList()
        val prev = HashMap<Int, Int>()
        val seen = HashSet<Int>()
        val q = ArrayDeque<Pair<Int, Int>>()
        q.addLast(Pair(fx, fy))
        seen.add(idx(fx, fy))
        val d4 = listOf(Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1))
        while (q.isNotEmpty()) {
            val (x, y) = q.removeFirst()
            if (x == tx && y == ty) {
                val p = ArrayList<Pair<Int, Int>>()
                var cur = idx(x, y)
                while (cur != idx(fx, fy)) {
                    p.add(Pair(cx(cur), cy(cur)))
                    cur = prev[cur]!!
                }
                p.reverse()
                return p
            }
            for ((dx, dy) in d4) {
                val nx = x + dx
                val ny = y + dy
                if (!ok(nx, ny)) continue
                val ni = idx(nx, ny)
                if (ni in seen) continue
                seen.add(ni)
                prev[ni] = idx(x, y)
                q.addLast(Pair(nx, ny))
            }
        }
        return null
    }
}
