package com.minelab.game

import kotlin.random.Random

/**
 * ETAGE 1 :
 *  1) Grande salle de demineur (chiffres bases sur le layout d'origine : stables).
 *     Des COEURS sont caches sous certaines dalles sures.
 *  2) Salle du coffre : chaque dalle de pression est recouverte d'un MINI-DEMINEUR
 *     3x3 qu'il faut resoudre pour la decouvrir. 3 blocs sur les 3 dalles -> coffre -> CLE.
 *  3) Salle de rangement (sokoban durci, solution unique) -> TRAPPE.
 *     Sokoban resolu -> un 2e coffre apparait : il contient le JOYSTICK.
 *
 * SOUS-SOL (via la trappe) :
 *  Couloir -> salle de l'enigme des couleurs (Simon). Resolue -> un coffre et
 *  2 portes apparaissent. Porte gauche -> salle de l'etoile (victoire).
 *  Porte droite -> scellee (prochaine mise a jour).
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

    val wid = hallW + 24
    val uy0 = (if (hallH > 11) hallH else 11) + 3
    val hei = uy0 + 12

    val grid = IntArray(wid * hei) { WALL }

    /** Disposition d'origine des mines : les chiffres n'en bougent JAMAIS. */
    val layout = HashSet<Int>()
    val mines = HashSet<Int>()
    val revealed = HashSet<Int>()
    val flagged = HashSet<Int>()
    val exploded = HashSet<Int>()
    val defused = HashSet<Int>()

    /** Coeurs caches sous des dalles sures de la grande salle. */
    val hearts = HashSet<Int>()

    val blocks = HashSet<Int>()
    val blocksInit = HashSet<Int>()
    val plates = HashSet<Int>()
    /** Dalles de pression dont le mini-demineur a ete resolu. */
    val plateSolved = HashSet<Int>()
    val targets = HashSet<Int>()
    private val roomB = ArrayList<Int>()

    var chest = -1          // coffre de la cle
    var chestOpen = false
    var hasKey = false
    var door = -1           // porte a cle entre A et B
    var trapOpen = false
    var chest2 = -1         // coffre du joystick (apparait apres le sokoban)
    var chest2Spawned = false
    var chest2Open = false

    // Sous-sol
    var altar = -1
    val simonTiles = IntArray(4)          // rouge, bleu, vert, jaune
    val simonSeq = ArrayList<Int>()       // suite de couleurs (0..3)
    var simonSolved = false
    var chest3 = -1
    var chest3Spawned = false
    var chest3Open = false
    var door1 = -1          // vers l'etoile
    var door2 = -1          // scellee
    var undergroundStartX = 0
    var undergroundStartY = 0

    var startX = 1
    var startY = 1
    var trapX = 0
    var trapY = 0
    var exitX = 0           // l'etoile
    var exitY = 0
    var totalMines = 0

    private val ax = hallW + 2
    private val bx = hallW + 12

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
        placeMines()
        placeHearts()
        totalMines = mines.size
        blocksInit.addAll(blocks)
        revealCascade(startX, startY)
    }

    // ------------------------------------------------------------ etage 1

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

    /**
     * Sokoban durci. Alcove (dalles bleues) en haut, fermee sauf deux puits :
     * les caisses n'entrent que par la colonne bx+5, le heros contourne par bx+8.
     * Des murs interieurs imposent des manoeuvres et un ordre unique.
     */
    private fun buildStorageRoom() {
        for (y in 1..11) for (x in bx..bx + 10) grid[idx(x, y)] = FLOOR

        // Fermeture de l'alcove (rangee y=1) : acces caisses en +5, acces heros en +8
        grid[idx(bx, 1)] = WALL
        grid[idx(bx + 9, 1)] = WALL
        grid[idx(bx + 10, 1)] = WALL
        grid[idx(bx, 2)] = WALL
        for (x in bx + 1..bx + 4) grid[idx(x, 2)] = WALL
        grid[idx(bx + 6, 2)] = WALL
        grid[idx(bx + 7, 2)] = WALL
        grid[idx(bx + 9, 2)] = WALL
        grid[idx(bx + 10, 2)] = WALL

        // Murs interieurs : couloirs etroits autour de la colonne des caisses
        grid[idx(bx + 4, 5)] = WALL
        grid[idx(bx + 6, 5)] = WALL
        grid[idx(bx + 2, 7)] = WALL
        grid[idx(bx + 8, 8)] = WALL

        for (k in 1..4) targets.add(idx(bx + k, 1))

        // Caisses dispersees : chacune doit etre ramenee dans la colonne +5
        blocks.add(idx(bx + 5, 4))
        blocks.add(idx(bx + 3, 6))
        blocks.add(idx(bx + 7, 7))
        blocks.add(idx(bx + 5, 10))

        trapX = bx + 10
        trapY = 11
        chest2 = idx(bx + 1, 10)

        for (y in 1..11) for (x in bx..bx + 10) {
            if (isFloor(x, y)) roomB.add(idx(x, y))
        }
    }

    fun revealRoomB() {
        revealed.addAll(roomB)
    }

    // ------------------------------------------------------------ sous-sol

    private fun buildUnderground() {
        val cy0 = uy0 + 5
        // Couloir
        for (x in 3..11) grid[idx(x, cy0)] = FLOOR
        undergroundStartX = 3
        undergroundStartY = cy0

        // Salle de l'enigme (9x9)
        val rx0 = 12
        for (y in uy0 + 1..uy0 + 9) for (x in rx0..rx0 + 8) grid[idx(x, y)] = FLOOR

        altar = idx(rx0 + 4, uy0 + 5)
        simonTiles[0] = idx(rx0 + 4, uy0 + 2)   // rouge (nord)
        simonTiles[1] = idx(rx0 + 1, uy0 + 5)   // bleu (ouest)
        simonTiles[2] = idx(rx0 + 7, uy0 + 5)   // vert (est)
        simonTiles[3] = idx(rx0 + 4, uy0 + 8)   // jaune (sud)

        // Suite de 4 couleurs, reproductible via la graine
        repeat(4) { simonSeq.add(rnd.nextInt(4)) }

        chest3 = idx(rx0 + 2, uy0 + 2)
        door1 = idx(rx0 + 9, uy0 + 3)
        door2 = idx(rx0 + 9, uy0 + 7)

        // Salle de l'etoile, derriere la porte gauche
        for (y in uy0 + 2..uy0 + 4) for (x in rx0 + 10..rx0 + 12) grid[idx(x, y)] = FLOOR
        exitX = rx0 + 11
        exitY = uy0 + 3

        // Le sous-sol est eclaire (pas de demineur ici)
        for (y in uy0 until hei) for (x in 0 until wid) {
            if (isFloor(x, y)) revealed.add(idx(x, y))
        }
    }

    /** Appelee quand l'enigme des couleurs est resolue. */
    fun spawnAfterSimon() {
        simonSolved = true
        chest3Spawned = true
        grid[door1] = DOOR
        grid[door2] = DOOR
    }

    fun openDoor1() {
        grid[door1] = FLOOR
        for (y in uy0 + 2..uy0 + 4) for (x in 22..24) {
            if (isFloor(x, y)) revealed.add(idx(x, y))
        }
        revealed.add(door1)
        revealed.add(idx(exitX, exitY))
    }

    // ------------------------------------------------------------ mines & coeurs

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
        val want = 4
        while (placed < want && guard < 20000) {
            guard++
            val c = cells[rnd.nextInt(cells.size)]
            if (c in hearts) continue
            hearts.add(c)
            placed++
        }
    }

    // ------------------------------------------------------------ demineur

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

    fun isWalkable(x: Int, y: Int): Boolean {
        if (!isFloor(x, y)) return false
        val i = idx(x, y)
        if (i in mines || i in flagged || i in blocks) return false
        if (i == chest) return false
        if (chest2Spawned && i == chest2) return false
        if (chest3Spawned && i == chest3) return false
        if (i == altar) return false
        if (x == trapX && y == trapY && !trapOpen) return false
        return i in revealed
    }

    fun canPushInto(x: Int, y: Int): Boolean {
        if (!isFloor(x, y)) return false
        val i = idx(x, y)
        if (i in blocks || i == chest || i in mines || i in flagged) return false
        if (chest2Spawned && i == chest2) return false
        if (i == door) return false
        if (x == trapX && y == trapY) return false
        // Impossible de poser un bloc sur une dalle dont le mini-demineur n'est pas resolu
        if (i in plates && i !in plateSolved) return false
        return true
    }

    fun platesSolved(): Boolean = plates.all { it in blocks && it in plateSolved }
    fun trapSolved(): Boolean = targets.all { it in blocks }

    fun resetPuzzle() {
        blocks.clear()
        blocks.addAll(blocksInit)
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

    // ------------------------------------------------------------ chemin

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
