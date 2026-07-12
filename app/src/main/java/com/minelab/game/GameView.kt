package com.minelab.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sign

/**
 * Vue 2D de dessus : le labyrinthe est un grand plateau de demineur.
 * On touche une dalle : le petit bonhomme s'y rend tout seul (plus court chemin
 * par les dalles deja revelees), puis il la sonde.
 * Appui long (ou bouton DRAPEAU) : poser/enlever un drapeau.
 */
class GameView(context: Context) : View(context) {

    private var world = World()

    // Heros : position en cases (entiere) + position affichee (fluide)
    private var hx = 1
    private var hy = 1
    private var fx = 1.5f
    private var fy = 1.5f

    private var path: List<Pair<Int, Int>> = emptyList()
    private var pathStep = 0
    private var pendingReveal = -1
    private var pendingDisarm = -1
    private var disarmed = 0

    private var hp = 100
    private var flagMode = false
    private var gameOver = false
    private var victory = false
    private var message = "Touchez une dalle : le heros ira la sonder."
    private var msgTimer = 4f
    private var boomFlash = 0f
    private var walkPhase = 0f

    // Camera (en cases)
    private var camX = 1.5f
    private var camY = 1.5f
    private var tile = 90f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastTime = System.nanoTime()

    private var boardTop = 0f
    private var boardBottom = 0f

    private val btnFlag = RectF()
    private val btnZoomOut = RectF()
    private val btnZoomIn = RectF()
    private val btnRestart = RectF()

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private val rect = RectF()

    init {
        isFocusable = true
        newGame()
    }

    private fun newGame() {
        world = World()
        hx = world.startX; hy = world.startY
        fx = hx + 0.5f; fy = hy + 0.5f
        camX = fx; camY = fy
        path = emptyList(); pathStep = 0; pendingReveal = -1; pendingDisarm = -1
        disarmed = 0
        hp = 100
        gameOver = false; victory = false
        flagMode = false
        boomFlash = 0f
        world.revealCascade(hx, hy)
        showMsg("Touchez une dalle : le heros ira la sonder.")
    }

    private fun showMsg(m: String) { message = m; msgTimer = 3.5f }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        tile = min(w, h) / 9f
        boardTop = h * 0.10f
        boardBottom = h - h * 0.11f
        val bh = h * 0.075f
        val y0 = boardBottom + (h - boardBottom - bh) / 2f
        val m = w * 0.03f
        val small = bh * 1.15f
        btnFlag.set(m, y0, m + w * 0.34f, y0 + bh)
        btnZoomOut.set(w - m - small * 3.4f, y0, w - m - small * 2.4f, y0 + bh)
        btnZoomIn.set(w - m - small * 2.2f, y0, w - m - small * 1.2f, y0 + bh)
        btnRestart.set(w - m - small, y0, w - m, y0 + bh)
    }

    // ============================================================ LOGIQUE

    private fun update(dt: Float) {
        // Camera qui suit doucement le heros
        camX += (fx - camX) * min(1f, dt * 8f)
        camY += (fy - camY) * min(1f, dt * 8f)

        msgTimer -= dt
        boomFlash = (boomFlash - dt * 1.6f).coerceAtLeast(0f)

        if (gameOver || victory) return

        // Deplacement automatique le long du chemin
        if (pathStep < path.size) {
            walkPhase += dt * 12f
            val (tx, ty) = path[pathStep]
            val gx = tx + 0.5f
            val gy = ty + 0.5f
            val speed = 6.5f * dt
            val dx = gx - fx
            val dy = gy - fy
            val d = hypot(dx, dy)
            if (d <= speed) {
                fx = gx; fy = gy
                hx = tx; hy = ty
                pathStep++
                onArriveCell()
            } else {
                fx += sign(dx) * min(abs(dx), speed)
                fy += sign(dy) * min(abs(dy), speed)
            }
        } else if (pendingReveal >= 0) {
            val i = pendingReveal
            pendingReveal = -1
            doReveal(i % world.size, i / world.size)
        } else if (pendingDisarm >= 0) {
            val i = pendingDisarm
            pendingDisarm = -1
            doDisarm(i % world.size, i / world.size)
        }
    }

    private fun onArriveCell() {
        if (hx == world.exitX && hy == world.exitY) {
            victory = true
            return
        }
        if (pathStep >= path.size && pendingReveal < 0) showMsg("Arrive.")
    }

    private fun doReveal(x: Int, y: Int) {
        val i = world.idx(x, y)
        if (i in world.flagged) return
        if (i in world.mines) {
            world.mines.remove(i)
            world.exploded.add(i)
            world.revealed.add(i)
            hp -= 20
            boomFlash = 1f
            showMsg("BOUM ! Dalle piegee. -20 PV")
            if (hp <= 0) { hp = 0; gameOver = true }
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
            world.revealed.add(i)
            disarmed++
            showMsg("Mine desamorcee ! ($disarmed) La voie est libre.")
        } else {
            world.revealCascade(x, y)
            showMsg("Fausse alerte : aucune mine sous cette dalle.")
        }
    }

    /** Amene le heros sur une case voisine sure de (gx,gy). Renvoie false si impossible. */
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

    private fun onTap(gx: Int, gy: Int) {
        if (gameOver || victory) return
        if (!world.isFloor(gx, gy)) { showMsg("C'est un mur."); return }
        val i = world.idx(gx, gy)

        // Dalle deja revelee et sure : on s'y rend
        if (i in world.revealed && i !in world.mines && i !in world.flagged) {
            val p = world.findPath(hx, hy, gx, gy)
            if (p == null) { showMsg("Aucun chemin sur vers cette dalle."); return }
            path = p; pathStep = 0; pendingReveal = -1; pendingDisarm = -1
            return
        }
        // Dalle marquee d'un drapeau : le heros va la DESAMORCER
        if (i in world.flagged) {
            if (!walkNextTo(gx, gy)) { showMsg("Dalle inaccessible."); return }
            pendingReveal = -1
            pendingDisarm = i
            return
        }

        // Dalle inconnue : le heros va la SONDER (attention, si c'est une mine : boum)
        if (!walkNextTo(gx, gy)) { showMsg("Le heros ne peut pas atteindre cette dalle."); return }
        pendingDisarm = -1
        pendingReveal = i
    }

    private fun onLongPress(gx: Int, gy: Int) {
        if (gameOver || victory) return
        if (!world.isFloor(gx, gy)) return
        val i = world.idx(gx, gy)
        if (i in world.revealed) { showMsg("Dalle deja revelee."); return }
        if (i in world.flagged) { world.flagged.remove(i); showMsg("Drapeau retire.") }
        else { world.flagged.add(i); showMsg("Drapeau pose.") }
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
        paint.color = Color.rgb(10, 12, 20)
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
        if (gameOver || victory) drawEnd(canvas, w, h)

        postInvalidateOnAnimation()
    }

    private fun cx(gx: Float, w: Float) = w / 2f + (gx - camX) * tile
    private fun cy(gy: Float) = (boardTop + boardBottom) / 2f + (gy - camY) * tile

    private fun drawBoard(canvas: Canvas, w: Float) {
        val boardH = boardBottom - boardTop
        val half = (min(w, boardH) / tile).toInt() / 2 + 3
        val cgx = camX.toInt()
        val cgy = camY.toInt()
        val gap = tile * 0.045f
        val r = tile * 0.16f

        for (gy in cgy - half - 1..cgy + half + 1) {
            for (gx in cgx - half - 1..cgx + half + 1) {
                if (!world.inside(gx, gy)) continue
                val l = cx(gx.toFloat(), w)
                val t = cy(gy.toFloat())
                if (l > w || t > boardBottom || l + tile < 0 || t + tile < boardTop) continue
                rect.set(l + gap, t + gap, l + tile - gap, t + tile - gap)
                val i = world.idx(gx, gy)

                if (!world.isFloor(gx, gy)) {
                    // Mur du labyrinthe
                    paint.color = Color.rgb(26, 30, 42)
                    canvas.drawRoundRect(rect, r * 0.5f, r * 0.5f, paint)
                    continue
                }

                val isExit = gx == world.exitX && gy == world.exitY
                val rev = i in world.revealed

                when {
                    i in world.exploded -> {
                        paint.color = Color.rgb(120, 30, 30)
                        canvas.drawRoundRect(rect, r, r, paint)
                        paint.color = Color.rgb(25, 25, 25)
                        canvas.drawCircle(rect.centerX(), rect.centerY(), tile * 0.20f, paint)
                    }
                    isExit && rev -> {
                        paint.color = Color.rgb(45, 190, 100)
                        canvas.drawRoundRect(rect, r, r, paint)
                        paint.color = Color.rgb(255, 255, 255)
                        paint.textAlign = Paint.Align.CENTER
                        paint.textSize = tile * 0.5f
                        paint.isFakeBoldText = true
                        canvas.drawText("★", rect.centerX(), rect.centerY() + tile * 0.18f, paint)
                        paint.isFakeBoldText = false
                    }
                    rev -> {
                        // Dalle retournee (claire) + chiffre
                        paint.color = Color.rgb(222, 230, 238)
                        canvas.drawRoundRect(rect, r, r, paint)
                        val n = world.countAround(gx, gy)
                        if (n > 0) {
                            paint.color = numberColor(n)
                            paint.textAlign = Paint.Align.CENTER
                            paint.textSize = tile * 0.55f
                            paint.isFakeBoldText = true
                            canvas.drawText("$n", rect.centerX(), rect.centerY() + tile * 0.19f, paint)
                            paint.isFakeBoldText = false
                        }
                    }
                    else -> {
                        // Dalle non revelee (relief sombre facon demineur)
                        paint.color = Color.rgb(40, 48, 62)
                        canvas.drawRoundRect(rect, r, r, paint)
                        rect.inset(tile * 0.05f, tile * 0.05f)
                        paint.color = Color.rgb(53, 63, 80)
                        canvas.drawRoundRect(rect, r * 0.8f, r * 0.8f, paint)
                        rect.inset(-tile * 0.05f, -tile * 0.05f)
                        if (i in world.flagged) drawFlag(canvas, rect)
                    }
                }
            }
        }

        // Le petit bonhomme
        drawHero(canvas, w)
    }

    private fun drawFlag(canvas: Canvas, r: RectF) {
        val cxx = r.centerX()
        val cyy = r.centerY()
        val s = tile * 0.30f
        paint.color = Color.rgb(225, 228, 235)
        paint.strokeWidth = tile * 0.05f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(cxx - s * 0.15f, cyy + s * 0.75f, cxx - s * 0.15f, cyy - s * 0.8f, paint)
        paint.style = Paint.Style.FILL
        val p = Path()
        p.moveTo(cxx - s * 0.15f, cyy - s * 0.85f)
        p.lineTo(cxx + s * 0.85f, cyy - s * 0.40f)
        p.lineTo(cxx - s * 0.15f, cyy + s * 0.05f)
        p.close()
        paint.color = Color.rgb(230, 55, 50)
        canvas.drawPath(p, paint)
    }

    private fun drawHero(canvas: Canvas, w: Float) {
        val l = cx(fx - 0.5f, w)
        val t = cy(fy - 0.5f)
        val cxx = l + tile / 2f
        val walking = pathStep < path.size
        val bob = if (walking) (kotlin.math.sin(walkPhase.toDouble()).toFloat() * tile * 0.03f) else 0f
        val swing = if (walking) (kotlin.math.sin(walkPhase.toDouble()).toFloat() * tile * 0.06f) else 0f
        val by = t + tile * 0.86f + bob

        // Ombre
        paint.color = Color.argb(80, 0, 0, 0)
        canvas.drawOval(cxx - tile * 0.20f, by - tile * 0.05f, cxx + tile * 0.20f, by + tile * 0.05f, paint)
        // Jambes
        paint.color = Color.rgb(45, 55, 85)
        canvas.drawRect(cxx - tile * 0.13f + swing, by - tile * 0.18f, cxx - tile * 0.02f + swing, by, paint)
        canvas.drawRect(cxx + tile * 0.02f - swing, by - tile * 0.18f, cxx + tile * 0.13f - swing, by, paint)
        // Corps
        paint.color = Color.rgb(225, 70, 55)
        rect.set(cxx - tile * 0.17f, by - tile * 0.46f, cxx + tile * 0.17f, by - tile * 0.16f)
        canvas.drawRoundRect(rect, tile * 0.06f, tile * 0.06f, paint)
        // Bras
        canvas.drawRect(cxx - tile * 0.24f, by - tile * 0.42f, cxx - tile * 0.16f, by - tile * 0.22f, paint)
        canvas.drawRect(cxx + tile * 0.16f, by - tile * 0.42f, cxx + tile * 0.24f, by - tile * 0.22f, paint)
        // Tete
        paint.color = Color.rgb(250, 205, 60)
        canvas.drawCircle(cxx, by - tile * 0.58f, tile * 0.16f, paint)
        paint.color = Color.rgb(30, 30, 30)
        canvas.drawCircle(cxx - tile * 0.06f, by - tile * 0.61f, tile * 0.025f, paint)
        canvas.drawCircle(cxx + tile * 0.06f, by - tile * 0.61f, tile * 0.025f, paint)
        rect.set(cxx - tile * 0.07f, by - tile * 0.57f, cxx + tile * 0.07f, by - tile * 0.50f)
        canvas.drawArc(rect, 0f, 180f, true, paint)
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

    private fun drawHud(canvas: Canvas, w: Float, h: Float) {
        // Bandeau haut
        paint.color = Color.rgb(18, 21, 32)
        canvas.drawRect(0f, 0f, w, boardTop, paint)
        val ts = h * 0.024f

        // Barre de vie
        val bw = w * 0.30f
        val bx = w * 0.04f
        val by = boardTop * 0.30f
        paint.color = Color.rgb(50, 20, 20)
        rect.set(bx, by, bx + bw, by + ts * 1.3f)
        canvas.drawRoundRect(rect, 10f, 10f, paint)
        paint.color = Color.rgb(215, 55, 50)
        rect.set(bx, by, bx + bw * (hp / 100f), by + ts * 1.3f)
        canvas.drawRoundRect(rect, 10f, 10f, paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = ts
        paint.isFakeBoldText = true
        canvas.drawText("PV $hp", bx + ts * 0.4f, by + ts * 1.02f, paint)
        paint.isFakeBoldText = false

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            "Mines : ${world.mines.size}  Drapeaux : ${world.flagged.size}  OK : $disarmed",
            w - w * 0.04f, by + ts * 1.02f, paint
        )

        if (msgTimer > 0f) {
            paint.textAlign = Paint.Align.CENTER
            paint.color = Color.rgb(255, 225, 140)
            paint.textSize = ts * 0.95f
            canvas.drawText(message, w / 2f, boardTop * 0.82f, paint)
        }

        // Bandeau bas
        paint.color = Color.rgb(18, 21, 32)
        canvas.drawRect(0f, boardBottom, w, h, paint)
        drawBtn(canvas, btnFlag, if (flagMode) "DRAPEAU : ON" else "DRAPEAU : OFF", flagMode)
        drawBtn(canvas, btnZoomOut, "−", false)
        drawBtn(canvas, btnZoomIn, "+", false)
        drawBtn(canvas, btnRestart, "⟳", false)
    }

    private fun drawBtn(canvas: Canvas, r: RectF, label: String, on: Boolean) {
        paint.color = if (on) Color.rgb(200, 60, 55) else Color.rgb(45, 52, 68)
        canvas.drawRoundRect(r, r.height() * 0.3f, r.height() * 0.3f, paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = r.height() * 0.42f
        paint.isFakeBoldText = true
        canvas.drawText(label, r.centerX(), r.centerY() + r.height() * 0.15f, paint)
        paint.isFakeBoldText = false
    }

    private fun drawEnd(canvas: Canvas, w: Float, h: Float) {
        paint.color = Color.argb(215, 0, 0, 0)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = h * 0.06f
        paint.isFakeBoldText = true
        paint.color = if (victory) Color.rgb(80, 225, 120) else Color.rgb(235, 65, 60)
        canvas.drawText(if (victory) "VICTOIRE !" else "GAME OVER", w / 2f, h * 0.42f, paint)
        paint.isFakeBoldText = false
        paint.color = Color.WHITE
        paint.textSize = h * 0.026f
        canvas.drawText("Touchez l'ecran pour rejouer", w / 2f, h * 0.5f, paint)
    }

    // ============================================================ TACTILE

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; downTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_UP -> {
                if (gameOver || victory) { newGame(); return true }
                val dx = e.x - downX
                val dy = e.y - downY
                if (hypot(dx, dy) > tile * 0.5f) return true   // glissement : ignore
                val longPress = System.currentTimeMillis() - downTime > 400

                // Boutons du bas
                if (btnFlag.contains(e.x, e.y)) {
                    flagMode = !flagMode
                    showMsg(if (flagMode) "Mode drapeau actif." else "Mode deplacement actif.")
                    return true
                }
                if (btnZoomOut.contains(e.x, e.y)) { tile = (tile * 0.85f).coerceAtLeast(38f); return true }
                if (btnZoomIn.contains(e.x, e.y)) { tile = (tile * 1.18f).coerceAtMost(220f); return true }
                if (btnRestart.contains(e.x, e.y)) { newGame(); return true }

                // Plateau
                if (e.y in boardTop..boardBottom) {
                    val gx = floor(camX + (e.x - width / 2f) / tile).toInt()
                    val gy = floor(camY + (e.y - (boardTop + boardBottom) / 2f) / tile).toInt()
                    if (!world.inside(gx, gy)) return true
                    if (flagMode || longPress) onLongPress(gx, gy) else onTap(gx, gy)
                }
            }
        }
        return true
    }
}
