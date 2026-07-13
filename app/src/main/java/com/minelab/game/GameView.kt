package com.minelab.game

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

class GameView(context: Context) : View(context) {

    private val TITLE = 0
    private val PLAYING = 1
    private var state = TITLE

    private var showHelp = false
    private var showMenu = false
    private var showInv = false

    private var playerName = "Heros"
    private var difficulty = 1
    private var godMode = false

    private val prefs = context.getSharedPreferences("minelab", Context.MODE_PRIVATE)

    private var world = World()
    private var hx = 1
    private var hy = 1
    private var fx = 1.5f
    private var fy = 1.5f

    private var path: List<Pair<Int, Int>> = emptyList()
    private var pathStep = 0
    private var pendingReveal = -1
    private var pendingDisarm = -1
    private var pendingChest = false
    private var pendingDoor = false
    private var pendingChest2 = false
    private var pendingChest3 = false
    private var pendingAltar = false
    private var pendingDoor1 = false
    private var pendingDoor2 = false
    private var pendingMini = -1
    private var pendingTorch = -1
    private var pendingDoor3 = false

    private var hp = 100
    private var disarmed = 0
    private var flagsLeft = 0
    private var heartsGot = 0
    private var flagMode = false
    private var gameOver = false
    private var victory = false

    // Joystick
    private var swordOwned = false
    private var lighterOwned = false
    private var energyCount = 0
    private var joyOwned = false
    private var joyOn = false
    private var joyPointer = -1
    private var joyDX = 0f
    private var joyDY = 0f
    private val joyCenter = floatArrayOf(0f, 0f)
    private var joyRadius = 0f

    // Mini-demineur 3x3 (dalle de pression en cours)
    private var miniPlate = -1
    private val miniLayout = HashSet<Int>()
    private val miniRev = HashSet<Int>()
    private val miniFlag = HashSet<Int>()
    private val miniRects = Array(9) { RectF() }

    // Enigme des couleurs (Simon)
    private var simonState = 0        // 0 repos, 1 lecture, 2 saisie
    private var simonPos = -1
    private var simonTimer = 0f
    private var simonInput = 0
    private var simonFlash = -1
    private var simonFlashT = 0f

    private var message = ""
    private var msgTimer = 0f
    private var boomFlash = 0f
    private var damageT = 0f
    private var walkPhase = 0f
    private var keyAnim = 0f
    private var time = 0f

    private var camX = 1.5f
    private var camY = 1.5f
    private var following = true
    private var tile = 100f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private var lastTime = System.nanoTime()

    // Sprites
    private val sChestClosed: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.chest_closed)
    private val sChestOpen: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.chest_open)
    private val sKey: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.key)
    private val sLadder: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.ladder)
    @Suppress("unused")
    private val sTorch: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.torch)
    @Suppress("unused")
    private val sSword: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.sword)
    private val sSwordV: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.sword_v)
    private val sVault: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.vault)
    private val sEnergy: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.energy)
    private val sLighter: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.lighter)
    private val sHeroDown: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.hero_down)
    private val sHeroUp: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.hero_up)
    private val sHeroLeft: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.hero_left)
    private val sHeroRight: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.hero_right)
    private val sCrate: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.crate)
    private val sPlate: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.plate)
    private val sBomb: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.bomb)
    private val sFlag: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.flag)
    private val sHeart: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.heart)
    private val sTorchLit: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.torch_lit)
    private val sTorchOff: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.torch_off)
    private val sFloor: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.t_slab)
    private val sFloorDark: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.floor_rock)
    private val sFloorWood: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.t_ornate)
    private val sFloorRoom: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.t_marble)
    @Suppress("unused")
    private val sFloorCobble: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.t_cobble)
    private val sWall: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.wall_brick)
    private val sWallMossy: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.wall_mossy)
    private val sMonsters: Array<Bitmap> = arrayOf(
        BitmapFactory.decodeResource(resources, R.drawable.mob1),
        BitmapFactory.decodeResource(resources, R.drawable.mob2),
        BitmapFactory.decodeResource(resources, R.drawable.mob3),
        BitmapFactory.decodeResource(resources, R.drawable.mob4),
        BitmapFactory.decodeResource(resources, R.drawable.mob5),
        BitmapFactory.decodeResource(resources, R.drawable.mob6),
        BitmapFactory.decodeResource(resources, R.drawable.mob7),
        BitmapFactory.decodeResource(resources, R.drawable.mob8)
    )

    /** Direction du heros : 0 bas, 1 haut, 2 gauche, 3 droite. */
    private var heroDir = 0

    /** Un monstre. */
    private class Mob(var x: Float, var y: Float, var hp: Int, val sprite: Int) {
        var hitT = 0f
        var stepT = 0f
    }

    private val mobs = ArrayList<Mob>()
    private var attackCd = 0f
    private var attackAnim = 0f

    private var boardTop = 0f
    private var boardBottom = 0f

    private val btnFlag = RectF()
    private val btnZoomOut = RectF()
    private val btnZoomIn = RectF()
    private val btnCenter = RectF()
    private val btnMenu = RectF()
    private val btnSword = RectF()
    private val mMap = RectF()
    private var showMap = false

    private val tName = RectF()
    private val tDiff = arrayOf(RectF(), RectF(), RectF())
    private val tGod = RectF()
    private val tNew = RectF()
    private val tCont = RectF()
    private val tHelp = RectF()

    private val mResume = RectF()
    private val mInv = RectF()
    private val mSave = RectF()
    private val mReset = RectF()
    private val mHelp = RectF()
    private val mRestart = RectF()
    private val mQuit = RectF()
    private val invJoyRect = RectF()
    private val invEnergyRect = RectF()

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downTime = 0L
    private var dragging = false
    private val rect = RectF()
    private val tmpRect = RectF()

    private val simonColors = intArrayOf(
        Color.rgb(225, 65, 60),    // rouge
        Color.rgb(60, 130, 230),   // bleu
        Color.rgb(60, 195, 105),   // vert
        Color.rgb(245, 205, 70)    // jaune
    )

    init {
        isFocusable = true
        playerName = prefs.getString("name", "Heros") ?: "Heros"
        difficulty = prefs.getInt("diff", 1)
        godMode = prefs.getBoolean("god", false)
    }

    // ============================================================ DIFFICULTE

    private fun diffName(d: Int) = when (d) {
        0 -> "FACILE"
        2 -> "DIFFICILE"
        else -> "NORMAL"
    }

    private fun hallSize(d: Int) = when (d) {
        0 -> 12
        2 -> 22
        else -> 16
    }

    private fun diffDensity(d: Int) = when (d) {
        0 -> 0.12
        2 -> 0.18
        else -> 0.15
    }

    // ============================================================ PARTIE

    private fun newGame() {
        val s = hallSize(difficulty)
        world = World(s, s, System.currentTimeMillis(), diffDensity(difficulty))
        hx = world.startX; hy = world.startY
        fx = hx + 0.5f; fy = hy + 0.5f
        camX = fx; camY = fy
        following = true
        path = emptyList(); pathStep = 0
        clearPendings()
        hp = 100
        disarmed = 0
        heartsGot = 0
        flagsLeft = world.totalMines
        joyOwned = false; joyOn = false
        swordOwned = false; lighterOwned = false; energyCount = 0
        mobs.clear()
        miniPlate = -1
        simonState = 0; simonInput = 0
        gameOver = false; victory = false; flagMode = false
        showHelp = false; showMenu = false; showInv = false
        boomFlash = 0f; keyAnim = 0f
        heroDir = 0
        state = PLAYING
        showMsg("Traversez le champ de mines jusqu'au passage de droite !")
        saveGame()
    }

    private fun showMsg(m: String) { message = m; msgTimer = 4.5f }

    // ============================================================ SAUVEGARDE

    private fun hasSave() = prefs.getBoolean("has", false)
    private fun setToStr(s: Collection<Int>) = s.joinToString(",")

    private fun strToSet(s: String?): HashSet<Int> {
        val out = HashSet<Int>()
        if (s.isNullOrBlank()) return out
        for (p in s.split(",")) p.trim().toIntOrNull()?.let { out.add(it) }
        return out
    }

    private fun saveGame() {
        val e = prefs.edit()
        e.putBoolean("has", true)
        e.putString("name", playerName)
        e.putInt("diff", difficulty)
        e.putBoolean("god", godMode)
        e.putLong("seed", world.seed)
        e.putInt("hw", world.hallW)
        e.putInt("hh", world.hallH)
        e.putFloat("dens", world.density.toFloat())
        e.putInt("hp", hp)
        e.putInt("hx", hx)
        e.putInt("hy", hy)
        e.putInt("dis", disarmed)
        e.putInt("hgot", heartsGot)
        e.putInt("flags", flagsLeft)
        e.putBoolean("key", world.hasKey)
        e.putBoolean("chest", world.chestOpen)
        e.putBoolean("trap", world.trapOpen)
        e.putBoolean("door", world.grid[world.door] == World.FLOOR)
        e.putBoolean("c2s", world.chest2Spawned)
        e.putBoolean("c2o", world.chest2Open)
        e.putBoolean("c3s", world.chest3Spawned)
        e.putBoolean("c3o", world.chest3Open)
        e.putBoolean("simon", world.simonSolved)
        e.putBoolean("d1", world.grid[world.door1] == World.FLOOR)
        e.putBoolean("joy", joyOwned)
        e.putBoolean("sword", swordOwned)
        e.putBoolean("lit", lighterOwned)
        e.putBoolean("ltaken", world.lighterTaken)
        e.putString("torches", setToStr(world.torchLit))
        e.putBoolean("sk2", world.sokoban2Spawned)
        e.putBoolean("d3s", world.door3Spawned)
        e.putBoolean("d3o", world.door3Open)
        e.putBoolean("mobsD", world.mobsDead)
        e.putString("mobs", mobs.joinToString(";") { "${it.x},${it.y},${it.hp},${it.sprite}" })
        e.putInt("energy", energyCount)
        e.putBoolean("joyOn", joyOn)
        e.putString("mines", setToStr(world.mines))
        e.putString("rev", setToStr(world.revealed))
        e.putString("flg", setToStr(world.flagged))
        e.putString("exp", setToStr(world.exploded))
        e.putString("def", setToStr(world.defused))
        e.putString("blk", setToStr(world.blocks))
        e.putString("hearts", setToStr(world.hearts))
        e.putString("psol", setToStr(world.plateSolved))
        e.apply()
    }

    private fun loadGame(): Boolean {
        if (!hasSave()) return false
        world = World(
            prefs.getInt("hw", 16),
            prefs.getInt("hh", 16),
            prefs.getLong("seed", 0L),
            prefs.getFloat("dens", 0.15f).toDouble()
        )
        world.mines.clear(); world.mines.addAll(strToSet(prefs.getString("mines", "")))
        world.revealed.addAll(strToSet(prefs.getString("rev", "")))
        world.flagged.addAll(strToSet(prefs.getString("flg", "")))
        world.exploded.addAll(strToSet(prefs.getString("exp", "")))
        world.defused.addAll(strToSet(prefs.getString("def", "")))
        world.blocks.clear(); world.blocks.addAll(strToSet(prefs.getString("blk", "")))
        world.hearts.clear(); world.hearts.addAll(strToSet(prefs.getString("hearts", "")))
        world.plateSolved.addAll(strToSet(prefs.getString("psol", "")))
        world.hasKey = prefs.getBoolean("key", false)
        world.chestOpen = prefs.getBoolean("chest", false)
        world.trapOpen = prefs.getBoolean("trap", false)
        world.chest2Spawned = prefs.getBoolean("c2s", false)
        world.chest2Open = prefs.getBoolean("c2o", false)
        world.chest3Open = prefs.getBoolean("c3o", false)
        if (prefs.getBoolean("door", false)) world.grid[world.door] = World.FLOOR
        if (prefs.getBoolean("simon", false)) world.spawnAfterSimon()
        world.chest3Spawned = prefs.getBoolean("c3s", false) || world.simonSolved
        if (prefs.getBoolean("d1", false)) world.openDoor1()

        playerName = prefs.getString("name", "Heros") ?: "Heros"
        difficulty = prefs.getInt("diff", 1)
        godMode = prefs.getBoolean("god", false)
        hp = prefs.getInt("hp", 100)
        hx = prefs.getInt("hx", world.startX)
        hy = prefs.getInt("hy", world.startY)
        disarmed = prefs.getInt("dis", 0)
        heartsGot = prefs.getInt("hgot", 0)
        flagsLeft = prefs.getInt("flags", world.totalMines)
        joyOwned = prefs.getBoolean("joy", false)
        swordOwned = prefs.getBoolean("sword", false)
        lighterOwned = prefs.getBoolean("lit", false)
        world.lighterTaken = prefs.getBoolean("ltaken", false)
        world.torchLit.addAll(strToSet(prefs.getString("torches", "")))
        if (prefs.getBoolean("sk2", false)) { world.sokoban2Spawned = true }
        if (prefs.getBoolean("d3s", false)) world.spawnDoor3AndMobs()
        world.mobsDead = prefs.getBoolean("mobsD", false)
        if (prefs.getBoolean("d3o", false)) world.openDoor3()
        mobs.clear()
        val ms = prefs.getString("mobs", "") ?: ""
        if (ms.isNotBlank()) {
            for (part in ms.split(";")) {
                val f = part.split(",")
                if (f.size == 4) {
                    mobs.add(Mob(f[0].toFloat(), f[1].toFloat(), f[2].toInt(), f[3].toInt()))
                }
            }
        }
        energyCount = prefs.getInt("energy", 0)
        joyOn = prefs.getBoolean("joyOn", false)

        fx = hx + 0.5f; fy = hy + 0.5f
        camX = fx; camY = fy
        following = true
        path = emptyList(); pathStep = 0
        clearPendings()
        miniPlate = -1
        simonState = 0; simonInput = 0
        gameOver = false; victory = false; flagMode = false
        showHelp = false; showMenu = false; showInv = false
        state = PLAYING
        showMsg("Partie chargee. Bon retour, $playerName !")
        return true
    }

    // ============================================================ LAYOUT

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val wf = w.toFloat()
        val hf = h.toFloat()
        tile = min(w, h) / 9f
        boardTop = hf * 0.13f
        boardBottom = hf - hf * 0.10f

        val bh = hf * 0.062f
        val y0 = boardBottom + (hf - boardBottom - bh) / 2f
        val m = wf * 0.025f
        val gap = wf * 0.012f
        var x = wf - m
        btnMenu.set(x - bh, y0, x, y0 + bh); x -= bh + gap
        btnCenter.set(x - bh, y0, x, y0 + bh); x -= bh + gap
        btnZoomIn.set(x - bh, y0, x, y0 + bh); x -= bh + gap
        btnZoomOut.set(x - bh, y0, x, y0 + bh); x -= bh + gap
        btnSword.set(x - bh, y0, x, y0 + bh); x -= bh + gap
        btnFlag.set(m, y0, x, y0 + bh)

        joyRadius = min(wf, hf) * 0.11f
        joyCenter[0] = m + joyRadius * 1.4f
        joyCenter[1] = boardBottom - joyRadius * 1.5f

        val cw = wf * 0.78f
        val cx0 = (wf - cw) / 2f
        val rh = hf * 0.062f
        tName.set(cx0, hf * 0.30f, cx0 + cw, hf * 0.30f + rh)
        val dw = (cw - 2 * gap) / 3f
        for (k in 0..2) {
            tDiff[k].set(cx0 + k * (dw + gap), hf * 0.42f, cx0 + k * (dw + gap) + dw, hf * 0.42f + rh)
        }
        tGod.set(cx0, hf * 0.52f, cx0 + cw, hf * 0.52f + rh)
        tNew.set(cx0, hf * 0.63f, cx0 + cw, hf * 0.63f + rh * 1.15f)
        tCont.set(cx0, hf * 0.72f, cx0 + cw, hf * 0.72f + rh * 1.15f)
        tHelp.set(cx0, hf * 0.81f, cx0 + cw, hf * 0.81f + rh * 1.15f)

        val mw = wf * 0.74f
        val mx = (wf - mw) / 2f
        var my = hf * 0.20f
        val mh = hf * 0.062f
        val mg = hf * 0.012f
        mResume.set(mx, my, mx + mw, my + mh); my += mh + mg
        mInv.set(mx, my, mx + mw, my + mh); my += mh + mg
        mMap.set(mx, my, mx + mw, my + mh); my += mh + mg
        mSave.set(mx, my, mx + mw, my + mh); my += mh + mg
        mReset.set(mx, my, mx + mw, my + mh); my += mh + mg
        mHelp.set(mx, my, mx + mw, my + mh); my += mh + mg
        mRestart.set(mx, my, mx + mw, my + mh); my += mh + mg
        mQuit.set(mx, my, mx + mw, my + mh)
    }

    // ============================================================ LOGIQUE

    private fun update(dt: Float) {
        time += dt
        msgTimer -= dt
        boomFlash = (boomFlash - dt * 1.6f).coerceAtLeast(0f)
        damageT = (damageT - dt).coerceAtLeast(0f)
        keyAnim = (keyAnim - dt).coerceAtLeast(0f)
        simonFlashT = (simonFlashT - dt).coerceAtLeast(0f)
        attackCd = (attackCd - dt).coerceAtLeast(0f)
        attackAnim = (attackAnim - dt * 3f).coerceAtLeast(0f)
        if (state != PLAYING) return

        if (following) {
            camX += (fx - camX) * min(1f, dt * 8f)
            camY += (fy - camY) * min(1f, dt * 8f)
        }
        clampCam()

        // Lecture de la sequence de l'enigme des couleurs
        if (simonState == 1) {
            simonTimer -= dt
            if (simonTimer <= 0f) {
                simonPos++
                if (simonPos >= world.simonSeq.size) {
                    simonState = 2
                    simonInput = 0
                    showMsg("A vous : marchez sur les couleurs dans le meme ordre !")
                } else {
                    simonFlash = world.simonSeq[simonPos]
                    simonFlashT = 0.42f
                    simonTimer = 0.68f
                }
            }
        }

        if (gameOver || victory || showMenu || showInv || showHelp || showMap || miniPlate >= 0) return

        updateMobs(dt)

        if (pathStep < path.size) {
            walkPhase += dt * 12f
            val (tx, ty) = path[pathStep]
            val gx = tx + 0.5f
            val gy = ty + 0.5f
            val speed = 7f * dt
            val dx = gx - fx
            val dy = gy - fy
            if (abs(dx) > 0.01f || abs(dy) > 0.01f) {
                heroDir = if (abs(dx) >= abs(dy)) {
                    if (dx > 0) 3 else 2
                } else {
                    if (dy > 0) 0 else 1
                }
            }
            if (hypot(dx, dy) <= speed) {
                fx = gx; fy = gy
                hx = tx; hy = ty
                pathStep++
                onArrive()
                if (victory) return
            } else {
                fx += sign(dx) * min(abs(dx), speed)
                fy += sign(dy) * min(abs(dy), speed)
            }
        } else {
            // Deplacement direct au joystick
            if (joyOn && joyOwned && joyPointer >= 0 && hypot(joyDX, joyDY) > 0.4f) {
                following = true
                val dirX = if (abs(joyDX) >= abs(joyDY)) sign(joyDX).toInt() else 0
                val dirY = if (abs(joyDX) < abs(joyDY)) sign(joyDY).toInt() else 0
                val nx = hx + dirX
                val ny = hy + dirY
                val ni = world.idx(nx, ny)
                if (ni in world.blocks) {
                    tryPush(nx, ny)
                } else if (world.isWalkable(nx, ny)) {
                    clearPendings()
                    path = listOf(Pair(nx, ny))
                    pathStep = 0
                }
            }
            if (pendingReveal >= 0) {
                val i = pendingReveal; pendingReveal = -1
                doReveal(world.cx(i), world.cy(i)); saveGame()
            } else if (pendingDisarm >= 0) {
                val i = pendingDisarm; pendingDisarm = -1
                doDisarm(world.cx(i), world.cy(i)); saveGame()
            } else if (pendingChest) {
                pendingChest = false; openChest(); saveGame()
            } else if (pendingDoor) {
                pendingDoor = false; openDoor(); saveGame()
            } else if (pendingChest2) {
                pendingChest2 = false; openChest2(); saveGame()
            } else if (pendingChest3) {
                pendingChest3 = false; openChest3(); saveGame()
            } else if (pendingAltar) {
                pendingAltar = false; startSimon()
            } else if (pendingDoor1) {
                pendingDoor1 = false
                world.openDoor1(); saveGame()
                showMsg("La porte s'ouvre sur une salle plongee dans le noir...")
            } else if (pendingDoor2) {
                pendingDoor2 = false
                showMsg("Cette porte est scellee par une magie ancienne. (Prochaine mise a jour !)")
            } else if (pendingMini >= 0) {
                val p = pendingMini; pendingMini = -1
                openMini(p)
            } else if (pendingTorch >= 0) {
                val t = pendingTorch; pendingTorch = -1
                lightTorch(t)
            } else if (pendingDoor3) {
                pendingDoor3 = false
                if (!world.mobsDead) showMsg("Les 2 monstres gardent la porte : battez-vous !")
                else { world.openDoor3(); saveGame(); showMsg("La porte s'ouvre : l'etoile est la !") }
            }
        }
    }

    // ------------------------------------------------------------ combat

    private fun spawnMobs() {
        mobs.clear()
        val r = Random(world.seed + 77)
        for (k in 0..1) {
            val c = world.mobSpawn[k]
            mobs.add(Mob(world.cx(c) + 0.5f, world.cy(c) + 0.5f, 100, r.nextInt(8)))
        }
    }

    private fun updateMobs(dt: Float) {
        if (mobs.isEmpty()) return
        var alive = 0
        for (m in mobs) {
            m.hitT = (m.hitT - dt).coerceAtLeast(0f)
            if (m.hp <= 0) continue
            alive++
            val dx = fx - m.x
            val dy = fy - m.y
            val d = hypot(dx, dy)
            if (d > 0.8f) {
                val sp = 1.7f * dt
                val nx = m.x + dx / d * sp
                val ny = m.y + dy / d * sp
                if (world.isFloor(nx.toInt(), m.y.toInt())) m.x = nx
                if (world.isFloor(m.x.toInt(), ny.toInt())) m.y = ny
            }
            if (d < 0.85f) {
                if (!godMode) {
                    hp -= (16f * dt).toInt().coerceAtLeast(0)
                    if (time % 0.5f < dt) hp -= 1
                    if (hp <= 0) {
                        hp = 0
                        gameOver = true
                        prefs.edit().putBoolean("has", false).apply()
                    }
                }
                damageT = 0.25f
            }
        }
        if (alive == 0 && !world.mobsDead) {
            world.mobsDead = true
            showMsg("Les deux monstres sont vaincus ! La porte se deverrouille.")
            saveGame()
        }
    }

    private fun doAttack() {
        if (!swordOwned) { showMsg("Vous n'avez pas d'epee."); return }
        if (attackCd > 0f) return
        attackCd = 0.45f
        attackAnim = 1f
        var hit = false
        for (m in mobs) {
            if (m.hp <= 0) continue
            if (hypot(m.x - fx, m.y - fy) <= 1.45f) {
                m.hp -= 40
                m.hitT = 0.28f
                hit = true
                if (m.hp <= 0) showMsg("Monstre terrasse !")
            }
        }
        if (!hit) showMsg("Votre epee fend l'air...")
    }

    /** Arrivee sur une nouvelle case : coeurs, trappe, dalles colorees. */
    private fun onArrive() {
        val i = world.idx(hx, hy)

        // Le briquet, au centre de la salle des torches
        if (i == world.lighter && !world.lighterTaken) {
            world.lighterTaken = true
            lighterOwned = true
            showMsg("Vous ramassez un BRIQUET ! Allumez les 4 torches de la salle.")
            saveGame()
        }
        // Coeur ramasse
        if (i in world.hearts && i in world.revealed) {
            world.hearts.remove(i)
            heartsGot++
            hp = (hp + 20).coerceAtMost(100)
            showMsg("Un coeur ! +20 PV")
            saveGame()
        }
        // Descente par la trappe
        if (hx == world.trapX && hy == world.trapY && world.trapOpen) {
            hx = world.undergroundStartX
            hy = world.undergroundStartY
            fx = hx + 0.5f; fy = hy + 0.5f
            camX = fx; camY = fy
            path = emptyList(); pathStep = 0
            clearPendings()
            showMsg("Vous descendez par la trappe... un couloir sombre s'etend devant vous.")
            saveGame()
            return
        }
        // L'etoile : victoire
        if (hx == world.exitX && hy == world.exitY) {
            victory = true
            prefs.edit().putBoolean("has", false).apply()
            return
        }
        // Saisie de l'enigme des couleurs
        if (simonState == 2) {
            for (k in 0..3) {
                if (i == world.simonTiles[k]) {
                    simonFlash = k
                    simonFlashT = 0.3f
                    if (k == world.simonSeq[simonInput]) {
                        simonInput++
                        if (simonInput >= world.simonSeq.size) {
                            simonState = 0
                            world.spawnAfterSimon()
                            showMsg("L'enigme est resolue ! Un coffre et deux portes apparaissent !")
                            saveGame()
                        }
                    } else {
                        simonState = 0
                        hurt(5, "Mauvaise couleur ! Retouchez le socle pour reecouter.")
                    }
                }
            }
        }
    }

    private fun clampCam() {
        val visW = width / tile
        val visH = (boardBottom - boardTop) / tile
        val w = world.wid.toFloat()
        val h = world.hei.toFloat()
        camX = if (w <= visW) w / 2f else camX.coerceIn(visW / 2f, w - visW / 2f)
        camY = if (h <= visH) h / 2f else camY.coerceIn(visH / 2f, h - visH / 2f)
    }

    private fun clearPendings() {
        pendingReveal = -1; pendingDisarm = -1
        pendingChest = false; pendingDoor = false
        pendingChest2 = false; pendingChest3 = false
        pendingAltar = false; pendingDoor1 = false; pendingDoor2 = false
        pendingMini = -1; pendingTorch = -1; pendingDoor3 = false
    }

    private fun hurt(n: Int, why: String) {
        if (godMode) { showMsg("$why (mode test : aucun degat)"); return }
        hp -= n
        showMsg(why)
        if (hp <= 0) {
            hp = 0
            gameOver = true
            prefs.edit().putBoolean("has", false).apply()
        }
    }

    private fun doReveal(x: Int, y: Int) {
        val i = world.idx(x, y)
        if (i in world.flagged || i in world.revealed) return
        if (i in world.mines) {
            world.mines.remove(i)
            world.exploded.add(i)
            world.revealed.add(i)
            boomFlash = 1f
            hurt(20, "BOUM ! -20 PV. Marquez d'abord (appui long) puis desamorcez !")
        } else {
            world.revealCascade(x, y)
            val n = world.countAround(x, y)
            showMsg(if (n == 0) "Dalle sure : zone degagee !" else "Dalle sure : $n mine(s) autour.")
        }
    }

    private fun doDisarm(x: Int, y: Int) {
        val i = world.idx(x, y)
        world.flagged.remove(i)
        if (i in world.mines) {
            world.mines.remove(i)
            world.defused.add(i)
            world.revealed.add(i)
            disarmed++
            showMsg("Mine desamorcee ! Drapeaux restants : $flagsLeft")
        } else {
            world.revealCascade(x, y)
            showMsg("Fausse alerte : drapeau gaspille ! Restants : $flagsLeft")
        }
    }

    private fun openChest() {
        if (!world.platesSolved()) {
            showMsg("Le coffre est scelle... Resolvez l'enigme de cette salle pour l'ouvrir.")
            return
        }
        if (world.chestOpen) { showMsg("Le coffre est vide."); return }
        world.chestOpen = true
        world.hasKey = true
        flagsLeft += 5
        keyAnim = 3f
        showMsg("Le coffre s'ouvre... une CLE EN OR s'en echappe ! (+5 drapeaux)")
    }

    private fun openChest2() {
        if (world.chest2Open) { showMsg("Ce coffre est vide."); return }
        world.chest2Open = true
        joyOwned = true
        showMsg("Un JOYSTICK ! Activez-le depuis l'inventaire (menu ☰).")
    }

    private fun openChest3() {
        if (world.chest3Open) { showMsg("Le coffre-fort est vide."); return }
        world.chest3Open = true
        swordOwned = true
        energyCount++
        flagsLeft += 5
        showMsg("Le coffre-fort s'ouvre : une EPEE, une canette d'energie et 5 drapeaux !")
    }

    private fun openDoor() {
        if (!world.hasKey) { showMsg("Porte verrouillee. Il faut la cle du coffre."); return }
        world.grid[world.door] = World.FLOOR
        world.revealed.add(world.door)
        world.revealRoomB()
        showMsg("La cle tourne dans la serrure... la porte s'ouvre !")
    }

    private fun startSimon() {
        if (world.simonSolved) { showMsg("Le socle est eteint : l'enigme est deja resolue."); return }
        simonState = 1
        simonPos = -1
        simonTimer = 0.7f
        showMsg("Le socle s'illumine... memorisez l'ordre des couleurs !")
    }

    private fun lightTorch(i: Int) {
        if (!lighterOwned) { showMsg("Il vous faut un briquet ! Il est au centre de la salle."); return }
        if (i in world.torchLit) { showMsg("Cette torche brule deja."); return }
        world.torchLit.add(i)
        val n = world.torchLit.size
        if (n >= 4 && !world.sokoban2Spawned) {
            world.spawnSokoban2()
            showMsg("Les 4 torches brulent ! Des caisses tombent du plafond...")
        } else {
            showMsg("Torche allumee ($n/4).")
        }
        saveGame()
    }

    // ------------------------------------------------------------ mini-demineur

    /** Genere le mini-plateau 3x3 de la dalle p (reproductible via la graine). */
    private fun openMini(p: Int) {
        miniPlate = p
        miniLayout.clear(); miniRev.clear(); miniFlag.clear()
        val r = Random(world.seed + p)
        while (miniLayout.size < 2) miniLayout.add(r.nextInt(9))
    }

    private fun miniCount(c: Int): Int {
        val x = c % 3
        val y = c / 3
        var n = 0
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = x + dx
            val ny = y + dy
            if (nx in 0..2 && ny in 0..2 && (ny * 3 + nx) in miniLayout) n++
        }
        return n
    }

    private fun miniTap(c: Int, long: Boolean) {
        if (c in miniRev) return
        if (long) {
            if (c in miniFlag) miniFlag.remove(c) else miniFlag.add(c)
            return
        }
        if (c in miniFlag) return
        if (c in miniLayout) {
            hurt(10, "BOUM ! Le mini-demineur se reinitialise. -10 PV")
            miniRev.clear(); miniFlag.clear()
            if (gameOver) miniPlate = -1
            return
        }
        miniRev.add(c)
        if (miniRev.size >= 9 - miniLayout.size) {
            world.plateSolved.add(miniPlate)
            miniPlate = -1
            val n = world.plateSolved.size
            showMsg("Dalle decouverte ! ($n/3) Vous pouvez y pousser un bloc.")
            saveGame()
        }
    }

    // ------------------------------------------------------------ actions

    private fun walkNextTo(gx: Int, gy: Int): Boolean {
        var best: List<Pair<Int, Int>>? = null
        for ((dx, dy) in listOf(Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1))) {
            val nx = gx + dx
            val ny = gy + dy
            if (!world.isWalkable(nx, ny)) continue
            val p = world.findPath(hx, hy, nx, ny) ?: continue
            if (best == null || p.size < best!!.size) best = p
        }
        val b = best ?: return false
        path = b
        pathStep = 0
        return true
    }

    private fun tryPush(bxx: Int, byy: Int): Boolean {
        val dx = bxx - hx
        val dy = byy - hy
        if (abs(dx) + abs(dy) != 1) return false
        val tx = bxx + dx
        val ty = byy + dy
        if (!world.canPushInto(tx, ty)) {
            val ti = world.idx(tx, ty)
            if (ti in world.plates && ti !in world.plateSolved) {
                showMsg("Cette dalle est recouverte : resolvez d'abord son mini-demineur !")
            } else {
                showMsg("La caisse ne peut pas aller plus loin.")
            }
            return true
        }
        world.blocks.remove(world.idx(bxx, byy))
        world.blocks.add(world.idx(tx, ty))
        world.revealed.add(world.idx(tx, ty))
        world.revealed.add(world.idx(bxx, byy))
        clearPendings()
        path = listOf(Pair(bxx, byy))
        pathStep = 0

        if (world.sokoban2Solved() && !world.door3Spawned) {
            world.spawnDoor3AndMobs()
            spawnMobs()
            showMsg("Un grondement... une PORTE apparait, mais 2 MONSTRES surgissent !")
        } else if (world.trapSolved() && !world.trapOpen) {
            world.trapOpen = true
            world.chest2Spawned = true
            world.revealed.add(world.idx(world.trapX, world.trapY))
            showMsg("CLAC ! La TRAPPE s'ouvre... et un coffre apparait dans la salle !")
        } else if (world.platesSolved() && !world.chestOpen) {
            showMsg("Un declic sourd... le coffre est deverrouille !")
        } else {
            val d = world.plates.count { it in world.blocks }
            val c = world.targets.count { it in world.blocks }
            val c2 = world.targets2.count { it in world.blocks }
            showMsg(
                when {
                    world.sokoban2Spawned -> "Caisses rangees : $c2 / 5"
                    world.hasKey -> "Caisses rangees : $c / 4"
                    else -> "Blocs en place : $d / 3"
                }
            )
        }
        saveGame()
        return true
    }

    private fun onTap(gx: Int, gy: Int) {
        if (gameOver || victory) return
        val i = world.idx(gx, gy)

        // Dalle de pression recouverte : ouvrir le mini-demineur
        if (i in world.plates && i !in world.plateSolved && i !in world.blocks) {
            if (!walkNextTo(gx, gy)) { showMsg("Approchez-vous de la dalle."); return }
            clearPendings(); pendingMini = i
            return
        }
        if (world.isDoor(gx, gy)) {
            if (!walkNextTo(gx, gy)) { showMsg("Approchez-vous de la porte."); return }
            clearPendings()
            when (i) {
                world.door1 -> pendingDoor1 = true
                world.door2 -> pendingDoor2 = true
                world.door3 -> pendingDoor3 = true
                else -> pendingDoor = true
            }
            return
        }
        if (world.isTorch(i)) {
            if (!walkNextTo(gx, gy)) { showMsg("Approchez-vous de la torche."); return }
            clearPendings(); pendingTorch = i
            return
        }
        if (!world.isFloor(gx, gy)) { showMsg("C'est un mur."); return }

        if (i == world.altar) {
            if (!walkNextTo(gx, gy)) { showMsg("Approchez-vous du socle."); return }
            clearPendings(); pendingAltar = true
            return
        }
        if (i in world.blocks) {
            if (tryPush(gx, gy)) return
            if (!walkNextTo(gx, gy)) { showMsg("Caisse inaccessible."); return }
            clearPendings()
            showMsg("Le heros se place a cote. Retouchez la caisse pour pousser.")
            return
        }
        if (i == world.chest) {
            if (!walkNextTo(gx, gy)) { showMsg("Coffre inaccessible."); return }
            clearPendings(); pendingChest = true
            return
        }
        if (world.chest2Spawned && i == world.chest2) {
            if (!walkNextTo(gx, gy)) { showMsg("Coffre inaccessible."); return }
            clearPendings(); pendingChest2 = true
            return
        }
        if (world.chest3Spawned && i == world.chest3) {
            if (!walkNextTo(gx, gy)) { showMsg("Coffre inaccessible."); return }
            clearPendings(); pendingChest3 = true
            return
        }
        if (i in world.revealed && i !in world.mines && i !in world.flagged) {
            val p = world.findPath(hx, hy, gx, gy)
            if (p == null) { showMsg("Aucun chemin sur vers cette dalle."); return }
            clearPendings()
            path = p; pathStep = 0
            return
        }
        if (i in world.flagged) {
            if (!walkNextTo(gx, gy)) { showMsg("Dalle inaccessible."); return }
            clearPendings(); pendingDisarm = i
            return
        }
        if (!walkNextTo(gx, gy)) { showMsg("Le heros ne peut pas atteindre cette dalle."); return }
        clearPendings(); pendingReveal = i
    }

    private fun onFlag(gx: Int, gy: Int) {
        if (gameOver || victory) return
        if (!world.isFloor(gx, gy)) return
        val i = world.idx(gx, gy)
        if (i in world.blocks || i == world.chest) return
        if (i in world.revealed) { showMsg("Dalle deja revelee."); return }
        if (i in world.flagged) {
            world.flagged.remove(i); flagsLeft++
            showMsg("Drapeau recupere. Restants : $flagsLeft")
        } else {
            if (flagsLeft <= 0) { showMsg("Plus de drapeaux ! Retirez-en un ailleurs."); return }
            world.flagged.add(i); flagsLeft--
            showMsg("Drapeau pose ($flagsLeft). Retouchez la dalle pour desamorcer.")
        }
        saveGame()
    }

    // ============================================================ RENDU

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = ((now - lastTime) / 1e9).toFloat().coerceIn(0f, 0.05f)
        lastTime = now
        update(dt)

        val w = width.toFloat()
        val h = height.toFloat()
        paint.style = Paint.Style.FILL

        if (state == TITLE) {
            drawTitle(canvas, w, h)
            if (showHelp) drawHelp(canvas, w, h)
            postInvalidateOnAnimation()
            return
        }

        paint.color = Color.rgb(9, 11, 18)
        canvas.drawRect(0f, 0f, w, h, paint)

        canvas.save()
        canvas.clipRect(0f, boardTop, w, boardBottom)
        drawBoard(canvas, w)
        canvas.restore()

        drawHud(canvas, w, h)
        if (joyOn && joyOwned) drawJoystick(canvas)

        if (boomFlash > 0f) {
            paint.color = Color.argb((boomFlash * 170).toInt(), 255, 120, 30)
            canvas.drawRect(0f, 0f, w, h, paint)
        }
        if (damageT > 0f) {
            paint.color = Color.argb((damageT * 320).toInt().coerceAtMost(140), 220, 30, 30)
            canvas.drawRect(0f, 0f, w, h, paint)
        }
        if (showMap) drawMap(canvas, w, h)
        if (miniPlate >= 0) drawMini(canvas, w, h)
        if (showMenu) drawMenu(canvas, w, h)
        if (showInv) drawInventory(canvas, w, h)
        if (showHelp) drawHelp(canvas, w, h)
        if (gameOver || victory) drawEnd(canvas, w, h)

        postInvalidateOnAnimation()
    }

    // ---------------------------------------------------------- habillage donjon

    /** Mur de pierre en fond, assombri, avec vignette. */
    private fun drawStoneBg(canvas: Canvas, w: Float, h: Float, dark: Int = 150) {
        val t = h * 0.11f
        var y = 0f
        var row = 0
        while (y < h) {
            var x = 0f
            while (x < w) {
                tmpRect.set(x, y, x + t, y + t)
                drawTex(canvas, if ((row + (x / t).toInt()) % 5 == 0) sWallMossy else sWall, tmpRect)
                x += t
            }
            y += t
            row++
        }
        paint.color = Color.argb(dark, 8, 9, 16)
        canvas.drawRect(0f, 0f, w, h, paint)
        // vignette
        paint.color = Color.argb(120, 0, 0, 0)
        canvas.drawRect(0f, 0f, w, h * 0.06f, paint)
        canvas.drawRect(0f, h * 0.94f, w, h, paint)
    }

    /** Braises qui montent doucement. */
    private fun drawEmbers(canvas: Canvas, w: Float, h: Float) {
        for (k in 0 until 26) {
            val seedX = ((k * 7919) % 1000) / 1000f
            val sp = 0.35f + ((k * 31) % 50) / 100f
            val yy = h * (1.05f - ((time * sp * 0.12f + seedX) % 1.15f))
            val xx = w * seedX + sin(time * 1.4f + k) * w * 0.03f
            val a = (110 * (1f - (h - yy) / h)).toInt().coerceIn(20, 130)
            paint.color = Color.argb(a, 255, 170 + (k % 3) * 25, 70)
            canvas.drawCircle(xx, yy, h * 0.0035f + (k % 3) * h * 0.0012f, paint)
        }
    }

    /** Torche murale decorative, avec halo. */
    private fun drawWallTorch(canvas: Canvas, cxx: Float, cyy: Float, size: Float, k: Int) {
        val f = 0.92f + 0.1f * sin(time * 9f + k * 2.1f)
        paint.color = Color.argb(48, 255, 165, 60)
        canvas.drawCircle(cxx, cyy, size * 1.1f * f, paint)
        paint.color = Color.argb(28, 255, 200, 110)
        canvas.drawCircle(cxx, cyy, size * 1.9f * f, paint)
        drawSprite(canvas, sTorchLit, cxx, cyy, size * f)
    }

    /** Cadre de pierre a bordure doree. */
    private fun drawFrame(canvas: Canvas, r: RectF, fill: Int, gold: Int) {
        val rad = r.height() * 0.22f
        paint.color = fill
        canvas.drawRoundRect(r, rad, rad, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r.height() * 0.055f
        paint.color = gold
        canvas.drawRoundRect(r, rad, rad, paint)
        paint.strokeWidth = r.height() * 0.018f
        paint.color = Color.argb(70, 255, 255, 255)
        tmpRect.set(r.left + r.height() * 0.08f, r.top + r.height() * 0.09f, r.right - r.height() * 0.08f, r.bottom - r.height() * 0.09f)
        canvas.drawRoundRect(tmpRect, rad * 0.7f, rad * 0.7f, paint)
        paint.style = Paint.Style.FILL
    }

    // ---------------------------------------------------------- titre

    private fun drawTitle(canvas: Canvas, w: Float, h: Float) {
        drawStoneBg(canvas, w, h, 165)

        // Torches de part et d'autre du titre
        drawWallTorch(canvas, w * 0.13f, h * 0.13f, h * 0.075f, 0)
        drawWallTorch(canvas, w * 0.87f, h * 0.13f, h * 0.075f, 1)

        // Titre grave dans la pierre
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.textSize = h * 0.062f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = h * 0.009f
        paint.color = Color.rgb(60, 40, 12)
        canvas.drawText("MINELAB", w / 2f, h * 0.135f, paint)
        paint.style = Paint.Style.FILL
        val glow = 0.5f + 0.5f * sin(time * 2f)
        paint.color = Color.rgb(255, (196 + 30 * glow).toInt(), 80)
        canvas.drawText("MINELAB", w / 2f, h * 0.135f, paint)
        paint.isFakeBoldText = false
        paint.color = Color.rgb(190, 168, 128)
        paint.textSize = h * 0.018f
        canvas.drawText("~ Demineur, enigmes et tresors ~", w / 2f, h * 0.172f, paint)

        // Petit trophee : cle + coffre + epee autour du titre
        drawSprite(canvas, sChestClosed, w * 0.16f, h * 0.225f, h * 0.055f, 200)
        drawSprite(canvas, sSwordV, w * 0.84f, h * 0.225f, h * 0.055f, 200)

        // --- Panneaux
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.rgb(200, 178, 138)
        paint.textSize = h * 0.016f
        paint.isFakeBoldText = true
        canvas.drawText("NOM DU HEROS", tName.left + h * 0.008f, tName.top - h * 0.011f, paint)
        canvas.drawText("DIFFICULTE", tDiff[0].left + h * 0.008f, tDiff[0].top - h * 0.011f, paint)
        paint.isFakeBoldText = false

        drawPanelBtn(canvas, tName, playerName, false)
        for (k in 0..2) drawPanelBtn(canvas, tDiff[k], diffName(k), difficulty == k)
        drawPanelBtn(canvas, tGod, if (godMode) "VIE ILLIMITEE : ON" else "VIE ILLIMITEE : OFF", godMode)
        drawPanelBtn(canvas, tNew, "NOUVELLE PARTIE", false)
        drawPanelBtn(canvas, tCont, if (hasSave()) "CONTINUER" else "AUCUNE SAUVEGARDE", false, hasSave())
        drawPanelBtn(canvas, tHelp, "COMMENT JOUER ?", false)

        // --- Le heros fait les cent pas sur une dalle de pierre
        val fh = h * 0.09f
        val fy = h * 0.945f
        var x = 0f
        while (x < w) {
            tmpRect.set(x, fy - fh * 0.55f, x + fh, fy + fh * 0.45f)
            drawTex(canvas, sFloor, tmpRect)
            x += fh
        }
        paint.color = Color.argb(90, 10, 12, 20)
        canvas.drawRect(0f, fy - fh * 0.55f, w, h, paint)

        val span = w * 0.62f
        val phase = (time * 0.22f) % 2f
        val tt = if (phase < 1f) phase else 2f - phase
        val hxp = w * 0.19f + span * tt
        val goingRight = phase < 1f
        val bob = abs(sin(time * 7f)) * h * 0.006f
        paint.color = Color.argb(90, 0, 0, 0)
        canvas.drawOval(hxp - h * 0.026f, fy - h * 0.004f, hxp + h * 0.026f, fy + h * 0.008f, paint)
        drawSprite(canvas, if (goingRight) sHeroRight else sHeroLeft, hxp, fy - h * 0.033f - bob, h * 0.1f)

        // Une caisse et une bombe posees au sol, pour l'ambiance
        drawSprite(canvas, sCrate, w * 0.08f, fy - h * 0.022f, h * 0.062f)
        drawSprite(canvas, sBomb, w * 0.92f, fy - h * 0.016f, h * 0.05f)

        drawEmbers(canvas, w, h)
    }

    private fun drawPanelBtn(canvas: Canvas, r: RectF, label: String, on: Boolean, enabled: Boolean = true) {
        val fill = when {
            !enabled -> Color.rgb(30, 30, 38)
            on -> Color.rgb(132, 84, 24)
            else -> Color.rgb(46, 44, 54)
        }
        val gold = when {
            !enabled -> Color.rgb(70, 66, 62)
            on -> Color.rgb(255, 208, 96)
            else -> Color.rgb(168, 136, 72)
        }
        if (on) {
            val pulse = 0.5f + 0.5f * sin(time * 3f)
            paint.color = Color.argb((30 + 40 * pulse).toInt(), 255, 200, 90)
            tmpRect.set(r.left - r.height() * 0.12f, r.top - r.height() * 0.12f, r.right + r.height() * 0.12f, r.bottom + r.height() * 0.12f)
            canvas.drawRoundRect(tmpRect, r.height() * 0.3f, r.height() * 0.3f, paint)
        }
        drawFrame(canvas, r, fill, gold)
        paint.color = if (enabled) Color.rgb(248, 238, 214) else Color.rgb(96, 96, 104)
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.textSize = r.height() * 0.33f
        canvas.drawText(label, r.centerX(), r.centerY() + r.height() * 0.12f, paint)
        paint.isFakeBoldText = false
    }

    // ---------------------------------------------------------- plateau

    private fun sx(gx: Float, w: Float) = w / 2f + (gx - camX) * tile
    private fun sy(gy: Float) = (boardTop + boardBottom) / 2f + (gy - camY) * tile

    private fun drawBoard(canvas: Canvas, w: Float) {
        val boardH = boardBottom - boardTop
        val nx = (w / tile).toInt() / 2 + 2
        val ny = (boardH / tile).toInt() / 2 + 2
        val cgx = camX.toInt()
        val cgy = camY.toInt()
        val gap = tile * 0.045f
        val rad = tile * 0.16f

        for (gy in cgy - ny..cgy + ny) {
            for (gx in cgx - nx..cgx + nx) {
                if (!world.inside(gx, gy)) continue
                val l = sx(gx.toFloat(), w)
                val t = sy(gy.toFloat())
                if (l > w || t > boardBottom || l + tile < 0f || t + tile < boardTop) continue
                rect.set(l + gap, t + gap, l + tile - gap, t + tile - gap)
                val i = world.idx(gx, gy)

                if (world.grid[i] == World.WALL) { drawWall(canvas, gx, gy); continue }
                if (world.grid[i] == World.DOOR) { drawDoor(canvas); continue }

                val rev = i in world.revealed
                val isTrap = gx == world.trapX && gy == world.trapY
                val isExit = gx == world.exitX && gy == world.exitY

                when {
                    isTrap && rev -> drawTrap(canvas)
                    i in world.exploded -> drawBomb(canvas, true)
                    i in world.defused -> drawBomb(canvas, false)
                    rev -> {
                        val tex = when {
                            gy >= world.uy0 -> sFloorWood            // sous-sol : dalles ornees
                            gx > world.hallW -> sFloorRoom           // salles d'enigme : marbre
                            else -> sFloor                           // grand demineur : dalles claires
                        }
                        tmpRect.set(rect.left - tile * 0.04f, rect.top - tile * 0.04f, rect.right + tile * 0.04f, rect.bottom + tile * 0.04f)
                        drawTex(canvas, tex, tmpRect)
                        if (i in world.plates) {
                            if (i in world.plateSolved) drawPlate(canvas, i) else drawCoveredPlate(canvas)
                        }
                        if (i in world.targets || i in world.targets2) drawTarget(canvas, i)
                        if (world.isTorch(i)) drawFloorTorch(canvas, i)
                        if (i == world.lighter && !world.lighterTaken) drawLighter(canvas)
                        var k = -1
                        for (s in 0..3) if (i == world.simonTiles[s]) k = s
                        if (k >= 0) drawSimonTile(canvas, k)
                        if (i == world.altar) drawAltar(canvas)
                        if (isExit) drawStar(canvas)
                        if (i in world.hearts) drawHeart(canvas)
                        val n = world.countAround(gx, gy)
                        if (n > 0 && i !in world.plates && i !in world.targets && i !in world.targets2 &&
                            k < 0 && i != world.altar && !isExit && i !in world.hearts &&
                            !world.isTorch(i) && i != world.lighter
                        ) {
                            paint.textAlign = Paint.Align.CENTER
                            paint.textSize = tile * 0.58f
                            paint.isFakeBoldText = true
                            paint.style = Paint.Style.STROKE
                            paint.strokeWidth = tile * 0.06f
                            paint.color = Color.argb(190, 245, 245, 250)
                            canvas.drawText("$n", rect.centerX(), rect.centerY() + tile * 0.21f, paint)
                            paint.style = Paint.Style.FILL
                            paint.color = numberColor(n)
                            canvas.drawText("$n", rect.centerX(), rect.centerY() + tile * 0.21f, paint)
                            paint.isFakeBoldText = false
                        }
                    }
                    else -> {
                        paint.color = Color.rgb(18, 22, 30)
                        canvas.drawRoundRect(rect, rad, rad, paint)
                        tmpRect.set(rect)
                        tmpRect.inset(tile * 0.03f, tile * 0.05f)
                        tmpRect.offset(0f, -tile * 0.02f)
                        drawTex(canvas, sFloorDark, tmpRect)
                        paint.color = Color.argb(70, 40, 60, 110)
                        canvas.drawRect(tmpRect, paint)
                        if (i in world.flagged) drawFlag(canvas)
                    }
                }
                if (i == world.chest) drawChest(canvas, world.platesSolved(), world.chestOpen, true)
                if (world.chest2Spawned && i == world.chest2) drawChest(canvas, true, world.chest2Open, false)
                if (world.chest3Spawned && i == world.chest3) drawChest(canvas, true, world.chest3Open, false, vault = true)
                if (i in world.blocks) drawCrate(canvas, i in world.plates || i in world.targets || i in world.targets2)
            }
        }
        drawHero(canvas, w)
    }

    /** Dessine un sprite centre sur (cx,cy) a la taille demandee. */
    private fun drawSprite(canvas: Canvas, bmp: Bitmap, cxx: Float, cyy: Float, size: Float, alpha: Int = 255) {
        bmpPaint.alpha = alpha
        tmpRect.set(cxx - size / 2f, cyy - size / 2f, cxx + size / 2f, cyy + size / 2f)
        canvas.drawBitmap(bmp, null, tmpRect, bmpPaint)
        bmpPaint.alpha = 255
    }

    private fun drawTex(canvas: Canvas, bmp: Bitmap, r: RectF, alpha: Int = 255) {
        bmpPaint.alpha = alpha
        canvas.drawBitmap(bmp, null, r, bmpPaint)
        bmpPaint.alpha = 255
    }

    private fun drawWall(canvas: Canvas, gx: Int, gy: Int) {
        val under = gy >= world.uy0
        tmpRect.set(rect.left - tile * 0.045f, rect.top - tile * 0.045f, rect.right + tile * 0.045f, rect.bottom + tile * 0.045f)
        drawTex(canvas, if (under) sWallMossy else sWall, tmpRect)
        paint.color = Color.argb(if (under) 95 else 75, 0, 0, 0)
        canvas.drawRect(tmpRect, paint)

        // Torche : uniquement sur un mur qui BORDE une salle, accrochee sur sa face,
        // et seulement si la salle en question est deja decouverte.
        val below = world.idx(gx, gy + 1)
        val onFace = world.isFloor(gx, gy + 1) && below in world.revealed
        if (onFace && gx % 3 == 1) {
            val flicker = 0.93f + 0.09f * sin(time * 9f + gx * 1.7f)
            val txx = rect.centerX()
            val tyy = rect.bottom - tile * 0.06f
            paint.color = Color.argb(58, 255, 165, 60)
            canvas.drawCircle(txx, tyy, tile * 0.62f * flicker, paint)
            paint.color = Color.argb(40, 255, 190, 90)
            canvas.drawCircle(txx, tyy, tile * 1.05f * flicker, paint)
            drawSprite(canvas, sTorchLit, txx, tyy - tile * 0.06f, tile * 0.85f * flicker)
        }
    }

    private fun drawDoor(canvas: Canvas) {
        val rad = tile * 0.1f
        paint.color = Color.rgb(96, 56, 140)
        canvas.drawRoundRect(rect, rad, rad, paint)
        tmpRect.set(rect)
        tmpRect.inset(tile * 0.07f, tile * 0.07f)
        paint.color = Color.rgb(126, 78, 178)
        canvas.drawRoundRect(tmpRect, rad, rad, paint)
        paint.color = Color.rgb(248, 214, 96)
        canvas.drawCircle(rect.centerX(), rect.centerY() - tile * 0.04f, tile * 0.1f, paint)
        val p = Path()
        p.moveTo(rect.centerX() - tile * 0.05f, rect.centerY() + tile * 0.19f)
        p.lineTo(rect.centerX() + tile * 0.05f, rect.centerY() + tile * 0.19f)
        p.lineTo(rect.centerX() + tile * 0.03f, rect.centerY() + tile * 0.02f)
        p.lineTo(rect.centerX() - tile * 0.03f, rect.centerY() + tile * 0.02f)
        p.close()
        canvas.drawPath(p, paint)
        paint.color = Color.rgb(60, 34, 90)
        canvas.drawCircle(rect.centerX(), rect.centerY() - tile * 0.04f, tile * 0.04f, paint)
    }

    private fun drawBomb(canvas: Canvas, boom: Boolean) {
        val rad = tile * 0.16f
        paint.color = if (boom) Color.rgb(120, 40, 38) else Color.rgb(64, 72, 86)
        canvas.drawRoundRect(rect, rad, rad, paint)
        if (boom) {
            val pulse = 0.5f + 0.5f * sin(time * 6f)
            paint.color = Color.argb((50 + 50 * pulse).toInt(), 255, 140, 40)
            canvas.drawCircle(rect.centerX(), rect.centerY(), tile * 0.42f, paint)
        }
        drawSprite(canvas, sBomb, rect.centerX(), rect.centerY(), tile * 0.78f, if (boom) 255 else 150)
        if (!boom) {
            paint.color = Color.rgb(80, 220, 130)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = tile * 0.42f
            paint.isFakeBoldText = true
            canvas.drawText("✓", rect.centerX(), rect.centerY() + tile * 0.15f, paint)
            paint.isFakeBoldText = false
        }
    }

    private fun drawPlate(canvas: Canvas, i: Int) {
        drawSprite(canvas, sPlate, rect.centerX(), rect.centerY(), tile * 0.94f)
        if (i !in world.blocks) {
            val pulse = 0.5f + 0.5f * sin(time * 3f)
            paint.color = Color.argb((50 + 50 * pulse).toInt(), 255, 190, 70)
            canvas.drawCircle(rect.centerX(), rect.centerY(), tile * 0.22f, paint)
        }
    }

    /** Dalle de pression encore recouverte : petite grille 3x3 a resoudre. */
    private fun drawCoveredPlate(canvas: Canvas) {
        tmpRect.set(rect)
        tmpRect.inset(tile * 0.08f, tile * 0.08f)
        paint.color = Color.rgb(46, 54, 70)
        canvas.drawRoundRect(tmpRect, tile * 0.06f, tile * 0.06f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = tile * 0.025f
        paint.color = Color.rgb(96, 108, 132)
        val x1 = tmpRect.left + tmpRect.width() / 3f
        val x2 = tmpRect.left + tmpRect.width() * 2f / 3f
        val y1 = tmpRect.top + tmpRect.height() / 3f
        val y2 = tmpRect.top + tmpRect.height() * 2f / 3f
        canvas.drawLine(x1, tmpRect.top, x1, tmpRect.bottom, paint)
        canvas.drawLine(x2, tmpRect.top, x2, tmpRect.bottom, paint)
        canvas.drawLine(tmpRect.left, y1, tmpRect.right, y1, paint)
        canvas.drawLine(tmpRect.left, y2, tmpRect.right, y2, paint)
        canvas.drawRoundRect(tmpRect, tile * 0.06f, tile * 0.06f, paint)
        paint.style = Paint.Style.FILL
        val pulse = 0.5f + 0.5f * sin(time * 3f)
        paint.color = Color.argb((170 + 85 * pulse).toInt(), 255, 210, 90)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = tile * 0.34f
        paint.isFakeBoldText = true
        canvas.drawText("?", rect.centerX(), rect.centerY() + tile * 0.12f, paint)
        paint.isFakeBoldText = false
    }

    /** Torche murale de la salle des torches : eteinte ou allumee. */
    private fun drawFloorTorch(canvas: Canvas, i: Int) {
        val lit = i in world.torchLit
        val cxx = rect.centerX()
        val cyy = rect.centerY()
        if (lit) {
            val flicker = 0.92f + 0.1f * sin(time * 9f + i)
            paint.color = Color.argb(70, 255, 170, 60)
            canvas.drawCircle(cxx, cyy, tile * 0.75f * flicker, paint)
            paint.color = Color.argb(45, 255, 200, 110)
            canvas.drawCircle(cxx, cyy, tile * 1.25f * flicker, paint)
            drawSprite(canvas, sTorchLit, cxx, cyy, tile * 1.0f * flicker)
        } else {
            drawSprite(canvas, sTorchOff, cxx, cyy, tile * 0.95f)
            if (lighterOwned) {
                val pulse = 0.5f + 0.5f * sin(time * 4f)
                paint.color = Color.argb((40 + 60 * pulse).toInt(), 255, 190, 90)
                canvas.drawCircle(cxx, cyy, tile * 0.4f, paint)
            }
        }
    }

    private fun drawLighter(canvas: Canvas) {
        val bob = sin(time * 3f) * tile * 0.05f
        val pulse = 0.5f + 0.5f * sin(time * 4f)
        paint.color = Color.argb((50 + 60 * pulse).toInt(), 255, 200, 110)
        canvas.drawCircle(rect.centerX(), rect.centerY() + bob, tile * 0.42f, paint)
        drawSprite(canvas, sLighter, rect.centerX(), rect.centerY() + bob, tile * 0.72f)
    }

    private fun drawMobs(canvas: Canvas, w: Float) {
        for (m in mobs) {
            if (m.hp <= 0) continue
            val cxx = sx(m.x, w)
            val cyy = sy(m.y)
            val bounce = abs(sin(time * 4f + m.sprite)) * tile * 0.06f
            paint.color = Color.argb(95, 0, 0, 0)
            canvas.drawOval(cxx - tile * 0.28f, cyy + tile * 0.2f, cxx + tile * 0.28f, cyy + tile * 0.34f, paint)
            val alpha = if (m.hitT > 0f) 160 else 255
            drawSprite(canvas, sMonsters[m.sprite], cxx, cyy - tile * 0.14f - bounce, tile * 1.15f, alpha)
            if (m.hitT > 0f) {
                paint.color = Color.argb((150 * (m.hitT / 0.28f)).toInt(), 255, 70, 60)
                canvas.drawCircle(cxx, cyy - tile * 0.15f, tile * 0.5f, paint)
            }
            // Barre de vie
            val bw = tile * 0.6f
            paint.color = Color.argb(200, 25, 25, 30)
            canvas.drawRect(cxx - bw, cyy - tile * 0.78f, cxx + bw, cyy - tile * 0.68f, paint)
            paint.color = Color.rgb(220, 60, 55)
            canvas.drawRect(cxx - bw, cyy - tile * 0.78f, cxx - bw + 2 * bw * (m.hp / 100f), cyy - tile * 0.68f, paint)
        }
    }

    private fun drawTarget(canvas: Canvas, i: Int) {
        val on = i in world.blocks
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = tile * 0.07f
        paint.color = if (on) Color.rgb(60, 190, 110) else Color.rgb(64, 152, 230)
        tmpRect.set(rect)
        tmpRect.inset(tile * 0.15f, tile * 0.15f)
        canvas.drawRoundRect(tmpRect, tile * 0.05f, tile * 0.05f, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(rect.centerX(), rect.centerY(), tile * 0.06f, paint)
    }

    private fun drawSimonTile(canvas: Canvas, k: Int) {
        val flashing = simonFlash == k && simonFlashT > 0f
        val base = simonColors[k]
        paint.color = if (flashing) base else Color.argb(
            255,
            (Color.red(base) * 0.45f).toInt(),
            (Color.green(base) * 0.45f).toInt(),
            (Color.blue(base) * 0.45f).toInt()
        )
        tmpRect.set(rect)
        tmpRect.inset(tile * 0.08f, tile * 0.08f)
        canvas.drawRoundRect(tmpRect, tile * 0.1f, tile * 0.1f, paint)
        if (flashing) {
            paint.color = Color.argb(120, 255, 255, 255)
            canvas.drawCircle(rect.centerX(), rect.centerY(), tile * 0.45f, paint)
        }
    }

    private fun drawAltar(canvas: Canvas) {
        val cxx = rect.centerX()
        val cyy = rect.centerY()
        paint.color = Color.rgb(70, 78, 96)
        canvas.drawRoundRect(
            RectF(cxx - tile * 0.28f, cyy - tile * 0.1f, cxx + tile * 0.28f, cyy + tile * 0.32f),
            tile * 0.06f, tile * 0.06f, paint
        )
        paint.color = Color.rgb(100, 110, 132)
        canvas.drawRoundRect(
            RectF(cxx - tile * 0.34f, cyy - tile * 0.22f, cxx + tile * 0.34f, cyy - tile * 0.06f),
            tile * 0.05f, tile * 0.05f, paint
        )
        val pulse = 0.5f + 0.5f * sin(time * 3f)
        paint.color = if (world.simonSolved) Color.rgb(90, 100, 120)
        else Color.argb(255, 160 + (95 * pulse).toInt(), 120 + (80 * pulse).toInt(), 255)
        canvas.drawCircle(cxx, cyy - tile * 0.14f, tile * 0.09f, paint)
    }

    private fun drawStar(canvas: Canvas) {
        val pulse = 0.5f + 0.5f * sin(time * 4f)
        paint.color = Color.argb((70 + 80 * pulse).toInt(), 90, 240, 150)
        canvas.drawCircle(rect.centerX(), rect.centerY(), tile * 0.42f, paint)
        paint.color = Color.rgb(60, 210, 115)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = tile * 0.6f
        paint.isFakeBoldText = true
        canvas.drawText("★", rect.centerX(), rect.centerY() + tile * 0.22f, paint)
        paint.isFakeBoldText = false
    }

    private fun drawHeart(canvas: Canvas) {
        val bob = sin(time * 3.5f) * tile * 0.04f
        val pulse = 0.5f + 0.5f * sin(time * 4f)
        paint.color = Color.argb((45 + 50 * pulse).toInt(), 255, 90, 110)
        canvas.drawCircle(rect.centerX(), rect.centerY() + bob, tile * 0.4f, paint)
        drawSprite(canvas, sHeart, rect.centerX(), rect.centerY() + bob, tile * 0.68f)
    }

    private fun drawCrate(canvas: Canvas, onTarget: Boolean) {
        if (onTarget) {
            val pulse = 0.5f + 0.5f * sin(time * 3.5f)
            paint.color = Color.argb((60 + 60 * pulse).toInt(), 90, 230, 130)
            canvas.drawCircle(rect.centerX(), rect.centerY(), tile * 0.48f, paint)
        }
        drawSprite(canvas, sCrate, rect.centerX(), rect.centerY(), tile * 0.98f)
    }

    private fun drawChest(canvas: Canvas, unlocked: Boolean, open: Boolean, withKeyAnim: Boolean, vault: Boolean = false) {
        val cxx = rect.centerX()
        val cyy = rect.centerY()
        if (unlocked && !open) {
            val pulse = 0.5f + 0.5f * sin(time * 4f)
            paint.color = Color.argb((60 + 90 * pulse).toInt(), 255, 220, 90)
            canvas.drawCircle(cxx, cyy, tile * 0.5f, paint)
        }
        val bmp = when {
            vault -> sVault
            open -> sChestOpen
            else -> sChestClosed
        }
        drawSprite(canvas, bmp, cxx, cyy - tile * 0.04f, tile * 1.02f)
        if (vault && open) {
            paint.color = Color.argb(120, 255, 240, 160)
            canvas.drawCircle(cxx, cyy, tile * 0.3f, paint)
        }
        if (withKeyAnim && keyAnim > 0f) {
            val t = 1f - keyAnim / 3f
            val ky = cyy - tile * 0.45f - tile * 0.75f * t
            drawSprite(canvas, sKey, cxx, ky, tile * 0.62f, (255 * (1f - t * 0.3f)).toInt())
            paint.color = Color.argb((170 * (1f - t)).toInt(), 255, 245, 190)
            for (k in 0..5) {
                val a = time * 3f + k * 1.05f
                canvas.drawCircle(cxx + cos(a) * tile * 0.34f, ky + sin(a) * tile * 0.26f, tile * 0.035f, paint)
            }
        }
    }

    private fun drawKeyShape(canvas: Canvas, cxx: Float, cyy: Float, sz: Float) {
        drawSprite(canvas, sKey, cxx, cyy, sz * 1.6f)
    }

    private fun drawTrap(canvas: Canvas) {
        val rad = tile * 0.12f
        if (!world.trapOpen) {
            paint.color = Color.rgb(84, 72, 54)
            canvas.drawRoundRect(rect, rad, rad, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = tile * 0.05f
            paint.color = Color.rgb(56, 47, 34)
            canvas.drawLine(rect.left, rect.centerY(), rect.right, rect.centerY(), paint)
            canvas.drawLine(rect.centerX(), rect.top, rect.centerX(), rect.bottom, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(190, 175, 90)
            canvas.drawCircle(rect.centerX(), rect.centerY(), tile * 0.08f, paint)
        } else {
            val pulse = 0.5f + 0.5f * sin(time * 5f)
            paint.color = Color.argb((60 + 70 * pulse).toInt(), 90, 240, 150)
            canvas.drawCircle(rect.centerX(), rect.centerY(), tile * 0.5f, paint)
            paint.color = Color.rgb(14, 16, 24)
            canvas.drawRoundRect(rect, rad, rad, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = tile * 0.05f
            paint.color = Color.rgb(80, 230, 140)
            canvas.drawRoundRect(rect, rad, rad, paint)
            paint.style = Paint.Style.FILL
            drawSprite(canvas, sLadder, rect.centerX(), rect.centerY(), tile * 0.9f)
        }
    }

    private fun drawFlag(canvas: Canvas) {
        drawSprite(canvas, sFlag, rect.centerX(), rect.centerY() - tile * 0.03f, tile * 0.8f)
    }

    private fun drawHero(canvas: Canvas, w: Float) {
        val cxx = sx(fx, w)
        val walking = pathStep < path.size
        val bob = if (walking) abs(sin(walkPhase * 0.5f)) * tile * 0.06f else 0f
        val cyy = sy(fy) - tile * 0.12f - bob
        paint.color = Color.argb(95, 0, 0, 0)
        canvas.drawOval(
            cxx - tile * 0.26f, sy(fy) + tile * 0.22f,
            cxx + tile * 0.26f, sy(fy) + tile * 0.36f, paint
        )
        val bmp = when (heroDir) {
            1 -> sHeroUp
            2 -> sHeroLeft
            3 -> sHeroRight
            else -> sHeroDown
        }
        drawSprite(canvas, bmp, cxx, cyy, tile * 1.25f)
        if (attackAnim > 0f) {
            val a = (1f - attackAnim) * 360f
            canvas.save()
            canvas.rotate(a, cxx, cyy)
            drawSprite(canvas, sSwordV, cxx, cyy - tile * 0.45f, tile * 0.8f)
            canvas.restore()
            paint.color = Color.argb((120 * attackAnim).toInt(), 255, 255, 220)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = tile * 0.08f
            canvas.drawCircle(cxx, cyy, tile * 0.72f, paint)
            paint.style = Paint.Style.FILL
        }
    }

    private fun numberColor(n: Int): Int = when (n) {
        1 -> Color.rgb(35, 110, 210)
        2 -> Color.rgb(25, 135, 65)
        3 -> Color.rgb(215, 45, 45)
        4 -> Color.rgb(35, 40, 140)
        5 -> Color.rgb(140, 35, 35)
        6 -> Color.rgb(25, 140, 140)
        7 -> Color.rgb(30, 30, 30)
        else -> Color.rgb(110, 110, 110)
    }

    // ---------------------------------------------------------- mini-demineur

    private fun drawMini(canvas: Canvas, w: Float, h: Float) {
        paint.color = Color.argb(215, 8, 10, 18)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.rgb(255, 210, 90)
        paint.isFakeBoldText = true
        paint.textSize = h * 0.028f
        canvas.drawText("MINI-DEMINEUR DE LA DALLE", w / 2f, h * 0.18f, paint)
        paint.isFakeBoldText = false
        paint.color = Color.rgb(170, 180, 200)
        paint.textSize = h * 0.017f
        canvas.drawText("2 mines cachees. Revelez les 7 cases sures.", w / 2f, h * 0.22f, paint)
        canvas.drawText("Appui long = drapeau. Erreur = -10 PV et tout se referme.", w / 2f, h * 0.25f, paint)

        val cs = min(w, h) * 0.16f
        val gap = cs * 0.08f
        val total = cs * 3 + gap * 2
        val x0 = (w - total) / 2f
        val y0 = h * 0.32f
        for (c in 0..8) {
            val gx = c % 3
            val gy = c / 3
            miniRects[c].set(
                x0 + gx * (cs + gap), y0 + gy * (cs + gap),
                x0 + gx * (cs + gap) + cs, y0 + gy * (cs + gap) + cs
            )
            val r = miniRects[c]
            if (c in miniRev) {
                paint.color = Color.rgb(226, 232, 240)
                canvas.drawRoundRect(r, cs * 0.14f, cs * 0.14f, paint)
                val n = miniCount(c)
                if (n > 0) {
                    paint.color = numberColor(n)
                    paint.textSize = cs * 0.55f
                    paint.isFakeBoldText = true
                    canvas.drawText("$n", r.centerX(), r.centerY() + cs * 0.2f, paint)
                    paint.isFakeBoldText = false
                }
            } else {
                paint.color = Color.rgb(46, 56, 74)
                canvas.drawRoundRect(r, cs * 0.14f, cs * 0.14f, paint)
                if (c in miniFlag) {
                    paint.color = Color.rgb(230, 55, 50)
                    paint.textSize = cs * 0.5f
                    canvas.drawText("⚑", r.centerX(), r.centerY() + cs * 0.17f, paint)
                }
            }
        }
        paint.color = Color.rgb(150, 160, 185)
        paint.textSize = h * 0.018f
        canvas.drawText("Touchez en dehors de la grille pour fermer", w / 2f, y0 + total + h * 0.05f, paint)
    }

    // ---------------------------------------------------------- joystick

    private fun drawJoystick(canvas: Canvas) {
        paint.color = Color.argb(70, 255, 255, 255)
        canvas.drawCircle(joyCenter[0], joyCenter[1], joyRadius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = joyRadius * 0.07f
        paint.color = Color.argb(120, 255, 255, 255)
        canvas.drawCircle(joyCenter[0], joyCenter[1], joyRadius, paint)
        paint.style = Paint.Style.FILL
        val kx = joyCenter[0] + joyDX * joyRadius * 0.55f
        val ky = joyCenter[1] + joyDY * joyRadius * 0.55f
        paint.color = Color.argb(210, 220, 226, 238)
        canvas.drawCircle(kx, ky, joyRadius * 0.42f, paint)
        paint.color = Color.rgb(60, 70, 92)
        canvas.drawCircle(kx, ky, joyRadius * 0.18f, paint)
    }

    // ---------------------------------------------------------- HUD

    private fun drawHud(canvas: Canvas, w: Float, h: Float) {
        paint.color = Color.rgb(17, 20, 30)
        canvas.drawRect(0f, 0f, w, boardTop, paint)
        val ts = h * 0.021f

        val bw = w * 0.28f
        val bx = w * 0.04f
        val by = boardTop * 0.2f
        paint.color = Color.rgb(52, 22, 22)
        tmpRect.set(bx, by, bx + bw, by + ts * 1.35f)
        canvas.drawRoundRect(tmpRect, 12f, 12f, paint)
        paint.color = if (godMode) Color.rgb(70, 160, 220) else Color.rgb(215, 55, 50)
        tmpRect.set(bx, by, bx + bw * (if (godMode) 1f else hp / 100f), by + ts * 1.35f)
        canvas.drawRoundRect(tmpRect, 12f, 12f, paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = ts
        paint.isFakeBoldText = true
        canvas.drawText(if (godMode) "$playerName  PV ∞" else "$playerName  PV $hp", bx + ts * 0.4f, by + ts * 1.05f, paint)
        paint.isFakeBoldText = false

        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = ts * 0.9f
        canvas.drawText("Mines ${world.mines.size}   Drapeaux $flagsLeft", w - w * 0.09f, by + ts * 1.05f, paint)
        if (world.hasKey) {
            paint.color = Color.rgb(255, 216, 92)
            drawKeyShape(canvas, w - w * 0.045f, by + ts * 0.7f, ts * 1.5f)
        }

        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.rgb(150, 160, 180)
        paint.textSize = ts * 0.85f
        val c = world.targets.count { it in world.blocks }
        val doorOpen = world.grid[world.door] == World.FLOOR
        val underground = hy >= world.uy0
        val c2 = world.targets2.count { it in world.blocks }
        val obj = when {
            world.door3Open -> "Objectif : rejoindre l'ETOILE !"
            world.mobsSpawned && !world.mobsDead -> "Objectif : battre les 2 monstres (bouton epee) !"
            world.sokoban2Spawned -> "Objectif : ranger les 5 caisses dans l'alcove ($c2/5)."
            world.lighterTaken -> "Objectif : allumer les 4 torches (${world.torchLit.size}/4)."
            world.grid[world.door1] == World.FLOOR -> "Objectif : trouver le briquet au centre de la salle."
            underground && world.simonSolved -> "Objectif : le coffre-fort, puis la porte de droite."
            underground -> "Objectif : touchez le socle et resolvez l'enigme des couleurs."
            world.trapOpen -> "Objectif : le coffre apparu, puis descendez par la TRAPPE !"
            doorOpen -> "Objectif : ranger les 4 caisses au fond de l'alcove ($c/4)."
            world.hasKey -> "Objectif : ouvrir la porte violette avec la cle."
            hx <= world.hallW -> "Objectif : traverser le champ de mines vers la droite."
            else -> "Objectif : resoudre l'enigme de la salle pour ouvrir le coffre."
        }
        canvas.drawText(obj, bx, boardTop * 0.55f, paint)

        if (msgTimer > 0f) {
            paint.textAlign = Paint.Align.CENTER
            paint.color = Color.rgb(255, 225, 140)
            paint.textSize = ts * 0.88f
            canvas.drawText(message, w / 2f, boardTop * 0.87f, paint)
        }

        paint.color = Color.rgb(17, 20, 30)
        canvas.drawRect(0f, boardBottom, w, h, paint)
        drawBtn(canvas, btnFlag, if (flagMode) "DRAPEAU ON ($flagsLeft)" else "DRAPEAU OFF ($flagsLeft)", flagMode)
        if (swordOwned) {
            paint.color = if (attackCd > 0f) Color.rgb(70, 60, 60) else Color.rgb(150, 55, 50)
            canvas.drawRoundRect(btnSword, btnSword.height() * 0.28f, btnSword.height() * 0.28f, paint)
            drawSprite(canvas, sSwordV, btnSword.centerX(), btnSword.centerY(), btnSword.height() * 0.82f)
        }
        drawBtn(canvas, btnZoomOut, "−", false)
        drawBtn(canvas, btnZoomIn, "+", false)
        drawBtn(canvas, btnCenter, "◎", false)
        drawBtn(canvas, btnMenu, "☰", false)
    }

    private fun drawBtn(canvas: Canvas, r: RectF, label: String, on: Boolean) {
        paint.color = if (on) Color.rgb(200, 60, 55) else Color.rgb(44, 51, 68)
        canvas.drawRoundRect(r, r.height() * 0.28f, r.height() * 0.28f, paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.textSize = if (label.length > 2) r.height() * 0.3f else r.height() * 0.45f
        canvas.drawText(label, r.centerX(), r.centerY() + r.height() * 0.15f, paint)
        paint.isFakeBoldText = false
    }

    private fun drawMenu(canvas: Canvas, w: Float, h: Float) {
        drawStoneBg(canvas, w, h, 190)
        drawWallTorch(canvas, w * 0.14f, h * 0.1f, h * 0.055f, 2)
        drawWallTorch(canvas, w * 0.86f, h * 0.1f, h * 0.055f, 3)

        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.textSize = h * 0.036f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = h * 0.006f
        paint.color = Color.rgb(60, 40, 12)
        canvas.drawText("MENU", w / 2f, h * 0.115f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(255, 205, 90)
        canvas.drawText("MENU", w / 2f, h * 0.115f, paint)
        paint.isFakeBoldText = false

        // Bandeau du heros
        val bh = h * 0.052f
        tmpRect.set(w * 0.13f, h * 0.135f, w * 0.87f, h * 0.135f + bh)
        drawFrame(canvas, tmpRect, Color.rgb(34, 32, 42), Color.rgb(150, 122, 66))
        drawSprite(canvas, sHeroDown, w * 0.18f, tmpRect.centerY(), bh * 1.05f)
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.rgb(240, 228, 200)
        paint.isFakeBoldText = true
        paint.textSize = h * 0.02f
        canvas.drawText(playerName, w * 0.235f, tmpRect.centerY() - h * 0.001f, paint)
        paint.isFakeBoldText = false
        paint.color = Color.rgb(180, 160, 122)
        paint.textSize = h * 0.015f
        canvas.drawText(
            "${diffName(difficulty)}   -   PV ${if (godMode) "∞" else "$hp"}   -   Drapeaux $flagsLeft",
            w * 0.235f, tmpRect.centerY() + h * 0.019f, paint
        )
        if (world.hasKey) drawSprite(canvas, sKey, w * 0.82f, tmpRect.centerY(), bh * 0.8f)

        drawPanelBtn(canvas, mResume, "REPRENDRE", false)
        drawPanelBtn(canvas, mInv, "INVENTAIRE", false)
        drawPanelBtn(canvas, mMap, "CARTE DU DONJON", false)
        drawPanelBtn(canvas, mSave, "SAUVEGARDER", false)
        drawPanelBtn(canvas, mReset, "REINITIALISER LES CAISSES", false)
        drawPanelBtn(canvas, mHelp, "COMMENT JOUER ?", false)
        drawPanelBtn(canvas, mRestart, "NOUVELLE PARTIE", false)
        drawPanelBtn(canvas, mQuit, "MENU PRINCIPAL", false)

        drawEmbers(canvas, w, h)
    }

    private fun drawInventory(canvas: Canvas, w: Float, h: Float) {
        drawStoneBg(canvas, w, h, 205)
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.textSize = h * 0.034f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = h * 0.006f
        paint.color = Color.rgb(60, 40, 12)
        canvas.drawText("INVENTAIRE", w / 2f, h * 0.1f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(255, 205, 90)
        canvas.drawText("INVENTAIRE", w / 2f, h * 0.1f, paint)
        paint.isFakeBoldText = false

        val joyLabel = when {
            !joyOwned -> "pas trouve"
            joyOn -> "ACTIF - touchez pour couper"
            else -> "touchez pour ACTIVER"
        }
        // (icone, libelle, valeur, couleur)
        val rows: List<Array<Any?>> = listOf(
            arrayOf(null, "Drapeaux", "$flagsLeft", Color.rgb(230, 55, 50)),
            arrayOf(sKey, "Cle en or", if (world.hasKey) "1" else "0", Color.rgb(255, 216, 92)),
            arrayOf(sSwordV, "Epee", if (swordOwned) "1 (combat a venir)" else "0", Color.rgb(180, 195, 220)),
            arrayOf(sLighter, "Briquet", if (lighterOwned) "1" else "0", Color.rgb(200, 210, 225)),
            arrayOf(sEnergy, "Canette d'energie", if (energyCount > 0) "$energyCount - touchez pour boire" else "0", Color.rgb(90, 160, 240)),
            arrayOf(null, "Coeurs ramasses", "$heartsGot", Color.rgb(230, 60, 80)),
            arrayOf(null, "Mines desamorcees", "$disarmed", Color.rgb(90, 200, 130)),
            arrayOf(null, "Points de vie", if (godMode) "illimites" else "$hp / 100", Color.rgb(215, 90, 85)),
            arrayOf(null, "Joystick", joyLabel, Color.rgb(120, 190, 240))
        )
        var y = h * 0.16f
        val bw = w * 0.84f
        val bx = (w - bw) / 2f
        val rh = h * 0.062f
        for (r in rows) {
            val icon = r[0] as Bitmap?
            val label = r[1] as String
            val value = r[2] as String
            val col = r[3] as Int
            tmpRect.set(bx, y, bx + bw, y + rh)
            if (label == "Joystick") invJoyRect.set(tmpRect)
            if (label == "Canette d'energie") invEnergyRect.set(tmpRect)
            val active = (label == "Joystick" && joyOn)
            drawFrame(
                canvas, tmpRect,
                if (active) Color.rgb(44, 62, 84) else Color.rgb(34, 32, 42),
                if (active) Color.rgb(120, 190, 240) else Color.rgb(140, 114, 62)
            )
            val icx = bx + rh * 0.55f
            val icy = tmpRect.centerY()
            if (icon != null) {
                drawSprite(canvas, icon, icx, icy, rh * 0.9f)
            } else {
                paint.color = col
                canvas.drawCircle(icx, icy, rh * 0.2f, paint)
            }
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = h * 0.019f
            canvas.drawText(label, bx + rh * 1.1f, icy + h * 0.007f, paint)
            paint.textAlign = Paint.Align.RIGHT
            paint.color = col
            paint.isFakeBoldText = true
            paint.textSize = h * 0.0165f
            canvas.drawText(value, bx + bw - h * 0.02f, icy + h * 0.007f, paint)
            paint.isFakeBoldText = false
            y += rh + h * 0.012f
        }
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.rgb(150, 160, 185)
        paint.textSize = h * 0.018f
        canvas.drawText("Touchez ailleurs pour fermer", w / 2f, h * 0.93f, paint)
    }

    private fun drawHelp(canvas: Canvas, w: Float, h: Float) {
        drawStoneBg(canvas, w, h, 218)
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.rgb(255, 205, 90)
        paint.isFakeBoldText = true
        paint.textSize = h * 0.026f
        canvas.drawText("COMMENT JOUER", w * 0.07f, h * 0.07f, paint)
        paint.isFakeBoldText = false
        paint.color = Color.WHITE
        paint.textSize = h * 0.0148f
        val lines = listOf(
            "1) LA GRANDE SALLE = un vrai demineur.",
            "• Touchez une dalle : le heros y va seul et la sonde.",
            "• Appui long = drapeau. Retoucher la dalle = desamorcage",
            "  sans risque, mais le drapeau est consomme.",
            "• Des COEURS sont caches sous certaines dalles : marchez",
            "  dessus pour recuperer +20 PV.",
            "",
            "2) LA SALLE DU COFFRE",
            "• Chaque dalle de pression est recouverte : touchez-la pour",
            "  ouvrir son MINI-DEMINEUR 3x3 et la decouvrir.",
            "• Poussez ensuite les 3 blocs dessus -> coffre -> CLE.",
            "• La cle ouvre la porte violette.",
            "",
            "3) LA SALLE DE RANGEMENT (sokoban)",
            "• 4 caisses -> 4 dalles bleues au fond de l'alcove.",
            "• Une seule solution : la plus profonde d'abord !",
            "• Reussi -> la TRAPPE s'ouvre + un coffre apparait",
            "  (il contient le JOYSTICK, activable dans l'inventaire).",
            "• Coince ? Menu ☰ > Reinitialiser les caisses.",
            "",
            "4) SALLE DES COULEURS (sous-sol)",
            "• Touchez le SOCLE : 4 couleurs clignotent dans un ordre.",
            "• Marchez sur les dalles colorees dans le MEME ordre.",
            "• Reussi -> coffre-fort (EPEE !) + 2 portes.",
            "• La porte du sud est scellee : une cle a trouver plus tard.",
            "",
            "5) SALLE DES TORCHES",
            "• Ramassez le BRIQUET au centre, allumez les 4 torches.",
            "• Des caisses tombent : sokoban 2 (5 caisses, plus dur).",
            "• Resolu -> une porte apparait + 2 MONSTRES.",
            "• Equipez l'epee et utilisez le bouton epee pour frapper !",
            "• Monstres vaincus -> la porte s'ouvre -> ETOILE = victoire.",
            "",
            "Touchez l'ecran pour fermer."
        )
        var y = h * 0.115f
        for (line in lines) {
            canvas.drawText(line, w * 0.07f, y, paint)
            y += h * 0.0265f
        }
    }

    /** Carte du donjon : vue d'ensemble des zones decouvertes. */
    private fun drawMap(canvas: Canvas, w: Float, h: Float) {
        drawStoneBg(canvas, w, h, 212)
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.textSize = h * 0.032f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = h * 0.006f
        paint.color = Color.rgb(60, 40, 12)
        canvas.drawText("CARTE DU DONJON", w / 2f, h * 0.085f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(255, 205, 90)
        canvas.drawText("CARTE DU DONJON", w / 2f, h * 0.085f, paint)
        paint.isFakeBoldText = false

        val mw = w * 0.92f
        val mh = h * 0.62f
        val cs = min(mw / world.wid, mh / world.hei)
        val ox = (w - cs * world.wid) / 2f
        val oy = h * 0.13f
        for (gy in 0 until world.hei) {
            for (gx in 0 until world.wid) {
                val i = world.idx(gx, gy)
                val g = world.grid[i]
                if (g == World.WALL) continue
                val known = i in world.revealed
                val l = ox + gx * cs
                val t = oy + gy * cs
                paint.color = when {
                    gx == world.exitX && gy == world.exitY && known -> Color.rgb(70, 220, 130)
                    gx == world.trapX && gy == world.trapY && known -> Color.rgb(220, 180, 70)
                    g == World.DOOR -> Color.rgb(150, 90, 200)
                    !known -> Color.rgb(38, 42, 56)
                    i in world.mines || i in world.flagged -> Color.rgb(180, 60, 55)
                    else -> Color.rgb(180, 186, 200)
                }
                canvas.drawRect(l, t, l + cs - 0.6f, t + cs - 0.6f, paint)
            }
        }
        // Le heros
        paint.color = Color.rgb(255, 220, 60)
        canvas.drawCircle(ox + (hx + 0.5f) * cs, oy + (hy + 0.5f) * cs, cs * 1.3f, paint)

        paint.textAlign = Paint.Align.LEFT
        paint.textSize = h * 0.017f
        var y = oy + cs * world.hei + h * 0.045f
        val leg = listOf(
            Pair("Vous", Color.rgb(255, 220, 60)),
            Pair("Salle exploree", Color.rgb(180, 186, 200)),
            Pair("Zone inconnue", Color.rgb(38, 42, 56)),
            Pair("Porte", Color.rgb(150, 90, 200)),
            Pair("Trappe / Etoile", Color.rgb(70, 220, 130))
        )
        for ((label, col) in leg) {
            paint.color = col
            canvas.drawRect(w * 0.1f, y - h * 0.012f, w * 0.1f + h * 0.016f, y + h * 0.004f, paint)
            paint.color = Color.WHITE
            canvas.drawText(label, w * 0.1f + h * 0.028f, y, paint)
            y += h * 0.028f
        }
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.rgb(150, 160, 185)
        canvas.drawText("Touchez l'ecran pour fermer", w / 2f, h * 0.95f, paint)
    }

    private fun drawEnd(canvas: Canvas, w: Float, h: Float) {
        drawStoneBg(canvas, w, h, 215)
        if (victory) {
            drawSprite(canvas, sChestOpen, w / 2f, h * 0.27f, h * 0.16f)
            drawEmbers(canvas, w, h)
        } else {
            drawSprite(canvas, sBomb, w / 2f, h * 0.27f, h * 0.13f)
        }
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.textSize = h * 0.055f
        paint.color = if (victory) Color.rgb(80, 225, 120) else Color.rgb(235, 65, 60)
        canvas.drawText(if (victory) "VICTOIRE !" else "GAME OVER", w / 2f, h * 0.42f, paint)
        paint.isFakeBoldText = false
        paint.color = Color.WHITE
        paint.textSize = h * 0.023f
        canvas.drawText("$playerName  -  ${diffName(difficulty)}", w / 2f, h * 0.47f, paint)
        canvas.drawText("Mines desamorcees : $disarmed   Coeurs : $heartsGot", w / 2f, h * 0.51f, paint)
        canvas.drawText("Touchez l'ecran pour revenir au menu", w / 2f, h * 0.56f, paint)
    }

    // ============================================================ TACTILE

    private fun askName() {
        val et = EditText(context)
        et.setText(playerName)
        et.inputType = InputType.TYPE_CLASS_TEXT
        AlertDialog.Builder(context)
            .setTitle("Nom du heros")
            .setView(et)
            .setPositiveButton("OK") { _, _ ->
                val n = et.text.toString().trim()
                playerName = if (n.isEmpty()) "Heros" else n.take(14)
                prefs.edit().putString("name", playerName).apply()
                invalidate()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun inJoystick(x: Float, y: Float) =
        hypot(x - joyCenter[0], y - joyCenter[1]) <= joyRadius * 1.35f

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val am = e.actionMasked
        // Gestion multi-touch du joystick
        if (joyOn && joyOwned && state == PLAYING && miniPlate < 0 &&
            !showMenu && !showInv && !showHelp && !showMap && !gameOver && !victory
        ) {
            when (am) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val id = e.getPointerId(e.actionIndex)
                    if (joyPointer < 0 && inJoystick(e.getX(e.actionIndex), e.getY(e.actionIndex))) {
                        joyPointer = id
                        updateJoy(e)
                        return true
                    }
                }
                MotionEvent.ACTION_MOVE -> if (joyPointer >= 0) { updateJoy(e); return true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (joyPointer >= 0) { joyPointer = -1; joyDX = 0f; joyDY = 0f; return true }
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (e.getPointerId(e.actionIndex) == joyPointer) {
                        joyPointer = -1; joyDX = 0f; joyDY = 0f
                        return true
                    }
                }
            }
        }

        when (am) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; lastX = e.x; lastY = e.y
                downTime = System.currentTimeMillis()
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (state != PLAYING || showMenu || showInv || showHelp || showMap || miniPlate >= 0) return true
                val dx = e.x - lastX
                val dy = e.y - lastY
                if (!dragging && hypot(e.x - downX, e.y - downY) > tile * 0.35f) dragging = true
                if (dragging && e.y in boardTop..boardBottom) {
                    camX -= dx / tile
                    camY -= dy / tile
                    following = false
                    clampCam()
                }
                lastX = e.x; lastY = e.y
            }
            MotionEvent.ACTION_UP -> return handleUp(e)
        }
        return true
    }

    private fun updateJoy(e: MotionEvent) {
        for (i in 0 until e.pointerCount) {
            if (e.getPointerId(i) == joyPointer) {
                val dx = (e.getX(i) - joyCenter[0]) / joyRadius
                val dy = (e.getY(i) - joyCenter[1]) / joyRadius
                val d = hypot(dx, dy)
                if (d > 1f) { joyDX = dx / d; joyDY = dy / d } else { joyDX = dx; joyDY = dy }
            }
        }
    }

    private fun handleUp(e: MotionEvent): Boolean {
        if (showMap) { showMap = false; return true }
        if (showHelp) { showHelp = false; return true }
        if (showInv) {
            if (invJoyRect.contains(e.x, e.y) && joyOwned) {
                joyOn = !joyOn
                saveGame()
                showMsg(if (joyOn) "Joystick active !" else "Joystick desactive.")
            } else if (invEnergyRect.contains(e.x, e.y) && energyCount > 0) {
                energyCount--
                hp = (hp + 30).coerceAtMost(100)
                saveGame()
                showMsg("Glouglou ! +30 PV")
            } else {
                showInv = false
            }
            return true
        }
        if (miniPlate >= 0) {
            val long = System.currentTimeMillis() - downTime > 400
            var hit = false
            for (c in 0..8) if (miniRects[c].contains(e.x, e.y)) { miniTap(c, long); hit = true }
            if (!hit && !long) miniPlate = -1
            return true
        }

        if (state == TITLE) {
            when {
                tName.contains(e.x, e.y) -> askName()
                tGod.contains(e.x, e.y) -> {
                    godMode = !godMode
                    prefs.edit().putBoolean("god", godMode).apply()
                }
                tNew.contains(e.x, e.y) -> newGame()
                tCont.contains(e.x, e.y) -> if (hasSave()) loadGame()
                tHelp.contains(e.x, e.y) -> showHelp = true
                else -> for (k in 0..2) if (tDiff[k].contains(e.x, e.y)) {
                    difficulty = k
                    prefs.edit().putInt("diff", k).apply()
                }
            }
            return true
        }

        if (showMenu) {
            when {
                mResume.contains(e.x, e.y) -> showMenu = false
                mInv.contains(e.x, e.y) -> showInv = true
                mMap.contains(e.x, e.y) -> { showMap = true; showMenu = false }
                mSave.contains(e.x, e.y) -> { saveGame(); showMenu = false; showMsg("Partie sauvegardee.") }
                mReset.contains(e.x, e.y) -> {
                    world.resetPuzzle(); saveGame(); showMenu = false
                    showMsg("Caisses remises a leur place de depart.")
                }
                mHelp.contains(e.x, e.y) -> showHelp = true
                mRestart.contains(e.x, e.y) -> newGame()
                mQuit.contains(e.x, e.y) -> { saveGame(); showMenu = false; state = TITLE }
            }
            return true
        }

        if (gameOver || victory) { state = TITLE; return true }
        if (dragging) return true

        val longPress = System.currentTimeMillis() - downTime > 400

        if (btnFlag.contains(e.x, e.y)) {
            flagMode = !flagMode
            showMsg(if (flagMode) "Mode drapeau : touchez une dalle pour la marquer." else "Mode normal.")
            return true
        }
        if (swordOwned && btnSword.contains(e.x, e.y)) { doAttack(); return true }
        if (btnZoomOut.contains(e.x, e.y)) { tile = (tile * 0.82f).coerceAtLeast(34f); clampCam(); return true }
        if (btnZoomIn.contains(e.x, e.y)) { tile = (tile * 1.22f).coerceAtMost(240f); clampCam(); return true }
        if (btnCenter.contains(e.x, e.y)) { following = true; return true }
        if (btnMenu.contains(e.x, e.y)) { showMenu = true; return true }

        if (e.y in boardTop..boardBottom) {
            val gx = floor(camX + (e.x - width / 2f) / tile).toInt()
            val gy = floor(camY + (e.y - (boardTop + boardBottom) / 2f) / tile).toInt()
            if (!world.inside(gx, gy)) return true
            following = true
            if (flagMode || longPress) onFlag(gx, gy) else onTap(gx, gy)
        }
        return true
    }
}
