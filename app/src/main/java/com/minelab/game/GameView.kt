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
import kotlin.math.sin

/**
 * Plateau de demineur 2D dans un labyrinthe.
 * - On touche une dalle : le heros s'y rend tout seul et la sonde.
 * - Appui long : drapeau. Toucher une dalle a drapeau : le heros la desamorce (sans risque).
 * - On peut faire glisser la carte au doigt, zoomer, et recentrer sur le heros.
 * - Salle a enigme : pousser les 3 blocs sur les 3 dalles -> coffre -> cle -> porte -> sortie.
 */
class GameView(context: Context) : View(context) {

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
    private var flagMode = false
    private var gameOver = false
    private var victory = false
    private var showHelp = false

    private var message = "Touchez une dalle : le heros ira la sonder."
    private var msgTimer = 5f
    private var boomFlash = 0f
    private var walkPhase = 0f

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
    private val btnRestart = RectF()
    private val btnHelp = RectF()

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downTime = 0L
    private var dragging = false
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
        following = true
        path = emptyList(); pathStep = 0
        pendingReveal = -1; pendingDisarm = -1; pendingChest = false; pendingDoor = false
        hp = 100; disarmed = 0
        gameOver = false; victory = false; flagMode = false; showHelp = false
        boomFlash = 0f
        showMsg("Trouvez la salle a enigme (en bas a droite de la carte) !")
    }

    private fun showMsg(m: String) { message = m; msgTimer = 4f }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        tile = min(w, h) / 9f
        boardTop = h * 0.13f
        boardBottom = h - h * 0.10f

        val bh = h * 0.062f
        val y0 = boardBottom + (h - boardBottom - bh) / 2f
        val m = w * 0.025f
        val gap = w * 0.012f
        var x = w - m
        btnHelp.set(x - bh, y0, x, y0 + bh); x -= bh + gap
        btnRestart.set(x - bh, y0, x, y0 + bh); x -= bh + gap
        btnCenter.set(x - bh, y0, x, y0 + bh); x -= bh + gap
        btnZoomIn.set(x - bh, y0, x, y0 + bh); x -= bh + gap
        btnZoomOut.set(x - bh, y0, x, y0 + bh); x -= bh + gap
        btnFlag.set(m, y0, x, y0 + bh)
    }

    // ============================================================ LOGIQUE

    private fun update(dt: Float) {
        msgTimer -= dt
        boomFlash = (boomFlash - dt * 1.6f).coerceAtLeast(0f)

        if (following) {
            camX += (fx - camX) * min(1f, dt * 8f)
            camY += (fy - camY) * min(1f, dt * 8f)
        }
        clampCam()

        if (gameOver || victory) return

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
                if (hx == world.exitX && hy == world.exitY) { victory = true; return }
            } else {
                fx += sign(dx) * min(abs(dx), speed)
                fy += sign(dy) * min(abs(dy), speed)
            }
        } else {
            // Actions declenchees a l'arrivee
            if (pendingReveal >= 0) {
                val i = pendingReveal; pendingReveal = -1
                doReveal(i % world.size, i / world.size)
            } else if (pendingDisarm >= 0) {
                val i = pendingDisarm; pendingDisarm = -1
                doDisarm(i % world.size, i / world.size)
            } else if (pendingChest) {
                pendingChest = false
                openChest()
            } else if (pendingDoor) {
                pendingDoor = false
                openDoor()
            }
        }
    }

    private fun clampCam() {
        val visW = width / tile
        val visH = (boardBottom - boardTop) / tile
        val s = world.size.toFloat()
        camX = if (s <= visW) s / 2f else camX.coerceIn(visW / 2f, s - visW / 2f)
        camY = if (s <= visH) s / 2f else camY.coerceIn(visH / 2f, s - visH / 2f)
    }

    private fun clearPendings() {
        pendingReveal = -1; pendingDisarm = -1; pendingChest = false; pendingDoor = false
    }

    private fun doReveal(x: Int, y: Int) {
        val i = world.idx(x, y)
        if (i in world.flagged || i in world.revealed) return
        if (i in world.mines) {
            world.mines.remove(i)
            world.exploded.add(i)
            world.revealed.add(i)
            hp -= 20
            boomFlash = 1f
            showMsg("BOUM ! -20 PV. Astuce : appui long = drapeau, puis touchez pour desamorcer.")
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
            showMsg("Mine desamorcee ! La voie est libre.")
        } else {
            world.revealCascade(x, y)
            showMsg("Fausse alerte : pas de mine ici.")
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
        showMsg("Le coffre s'ouvre : vous trouvez une CLE en or !")
    }

    private fun openDoor() {
        if (!world.hasKey) { showMsg("Porte verrouillee. Il faut la cle du coffre."); return }
        world.grid[world.door] = World.FLOOR
        val dx = world.door % world.size
        val dy = world.door / world.size
        world.revealed.add(world.door)
        world.revealCascade(dx, dy)
        showMsg("La porte s'ouvre ! La sortie est juste derriere.")
    }

    /** Amene le heros sur une case voisine sure de (gx,gy). */
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

    /** Pousse un bloc si le heros est juste a cote. */
    private fun tryPush(bx: Int, by: Int): Boolean {
        val dx = bx - hx
        val dy = by - hy
        if (abs(dx) + abs(dy) != 1) return false
        val tx = bx + dx
        val ty = by + dy
        if (!world.canPushInto(tx, ty)) { showMsg("Le bloc ne peut pas aller plus loin."); return true }
        world.blocks.remove(world.idx(bx, by))
        world.blocks.add(world.idx(tx, ty))
        world.revealed.add(world.idx(tx, ty))
        world.revealed.add(world.idx(bx, by))
        clearPendings()
        path = listOf(Pair(bx, by))   // le heros avance sur la case liberee
        pathStep = 0
        val done = world.plates.count { it in world.blocks }
        if (world.platesSolved()) showMsg("Les 3 dalles sont activees ! Le coffre est deverrouille.")
        else showMsg("Dalles activees : $done / 3")
        return true
    }

    private fun onTap(gx: Int, gy: Int) {
        if (gameOver || victory) return
        val i = world.idx(gx, gy)

        // Porte
        if (world.isDoor(gx, gy)) {
            if (!walkNextTo(gx, gy)) { showMsg("Approchez-vous de la porte."); return }
            clearPendings(); pendingDoor = true
            return
        }
        if (!world.isFloor(gx, gy)) { showMsg("C'est un mur."); return }

        // Bloc a pousser
        if (i in world.blocks) {
            if (tryPush(gx, gy)) return
            if (!walkNextTo(gx, gy)) { showMsg("Bloc inaccessible."); return }
            clearPendings()
            showMsg("Le heros se place a cote du bloc. Retouchez-le pour le pousser.")
            return
        }

        // Coffre
        if (i == world.chest) {
            if (!walkNextTo(gx, gy)) { showMsg("Coffre inaccessible."); return }
            clearPendings(); pendingChest = true
            return
        }

        // Dalle deja revelee et sure : on s'y rend
        if (i in world.revealed && i !in world.mines && i !in world.flagged) {
            val p = world.findPath(hx, hy, gx, gy)
            if (p == null) { showMsg("Aucun chemin sur vers cette dalle."); return }
            clearPendings()
            path = p; pathStep = 0
            return
        }

        // Dalle a drapeau : desamorcage (sans risque)
        if (i in world.flagged) {
            if (!walkNextTo(gx, gy)) { showMsg("Dalle inaccessible."); return }
            clearPendings(); pendingDisarm = i
            return
        }

        // Dalle inconnue : sondage (risque : -20 PV si c'est une mine)
        if (!walkNextTo(gx, gy)) { showMsg("Le heros ne peut pas atteindre cette dalle."); return }
        clearPendings(); pendingReveal = i
    }

    private fun onLongPress(gx: Int, gy: Int) {
        if (gameOver || victory) return
        if (!world.isFloor(gx, gy)) return
        val i = world.idx(gx, gy)
        if (i in world.blocks || i == world.chest) return
        if (i in world.revealed) { showMsg("Dalle deja revelee."); return }
        if (i in world.flagged) { world.flagged.remove(i); showMsg("Drapeau retire.") }
        else { world.flagged.add(i); showMsg("Drapeau pose. Retouchez la dalle pour la desamorcer.") }
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
        if (showHelp) drawHelp(canvas, w, h)
        if (gameOver || victory) drawEnd(canvas, w, h)

        postInvalidateOnAnimation()
    }

    private fun sx(gx: Float, w: Float) = w / 2f + (gx - camX) * tile
    private fun sy(gy: Float) = (boardTop + boardBottom) / 2f + (gy - camY) * tile

    private fun drawBoard(canvas: Canvas, w: Float) {
        val boardH = boardBottom - boardTop
        val hx0 = (w / tile).toInt() / 2 + 2
        val hy0 = (boardH / tile).toInt() / 2 + 2
        val cgx = camX.toInt()
        val cgy = camY.toInt()
        val gap = tile * 0.045f
        val r = tile * 0.16f

        for (gy in cgy - hy0..cgy + hy0) {
            for (gx in cgx - hx0..cgx + hx0) {
                if (!world.inside(gx, gy)) continue
                val l = sx(gx.toFloat(), w)
                val t = sy(gy.toFloat())
                if (l > w || t > boardBottom || l + tile < 0f || t + tile < boardTop) continue
                rect.set(l + gap, t + gap, l + tile - gap, t + tile - gap)
                val i = world.idx(gx, gy)

                // Mur
                if (world.grid[i] == World.WALL) {
                    paint.color = Color.rgb(24, 28, 40)
                    canvas.drawRoundRect(rect, r * 0.5f, r * 0.5f, paint)
                    continue
                }
                // Porte verrouillee
                if (world.grid[i] == World.DOOR) {
                    paint.color = Color.rgb(120, 70, 175)
                    canvas.drawRoundRect(rect, r, r, paint)
                    paint.color = Color.rgb(245, 215, 90)
                    canvas.drawCircle(rect.centerX(), rect.centerY() - tile * 0.05f, tile * 0.09f, paint)
                    canvas.drawRect(
                        rect.centerX() - tile * 0.035f, rect.centerY() - tile * 0.02f,
                        rect.centerX() + tile * 0.035f, rect.centerY() + tile * 0.16f, paint
                    )
                    continue
                }

                val rev = i in world.revealed
                val isExit = gx == world.exitX && gy == world.exitY

                when {
                    i in world.exploded -> {
                        paint.color = Color.rgb(115, 32, 32)
                        canvas.drawRoundRect(rect, r, r, paint)
                        paint.color = Color.rgb(22, 22, 22)
                        canvas.drawCircle(rect.centerX(), rect.centerY(), tile * 0.19f, paint)
                    }
                    isExit -> {
                        paint.color = Color.rgb(45, 195, 105)
                        canvas.drawRoundRect(rect, r, r, paint)
                        paint.color = Color.WHITE
                        paint.textAlign = Paint.Align.CENTER
                        paint.textSize = tile * 0.55f
                        paint.isFakeBoldText = true
                        canvas.drawText("★", rect.centerX(), rect.centerY() + tile * 0.2f, paint)
                        paint.isFakeBoldText = false
                    }
                    rev -> {
                        paint.color = Color.rgb(222, 230, 238)
                        canvas.drawRoundRect(rect, r, r, paint)
                        if (i in world.plates) drawPlate(canvas, i)
                        val n = world.countAround(gx, gy)
                        if (n > 0 && i !in world.plates) {
                            paint.color = numberColor(n)
                            paint.textAlign = Paint.Align.CENTER
                            paint.textSize = tile * 0.55f
                            paint.isFakeBoldText = true
                            canvas.drawText("$n", rect.centerX(), rect.centerY() + tile * 0.19f, paint)
                            paint.isFakeBoldText = false
                        }
                    }
                    else -> {
                        paint.color = Color.rgb(40, 48, 62)
                        canvas.drawRoundRect(rect, r, r, paint)
                        rect.inset(tile * 0.05f, tile * 0.05f)
                        paint.color = Color.rgb(54, 64, 82)
                        canvas.drawRoundRect(rect, r * 0.8f, r * 0.8f, paint)
                        rect.inset(-tile * 0.05f, -tile * 0.05f)
                        if (i in world.flagged) drawFlag(canvas)
                    }
                }

                if (i == world.chest) drawChest(canvas)
                if (i in world.blocks) drawBlock(canvas, i in world.plates)
            }
        }

        drawHero(canvas, w)
    }

    private fun drawPlate(canvas: Canvas, i: Int) {
        val cxx = rect.centerX()
        val cyy = rect.centerY()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = tile * 0.06f
        paint.color = if (i in world.blocks) Color.rgb(45, 175, 95) else Color.rgb(200, 120, 40)
        canvas.drawCircle(cxx, cyy, tile * 0.24f, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cxx, cyy, tile * 0.09f, paint)
    }

    private fun drawBlock(canvas: Canvas, onPlate: Boolean) {
        val inset = tile * 0.02f
        rect.inset(inset, inset)
        paint.color = if (onPlate) Color.rgb(90, 165, 85) else Color.rgb(150, 105, 55)
        canvas.drawRoundRect(rect, tile * 0.1f, tile * 0.1f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = tile * 0.05f
        paint.color = if (onPlate) Color.rgb(55, 115, 55) else Color.rgb(105, 70, 35)
        canvas.drawRoundRect(rect, tile * 0.1f, tile * 0.1f, paint)
        canvas.drawLine(rect.left, rect.top, rect.right, rect.bottom, paint)
        canvas.drawLine(rect.right, rect.top, rect.left, rect.bottom, paint)
        paint.style = Paint.Style.FILL
        rect.inset(-inset, -inset)
    }

    private fun drawChest(canvas: Canvas) {
        val cxx = rect.centerX()
        val cyy = rect.centerY()
        val s = tile * 0.34f
        val unlocked = world.platesSolved()
        paint.color = if (world.chestOpen) Color.rgb(105, 80, 50) else Color.rgb(150, 100, 45)
        canvas.drawRoundRect(
            RectF(cxx - s, cyy - s * 0.7f, cxx + s, cyy + s * 0.8f),
            tile * 0.06f, tile * 0.06f, paint
        )
        paint.color = if (world.chestOpen) Color.rgb(70, 55, 35) else Color.rgb(120, 80, 35)
        canvas.drawRoundRect(
            RectF(cxx - s, cyy - s * 0.9f, cxx + s, cyy - s * 0.2f),
            tile * 0.06f, tile * 0.06f, paint
        )
        paint.color = if (world.chestOpen) Color.rgb(160, 150, 120)
        else if (unlocked) Color.rgb(255, 220, 80) else Color.rgb(190, 175, 90)
        canvas.drawRect(cxx - s * 0.16f, cyy - s * 0.5f, cxx + s * 0.16f, cyy + s * 0.35f, paint)
        if (unlocked && !world.chestOpen) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = tile * 0.04f
            paint.color = Color.rgb(255, 235, 120)
            canvas.drawRoundRect(
                RectF(cxx - s * 1.15f, cyy - s * 1.05f, cxx + s * 1.15f, cyy + s * 0.95f),
                tile * 0.08f, tile * 0.08f, paint
            )
            paint.style = Paint.Style.FILL
        }
    }

    private fun drawFlag(canvas: Canvas) {
        val cxx = rect.centerX()
        val cyy = rect.centerY()
        val s = tile * 0.30f
        paint.color = Color.rgb(228, 232, 240)
        paint.strokeWidth = tile * 0.05f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(cxx - s * 0.15f, cyy + s * 0.75f, cxx - s * 0.15f, cyy - s * 0.8f, paint)
        paint.style = Paint.Style.FILL
        val p = Path()
        p.moveTo(cxx - s * 0.15f, cyy - s * 0.85f)
        p.lineTo(cxx + s * 0.85f, cyy - s * 0.4f)
        p.lineTo(cxx - s * 0.15f, cyy + s * 0.05f)
        p.close()
        paint.color = Color.rgb(230, 55, 50)
        canvas.drawPath(p, paint)
    }

    private fun drawHero(canvas: Canvas, w: Float) {
        val l = sx(fx - 0.5f, w)
        val t = sy(fy - 0.5f)
        val cxx = l + tile / 2f
        val walking = pathStep < path.size
        val bob = if (walking) sin(walkPhase.toDouble()).toFloat() * tile * 0.03f else 0f
        val swing = if (walking) sin(walkPhase.toDouble()).toFloat() * tile * 0.06f else 0f
        val by = t + tile * 0.86f + bob

        paint.color = Color.argb(80, 0, 0, 0)
        canvas.drawOval(cxx - tile * 0.2f, by - tile * 0.05f, cxx + tile * 0.2f, by + tile * 0.05f, paint)
        paint.color = Color.rgb(45, 55, 85)
        canvas.drawRect(cxx - tile * 0.13f + swing, by - tile * 0.18f, cxx - tile * 0.02f + swing, by, paint)
        canvas.drawRect(cxx + tile * 0.02f - swing, by - tile * 0.18f, cxx + tile * 0.13f - swing, by, paint)
        paint.color = Color.rgb(225, 70, 55)
        rect.set(cxx - tile * 0.17f, by - tile * 0.46f, cxx + tile * 0.17f, by - tile * 0.16f)
        canvas.drawRoundRect(rect, tile * 0.06f, tile * 0.06f, paint)
        canvas.drawRect(cxx - tile * 0.24f, by - tile * 0.42f, cxx - tile * 0.16f, by - tile * 0.22f, paint)
        canvas.drawRect(cxx + tile * 0.16f, by - tile * 0.42f, cxx + tile * 0.24f, by - tile * 0.22f, paint)
        paint.color = Color.rgb(250, 205, 60)
        canvas.drawCircle(cxx, by - tile * 0.58f, tile * 0.16f, paint)
        paint.color = Color.rgb(30, 30, 30)
        canvas.drawCircle(cxx - tile * 0.06f, by - tile * 0.61f, tile * 0.025f, paint)
        canvas.drawCircle(cxx + tile * 0.06f, by - tile * 0.61f, tile * 0.025f, paint)
        rect.set(cxx - tile * 0.07f, by - tile * 0.57f, cxx + tile * 0.07f, by - tile * 0.5f)
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
        paint.color = Color.rgb(17, 20, 30)
        canvas.drawRect(0f, 0f, w, boardTop, paint)
        val ts = h * 0.021f

        // Barre de vie
        val bw = w * 0.26f
        val bx = w * 0.04f
        val by = boardTop * 0.22f
        paint.color = Color.rgb(52, 22, 22)
        rect.set(bx, by, bx + bw, by + ts * 1.35f)
        canvas.drawRoundRect(rect, 12f, 12f, paint)
        paint.color = Color.rgb(215, 55, 50)
        rect.set(bx, by, bx + bw * (hp / 100f), by + ts * 1.35f)
        canvas.drawRoundRect(rect, 12f, 12f, paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = ts
        paint.isFakeBoldText = true
        canvas.drawText("PV $hp", bx + ts * 0.4f, by + ts * 1.05f, paint)
        paint.isFakeBoldText = false

        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = ts * 0.9f
        val key = if (world.hasKey) "CLE : oui" else "CLE : non"
        canvas.drawText(
            "Mines ${world.mines.size}  Drapeaux ${world.flagged.size}  $key",
            w - w * 0.04f, by + ts * 1.05f, paint
        )

        // Objectif
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.rgb(150, 160, 180)
        paint.textSize = ts * 0.85f
        val done = world.plates.count { it in world.blocks }
        val obj = when {
            world.hasKey -> "Objectif : ouvrir la porte, puis rejoindre l'etoile."
            world.platesSolved() -> "Objectif : ouvrir le coffre pour recuperer la cle."
            else -> "Objectif : pousser les 3 blocs sur les 3 dalles ($done/3)."
        }
        canvas.drawText(obj, bx, boardTop * 0.55f, paint)

        if (msgTimer > 0f) {
            paint.textAlign = Paint.Align.CENTER
            paint.color = Color.rgb(255, 225, 140)
            paint.textSize = ts * 0.92f
            canvas.drawText(message, w / 2f, boardTop * 0.86f, paint)
        }

        // Barre du bas
        paint.color = Color.rgb(17, 20, 30)
        canvas.drawRect(0f, boardBottom, w, h, paint)
        drawBtn(canvas, btnFlag, if (flagMode) "DRAPEAU : ON" else "DRAPEAU : OFF", flagMode)
        drawBtn(canvas, btnZoomOut, "−", false)
        drawBtn(canvas, btnZoomIn, "+", false)
        drawBtn(canvas, btnCenter, "◎", false)
        drawBtn(canvas, btnRestart, "⟳", false)
        drawBtn(canvas, btnHelp, "?", false)
    }

    private fun drawBtn(canvas: Canvas, r: RectF, label: String, on: Boolean) {
        paint.color = if (on) Color.rgb(200, 60, 55) else Color.rgb(44, 51, 68)
        canvas.drawRoundRect(r, r.height() * 0.28f, r.height() * 0.28f, paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.textSize = if (label.length > 2) r.height() * 0.32f else r.height() * 0.45f
        canvas.drawText(label, r.centerX(), r.centerY() + r.height() * 0.15f, paint)
        paint.isFakeBoldText = false
    }

    private fun drawHelp(canvas: Canvas, w: Float, h: Float) {
        paint.color = Color.argb(235, 8, 10, 18)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.rgb(255, 225, 140)
        paint.textSize = h * 0.028f
        paint.isFakeBoldText = true
        canvas.drawText("COMMENT JOUER", w * 0.08f, h * 0.14f, paint)
        paint.isFakeBoldText = false
        paint.color = Color.WHITE
        paint.textSize = h * 0.019f
        val lines = listOf(
            "• Touchez une dalle : le heros y va tout seul et la sonde.",
            "• Une dalle sure affiche le nombre de mines autour.",
            "• Sonder une mine = -20 PV. Pour eviter ca :",
            "   APPUI LONG sur une dalle suspecte = drapeau,",
            "   puis retouchez-la : le heros la DESAMORCE sans risque.",
            "• Glissez le doigt pour deplacer la carte, ◎ pour recentrer.",
            "• Boutons - et + pour zoomer.",
            "",
            "ENIGME (salle en bas a droite) :",
            "• Placez-vous a cote d'un bloc puis touchez-le pour le pousser.",
            "• Les 3 blocs sur les 3 dalles -> le coffre s'ouvre.",
            "• Le coffre contient la CLE -> ouvre la PORTE violette.",
            "• Derriere la porte : l'etoile verte = victoire !",
            "",
            "(Les monstres et le combat arrivent bientot.)",
            "",
            "Touchez l'ecran pour fermer."
        )
        var y = h * 0.2f
        for (line in lines) {
            canvas.drawText(line, w * 0.08f, y, paint)
            y += h * 0.032f
        }
    }

    private fun drawEnd(canvas: Canvas, w: Float, h: Float) {
        paint.color = Color.argb(215, 0, 0, 0)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = h * 0.055f
        paint.isFakeBoldText = true
        paint.color = if (victory) Color.rgb(80, 225, 120) else Color.rgb(235, 65, 60)
        canvas.drawText(if (victory) "VICTOIRE !" else "GAME OVER", w / 2f, h * 0.42f, paint)
        paint.isFakeBoldText = false
        paint.color = Color.WHITE
        paint.textSize = h * 0.024f
        canvas.drawText("Mines desamorcees : $disarmed", w / 2f, h * 0.48f, paint)
        canvas.drawText("Touchez l'ecran pour rejouer", w / 2f, h * 0.53f, paint)
    }

    // ============================================================ TACTILE

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y
                lastX = e.x; lastY = e.y
                downTime = System.currentTimeMillis()
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
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
            MotionEvent.ACTION_UP -> {
                if (showHelp) { showHelp = false; return true }
                if (gameOver || victory) { newGame(); return true }
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
                if (btnRestart.contains(e.x, e.y)) { newGame(); return true }
                if (btnHelp.contains(e.x, e.y)) { showHelp = true; return true }

                if (e.y in boardTop..boardBottom) {
                    val gx = floor(camX + (e.x - width / 2f) / tile).toInt()
                    val gy = floor(camY + (e.y - (boardTop + boardBottom) / 2f) / tile).toInt()
                    if (!world.inside(gx, gy)) return true
                    following = true
                    if (flagMode || longPress) onLongPress(gx, gy) else onTap(gx, gy)
                }
            }
        }
        return true
    }
}
