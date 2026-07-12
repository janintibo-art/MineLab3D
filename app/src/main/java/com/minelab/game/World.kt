package com.minelab.game

import kotlin.random.Random

/**
 * Grand labyrinthe (41x41) dont chaque case de sol est une dalle de demineur.
 *
 * Contient aussi la SALLE A ENIGME : pousser les 3 blocs sur les 3 dalles de
 * pression ouvre le coffre, qui contient la CLE, qui ouvre la PORTE menant a
 * la salle de sortie.
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

    // Enigme
    val blocks = HashSet<Int>()
    val plates = HashSet<Int>()
    var chest = -1
    var chestOpen = false
    var hasKey = false
    var door = -1

    var startX = 1
    var startY = 1
    var exitX = 0
    var exitY = 0
    var totalMines = 0

    private val rx = size - 14
    private val ry = size - 14
    private val rw = 9
    private val rh = 9

    fun idx(x: Int, y: Int) = y * size + x
    fun inside(x: Int, y: Int) = x in 0 until size && y in 0 until size
    fun isFloor(x: Int, y: Int) = inside(x, y) && grid[idx(x, y)] == FLOOR
    fun isDoor(x: Int, y: Int) = inside(x, y) && grid[idx(x, y)] == DOOR

    init {
        carveMaze()
        carveRooms()
        openExtraPassages()
        buildPuzzleRoom()
        placeMines()
        totalMines = mines.size
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

    /** On abat des murs : plusieurs itineraires possibles, moins de culs-de-sac. */
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

    private fun buildPuzzleRoom() {
        // Enceinte
        for (y in ry - 1..ry + rh) for (x in rx - 1..rx + rw) {
            if (inside(x, y)) grid[idx(x, y)] = WALL
        }
        // Interieur de la salle a enigme
        for (y in ry until ry + rh) for (x in rx until rx + rw) grid[idx(x, y)] = FLOOR

        // Chambre de sortie, scellee, juste en dessous
        val ex0 = rx + 3
        val ey0 = ry + rh + 2
        for (y in ey0 - 2..ey0 + 3) for (x in ex0 - 2..ex0 + 4) {
            if (inside(x, y)) grid[idx(x, y)] = WALL
        }
        for (y in ey0 until ey0 + 3) for (x in ex0 until ex0 + 3) {
            if (inside(x, y)) grid[idx(x, y)] = FLOOR
        }
        // La porte : unique passage vers la sortie
        val dxp = ex0 + 1
        val dyp = ry + rh
        grid[idx(dxp, dyp)] = DOOR
        grid[idx(dxp, dyp + 1)] = FLOOR
        door = idx(dxp, dyp)

        exitX = ex0 + 1
        exitY = ey0 + 1

        // Deux tunnels de liaison avec le labyrinthe
        digTunnel(rx - 1, ry + 1, -1, 0)
        digTunnel(rx + 1, ry - 1, 0, -1)

        // Enigme
        plates.add(idx(rx + 1, ry + 1))
        plates.add(idx(rx + 7, ry + 1))
        plates.add(idx(rx + 1, ry + 7))
        blocks.add(idx(rx + 3, ry + 3))
        blocks.add(idx(rx + 5, ry + 3))
        blocks.add(idx(rx + 4, ry + 5))
        chest = idx(rx + 7, ry + 6)

        // Salle eclairee : dalles deja revelees
        for (y in ry - 1..ry + rh + 1) for (x in rx - 1..rx + rw + 1) {
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
        // Aucune mine dans la salle a enigme ni dans la sortie : c'est un puzzle
        for (y in ry - 2 until size) for (x in rx - 2 until size) {
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
        return i in revealed
    }

    fun canPushInto(x: Int, y: Int): Boolean {
        if (!isFloor(x, y)) return false
        val i = idx(x, y)
        if (i in blocks || i == chest || i in mines || i in flagged) return false
        return true
    }

    fun platesSolved(): Boolean = plates.all { it in blocks }

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

    /** Plus court chemin (BFS 4 directions) par les dalles sures deja revelees. */
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
                val path = ArrayList<Pair<Int, Int>>()
                var cur = idx(x, y)
                while (cur != idx(fx, fy)) {
                    path.add(Pair(cur % size, cur / size))
                    cur = prev[cur]!!
                }
                path.reverse()
                return path
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
