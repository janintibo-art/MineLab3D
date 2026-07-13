package com.minelab.game

import kotlin.random.Random

/**
 * Labyrinthe-demineur + zone finale en bas a droite :
 *
 *  SALLE A (coffre)                     SALLE B (rangement, derriere la porte)
 *  - 3 blocs a pousser sur 3 dalles     - 4 caisses a ranger dans l'alcove
 *  - le coffre s'ouvre -> CLE           - une seule solution : la plus profonde d'abord
 *                    ---- PORTE ---->   - une fois rangees, la TRAPPE s'ouvre = sortie
 */
class World(
    val size: Int = 41,
    val seed: Long = System.currentTimeMillis(),
    val density: Double = 0.13
) {

    companion object {
        const val WALL = 0
        const val FLOOR = 1
        const val DOOR = 2
    }

    private val rnd = Random(seed)

    val grid = IntArray(size * size) { WALL }
    val mines = HashSet<Int>()
    val revealed = HashSet<Int>()
    val flagged = HashSet<Int>()
    val exploded = HashSet<Int>()

    /** Toutes les caisses poussables (salle A + salle B). */
    val blocks = HashSet<Int>()
    /** Etat initial, pour le bouton "reinitialiser l'enigme". */
    val blocksInit = HashSet<Int>()

    val plates = HashSet<Int>()    // dalles de pression de la salle A (coffre)
    val targets = HashSet<Int>()   // dalles speciales de la salle B (rangement)

    var chest = -1
    var chestOpen = false
    var hasKey = false
    var door = -1
    var trapOpen = false

    var startX = 1
    var startY = 1
    var exitX = 0                  // la trappe
    var exitY = 0
    var totalMines = 0

    // Zone finale, ancree en bas a droite : 21 de large, 9 de haut
    private val zx = size - 22
    private val zy = size - 10
    private val bx = zx + 10       // origine de la salle B

    fun idx(x: Int, y: Int) = y * size + x
    fun inside(x: Int, y: Int) = x in 0 until size && y in 0 until size
    fun isFloor(x: Int, y: Int) = inside(x, y) && grid[idx(x, y)] == FLOOR
    fun isDoor(x: Int, y: Int) = inside(x, y) && grid[idx(x, y)] == DOOR

    init {
        carveMaze()
        carveRooms()
        openExtraPassages()
        buildFinalZone()
        placeMines()
        totalMines = mines.size
        blocksInit.addAll(blocks)
        revealCascade(startX, startY)
    }

    // ------------------------------------------------------------ generation

    private fun carveMaze() {
        val stack = ArrayDeque<Pair<Int, Int>>()
        grid[idx(1, 1)] = FLOOR
        stack.addLast(Pair(1, 1))
        val dirs = listOf(Pair(2, 0), Pair(-2, 0), Pair(0, 2), Pair(0, -2))
        while (stack.isNotEmpty()) {
            val (x, y) = stack.last()
            val opts = dirs.filter { (dx, dy) ->
                val nx = x + dx
                val ny = y + dy
                nx in 1 until size - 1 && ny in 1 until size - 1 && grid[idx(nx, ny)] == WALL
            }
            if (opts.isEmpty()) { stack.removeLast(); continue }
            val (dx, dy) = opts[rnd.nextInt(opts.size)]
            grid[idx(x + dx / 2, y + dy / 2)] = FLOOR
            grid[idx(x + dx, y + dy)] = FLOOR
            stack.addLast(Pair(x + dx, y + dy))
        }
    }

    private fun carveRooms() {
        repeat(6) {
            val w = 5
            val h = 5
            val x0 = 2 + 2 * rnd.nextInt((size - w - 4) / 2)
            val y0 = 2 + 2 * rnd.nextInt((size - h - 4) / 2)
            for (y in y0 until y0 + h) for (x in x0 until x0 + w) {
                if (x in 1 until size - 1 && y in 1 until size - 1) grid[idx(x, y)] = FLOOR
            }
        }
    }

    private fun openExtraPassages() {
        var n = size * 3
        var guard = 0
        while (n > 0 && guard < 20000) {
            guard++
            val x = 1 + rnd.nextInt(size - 2)
            val y = 1 + rnd.nextInt(size - 2)
            if (grid[idx(x, y)] != WALL) continue
            val h = isFloor(x - 1, y) && isFloor(x + 1, y)
            val v = isFloor(x, y - 1) && isFloor(x, y + 1)
            if (h || v) { grid[idx(x, y)] = FLOOR; n-- }
        }
    }

    /** Salle du coffre + porte + salle de rangement (Sokoban) + trappe. */
    private fun buildFinalZone() {
        // Enceinte de toute la zone
        for (y in zy - 1..zy + 9) for (x in zx - 1..zx + 21) {
            if (inside(x, y)) grid[idx(x, y)] = WALL
        }
        // Salle A (coffre) : 9 x 9
        for (y in zy..zy + 8) for (x in zx..zx + 8) grid[idx(x, y)] = FLOOR
        // Salle B (rangement) : 11 x 9
        for (y in zy..zy + 8) for (x in bx..bx + 10) grid[idx(x, y)] = FLOOR

        // La porte, unique passage de A vers B
        door = idx(zx + 9, zy + 4)
        grid[door] = DOOR

        // --- Salle A : 3 dalles, 3 blocs, 1 coffre
        plates.add(idx(zx + 1, zy + 1))
        plates.add(idx(zx + 7, zy + 1))
        plates.add(idx(zx + 1, zy + 7))
        blocks.add(idx(zx + 3, zy + 3))
        blocks.add(idx(zx + 5, zy + 3))
        blocks.add(idx(zx + 4, zy + 5))
        chest = idx(zx + 7, zy + 6)

        // --- Salle B : une alcove en cul-de-sac sur la rangee du haut.
        // Murs qui ferment l'alcove : on n'y entre que par (bx+5, zy)
        grid[idx(bx, zy)] = WALL
        for (x in bx + 1..bx + 4) grid[idx(x, zy + 1)] = WALL

        // Les 4 dalles speciales, au fond de l'alcove
        targets.add(idx(bx + 1, zy))
        targets.add(idx(bx + 2, zy))
        targets.add(idx(bx + 3, zy))
        targets.add(idx(bx + 4, zy))

        // Les 4 caisses, alignees dans la salle
        blocks.add(idx(bx + 6, zy + 2))
        blocks.add(idx(bx + 7, zy + 2))
        blocks.add(idx(bx + 8, zy + 2))
        blocks.add(idx(bx + 9, zy + 2))

        // La trappe (sortie), dans le coin oppose
        exitX = bx + 10
        exitY = zy + 8

        // Deux acces depuis le labyrinthe vers la salle A
        digTunnel(zx - 1, zy + 4, -1, 0)
        digTunnel(zx + 4, zy - 1, 0, -1)

        // La salle A est eclairee des le depart
        for (y in zy - 1..zy + 9) for (x in zx - 1..zx + 9) {
            if (isFloor(x, y)) revealed.add(idx(x, y))
        }
    }

    private fun digTunnel(sx: Int, sy: Int, dx: Int, dy: Int) {
        var x = sx
        var y = sy
        var steps = 0
        while (x in 1 until size - 1 && y in 1 until size - 1 && steps < size) {
            grid[idx(x, y)] = FLOOR
            x += dx
            y += dy
            steps++
            if (steps > 1 && isFloor(x, y)) break
        }
    }

    private fun placeMines() {
        val floors = ArrayList<Int>()
        for (i in grid.indices) if (grid[i] == FLOOR) floors.add(i)

        val safe = HashSet<Int>()
        for (dy in -2..2) for (dx in -2..2) {
            if (inside(startX + dx, startY + dy)) safe.add(idx(startX + dx, startY + dy))
        }
        // Aucune mine dans toute la zone finale : ce sont des enigmes, pas des pieges
        for (y in zy - 2 until size) for (x in zx - 2 until size) {
            if (inside(x, y)) safe.add(idx(x, y))
        }

        val target = (floors.size * density).toInt()
        var placed = 0
        var guard = 0
        while (placed < target && guard < 60000) {
            guard++
            val c = floors[rnd.nextInt(floors.size)]
            if (c in mines || c in safe) continue
            mines.add(c)
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
            if (inside(nx, ny) && idx(nx, ny) in mines) c++
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

    /** Une caisse peut-elle etre poussee ici ? */
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
                    p.add(Pair(cur % size, cur / size))
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
