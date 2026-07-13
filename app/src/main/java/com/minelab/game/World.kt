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
    }

    private val rnd = Random(seed)

    val wid = maxOf(hallW + 24, 40)
    val uy0 = maxOf(hallH, 11) + 3
    val hei = uy0 + 14

    val grid = IntArray(wid * hei) { WALL }

    val layout = HashSet<Int>()
    val mines = HashSet<Int>()
    val revealed = HashSet<Int>()
    val flagged = HashSet<Int>()
    val exploded = HashSet<Int>()
    val defused = HashSet<Int>()
    val hearts = HashSet<Int>()

    val blocks = HashSet<Int>()
    val blocksInit = HashSet<Int>()
    val plates = HashSet<Int>()
    val plateSolved = HashSet<Int>()
    val targets = HashSet<Int>()        // sokoban 1
    val targets2 = HashSet<Int>()       // sokoban 2
    private val crates2 = ArrayList<Int>()
    private val roomB = ArrayList<Int>()
    private val roomTorch = ArrayList<Int>()
    private val roomFinal = ArrayList<Int>()

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
        placeMines()
        placeHearts()
        totalMines = mines.size
        blocksInit.addAll(blocks)
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
        for (k in 1..5) targets2.add(idx(tx0 + k, ty0))

        lighter = idx(tx0 + 6, ty0 + 5)
        torches[0] = idx(tx0 + 1, ty0 + 2)
        torches[1] = idx(tx0 + 11, ty0 + 2)
        torches[2] = idx(tx0 + 1, ty0 + 9)
        torches[3] = idx(tx0 + 11, ty0 + 9)

        crates2.add(idx(tx0 + 6, ty0 + 3))
        crates2.add(idx(tx0 + 6, ty0 + 5))
        crates2.add(idx(tx0 + 6, ty0 + 7))
        crates2.add(idx(tx0 + 6, ty0 + 9))
        crates2.add(idx(tx0 + 2, ty0 + 4))

        mobSpawn[0] = idx(tx0 + 9, ty0 + 7)
        mobSpawn[1] = idx(tx0 + 4, ty0 + 8)

        door3 = idx(tx0 + 13, ty0 + 5)

        // Salle de l'etoile
        for (y in ty0 + 4..ty0 + 6) for (x in tx0 + 14..tx0 + 16) grid[idx(x, y)] = FLOOR
        exitX = tx0 + 15
        exitY = ty0 + 5

        for (y in ty0..ty0 + 10) for (x in tx0..tx0 + 12) if (isFloor(x, y)) roomTorch.add(idx(x, y))
        for (y in ty0 + 4..ty0 + 6) for (x in tx0 + 14..tx0 + 16) roomFinal.add(idx(x, y))
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
        sokoban2Spawned = true
        blocks.addAll(crates2)
        blocksInit.addAll(crates2)
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
        revealed.addAll(roomFinal)
    }

    fun sokoban2Solved(): Boolean = sokoban2Spawned && targets2.all { it in blocks }

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

    fun canPushInto(x: Int, y: Int): Boolean {
        if (!isFloor(x, y)) return false
        val i = idx(x, y)
        if (i in blocks || i == chest || i in mines || i in flagged) return false
        if (chest2Spawned && i == chest2) return false
        if (chest3Spawned && i == chest3) return false
        if (i == altar || isTorch(i)) return false
        if (i == door || i == door1 || i == door2 || i == door3) return false
        if (x == trapX && y == trapY) return false
        if (i in plates && i !in plateSolved) return false
        return true
    }

    fun platesSolved(): Boolean = plates.all { it in blocks && it in plateSolved }
    fun trapSolved(): Boolean = targets.all { it in blocks }

    fun resetPuzzle() {
        blocks.clear()
        blocks.addAll(blocksInit)
        if (!sokoban2Spawned) blocks.removeAll(crates2.toSet())
        trapOpen = false
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
