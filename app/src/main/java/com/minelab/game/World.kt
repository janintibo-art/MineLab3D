package com.minelab.game

import kotlin.random.Random

/** Monstre (le combat sera ajoute plus tard). */
class Monster(var x: Int, var y: Int) {
    var hp = 50
    var alive = true
}

/**
 * Grand labyrinthe dont chaque case de sol est une dalle de demineur.
 * Contient la generation, les mines, la revelation en cascade,
 * et la recherche de chemin (le heros se deplace tout seul).
 */
class World(val size: Int = 31, seed: Long = System.currentTimeMillis()) {

    companion object {
        const val WALL = 0
        const val FLOOR = 1
    }

    private val rnd = Random(seed)

    val grid = IntArray(size * size) { WALL }
    val mines = HashSet<Int>()
    val revealed = HashSet<Int>()
    val flagged = HashSet<Int>()
    val exploded = HashSet<Int>()
    val monsters = ArrayList<Monster>()

    var startX = 1
    var startY = 1
    var exitX = size - 2
    var exitY = size - 2
    var totalMines = 0

    fun idx(x: Int, y: Int) = y * size + x
    fun inside(x: Int, y: Int) = x in 0 until size && y in 0 until size
    fun isFloor(x: Int, y: Int) = inside(x, y) && grid[idx(x, y)] == FLOOR

    init {
        carveMaze()
        carveRooms()
        openExtraPassages()
        grid[idx(exitX, exitY)] = FLOOR
        placeMines()
        totalMines = mines.size
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

    /** Grandes salles (futures arenes de combat). */
    private fun carveRooms() {
        repeat(5) {
            val rw = 5
            val rh = 5
            val rx = 2 + 2 * rnd.nextInt((size - rw - 3) / 2)
            val ry = 2 + 2 * rnd.nextInt((size - rh - 3) / 2)
            for (y in ry until ry + rh) for (x in rx until rx + rw) {
                if (inside(x, y) && x in 1 until size - 1 && y in 1 until size - 1) {
                    grid[idx(x, y)] = FLOOR
                }
            }
        }
    }

    /** Quelques murs abattus : le labyrinthe devient moins etouffant. */
    private fun openExtraPassages() {
        var n = size * 2
        var guard = 0
        while (n > 0 && guard < 8000) {
            guard++
            val x = 1 + rnd.nextInt(size - 2)
            val y = 1 + rnd.nextInt(size - 2)
            if (grid[idx(x, y)] != WALL) continue
            val h = isFloor(x - 1, y) && isFloor(x + 1, y)
            val v = isFloor(x, y - 1) && isFloor(x, y + 1)
            if (h || v) { grid[idx(x, y)] = FLOOR; n-- }
        }
    }

    private fun placeMines() {
        val floors = ArrayList<Int>()
        for (i in grid.indices) if (grid[i] == FLOOR) floors.add(i)
        // Zone de depart sure (3x3) et sortie sure
        val safe = HashSet<Int>()
        for (dy in -1..1) for (dx in -1..1) {
            if (inside(startX + dx, startY + dy)) safe.add(idx(startX + dx, startY + dy))
            if (inside(exitX + dx, exitY + dy)) safe.add(idx(exitX + dx, exitY + dy))
        }
        val target = (floors.size * 0.13).toInt()
        var placed = 0
        var guard = 0
        while (placed < target && guard < 40000) {
            guard++
            val c = floors[rnd.nextInt(floors.size)]
            if (c in mines || c in safe) continue
            // On ne bouche pas un couloir etroit : sinon le niveau devient infaisable
            mines.add(c)
            placed++
        }
    }

    // ------------------------------------------------------------ demineur

    /** Nombre de mines parmi les 8 cases voisines. */
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

    /** Une dalle est "sure" (praticable) si revelee, sans mine et sans drapeau. */
    fun isWalkable(x: Int, y: Int): Boolean {
        if (!isFloor(x, y)) return false
        val i = idx(x, y)
        return i in revealed && i !in mines && i !in flagged
    }

    /** Revele une dalle, avec cascade si aucune mine autour (demineur classique). */
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

    /**
     * Plus court chemin (BFS, 4 directions) du heros vers (tx,ty),
     * en ne passant que par des dalles revelees et sures.
     * Renvoie la liste des cases a parcourir (sans la case de depart), ou null.
     */
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
