package com.minelab.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * Rendu 3D a la 3e personne (camera derriere le heros).
 * Le sol est un veritable plateau de demineur : dalles grises non revelees,
 * dalles claires revelees avec leur chiffre ecrit au sol, drapeaux rouges
 * sur les mines detectees. Le petit personnage a l'epee est visible devant la camera.
 *
 * Pas de raycasting : on projette directement des polygones (sol, murs, sprites)
 * avec une camera perspective, tries par distance (algorithme du peintre).
 */
class GameView(context: Context) : View(context) {

    // -------------------------------------------------------------- constantes
    private val fov = PI / 2.6
    private val wallH = 1.05           // hauteur des murs (unites monde)
    private val heroH = 0.52           // hauteur du heros : petit personnage !
    private val monsterH = 0.62
    private val camHeight = 1.30
    private val camPitch = 0.46        // inclinaison vers le bas
    private val camDistMax = 2.55      // recul de la camera derriere le heros
    private val near = 0.12
    private val viewRange = 9.0

    // -------------------------------------------------------------- etat du jeu
    private var world = World()
    private var px = 1.5
    private var py = 1.5
    private var angle = 0.0
    private var hp = 100.0
    private var message = "Sondez les dalles, desamorcez, atteignez la sortie !"
    private var msgTimer = 5.0
    private var flash = 0.0
    private var damageFlash = 0.0
    private var attackAnim = 0.0
    private var walkPhase = 0.0
    private var moving = false
    private var gameOver = false
    private var victory = false
    private var kills = 0
    private var disarmedCount = 0

    private var activeRiddle: Riddle? = null
    private var riddleCell = -1

    // -------------------------------------------------------------- camera
    private var camX = 0.0
    private var camY = 0.0
    private var camZ = 0.0
    private var fwX = 0.0; private var fwY = 0.0; private var fwZ = 0.0
    private var rgX = 0.0; private var rgY = 0.0
    private var upX = 0.0; private var upY = 0.0; private var upZ = 0.0
    private var focal = 1.0

    // -------------------------------------------------------------- controles
    private var moveF = false
    private var moveB = false
    private var turnL = false
    private var turnR = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastTime = System.nanoTime()

    private val btnUp = RectF()
    private val btnDown = RectF()
    private val btnLeft = RectF()
    private val btnRight = RectF()
    private val btnProbe = RectF()
    private val btnDisarm = RectF()
    private val btnSword = RectF()
    private val btnReplay = RectF()
    private val answerRects = arrayOf(RectF(), RectF(), RectF())

    /** Un polygone (ou sprite) a dessiner, trie par profondeur. */
    private class Item(val depth: Double, val draw: (Canvas) -> Unit)

    private val items = ArrayList<Item>(600)

    private fun reset() {
        world = World()
        px = 1.5; py = 1.5; angle = 0.0
        hp = 100.0
        gameOver = false; victory = false
        kills = 0; disarmedCount = 0
        activeRiddle = null; riddleCell = -1
        flash = 0.0; damageFlash = 0.0; attackAnim = 0.0
        world.revealCascade(1, 1)
        showMsg("Nouvelle partie ! Bonne chance.")
    }

    init {
        isFocusable = true
        world.revealCascade(1, 1)
    }

    private fun showMsg(m: String) {
        message = m
        msgTimer = 3.5
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        focal = (w / 2.0) / tan(fov / 2)
        val bs = h * 0.15f
        val m = h * 0.03f
        val x0 = m
        val y0 = h - m - 2f * bs
        btnUp.set(x0 + bs, y0, x0 + 2 * bs, y0 + bs)
        btnLeft.set(x0, y0 + bs, x0 + bs, y0 + 2 * bs)
        btnRight.set(x0 + 2 * bs, y0 + bs, x0 + 3 * bs, y0 + 2 * bs)
        btnDown.set(x0 + bs, y0 + bs, x0 + 2 * bs, y0 + 2 * bs)
        val bw = bs * 2.3f
        val bh = bs * 0.72f
        val xr = w - m - bw
        btnSword.set(xr, h - m - bh, xr + bw, h - m)
        btnDisarm.set(xr, h - m - 2.2f * bh, xr + bw, h - m - 1.2f * bh)
        btnProbe.set(xr, h - m - 3.4f * bh, xr + bw, h - m - 2.4f * bh)
        btnReplay.set(w / 2f - bw / 2, h * 0.62f, w / 2f + bw / 2, h * 0.62f + bh * 1.2f)
    }

    // ================================================================= LOGIQUE

    private fun update(dt: Double) {
        if (gameOver || victory || activeRiddle != null) { moving = false; return }

        if (turnL) angle -= 2.3 * dt
        if (turnR) angle += 2.3 * dt
        var sp = 0.0
        if (moveF) sp += 2.0
        if (moveB) sp -= 1.4
        moving = sp != 0.0
        if (moving) {
            walkPhase += dt * 11.0
            tryMove(px + cos(angle) * sp * dt, py + sin(angle) * sp * dt)
        }

        for (mo in world.monsters) {
            if (!mo.alive) continue
            mo.hitFlash = (mo.hitFlash - dt).coerceAtLeast(0.0)
            val dx = px - mo.x
            val dy = py - mo.y
            val d = hypot(dx, dy)
            if (d in 0.6..6.0) {
                val ms = 1.1 * dt
                val nx = mo.x + dx / d * ms
                val ny = mo.y + dy / d * ms
                if (world.cell(nx.toInt(), mo.y.toInt()) == World.FLOOR) mo.x = nx
                if (world.cell(mo.x.toInt(), ny.toInt()) == World.FLOOR) mo.y = ny
            }
            if (d < 0.8) {
                hp -= 14.0 * dt
                damageFlash = 0.35
            }
        }

        msgTimer -= dt
        flash = (flash - dt * 1.5).coerceAtLeast(0.0)
        damageFlash = (damageFlash - dt).coerceAtLeast(0.0)
        attackAnim = (attackAnim - dt * 3.5).coerceAtLeast(0.0)

        if (hp <= 0) { hp = 0.0; gameOver = true }
        if (world.idx(px.toInt(), py.toInt()) == world.exitIdx) victory = true
    }

    private fun tryMove(nx: Double, ny: Double) {
        val oldCell = world.idx(px.toInt(), py.toInt())
        if (canEnter(nx.toInt(), py.toInt())) px = nx
        if (canEnter(px.toInt(), ny.toInt())) py = ny
        val newCell = world.idx(px.toInt(), py.toInt())
        if (newCell != oldCell) enterCell(px.toInt(), py.toInt())
    }

    private fun canEnter(cx: Int, cy: Int): Boolean {
        val c = world.cell(cx, cy)
        if (c == World.WALL) return false
        if (c == World.DOOR) {
            val i = world.idx(cx, cy)
            activeRiddle = world.riddles[i]
            riddleCell = i
            return false
        }
        if (world.idx(cx, cy) in world.flagged) {
            showMsg("Dalle piegee ! Desamorcez-la d'abord.")
            return false
        }
        return true
    }

    private fun enterCell(cx: Int, cy: Int) {
        val i = world.idx(cx, cy)
        if (i in world.mines) {
            world.mines.remove(i)
            world.revealed.add(i)
            hp -= 25.0
            flash = 1.0
            showMsg("BOUM ! Dalle piegee ! -25 PV")
        } else {
            world.revealCascade(cx, cy)
        }
    }

    /** Dalle juste devant le heros. */
    private fun frontCell(): Pair<Int, Int> {
        val fx = px.toInt() + cos(angle).roundToInt().coerceIn(-1, 1)
        val fy = py.toInt() + sin(angle).roundToInt().coerceIn(-1, 1)
        return Pair(fx, fy)
    }

    private fun doProbe() {
        val f = frontCell()
        val c = world.cell(f.first, f.second)
        if (c == World.WALL) { showMsg("Ce n'est qu'un mur."); return }
        if (c == World.DOOR) { showMsg("Une porte scellee par une enigme..."); return }
        val i = world.idx(f.first, f.second)
        if (i in world.mines) {
            world.flagged.add(i)
            showMsg("MINE DETECTEE sous la dalle devant vous !")
        } else {
            world.revealCascade(f.first, f.second)
            val n = world.mineCountAround(f.first, f.second)
            showMsg(if (n == 0) "Dalle sure, zone degagee !" else "Dalle sure : $n mine(s) autour.")
        }
    }

    private fun doDisarm() {
        val f = frontCell()
        val i = world.idx(f.first, f.second)
        if (i in world.flagged && i in world.mines) {
            world.mines.remove(i)
            world.flagged.remove(i)
            world.revealCascade(f.first, f.second)
            disarmedCount++
            showMsg("Mine desamorcee ! ($disarmedCount/${world.totalMines})")
        } else if (i in world.mines) {
            showMsg("Sondez d'abord pour reperer la mine !")
        } else {
            showMsg("Rien a desamorcer ici.")
        }
    }

    private fun doAttack() {
        attackAnim = 1.0
        var hit = false
        for (mo in world.monsters) {
            if (!mo.alive) continue
            val dx = mo.x - px
            val dy = mo.y - py
            val d = hypot(dx, dy)
            if (d > 1.4 || d < 0.001) continue
            val dot = cos(angle) * dx / d + sin(angle) * dy / d
            if (dot < 0.35) continue
            mo.hp -= 25.0
            mo.hitFlash = 0.25
            hit = true
            if (mo.hp <= 0) {
                mo.alive = false
                kills++
                hp = (hp + 15.0).coerceAtMost(100.0)
                showMsg("Monstre vaincu ! +15 PV")
            } else showMsg("Coup d'epee !")
        }
        if (!hit) showMsg("Votre epee fend l'air.")
    }

    private fun answerRiddle(choice: Int) {
        val r = activeRiddle ?: return
        if (choice == r.correct) {
            world.grid[riddleCell] = World.FLOOR
            world.revealed.add(riddleCell)
            showMsg("Bonne reponse ! La porte s'ouvre.")
        } else {
            hp -= 10.0
            showMsg("Mauvaise reponse ! -10 PV")
            if (hp <= 0) { hp = 0.0; gameOver = true }
        }
        activeRiddle = null
        riddleCell = -1
    }

    // ================================================================= CAMERA 3D

    private fun setupCamera() {
        // Recul de la camera derriere le heros, reduit si un mur est dans le dos
        var d = camDistMax
        while (d > 0.35) {
            val cx = px - cos(angle) * d
            val cy = py - sin(angle) * d
            if (world.cell(cx.toInt(), cy.toInt()) != World.WALL) break
            d -= 0.15
        }
        camX = px - cos(angle) * d
        camY = py - sin(angle) * d
        camZ = camHeight

        val cp = cos(camPitch)
        val sp = sin(camPitch)
        fwX = cos(angle) * cp; fwY = sin(angle) * cp; fwZ = -sp
        rgX = -sin(angle); rgY = cos(angle)
        upX = cos(angle) * sp; upY = sin(angle) * sp; upZ = cp
    }

    /** Passage monde -> espace camera : (droite, haut, profondeur). */
    private fun toCam(x: Double, y: Double, z: Double, out: DoubleArray) {
        val vx = x - camX
        val vy = y - camY
        val vz = z - camZ
        out[0] = vx * rgX + vy * rgY
        out[1] = vx * upX + vy * upY + vz * upZ
        out[2] = vx * fwX + vy * fwY + vz * fwZ
    }

    private val tmp = DoubleArray(3)

    /** Projette un point monde en pixels. Renvoie null si derriere la camera. */
    private fun project(x: Double, y: Double, z: Double): FloatArray? {
        toCam(x, y, z, tmp)
        if (tmp[2] < near) return null
        val sx = (width / 2.0 + focal * tmp[0] / tmp[2]).toFloat()
        val sy = (height / 2.0 - focal * tmp[1] / tmp[2]).toFloat()
        return floatArrayOf(sx, sy, tmp[2].toFloat())
    }

    /**
     * Construit un Path a partir de sommets monde, avec decoupe au plan proche
     * (evite les deformations quand un polygone passe derriere la camera).
     */
    private fun buildPath(verts: Array<DoubleArray>): Path? {
        val cs = ArrayList<DoubleArray>(6)
        for (v in verts) {
            toCam(v[0], v[1], v[2], tmp)
            cs.add(doubleArrayOf(tmp[0], tmp[1], tmp[2]))
        }
        // Sutherland-Hodgman sur le plan d >= near
        val clipped = ArrayList<DoubleArray>(8)
        for (i in cs.indices) {
            val a = cs[i]
            val b = cs[(i + 1) % cs.size]
            val ain = a[2] >= near
            val bin = b[2] >= near
            if (ain) clipped.add(a)
            if (ain != bin) {
                val t = (near - a[2]) / (b[2] - a[2])
                clipped.add(
                    doubleArrayOf(
                        a[0] + (b[0] - a[0]) * t,
                        a[1] + (b[1] - a[1]) * t,
                        near
                    )
                )
            }
        }
        if (clipped.size < 3) return null
        val p = Path()
        for ((i, c) in clipped.withIndex()) {
            val sx = (width / 2.0 + focal * c[0] / c[2]).toFloat()
            val sy = (height / 2.0 - focal * c[1] / c[2]).toFloat()
            if (i == 0) p.moveTo(sx, sy) else p.lineTo(sx, sy)
        }
        p.close()
        return p
    }

    private fun shade(c: Int, f: Double): Int {
        val k = f.coerceIn(0.0, 1.0)
        return Color.rgb(
            (Color.red(c) * k).toInt(),
            (Color.green(c) * k).toInt(),
            (Color.blue(c) * k).toInt()
        )
    }

    private fun fog(dist: Double) = (1.0 / (1.0 + dist * 0.09)).coerceIn(0.25, 1.0)

    // ================================================================= RENDU

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = ((now - lastTime) / 1e9).coerceIn(0.0, 0.05)
        lastTime = now
        update(dt)
        setupCamera()

        val w = width
        val h = height

        // Fond : voute sombre du donjon
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(12, 12, 22)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        items.clear()
        collectFloor()
        collectWalls()
        collectSprites()

        items.sortByDescending { it.depth }   // algorithme du peintre
        for (it in items) it.draw(canvas)

        drawMinimap(canvas, w, h)
        drawHud(canvas, w, h)

        if (flash > 0) {
            paint.color = Color.argb((flash * 190).toInt(), 255, 130, 20)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        }
        if (damageFlash > 0) {
            paint.color = Color.argb((damageFlash * 150).toInt(), 220, 20, 20)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        }

        activeRiddle?.let { drawRiddle(canvas, w, h, it) }
        if (gameOver || victory) drawEndScreen(canvas, w, h)

        postInvalidateOnAnimation()
    }

    /** LE PLATEAU DE DEMINEUR : une dalle par case de sol. */
    private fun collectFloor() {
        val r = viewRange.toInt()
        val cx = px.toInt()
        val cy = py.toInt()
        val front = frontCell()

        for (gy in cy - r..cy + r) {
            for (gx in cx - r..cx + r) {
                if (gx !in 0 until world.size || gy !in 0 until world.size) continue
                val c = world.cell(gx, gy)
                if (c == World.WALL) continue
                val mx = gx + 0.5
                val my = gy + 0.5
                val dist = hypot(mx - camX, my - camY)
                if (dist > viewRange) continue
                // Cull : la dalle doit etre devant la camera
                if ((mx - camX) * fwX + (my - camY) * fwY < -0.8) continue

                val i = world.idx(gx, gy)
                val revealedTile = i in world.revealed
                val flaggedTile = i in world.flagged
                val isExit = i == world.exitIdx
                val isFront = gx == front.first && gy == front.second
                val n = if (revealedTile) world.mineCountAround(gx, gy) else 0
                val f = fog(dist)

                items.add(Item(dist) { cv ->
                    // Bord de la dalle (fait la grille du plateau)
                    val outer = arrayOf(
                        doubleArrayOf(gx.toDouble(), gy.toDouble(), 0.0),
                        doubleArrayOf(gx + 1.0, gy.toDouble(), 0.0),
                        doubleArrayOf(gx + 1.0, gy + 1.0, 0.0),
                        doubleArrayOf(gx.toDouble(), gy + 1.0, 0.0)
                    )
                    buildPath(outer)?.let { p ->
                        paint.color = shade(Color.rgb(58, 58, 70), f)
                        cv.drawPath(p, paint)
                    }
                    // Face superieure de la dalle
                    val e = 0.055
                    val z = if (revealedTile || flaggedTile) 0.0 else 0.09  // dalle non revelee = relief
                    val inner = arrayOf(
                        doubleArrayOf(gx + e, gy + e, z),
                        doubleArrayOf(gx + 1 - e, gy + e, z),
                        doubleArrayOf(gx + 1 - e, gy + 1 - e, z),
                        doubleArrayOf(gx + e, gy + 1 - e, z)
                    )
                    val base = when {
                        isExit -> Color.rgb(60, 205, 95)
                        flaggedTile -> Color.rgb(200, 70, 60)
                        c == World.DOOR -> Color.rgb(150, 80, 190)
                        revealedTile -> Color.rgb(205, 200, 185)
                        else -> Color.rgb(126, 130, 140)      // dalle a sonder
                    }
                    buildPath(inner)?.let { p ->
                        paint.color = shade(base, f * (if (isFront) 1.25 else 1.0).coerceAtMost(1.0))
                        cv.drawPath(p, paint)
                    }
                    // Petit cote clair pour donner du relief aux dalles non revelees
                    if (!revealedTile && !flaggedTile) {
                        val side = arrayOf(
                            doubleArrayOf(gx + e, gy + 1 - e, 0.0),
                            doubleArrayOf(gx + 1 - e, gy + 1 - e, 0.0),
                            doubleArrayOf(gx + 1 - e, gy + 1 - e, z),
                            doubleArrayOf(gx + e, gy + 1 - e, z)
                        )
                        buildPath(side)?.let { p ->
                            paint.color = shade(Color.rgb(92, 96, 106), f)
                            cv.drawPath(p, paint)
                        }
                    }
                    // Contour blanc sur la dalle ciblee
                    if (isFront) {
                        buildPath(inner)?.let { p ->
                            paint.style = Paint.Style.STROKE
                            paint.strokeWidth = height * 0.005f
                            paint.color = Color.argb(220, 255, 235, 120)
                            cv.drawPath(p, paint)
                            paint.style = Paint.Style.FILL
                        }
                    }
                    // Drapeau plante sur une mine detectee
                    if (flaggedTile) drawFlag(cv, mx, my, f)
                    // LE CHIFFRE DU DEMINEUR, ecrit au sol
                    if (revealedTile && n > 0 && !isExit && c != World.DOOR) {
                        project(mx, my, 0.02)?.let { s ->
                            val ts = (focal * 0.62 / s[2]).toFloat()
                            if (ts > 6f) {
                                paint.textAlign = Paint.Align.CENTER
                                paint.textSize = ts
                                paint.isFakeBoldText = true
                                paint.color = shade(numberColor(n), f)
                                cv.drawText("$n", s[0], s[1] + ts * 0.36f, paint)
                                paint.isFakeBoldText = false
                            }
                        }
                    }
                }
                )
            }
        }
    }

    private fun numberColor(n: Int): Int = when (n) {
        1 -> Color.rgb(20, 60, 220)
        2 -> Color.rgb(15, 130, 30)
        3 -> Color.rgb(215, 25, 25)
        4 -> Color.rgb(20, 20, 130)
        5 -> Color.rgb(130, 20, 20)
        6 -> Color.rgb(15, 130, 130)
        7 -> Color.rgb(15, 15, 15)
        else -> Color.rgb(90, 90, 90)
    }

    /** Murs en volume : seules les faces visibles sont dessinees. */
    private fun collectWalls() {
        val r = viewRange.toInt()
        val cx = px.toInt()
        val cy = py.toInt()
        for (gy in cy - r..cy + r) {
            for (gx in cx - r..cx + r) {
                if (world.cell(gx, gy) != World.WALL) continue
                val mx = gx + 0.5
                val my = gy + 0.5
                val dist = hypot(mx - camX, my - camY)
                if (dist > viewRange) continue
                if ((mx - camX) * fwX + (my - camY) * fwY < -1.2) continue
                val f = fog(dist)

                // Faces laterales (uniquement celles qui donnent sur du sol)
                addWallFace(gx, gy, 0, -1, f, 0.92)   // nord
                addWallFace(gx, gy, 0, 1, f, 0.70)    // sud
                addWallFace(gx, gy, -1, 0, f, 0.80)   // ouest
                addWallFace(gx, gy, 1, 0, f, 0.60)    // est

                // Dessus du mur
                val top = arrayOf(
                    doubleArrayOf(gx.toDouble(), gy.toDouble(), wallH),
                    doubleArrayOf(gx + 1.0, gy.toDouble(), wallH),
                    doubleArrayOf(gx + 1.0, gy + 1.0, wallH),
                    doubleArrayOf(gx.toDouble(), gy + 1.0, wallH)
                )
                items.add(Item(dist + 0.05) { cv ->
                    buildPath(top)?.let { p ->
                        paint.color = shade(Color.rgb(96, 102, 128), f)
                        cv.drawPath(p, paint)
                    }
                })
            }
        }
    }

    private fun addWallFace(gx: Int, gy: Int, dx: Int, dy: Int, f: Double, lum: Double) {
        if (world.cell(gx + dx, gy + dy) == World.WALL) return   // face cachee
        val verts: Array<DoubleArray> = when {
            dy == -1 -> arrayOf(
                doubleArrayOf(gx.toDouble(), gy.toDouble(), 0.0),
                doubleArrayOf(gx + 1.0, gy.toDouble(), 0.0),
                doubleArrayOf(gx + 1.0, gy.toDouble(), wallH),
                doubleArrayOf(gx.toDouble(), gy.toDouble(), wallH)
            )
            dy == 1 -> arrayOf(
                doubleArrayOf(gx.toDouble(), gy + 1.0, 0.0),
                doubleArrayOf(gx + 1.0, gy + 1.0, 0.0),
                doubleArrayOf(gx + 1.0, gy + 1.0, wallH),
                doubleArrayOf(gx.toDouble(), gy + 1.0, wallH)
            )
            dx == -1 -> arrayOf(
                doubleArrayOf(gx.toDouble(), gy.toDouble(), 0.0),
                doubleArrayOf(gx.toDouble(), gy + 1.0, 0.0),
                doubleArrayOf(gx.toDouble(), gy + 1.0, wallH),
                doubleArrayOf(gx.toDouble(), gy.toDouble(), wallH)
            )
            else -> arrayOf(
                doubleArrayOf(gx + 1.0, gy.toDouble(), 0.0),
                doubleArrayOf(gx + 1.0, gy + 1.0, 0.0),
                doubleArrayOf(gx + 1.0, gy + 1.0, wallH),
                doubleArrayOf(gx + 1.0, gy.toDouble(), wallH)
            )
        }
        val fx = gx + 0.5 + dx * 0.5
        val fy = gy + 0.5 + dy * 0.5
        val d = hypot(fx - camX, fy - camY)
        items.add(Item(d) { cv ->
            buildPath(verts)?.let { p ->
                paint.color = shade(Color.rgb(112, 118, 148), f * lum)
                cv.drawPath(p, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f
                paint.color = shade(Color.rgb(60, 64, 84), f)
                cv.drawPath(p, paint)
                paint.style = Paint.Style.FILL
            }
        })
    }

    /** Heros, monstres et portail. */
    private fun collectSprites() {
        // Le heros (petit personnage a l'epee)
        val dHero = hypot(px - camX, py - camY)
        items.add(Item(dHero) { cv -> drawHero(cv) })

        for (mo in world.monsters) {
            if (!mo.alive) continue
            val d = hypot(mo.x - camX, mo.y - camY)
            if (d > viewRange) continue
            items.add(Item(d) { cv -> drawMonster(cv, mo) })
        }

        val ex = world.exitIdx % world.size + 0.5
        val ey = world.exitIdx / world.size + 0.5
        val de = hypot(ex - camX, ey - camY)
        if (de < viewRange) items.add(Item(de) { cv -> drawPortal(cv, ex, ey) })
    }

    private fun drawFlag(cv: Canvas, mx: Double, my: Double, f: Double) {
        val b = project(mx, my, 0.0) ?: return
        val t = project(mx, my, 0.42) ?: return
        val hpx = b[1] - t[1]
        if (hpx < 3f) return
        paint.color = shade(Color.rgb(235, 235, 235), f)
        paint.strokeWidth = (hpx * 0.09f).coerceAtLeast(1.5f)
        paint.style = Paint.Style.STROKE
        cv.drawLine(b[0], b[1], t[0], t[1], paint)
        paint.style = Paint.Style.FILL
        val p = Path()
        p.moveTo(t[0], t[1])
        p.lineTo(t[0] + hpx * 0.45f, t[1] + hpx * 0.17f)
        p.lineTo(t[0], t[1] + hpx * 0.34f)
        p.close()
        paint.color = shade(Color.rgb(230, 45, 45), f)
        cv.drawPath(p, paint)
    }

    /** LE PETIT HEROS, vu de dos, avec son epee. */
    private fun drawHero(cv: Canvas) {
        val b = project(px, py, 0.0) ?: return
        val t = project(px, py, heroH) ?: return
        val hh = b[1] - t[1]                       // hauteur a l'ecran
        if (hh < 4f) return
        val cxs = b[0]
        val by = b[1]
        val bob = if (moving) (sin(walkPhase) * hh * 0.035f).toFloat() else 0f
        val legSwing = if (moving) (sin(walkPhase) * hh * 0.16f).toFloat() else 0f

        // Ombre au sol
        paint.color = Color.argb(90, 0, 0, 0)
        cv.drawOval(
            cxs - hh * 0.26f, by - hh * 0.06f,
            cxs + hh * 0.26f, by + hh * 0.06f, paint
        )
        // Jambes
        paint.color = Color.rgb(50, 55, 85)
        cv.drawRect(cxs - hh * 0.17f + legSwing, by - hh * 0.30f, cxs - hh * 0.03f + legSwing, by, paint)
        cv.drawRect(cxs + hh * 0.03f - legSwing, by - hh * 0.30f, cxs + hh * 0.17f - legSwing, by, paint)
        // Corps (tunique verte + ceinture)
        paint.color = Color.rgb(60, 140, 90)
        cv.drawRect(cxs - hh * 0.21f, by - hh * 0.70f + bob, cxs + hh * 0.21f, by - hh * 0.26f + bob, paint)
        paint.color = Color.rgb(110, 80, 40)
        cv.drawRect(cxs - hh * 0.21f, by - hh * 0.38f + bob, cxs + hh * 0.21f, by - hh * 0.31f + bob, paint)
        // Bouclier (bras gauche)
        paint.color = Color.rgb(160, 165, 180)
        cv.drawCircle(cxs - hh * 0.27f, by - hh * 0.50f + bob, hh * 0.13f, paint)
        paint.color = Color.rgb(190, 60, 55)
        cv.drawCircle(cxs - hh * 0.27f, by - hh * 0.50f + bob, hh * 0.06f, paint)
        // Tete + casque
        paint.color = Color.rgb(232, 195, 160)
        cv.drawCircle(cxs, by - hh * 0.82f + bob, hh * 0.16f, paint)
        paint.color = Color.rgb(190, 165, 60)
        cv.drawArc(
            cxs - hh * 0.17f, by - hh * 1.00f + bob,
            cxs + hh * 0.17f, by - hh * 0.66f + bob,
            180f, 180f, true, paint
        )
        // Epee (bras droit), animee lors de l'attaque
        val sw = (attackAnim * attackAnim).toFloat()
        cv.save()
        val hx = cxs + hh * 0.26f
        val hy = by - hh * 0.52f + bob
        cv.rotate(-20f + sw * 110f, hx, hy)
        paint.color = Color.rgb(120, 85, 45)
        cv.drawRect(hx - hh * 0.04f, hy - hh * 0.02f, hx + hh * 0.04f, hy + hh * 0.14f, paint)
        paint.color = Color.rgb(200, 170, 70)
        cv.drawRect(hx - hh * 0.14f, hy - hh * 0.06f, hx + hh * 0.14f, hy - hh * 0.02f, paint)
        paint.color = Color.rgb(225, 230, 240)
        val blade = Path()
        blade.moveTo(hx, hy - hh * 0.78f)
        blade.lineTo(hx + hh * 0.07f, hy - hh * 0.62f)
        blade.lineTo(hx + hh * 0.07f, hy - hh * 0.06f)
        blade.lineTo(hx - hh * 0.07f, hy - hh * 0.06f)
        blade.lineTo(hx - hh * 0.07f, hy - hh * 0.62f)
        blade.close()
        cv.drawPath(blade, paint)
        cv.restore()
    }

    private fun drawMonster(cv: Canvas, mo: Monster) {
        val b = project(mo.x, mo.y, 0.0) ?: return
        val t = project(mo.x, mo.y, monsterH) ?: return
        val hh = b[1] - t[1]
        if (hh < 4f) return
        val cxs = b[0]
        val by = b[1]
        val d = hypot(mo.x - camX, mo.y - camY)
        val f = fog(d)
        val hitFx = mo.hitFlash > 0

        paint.color = Color.argb(90, 0, 0, 0)
        cv.drawOval(cxs - hh * 0.3f, by - hh * 0.07f, cxs + hh * 0.3f, by + hh * 0.07f, paint)
        // Corps
        paint.color = if (hitFx) Color.rgb(255, 235, 235) else shade(Color.rgb(150, 45, 50), f)
        cv.drawCircle(cxs, by - hh * 0.42f, hh * 0.42f, paint)
        // Cornes
        paint.color = if (hitFx) Color.WHITE else shade(Color.rgb(95, 25, 30), f)
        cv.drawCircle(cxs - hh * 0.32f, by - hh * 0.74f, hh * 0.10f, paint)
        cv.drawCircle(cxs + hh * 0.32f, by - hh * 0.74f, hh * 0.10f, paint)
        // Yeux
        paint.color = Color.rgb(255, 220, 40)
        cv.drawCircle(cxs - hh * 0.16f, by - hh * 0.50f, hh * 0.07f, paint)
        cv.drawCircle(cxs + hh * 0.16f, by - hh * 0.50f, hh * 0.07f, paint)
        paint.color = Color.BLACK
        cv.drawCircle(cxs - hh * 0.16f, by - hh * 0.50f, hh * 0.03f, paint)
        cv.drawCircle(cxs + hh * 0.16f, by - hh * 0.50f, hh * 0.03f, paint)
        // Barre de vie
        val bw = hh * 0.5f
        paint.color = Color.argb(200, 0, 0, 0)
        cv.drawRect(cxs - bw, by - hh * 1.02f, cxs + bw, by - hh * 0.94f, paint)
        paint.color = Color.rgb(70, 210, 70)
        val frac = (mo.hp / 50.0).coerceIn(0.0, 1.0).toFloat()
        cv.drawRect(cxs - bw, by - hh * 1.02f, cxs - bw + 2 * bw * frac, by - hh * 0.94f, paint)
    }

    private fun drawPortal(cv: Canvas, ex: Double, ey: Double) {
        val b = project(ex, ey, 0.0) ?: return
        val t = project(ex, ey, 1.0) ?: return
        val hh = b[1] - t[1]
        if (hh < 4f) return
        paint.color = Color.argb(70, 120, 255, 160)
        cv.drawOval(b[0] - hh * 0.32f, t[1], b[0] + hh * 0.32f, b[1], paint)
        paint.color = Color.argb(190, 60, 235, 120)
        cv.drawOval(b[0] - hh * 0.2f, t[1] + hh * 0.12f, b[0] + hh * 0.2f, b[1] - hh * 0.04f, paint)
    }

    // ================================================================= HUD

    private fun drawMinimap(cv: Canvas, w: Int, h: Int) {
        val range = 5
        val cs = min(w, h) * 0.028f
        val ox = w - cs * (2 * range + 1) - h * 0.03f
        val oy = h * 0.03f
        paint.textAlign = Paint.Align.CENTER
        val cpx = px.toInt()
        val cpy = py.toInt()
        for (dy in -range..range) {
            for (dx in -range..range) {
                val gx = cpx + dx
                val gy = cpy + dy
                val l = ox + (dx + range) * cs
                val t = oy + (dy + range) * cs
                val c = world.cell(gx, gy)
                val i = world.idx(gx.coerceIn(0, world.size - 1), gy.coerceIn(0, world.size - 1))
                paint.color = when {
                    c == World.WALL -> Color.argb(215, 30, 32, 48)
                    c == World.DOOR -> Color.argb(215, 140, 60, 180)
                    i == world.exitIdx -> Color.argb(225, 40, 190, 80)
                    i in world.flagged -> Color.argb(225, 190, 40, 40)
                    i in world.revealed -> Color.argb(215, 200, 196, 180)
                    else -> Color.argb(215, 110, 114, 124)
                }
                cv.drawRect(l, t, l + cs - 1, t + cs - 1, paint)
            }
        }
        val pcx = ox + range * cs + cs / 2
        val pcy = oy + range * cs + cs / 2
        paint.color = Color.rgb(255, 205, 45)
        val a = angle.toFloat()
        val p = Path()
        p.moveTo(pcx + cos(a) * cs * 0.5f, pcy + sin(a) * cs * 0.5f)
        p.lineTo(pcx + cos(a + 2.5f) * cs * 0.36f, pcy + sin(a + 2.5f) * cs * 0.36f)
        p.lineTo(pcx + cos(a - 2.5f) * cs * 0.36f, pcy + sin(a - 2.5f) * cs * 0.36f)
        p.close()
        cv.drawPath(p, paint)
    }

    private fun drawHud(cv: Canvas, w: Int, h: Int) {
        val ts = h * 0.042f
        paint.textAlign = Paint.Align.LEFT
        val bw = w * 0.24f
        paint.color = Color.argb(180, 0, 0, 0)
        cv.drawRect(h * 0.03f, h * 0.03f, h * 0.03f + bw, h * 0.03f + ts, paint)
        paint.color = Color.rgb(200, 40, 40)
        cv.drawRect(h * 0.03f, h * 0.03f, h * 0.03f + bw * (hp / 100.0).toFloat(), h * 0.03f + ts, paint)
        paint.color = Color.WHITE
        paint.textSize = ts * 0.78f
        cv.drawText("PV ${hp.toInt()}", h * 0.045f, h * 0.03f + ts * 0.76f, paint)
        cv.drawText(
            "Mines : ${world.mines.size}   Desamorcees : $disarmedCount   Monstres : $kills",
            h * 0.03f, h * 0.03f + ts * 2.0f, paint
        )
        if (msgTimer > 0) {
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = ts
            paint.color = Color.argb(165, 0, 0, 0)
            cv.drawRect(w * 0.13f, h * 0.13f, w * 0.87f, h * 0.13f + ts * 1.5f, paint)
            paint.color = Color.rgb(255, 230, 150)
            cv.drawText(message, w / 2f, h * 0.13f + ts * 1.08f, paint)
        }
        drawBtn(cv, btnUp, "▲")
        drawBtn(cv, btnDown, "▼")
        drawBtn(cv, btnLeft, "◀")
        drawBtn(cv, btnRight, "▶")
        drawBtn(cv, btnProbe, "SONDER")
        drawBtn(cv, btnDisarm, "DESAMORCER")
        drawBtn(cv, btnSword, "EPEE !")
    }

    private fun drawBtn(cv: Canvas, r: RectF, label: String) {
        paint.color = Color.argb(115, 255, 255, 255)
        cv.drawRoundRect(r, 14f, 14f, paint)
        paint.color = Color.argb(235, 18, 18, 28)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = r.height() * 0.38f
        cv.drawText(label, r.centerX(), r.centerY() + r.height() * 0.13f, paint)
    }

    private fun drawRiddle(cv: Canvas, w: Int, h: Int, r: Riddle) {
        paint.color = Color.argb(218, 12, 10, 26)
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.color = Color.rgb(200, 160, 255)
        paint.textAlign = Paint.Align.CENTER
        val ts = h * 0.052f
        paint.textSize = ts
        cv.drawText("~ La porte murmure une enigme ~", w / 2f, h * 0.13f, paint)
        paint.color = Color.WHITE
        var y = h * 0.25f
        for (line in r.question.split("\n")) {
            cv.drawText(line, w / 2f, y, paint)
            y += ts * 1.25f
        }
        val bw = w * 0.55f
        val bh = h * 0.11f
        for (k in 0..2) {
            val t = h * 0.47f + k * bh * 1.25f
            answerRects[k].set(w / 2f - bw / 2, t, w / 2f + bw / 2, t + bh)
            paint.color = Color.argb(230, 60, 50, 110)
            cv.drawRoundRect(answerRects[k], 18f, 18f, paint)
            paint.color = Color.WHITE
            paint.textSize = bh * 0.4f
            cv.drawText(r.answers[k], w / 2f, t + bh * 0.62f, paint)
        }
    }

    private fun drawEndScreen(cv: Canvas, w: Int, h: Int) {
        paint.color = Color.argb(210, 0, 0, 0)
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = h * 0.1f
        paint.color = if (victory) Color.rgb(90, 230, 120) else Color.rgb(230, 60, 60)
        cv.drawText(if (victory) "VICTOIRE !" else "GAME OVER", w / 2f, h * 0.3f, paint)
        paint.color = Color.WHITE
        paint.textSize = h * 0.045f
        cv.drawText(
            "Mines desamorcees : $disarmedCount   Monstres vaincus : $kills",
            w / 2f, h * 0.44f, paint
        )
        paint.color = Color.argb(230, 60, 110, 200)
        cv.drawRoundRect(btnReplay, 18f, 18f, paint)
        paint.color = Color.WHITE
        paint.textSize = btnReplay.height() * 0.45f
        cv.drawText("REJOUER", btnReplay.centerX(), btnReplay.centerY() + btnReplay.height() * 0.16f, paint)
    }

    // ================================================================= TACTILE

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val down = e.actionMasked == MotionEvent.ACTION_DOWN ||
                e.actionMasked == MotionEvent.ACTION_POINTER_DOWN

        if (gameOver || victory) {
            if (down && btnReplay.contains(e.getX(e.actionIndex), e.getY(e.actionIndex))) reset()
            clearHolds()
            return true
        }
        if (activeRiddle != null) {
            if (down) {
                val x = e.getX(e.actionIndex)
                val y = e.getY(e.actionIndex)
                for (k in 0..2) if (answerRects[k].contains(x, y)) answerRiddle(k)
            }
            clearHolds()
            return true
        }

        if (down) {
            val x = e.getX(e.actionIndex)
            val y = e.getY(e.actionIndex)
            when {
                btnProbe.contains(x, y) -> doProbe()
                btnDisarm.contains(x, y) -> doDisarm()
                btnSword.contains(x, y) -> doAttack()
            }
        }

        clearHolds()
        val allUp = e.actionMasked == MotionEvent.ACTION_UP ||
                e.actionMasked == MotionEvent.ACTION_CANCEL
        if (!allUp) {
            for (i in 0 until e.pointerCount) {
                if (e.actionMasked == MotionEvent.ACTION_POINTER_UP && i == e.actionIndex) continue
                val x = e.getX(i)
                val y = e.getY(i)
                if (btnUp.contains(x, y)) moveF = true
                if (btnDown.contains(x, y)) moveB = true
                if (btnLeft.contains(x, y)) turnL = true
                if (btnRight.contains(x, y)) turnR = true
            }
        }
        return true
    }

    private fun clearHolds() {
        moveF = false; moveB = false; turnL = false; turnR = false
    }

    @Suppress("unused")
    private fun unusedAbs(v: Double) = abs(v)
}
