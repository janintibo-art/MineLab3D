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
    }

    private val rnd = Random(seed)

    val wid = maxOf(hallW + 24, 40)
    val uy0 = maxOf(hallH, 11) + 3
    val hei = uy0 + 22 + 36
    val iy0 = uy0 + 24          // premiere ligne de l'ile

    val grid = IntArray(wid * hei) { WALL }
    /** Terrain de surface (0 = donjon, sinon herbe/sable/mer...). */
    val terrain = IntArray(wid * hei) { TER_NONE }

    /** L'ile : maisons (case -> numero de sprite) et portail d'arrivee. */
    val houses = HashMap<Int, Int>()
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
    private fun buildIsland() {
        val h = 34
        val top = iy0
        val cxI = wid / 2f
        val cyI = top + h / 2f
        val rx = wid / 2f - 1.5f
        val ry = h / 2f - 1.5f
        villageCy = (cyI + 5).toInt()

        for (y in top until top + h) {
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
                when {
                    d < 0.60f -> { terrain[i] = TER_GRASS; grid[i] = FLOOR }
                    d < 0.72f -> { terrain[i] = TER_SAND; grid[i] = FLOOR }
                    d < 0.80f -> { terrain[i] = TER_SHORE; grid[i] = FLOOR }
                    d < 0.90f -> { terrain[i] = TER_SHALLOW; grid[i] = WALL }
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

        placeHouses()
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
    private fun placeHouses() {
        val px = cx(islandPortal)
        val spots = listOf(
            Pair(px - 5, villageCy - 2), Pair(px - 2, villageCy - 2),
            Pair(px + 2, villageCy - 2), Pair(px + 5, villageCy - 2),
            Pair(px - 5, villageCy + 2), Pair(px - 2, villageCy + 2),
            Pair(px + 2, villageCy + 2), Pair(px + 5, villageCy + 2),
            Pair(px - 3, villageCy + 5), Pair(px + 3, villageCy + 5)
        )
        var k = 1
        for ((x, y) in spots) {
            if (!inside(x, y)) continue
            val i = idx(x, y)
            if (terrain[i] == TER_WATER || terrain[i] == TER_SHALLOW) continue
            houses[i] = k
            grid[i] = WALL                 // on ne traverse pas une maison
            if (terrain[i] == TER_DIRT) terrain[i] = TER_EARTH
            k++
            if (k > 10) break
        }
    }

    fun isIsland(x: Int, y: Int) = inside(x, y) && terrain[idx(x, y)] != TER_NONE
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

    fun findPath(fx: Int, fy: Int, tx: Int, ty: Int): List<Pair<Int, Int>>? {
        if (!isWalkable(tx, ty)) return null
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
                if (!isWalkable(nx, ny)) continue
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
