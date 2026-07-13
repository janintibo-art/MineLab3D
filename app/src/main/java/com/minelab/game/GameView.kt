package com.minelab.game

import android.app.AlertDialog
import android.content.Context
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

    private var hp = 100
    private var disarmed = 0
    private var flagsLeft = 0
    private var flagMode = false
    private var gameOver = false
    private var victory = false

    private var message = ""
    private var msgTimer = 0f
    private var boomFlash = 0f
    private var walkPhase = 0f
    private var keyAnim = 0f
    private var time = 0f

    private var camX = 1.5f
    private var camY = 1.5f
    private var following = true
    private var tile = 100f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastTime = System.nanoTime()

    private var boardTop = 0f
    private var boardBottom = 0f

    private val btnFlag = RectF()
    private val btnZoomOut = RectF()
    private val btnZoomIn = RectF()
    private val btnCenter = RectF()
    private val btnMenu = RectF()

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

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downTime = 0L
    private var dragging = false
    private val rect = RectF()
    private val tmpRect = RectF()

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
        flagsLeft = world.totalMines
        gameOver = false; victory = false; flagMode = false
        showHelp = false; showMenu = false; showInv = false
        boomFlash = 0f; keyAnim = 0f
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
        e.putInt("flags", flagsLeft)
        e.putBoolean("key", world.hasKey)
        e.putBoolean("chest", world.chestOpen)
        e.putBoolean("trap", world.trapOpen)
        e.putBoolean("door", world.grid[world.door] == World.FLOOR)
        e.putString("mines", setToStr(world.mines))
        e.putString("rev", setToStr(world.revealed))
        e.putString("flg", setToStr(world.flagged))
        e.putString("exp", setToStr(world.exploded))
        e.putString("def", setToStr(world.defused))
        e.putString("blk", setToStr(world.blocks))
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
        world.revealed.clear(); world.revealed.addAll(strToSet(prefs.getString("rev", "")))
        world.flagged.clear(); world.flagged.addAll(strToSet(prefs.getString("flg", "")))
        world.exploded.clear(); world.exploded.addAll(strToSet(prefs.getString("exp", "")))
        world.defused.clear(); world.defused.addAll(strToSet(prefs.getString("def", "")))
        world.blocks.clear(); world.blocks.addAll(strToSet(prefs.getString("blk", "")))
        world.hasKey = prefs.getBoolean("key", false)
        world.chestOpen = prefs.getBoolean("chest", false)
        world.trapOpen = prefs.getBoolean("trap", false)
        if (prefs.getBoolean("door", false)) world.grid[world.door] = World.FLOOR

        playerName = prefs.getString("name", "Heros") ?: "Heros"
        difficulty = prefs.getInt("diff", 1)
        godMode = prefs.getBoolean("god", false)
        hp = prefs.getInt("hp", 100)
        hx = prefs.getInt("hx", world.startX)
        hy = prefs.getInt("hy", world.startY)
        disarmed = prefs.getInt("dis", 0)
        flagsLeft = prefs.getInt("flags", world.totalMines)

        fx = hx + 0.5f; fy = hy + 0.5f
        camX = fx; camY = fy
        following = true
        path = emptyList(); pathStep = 0
        clearPendings()
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
        btnFlag.set(m, y0, x, y0 + bh)

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
        var my = hf * 0.22f
        val mh = hf * 0.068f
        val mg = hf * 0.014f
        mResume.set(mx, my, mx + mw, my + mh); my += mh + mg
        mInv.set(mx, my, mx + mw, my + mh); my += mh + mg
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
        keyAnim = (keyAnim - dt).coerceAtLeast(0f)
        if (state != PLAYING) return

        if (following) {
            camX += (fx - camX) * min(1f, dt * 8f)
            camY += (fy - camY) * min(1f, dt * 8f)
        }
        clampCam()

        if (gameOver || victory || showMenu || showInv || showHelp) return

        if (pathStep < path.size) {
            walkPhase += dt * 12f
            val (tx, ty) = path[pathStep]
            val gx = tx + 0.5f
            val gy = ty + 0.5f
            val speed = 7f * dt
            val dx = gx - fx
            val dy = gy - fy
            if (hypot(dx, dy) <= speed) {
                fx = gx; fy = gy
                hx = tx; hy = ty
                pathStep++
                if (hx == world.exitX && hy == world.exitY) {
                    victory = true
                    prefs.edit().putBoolean("has", false).apply()
                    return
                }
            } else {
                fx += sign(dx) * min(abs(dx), speed)
                fy += sign(dy) * min(abs(dy), speed)
            }
        } else {
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
        pendingReveal = -1; pendingDisarm = -1; pendingChest = false; pendingDoor = false
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
            showMsg("Coffre verrouille : poussez les 3 blocs sur les 3 dalles.")
            return
        }
        if (world.chestOpen) { showMsg("Le coffre est vide."); return }
        world.chestOpen = true
        world.hasKey = true
        flagsLeft += 5
        keyAnim = 3f
        showMsg("Le coffre s'ouvre... une CLE EN OR s'en echappe ! (+5 drapeaux)")
    }

    private fun openDoor() {
        if (!world.hasKey) { showMsg("Porte verrouillee. Il faut la cle du coffre."); return }
        world.grid[world.door] = World.FLOOR
        world.revealed.add(world.door)
        world.revealRoomB()
        showMsg("La cle tourne dans la serrure... la porte s'ouvre !")
    }

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
        if (!world.canPushInto(tx, ty)) { showMsg("La caisse ne peut pas aller plus loin."); return true }
        world.blocks.remove(world.idx(bxx, byy))
        world.blocks.add(world.idx(tx, ty))
        world.revealed.add(world.idx(tx, ty))
        world.revealed.add(world.idx(bxx, byy))
        clearPendings()
        path = listOf(Pair(bxx, byy))
        pathStep = 0

        if (world.trapSolved() && !world.trapOpen) {
            world.trapOpen = true
            world.revealed.add(world.idx(world.exitX, world.exitY))
            showMsg("CLAC ! Les 4 caisses sont rangees : une TRAPPE s'ouvre !")
        } else if (world.platesSolved() && !world.chestOpen) {
            showMsg("Les 3 dalles sont enfoncees : le coffre est deverrouille !")
        } else {
            val d = world.plates.count { it in world.blocks }
            val c = world.targets.count { it in world.blocks }
            showMsg(if (world.hasKey) "Caisses rangees : $c / 4" else "Dalles enfoncees : $d / 3")
        }
        saveGame()
        return true
    }

    private fun onTap(gx: Int, gy: Int) {
        if (gameOver || victory) return
        val i = world.idx(gx, gy)

        if (world.isDoor(gx, gy)) {
            if (!walkNextTo(gx, gy)) { showMsg("Approchez-vous de la porte."); return }
            clearPendings(); pendingDoor = true
            return
        }
        if (!world.isFloor(gx, gy)) { showMsg("C'est un mur."); return }

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

        if (boomFlash > 0f) {
            paint.color = Color.argb((boomFlash * 170).toInt(), 255, 120, 30)
            canvas.drawRect(0f, 0f, w, h, paint)
        }
        if (showMenu) drawMenu(canvas, w, h)
        if (showInv) drawInventory(canvas, w, h)
        if (showHelp) drawHelp(canvas, w, h)
        if (gameOver || victory) drawEnd(canvas, w, h)

        postInvalidateOnAnimation()
    }

    // ---------------------------------------------------------- titre

    private fun drawTitle(canvas: Canvas, w: Float, h: Float) {
        paint.color = Color.rgb(12, 14, 24)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.color = Color.rgb(255, 210, 90)
        paint.textSize = h * 0.055f
        canvas.drawText("MINELAB", w / 2f, h * 0.15f, paint)
        paint.isFakeBoldText = false
        paint.color = Color.rgb(150, 160, 185)
        paint.textSize = h * 0.019f
        canvas.drawText("Demineur, caisses et coffre au tresor", w / 2f, h * 0.19f, paint)

        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.rgb(140, 150, 175)
        paint.textSize = h * 0.017f
        canvas.drawText("NOM DU HEROS (touchez pour modifier)", tName.left, tName.top - h * 0.012f, paint)
        drawPanelBtn(canvas, tName, playerName, false)
        canvas.drawText("DIFFICULTE", tDiff[0].left, tDiff[0].top - h * 0.012f, paint)
        for (k in 0..2) drawPanelBtn(canvas, tDiff[k], diffName(k), difficulty == k)
        drawPanelBtn(canvas, tGod, if (godMode) "VIE ILLIMITEE : ON (test)" else "VIE ILLIMITEE : OFF", godMode)
        drawPanelBtn(canvas, tNew, "NOUVELLE PARTIE", false)
        drawPanelBtn(canvas, tCont, if (hasSave()) "CONTINUER" else "AUCUNE SAUVEGARDE", false, hasSave())
        drawPanelBtn(canvas, tHelp, "COMMENT JOUER ?", false)
    }

    private fun drawPanelBtn(canvas: Canvas, r: RectF, label: String, on: Boolean, enabled: Boolean = true) {
        paint.color = when {
            !enabled -> Color.rgb(30, 34, 46)
            on -> Color.rgb(200, 145, 45)
            else -> Color.rgb(44, 51, 70)
        }
        canvas.drawRoundRect(r, r.height() * 0.25f, r.height() * 0.25f, paint)
        paint.color = if (enabled) Color.WHITE else Color.rgb(90, 95, 110)
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.textSize = r.height() * 0.36f
        canvas.drawText(label, r.centerX(), r.centerY() + r.height() * 0.13f, paint)
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

                if (world.grid[i] == World.WALL) { drawWall(canvas); continue }
                if (world.grid[i] == World.DOOR) { drawDoor(canvas); continue }

                val rev = i in world.revealed
                val isExit = gx == world.exitX && gy == world.exitY

                when {
                    isExit && rev -> drawTrap(canvas)
                    i in world.exploded -> drawBomb(canvas, true)
                    i in world.defused -> drawBomb(canvas, false)
                    rev -> {
                        paint.color = Color.rgb(226, 232, 240)
                        canvas.drawRoundRect(rect, rad, rad, paint)
                        paint.color = Color.rgb(198, 206, 216)
                        canvas.drawRect(rect.left, rect.bottom - tile * 0.05f, rect.right, rect.bottom, paint)
                        if (i in world.plates) drawPlate(canvas, i)
                        if (i in world.targets) drawTarget(canvas, i)
                        val n = world.countAround(gx, gy)
                        if (n > 0 && i !in world.plates && i !in world.targets) {
                            paint.color = numberColor(n)
                            paint.textAlign = Paint.Align.CENTER
                            paint.textSize = tile * 0.56f
                            paint.isFakeBoldText = true
                            canvas.drawText("$n", rect.centerX(), rect.centerY() + tile * 0.2f, paint)
                            paint.isFakeBoldText = false
                        }
                    }
                    else -> {
                        paint.color = Color.rgb(36, 44, 58)
                        canvas.drawRoundRect(rect, rad, rad, paint)
                        tmpRect.set(rect)
                        tmpRect.inset(tile * 0.045f, tile * 0.045f)
                        tmpRect.offset(0f, -tile * 0.02f)
                        paint.color = Color.rgb(58, 69, 88)
                        canvas.drawRoundRect(tmpRect, rad * 0.8f, rad * 0.8f, paint)
                        if (i in world.flagged) drawFlag(canvas)
                    }
                }
                if (i == world.chest) drawChest(canvas)
                if (i in world.blocks) drawCrate(canvas, i in world.plates || i in world.targets)
            }
        }
        drawHero(canvas, w)
    }

    private fun drawWall(canvas: Canvas) {
        paint.color = Color.rgb(30, 34, 46)
        canvas.drawRoundRect(rect, tile * 0.08f, tile * 0.08f, paint)
        paint.color = Color.rgb(42, 47, 62)
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + tile * 0.06f, paint)
    }

    private fun drawDoor(canvas: Canvas) {
        val rad = tile * 0.1f
        paint.color = Color.rgb(96, 56, 140)
        canvas.drawRoundRect(rect, rad, rad, paint)
        tmpRect.set(rect)
        tmpRect.inset(tile * 0.07f, tile * 0.07f)
        paint.color = Color.rgb(126, 78, 178)
        canvas.drawRoundRect(tmpRect, rad, rad, paint)
        // Serrure doree
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

    /** Bombe : rouge si explosee, grise si desamorcee (avec sa meche). */
    private fun drawBomb(canvas: Canvas, boom: Boolean) {
        val rad = tile * 0.16f
        paint.color = if (boom) Color.rgb(150, 45, 42) else Color.rgb(96, 104, 118)
        canvas.drawRoundRect(rect, rad, rad, paint)
        val cxx = rect.centerX()
        val cyy = rect.centerY() + tile * 0.03f
        paint.color = if (boom) Color.rgb(28, 26, 30) else Color.rgb(46, 50, 60)
        canvas.drawCircle(cxx, cyy, tile * 0.2f, paint)
        // reflet
        paint.color = Color.argb(90, 255, 255, 255)
        canvas.drawCircle(cxx - tile * 0.07f, cyy - tile * 0.08f, tile * 0.05f, paint)
        // meche
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = tile * 0.04f
        paint.color = if (boom) Color.rgb(230, 170, 60) else Color.rgb(120, 128, 140)
        val p = Path()
        p.moveTo(cxx + tile * 0.1f, cyy - tile * 0.17f)
        p.quadTo(cxx + tile * 0.24f, cyy - tile * 0.3f, cxx + tile * 0.14f, cyy - tile * 0.34f)
        canvas.drawPath(p, paint)
        paint.style = Paint.Style.FILL
        if (boom) {
            paint.color = Color.rgb(255, 210, 70)
            canvas.drawCircle(cxx + tile * 0.14f, cyy - tile * 0.36f, tile * 0.045f, paint)
        }
    }

    private fun drawPlate(canvas: Canvas, i: Int) {
        val on = i in world.blocks
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = tile * 0.06f
        paint.color = if (on) Color.rgb(60, 190, 110) else Color.rgb(205, 125, 45)
        canvas.drawCircle(rect.centerX(), rect.centerY(), tile * 0.24f, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(rect.centerX(), rect.centerY(), tile * 0.09f, paint)
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

    /** Caisse en bois avec planches, croix et coins metalliques. */
    private fun drawCrate(canvas: Canvas, onTarget: Boolean) {
        tmpRect.set(rect)
        tmpRect.inset(tile * 0.015f, tile * 0.015f)
        val body = if (onTarget) Color.rgb(126, 176, 96) else Color.rgb(170, 118, 62)
        val dark = if (onTarget) Color.rgb(74, 118, 58) else Color.rgb(112, 74, 36)
        val light = if (onTarget) Color.rgb(160, 205, 125) else Color.rgb(205, 155, 95)

        paint.color = body
        canvas.drawRoundRect(tmpRect, tile * 0.08f, tile * 0.08f, paint)
        // planches horizontales
        paint.color = light
        canvas.drawRect(tmpRect.left, tmpRect.top + tile * 0.06f, tmpRect.right, tmpRect.top + tile * 0.11f, paint)
        canvas.drawRect(tmpRect.left, tmpRect.bottom - tile * 0.11f, tmpRect.right, tmpRect.bottom - tile * 0.06f, paint)
        // croix
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = tile * 0.06f
        paint.color = dark
        canvas.drawLine(tmpRect.left + tile * 0.08f, tmpRect.top + tile * 0.08f, tmpRect.right - tile * 0.08f, tmpRect.bottom - tile * 0.08f, paint)
        canvas.drawLine(tmpRect.right - tile * 0.08f, tmpRect.top + tile * 0.08f, tmpRect.left + tile * 0.08f, tmpRect.bottom - tile * 0.08f, paint)
        paint.strokeWidth = tile * 0.045f
        canvas.drawRoundRect(tmpRect, tile * 0.08f, tile * 0.08f, paint)
        paint.style = Paint.Style.FILL
        // coins metalliques
        paint.color = Color.rgb(120, 126, 138)
        val s = tile * 0.09f
        canvas.drawRect(tmpRect.left, tmpRect.top, tmpRect.left + s, tmpRect.top + s, paint)
        canvas.drawRect(tmpRect.right - s, tmpRect.top, tmpRect.right, tmpRect.top + s, paint)
        canvas.drawRect(tmpRect.left, tmpRect.bottom - s, tmpRect.left + s, tmpRect.bottom, paint)
        canvas.drawRect(tmpRect.right - s, tmpRect.bottom - s, tmpRect.right, tmpRect.bottom, paint)
    }

    /** Coffre au tresor : ferme, deverrouille (brillant), ou ouvert avec l'or dedans. */
    private fun drawChest(canvas: Canvas) {
        val cxx = rect.centerX()
        val cyy = rect.centerY()
        val w = tile * 0.36f
        val unlocked = world.platesSolved()
        val open = world.chestOpen

        // Halo quand il est deverrouille mais pas encore ouvert
        if (unlocked && !open) {
            val pulse = 0.5f + 0.5f * sin(time * 4f)
            paint.color = Color.argb((70 + 90 * pulse).toInt(), 255, 220, 90)
            canvas.drawCircle(cxx, cyy, tile * 0.46f, paint)
        }

        // Corps du coffre
        paint.color = Color.rgb(122, 78, 40)
        tmpRect.set(cxx - w, cyy - w * 0.15f, cxx + w, cyy + w * 0.75f)
        canvas.drawRoundRect(tmpRect, tile * 0.05f, tile * 0.05f, paint)
        paint.color = Color.rgb(150, 100, 52)
        canvas.drawRect(cxx - w, cyy + w * 0.05f, cxx + w, cyy + w * 0.3f, paint)

        if (!open) {
            // Couvercle ferme, bombe
            paint.color = Color.rgb(96, 60, 30)
            tmpRect.set(cxx - w, cyy - w * 0.75f, cxx + w, cyy - w * 0.05f)
            canvas.drawRoundRect(tmpRect, tile * 0.14f, tile * 0.14f, paint)
            paint.color = Color.rgb(132, 86, 44)
            tmpRect.set(cxx - w * 0.9f, cyy - w * 0.68f, cxx + w * 0.9f, cyy - w * 0.2f)
            canvas.drawRoundRect(tmpRect, tile * 0.1f, tile * 0.1f, paint)
        } else {
            // Couvercle ouvert vers l'arriere + or a l'interieur
            paint.color = Color.rgb(70, 44, 22)
            tmpRect.set(cxx - w, cyy - w * 0.35f, cxx + w, cyy - w * 0.1f)
            canvas.drawRoundRect(tmpRect, tile * 0.05f, tile * 0.05f, paint)
            paint.color = Color.rgb(96, 60, 30)
            tmpRect.set(cxx - w * 0.95f, cyy - w * 1.15f, cxx + w * 0.95f, cyy - w * 0.45f)
            canvas.drawRoundRect(tmpRect, tile * 0.12f, tile * 0.12f, paint)
            paint.color = Color.rgb(255, 216, 92)
            canvas.drawCircle(cxx - w * 0.4f, cyy + w * 0.2f, tile * 0.06f, paint)
            canvas.drawCircle(cxx, cyy + w * 0.3f, tile * 0.07f, paint)
            canvas.drawCircle(cxx + w * 0.42f, cyy + w * 0.18f, tile * 0.055f, paint)
        }

        // Ferrures dorees
        paint.color = Color.rgb(238, 196, 78)
        canvas.drawRect(cxx - w * 0.12f, cyy - w * 0.15f, cxx + w * 0.12f, cyy + w * 0.75f, paint)
        if (!open) {
            // Cadenas
            paint.color = Color.rgb(238, 196, 78)
            canvas.drawRoundRect(
                RectF(cxx - w * 0.18f, cyy - w * 0.18f, cxx + w * 0.18f, cyy + w * 0.2f),
                tile * 0.03f, tile * 0.03f, paint
            )
            paint.color = Color.rgb(70, 44, 22)
            canvas.drawCircle(cxx, cyy + w * 0.01f, tile * 0.035f, paint)
        }

        // La cle qui s'echappe du coffre
        if (keyAnim > 0f) {
            val t = 1f - keyAnim / 3f
            val ky = cyy - w * 1.0f - tile * 0.7f * t
            paint.color = Color.argb((255 * (1f - t * 0.35f)).toInt(), 255, 225, 110)
            drawKeyShape(canvas, cxx, ky, tile * 0.42f)
            paint.color = Color.argb((160 * (1f - t)).toInt(), 255, 255, 200)
            for (k in 0..4) {
                val a = time * 3f + k * 1.25f
                canvas.drawCircle(
                    cxx + cos(a) * tile * 0.3f,
                    ky + sin(a) * tile * 0.22f,
                    tile * 0.035f, paint
                )
            }
        }
    }

    /** Dessine une vraie cle (anneau + tige + dents). */
    private fun drawKeyShape(canvas: Canvas, cxx: Float, cyy: Float, s: Float) {
        val col = paint.color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.16f
        paint.color = col
        canvas.drawCircle(cxx - s * 0.28f, cyy, s * 0.2f, paint)
        paint.style = Paint.Style.FILL
        canvas.drawRect(cxx - s * 0.1f, cyy - s * 0.07f, cxx + s * 0.45f, cyy + s * 0.07f, paint)
        canvas.drawRect(cxx + s * 0.2f, cyy + s * 0.05f, cxx + s * 0.28f, cyy + s * 0.24f, paint)
        canvas.drawRect(cxx + s * 0.36f, cyy + s * 0.05f, cxx + s * 0.44f, cyy + s * 0.2f, paint)
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
            paint.color = Color.rgb(170, 130, 70)
            canvas.drawLine(rect.left + tile * 0.14f, rect.top + tile * 0.1f, rect.left + tile * 0.14f, rect.bottom - tile * 0.1f, paint)
            canvas.drawLine(rect.right - tile * 0.14f, rect.top + tile * 0.1f, rect.right - tile * 0.14f, rect.bottom - tile * 0.1f, paint)
            var yy = rect.top + tile * 0.18f
            while (yy < rect.bottom - tile * 0.08f) {
                canvas.drawLine(rect.left + tile * 0.14f, yy, rect.right - tile * 0.14f, yy, paint)
                yy += tile * 0.17f
            }
            paint.style = Paint.Style.FILL
        }
    }

    private fun drawFlag(canvas: Canvas) {
        val cxx = rect.centerX()
        val cyy = rect.centerY()
        val s = tile * 0.3f
        paint.color = Color.rgb(228, 232, 240)
        paint.strokeWidth = tile * 0.05f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(cxx - s * 0.15f, cyy + s * 0.78f, cxx - s * 0.15f, cyy - s * 0.8f, paint)
        paint.style = Paint.Style.FILL
        val p = Path()
        p.moveTo(cxx - s * 0.15f, cyy - s * 0.85f)
        p.lineTo(cxx + s * 0.85f, cyy - s * 0.42f)
        p.lineTo(cxx - s * 0.15f, cyy + s * 0.02f)
        p.close()
        paint.color = Color.rgb(230, 55, 50)
        canvas.drawPath(p, paint)
    }

    private fun drawHero(canvas: Canvas, w: Float) {
        val l = sx(fx - 0.5f, w)
        val t = sy(fy - 0.5f)
        val cxx = l + tile / 2f
        val walking = pathStep < path.size
        val bob = if (walking) sin(walkPhase) * tile * 0.03f else 0f
        val swing = if (walking) sin(walkPhase) * tile * 0.06f else 0f
        val by = t + tile * 0.88f + bob

        paint.color = Color.argb(90, 0, 0, 0)
        canvas.drawOval(cxx - tile * 0.21f, by - tile * 0.05f, cxx + tile * 0.21f, by + tile * 0.05f, paint)
        paint.color = Color.rgb(42, 52, 82)
        canvas.drawRect(cxx - tile * 0.13f + swing, by - tile * 0.19f, cxx - tile * 0.02f + swing, by, paint)
        canvas.drawRect(cxx + tile * 0.02f - swing, by - tile * 0.19f, cxx + tile * 0.13f - swing, by, paint)
        paint.color = Color.rgb(222, 68, 54)
        tmpRect.set(cxx - tile * 0.18f, by - tile * 0.48f, cxx + tile * 0.18f, by - tile * 0.17f)
        canvas.drawRoundRect(tmpRect, tile * 0.06f, tile * 0.06f, paint)
        canvas.drawRect(cxx - tile * 0.25f, by - tile * 0.44f, cxx - tile * 0.17f, by - tile * 0.23f, paint)
        canvas.drawRect(cxx + tile * 0.17f, by - tile * 0.44f, cxx + tile * 0.25f, by - tile * 0.23f, paint)
        paint.color = Color.rgb(250, 205, 60)
        canvas.drawCircle(cxx, by - tile * 0.6f, tile * 0.17f, paint)
        paint.color = Color.rgb(30, 30, 30)
        canvas.drawCircle(cxx - tile * 0.06f, by - tile * 0.63f, tile * 0.026f, paint)
        canvas.drawCircle(cxx + tile * 0.06f, by - tile * 0.63f, tile * 0.026f, paint)
        tmpRect.set(cxx - tile * 0.07f, by - tile * 0.59f, cxx + tile * 0.07f, by - tile * 0.51f)
        canvas.drawArc(tmpRect, 0f, 180f, true, paint)
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
        canvas.drawText(
            "Mines ${world.mines.size}   Drapeaux $flagsLeft",
            w - w * 0.09f, by + ts * 1.05f, paint
        )
        if (world.hasKey) {
            paint.color = Color.rgb(255, 216, 92)
            drawKeyShape(canvas, w - w * 0.045f, by + ts * 0.7f, ts * 1.5f)
        }

        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.rgb(150, 160, 180)
        paint.textSize = ts * 0.85f
        val d = world.plates.count { it in world.blocks }
        val c = world.targets.count { it in world.blocks }
        val doorOpen = world.grid[world.door] == World.FLOOR
        val obj = when {
            world.trapOpen -> "Objectif : rejoindre la TRAPPE ouverte !"
            doorOpen -> "Objectif : ranger les 4 caisses au fond de l'alcove ($c/4)."
            world.hasKey -> "Objectif : ouvrir la porte violette avec la cle."
            world.platesSolved() -> "Objectif : ouvrir le coffre (la cle est dedans)."
            hx <= world.hallW -> "Objectif : traverser le champ de mines vers la droite."
            else -> "Objectif : pousser les 3 blocs sur les 3 dalles ($d/3)."
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
        paint.color = Color.argb(238, 8, 10, 18)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.rgb(255, 210, 90)
        paint.isFakeBoldText = true
        paint.textSize = h * 0.032f
        canvas.drawText("MENU", w / 2f, h * 0.15f, paint)
        paint.isFakeBoldText = false
        paint.color = Color.rgb(150, 160, 185)
        paint.textSize = h * 0.018f
        canvas.drawText("$playerName  -  ${diffName(difficulty)}", w / 2f, h * 0.19f, paint)
        drawPanelBtn(canvas, mResume, "REPRENDRE", false)
        drawPanelBtn(canvas, mInv, "INVENTAIRE", false)
        drawPanelBtn(canvas, mSave, "SAUVEGARDER", false)
        drawPanelBtn(canvas, mReset, "REINITIALISER LES CAISSES", false)
        drawPanelBtn(canvas, mHelp, "COMMENT JOUER ?", false)
        drawPanelBtn(canvas, mRestart, "NOUVELLE PARTIE", false)
        drawPanelBtn(canvas, mQuit, "MENU PRINCIPAL", false)
    }

    private fun drawInventory(canvas: Canvas, w: Float, h: Float) {
        paint.color = Color.argb(242, 10, 12, 22)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.rgb(255, 210, 90)
        paint.isFakeBoldText = true
        paint.textSize = h * 0.032f
        canvas.drawText("INVENTAIRE", w / 2f, h * 0.15f, paint)
        paint.isFakeBoldText = false

        val items = listOf(
            Triple("Drapeaux", "$flagsLeft", Color.rgb(230, 55, 50)),
            Triple("Cle en or", if (world.hasKey) "1" else "0", Color.rgb(255, 216, 92)),
            Triple("Mines desamorcees", "$disarmed", Color.rgb(90, 200, 130)),
            Triple("Mines restantes", "${world.mines.size}", Color.rgb(180, 190, 210)),
            Triple("Points de vie", if (godMode) "illimites" else "$hp", Color.rgb(215, 90, 85)),
            Triple("Epee", "a venir", Color.rgb(120, 130, 150))
        )
        var y = h * 0.23f
        val bw = w * 0.78f
        val bx = (w - bw) / 2f
        for ((label, value, col) in items) {
            tmpRect.set(bx, y, bx + bw, y + h * 0.06f)
            paint.color = Color.rgb(28, 33, 46)
            canvas.drawRoundRect(tmpRect, h * 0.012f, h * 0.012f, paint)
            paint.color = col
            canvas.drawCircle(bx + h * 0.03f, tmpRect.centerY(), h * 0.014f, paint)
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = h * 0.02f
            canvas.drawText(label, bx + h * 0.055f, tmpRect.centerY() + h * 0.007f, paint)
            paint.textAlign = Paint.Align.RIGHT
            paint.color = col
            paint.isFakeBoldText = true
            canvas.drawText(value, bx + bw - h * 0.02f, tmpRect.centerY() + h * 0.007f, paint)
            paint.isFakeBoldText = false
            y += h * 0.072f
        }
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.rgb(150, 160, 185)
        paint.textSize = h * 0.018f
        canvas.drawText("Touchez l'ecran pour fermer", w / 2f, h * 0.9f, paint)
    }

    private fun drawHelp(canvas: Canvas, w: Float, h: Float) {
        paint.color = Color.argb(242, 8, 10, 18)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.rgb(255, 225, 140)
        paint.isFakeBoldText = true
        paint.textSize = h * 0.026f
        canvas.drawText("COMMENT JOUER", w * 0.07f, h * 0.09f, paint)
        paint.isFakeBoldText = false
        paint.color = Color.WHITE
        paint.textSize = h * 0.0155f
        val lines = listOf(
            "1) LA GRANDE SALLE = un vrai demineur.",
            "• Touchez une dalle : le heros y va seul et la sonde.",
            "• Le chiffre = nombre de mines autour (il ne change JAMAIS).",
            "• Sonder une mine = -20 PV.",
            "• APPUI LONG (ou bouton DRAPEAU) = marquer une dalle.",
            "• Retouchez une dalle marquee : le heros la DESAMORCE sans",
            "  risque, mais le drapeau est CONSOMME.",
            "• Autant de drapeaux que de mines : un drapeau gaspille sur",
            "  une dalle vide = une mine a faire sauter plus tard !",
            "• Traversez jusqu'au passage de droite.",
            "",
            "2) LA SALLE DU COFFRE",
            "• Placez-vous a cote d'un bloc, touchez-le pour le pousser.",
            "• 3 blocs sur les 3 dalles orange -> le coffre s'ouvre -> CLE.",
            "• La cle ouvre la PORTE violette.",
            "",
            "3) LA SALLE DE RANGEMENT",
            "• 4 caisses a ranger sur les 4 dalles BLEUES de l'alcove.",
            "• Les caisses ne montent que par le puits du milieu, et le",
            "  heros ne peut les contourner que par le puits de droite :",
            "  il n'y a qu'UNE seule solution, la plus profonde d'abord.",
            "• Les 4 caisses rangees -> la TRAPPE s'ouvre = victoire.",
            "• Coince ? Menu ☰ > REINITIALISER LES CAISSES.",
            "",
            "Glissez le doigt = deplacer la carte.  − / + = zoom.  ◎ = recentrer.",
            "",
            "Touchez l'ecran pour fermer."
        )
        var y = h * 0.13f
        for (line in lines) {
            canvas.drawText(line, w * 0.07f, y, paint)
            y += h * 0.0272f
        }
    }

    private fun drawEnd(canvas: Canvas, w: Float, h: Float) {
        paint.color = Color.argb(218, 0, 0, 0)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.textSize = h * 0.055f
        paint.color = if (victory) Color.rgb(80, 225, 120) else Color.rgb(235, 65, 60)
        canvas.drawText(if (victory) "VICTOIRE !" else "GAME OVER", w / 2f, h * 0.42f, paint)
        paint.isFakeBoldText = false
        paint.color = Color.WHITE
        paint.textSize = h * 0.023f
        canvas.drawText("$playerName  -  ${diffName(difficulty)}", w / 2f, h * 0.47f, paint)
        canvas.drawText("Mines desamorcees : $disarmed", w / 2f, h * 0.51f, paint)
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

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; lastX = e.x; lastY = e.y
                downTime = System.currentTimeMillis()
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (state != PLAYING || showMenu || showInv || showHelp) return true
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

    private fun handleUp(e: MotionEvent): Boolean {
        if (showHelp) { showHelp = false; return true }
        if (showInv) { showInv = false; return true }

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
