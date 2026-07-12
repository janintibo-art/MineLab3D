package com.minelab.game

import kotlin.random.Random

class Monster(var x: Double, var y: Double) {
    var hp = 50.0
    var alive = true
    var hitFlash = 0.0
}

class Riddle(val question: String, val answers: List<String>, val correct: Int)

/**
 * Monde du jeu : labyrinthe genere aleatoirement, mines cachees,
 * salles avec monstres, portes a enigmes et sortie.
 */
class World(val size: Int = 21, seed: Long = System.currentTimeMillis()) {

    companion object {
        const val FLOOR = 0
        const val WALL = 1
        const val DOOR = 2

        val RIDDLES = listOf(
            Riddle(
                "Plus on m'enleve de la matiere,\nplus je deviens grand. Qui suis-je ?",
                listOf("Un mur", "Un trou", "Un roi"), 1
            ),
            Riddle(
                "Je commence la nuit et je termine\nle matin. Qui suis-je ?",
                listOf("La lune", "Le reve", "La lettre N"), 2
            ),
            Riddle(
                "Qu'est-ce qui monte\nmais ne redescend jamais ?",
                listOf("L'age", "La fumee", "Une fleche"), 0
            )
        )
    }

    private val rnd = Random(seed)
    val grid = IntArray(size * size) { WALL }
    val mines = HashSet<Int>()
    val revealed = HashSet<Int>()
    val flagged = HashSet<Int>()
    val monsters = ArrayList<Monster>()
    val riddles = HashMap<Int, Riddle>()
    var exitIdx = 0
    var totalMines = 0

    fun idx(x: Int, y: Int) = y * size + x

    fun cell(x: Int, y: Int): Int =
        if (x in 0 until size && y in 0 until size) grid[idx(x, y)] else WALL

    init {
        generateMaze()
        carveRooms()
        exitIdx = idx(size - 2, size - 2)
        grid[exitIdx] = FLOOR
        placeMines()
        placeDoors()
        revealed.add(idx(1, 1))
        totalMines = mines.size
    }

    /** Labyrinthe parfait par backtracking recursif. */
    private fun generateMaze() {
        val stack = ArrayDeque<Pair<Int, Int>>()
        grid[idx(1, 1)] = FLOOR
        stack.addLast(Pair(1, 1))
        val dirs = listOf(Pair(2, 0), Pair(-2, 0), Pair(0, 2), Pair(0, -2))
        while (stack.isNotEmpty()) {
            val cur = stack.last()
            val x = cur.first
            val y = cur.second
            val options = ArrayList<Pair<Int, Int>>()
            for (d in dirs) {
                val nx = x + d.first
                val ny = y + d.second
                if (nx in 1 until size - 1 && ny in 1 until size - 1 &&
                    grid[idx(nx, ny)] == WALL
                ) options.add(d)
            }
            if (options.isEmpty()) {
                stack.removeLast()
                continue
            }
            val d = options[rnd.nextInt(options.size)]
            grid[idx(x + d.first / 2, y + d.second / 2)] = FLOOR
            grid[idx(x + d.first, y + d.second)] = FLOOR
            stack.addLast(Pair(x + d.first, y + d.second))
        }
    }

    /** Salles de combat avec des monstres. */
    private fun carveRooms() {
        repeat(3) {
            val rx = 3 + 2 * rnd.nextInt((size - 9) / 2)
            val ry = 3 + 2 * rnd.nextInt((size - 9) / 2)
            for (y in ry until ry + 5) {
                for (x in rx until rx + 5) {
                    grid[idx(x, y)] = FLOOR
                }
            }
            monsters.add(Monster(rx + 2.5, ry + 2.5))
            if (rnd.nextBoolean()) monsters.add(Monster(rx + 1.5, ry + 3.5))
        }
    }

    private fun placeMines() {
        val floorCells = ArrayList<Int>()
        for (i in 0 until size * size) if (grid[i] == FLOOR) floorCells.add(i)
        val safe = HashSet<Int>()
        for (y in 0..3) for (x in 0..3) safe.add(idx(x, y))
        var placed = 0
        var guard = 0
        while (placed < size && guard < 5000) {
            guard++
            val c = floorCells[rnd.nextInt(floorCells.size)]
            if (c in mines || c in safe || c == exitIdx) continue
            mines.add(c)
            placed++
        }
    }

    /** Portes-enigmes placees sur des couloirs (2 voisins opposes). */
    private fun placeDoors() {
        val floorCells = ArrayList<Int>()
        for (i in 0 until size * size) if (grid[i] == FLOOR) floorCells.add(i)
        var doors = 0
        var guard = 0
        while (doors < 2 && guard < 2000) {
            guard++
            val c = floorCells[rnd.nextInt(floorCells.size)]
            if (c in mines || c == exitIdx || c == idx(1, 1)) continue
            val x = c % size
            val y = c / size
            if (x < 4 && y < 4) continue
            val n = cell(x, y - 1) != WALL
            val s = cell(x, y + 1) != WALL
            val w = cell(x - 1, y) != WALL
            val e = cell(x + 1, y) != WALL
            if ((n && s && !w && !e) || (w && e && !n && !s)) {
                grid[c] = DOOR
                riddles[c] = RIDDLES[doors % RIDDLES.size]
                doors++
            }
        }
    }

    /**
     * Revele une dalle. Si elle n'a aucune mine adjacente, revele en cascade
     * toutes les dalles voisines (comme au demineur classique).
     */
    fun revealCascade(sx: Int, sy: Int) {
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(Pair(sx, sy))
        while (stack.isNotEmpty()) {
            val p = stack.removeLast()
            val x = p.first
            val y = p.second
            if (x !in 0 until size || y !in 0 until size) continue
            val i = idx(x, y)
            if (grid[i] == WALL) continue
            if (i in revealed) continue
            if (i in mines) continue
            revealed.add(i)
            if (grid[i] == DOOR) continue
            if (mineCountAround(x, y) == 0) {
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    stack.addLast(Pair(x + dx, y + dy))
                }
            }
        }
    }

    /** Nombre de mines dans les 8 cases adjacentes. */
    fun mineCountAround(x: Int, y: Int): Int {
        var c = 0
        for (dy in -1..1) {
            for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until size && ny in 0 until size && idx(nx, ny) in mines) c++
            }
        }
        return c
    }
}
