package com.minelab.game

import kotlin.random.Random

/**
 * Carte en 3 zones, sans labyrinthe :
 *
 *  1) LA GRANDE SALLE DE DEMINEUR (a gauche) : un vrai plateau de demineur ouvert.
 *     Le heros doit s'y frayer un chemin jusqu'au passage de droite.
 *  2) LA SALLE DU COFFRE : 3 blocs a pousser sur 3 dalles -> le coffre s'ouvre -> CLE.
 *  3) LA SALLE DE RANGEMENT (derriere la porte) : 4 caisses a ranger dans une alcove
 *     en cul-de-sac. Un seul puits d'acces, un seul detour possible pour le heros :
 *     il n'existe qu'UNE solution. Une fois range : la TRAPPE s'ouvre.
 *
 * IMPORTANT : les chiffres du demineur sont calcules sur la disposition D'ORIGINE
 * des mines (layout), ils ne changent donc JAMAIS, meme apres desamorcage.
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
    val hei = (if (hallH > 11) hallH else 11) + 2

    val grid = IntArray(wid * hei) { WALL }

    /** Disposition d'origine des mines : sert a calculer les chiffres. NE CHANGE JAMAIS. */
    val layout = HashSet<Int>()
    /** Mines encore actives (une mine desamorcee ou explosee en sort). */
    val mines = HashSet<Int>()

    val revealed = HashSet<Int>()
    val flagged = HashSet<Int>()
    val exploded = HashSet<Int>()
    val defused = HashSet<Int>()

    val blocks = HashSet<Int>()
    val blocksInit = HashSet<Int>()
    val plates = HashSet<Int>()
    val targets = HashSet<Int>()
    private val roomB = ArrayList<Int>()

    var chest = -1
    var chestOpen = false
    var hasKey = false
    var door = -1
    var trapOpen = false

    var startX = 1
    var startY = 1
    var exitX = 0
    var exitY = 0
    var totalMines = 0

    // Origines des salles
    private val ax = hallW + 2          // salle du coffre
    private val bx = hallW + 12         // salle de rangement

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
        placeMines()
        totalMines = mines.size
        blocksInit.addAll(blocks)
        revealCascade(startX, startY)
    }

    // ------------------------------------------------------------ construction

    private fun buildHall() {
        for (y in 1..hallH) for (x in 1..hallW) grid[idx(x, y)] = FLOOR
        // Passage vers la salle du coffre
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

        // La porte : seul passage vers la salle de rangement
        door = idx(ax + 9, 6)
        grid[door] = DOOR

        // Salle eclairee
        for (y in 1..11) for (x in ax..ax + 8) revealed.add(idx(x, y))
        revealed.add(idx(hallW + 1, 5))
    }

    /**
     * Salle de rangement (11 x 11).
     *   ligne 1  : # G G G G . . . # #      (G = dalle bleue, alcove en cul-de-sac)
     *   ligne 2  : # # # # # . # # . # #    (deux puits : x=+5 (caisses) et x=+8 (heros))
     *   lignes 3+: salle ouverte, 4 caisses alignees dans le puits
     *
     * -> Les caisses ne peuvent monter que par le puits +5, et le heros ne peut
     *    les contourner que par le puits +8 : l'ordre et les poussees sont imposes.
     */
    private fun buildStorageRoom() {
        for (y in 1..11) for (x in bx..bx + 10) grid[idx(x, y)] = FLOOR

        // Fermeture de l'alcove
        grid[idx(bx, 1)] = WALL
        grid[idx(bx + 9, 1)] = WALL
        grid[idx(bx + 10, 1)] = WALL
        grid[idx(bx, 2)] = WALL
        for (x in bx + 1..bx + 4) grid[idx(x, 2)] = WALL
        grid[idx(bx + 6, 2)] = WALL
        grid[idx(bx + 7, 2)] = WALL
        grid[idx(bx + 9, 2)] = WALL
        grid[idx(bx + 10, 2)] = WALL

        // Les 4 dalles bleues au fond de l'alcove
        for (k in 1..4) targets.add(idx(bx + k, 1))

        // Les 4 caisses, dans le puits d'acces
        blocks.add(idx(bx + 5, 4))
        blocks.add(idx(bx + 5, 6))
        blocks.add(idx(bx + 5, 8))
        blocks.add(idx(bx + 5, 10))

        // La trappe
        exitX = bx + 10
        exitY = 11

        for (y in 1..11) for (x in bx..bx + 10) {
            if (isFloor(x, y)) roomB.add(idx(x, y))
        }
    }

    fun revealRoomB() {
        revealed.addAll(roomB)
    }

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

    // ------------------------------------------------------------ demineur

    /** Chiffre du demineur : base sur le LAYOUT d'origine, donc stable a vie. */
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
        if (x == exitX && y == exitY && !trapOpen) return false
        return i in revealed
    }

    fun canPushInto(x: Int, y: Int): Boolean {
        if (!isFloor(x, y)) return false
        val i = idx(x, y)
        if (i in blocks || i == chest || i in mines || i in flagged) return false
        if (i == door) return false
        if (x == exitX && y == exitY) return false
        return true
    }

    fun platesSolved(): Boolean = plates.all { it in blocks }
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
