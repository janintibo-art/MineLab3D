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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Moteur 3D "raycasting" (style Wolfenstein) dessine sur Canvas :
 * vue a la premiere personne dans le labyrinthe, mines a demineur,
 * monstres en billboard, portes-enigmes et HUD tactile.
 */
class GameView(context: Context) : View(context) {

    private val fov = PI / 3.0

    private var world = World()
    private var px = 1.5
    private var py = 1.5
    private var angle = 0.4
    private var hp = 100.0
    private var message = "Sondez le sol, desamorcez, et trouvez la sortie !"
    private var msgTimer = 5.0
    private var flash = 0.0
    private var damageFlash = 0.0
    private var attackAnim = 0.0
    private var gameOver = false
    private var victory = false
    private var kills = 0
    private var disarmedCount = 0

    private var activeRiddle: Riddle? = null
    private var riddleCell = -1

    // Controles
    private var moveF = false
    private var moveB = false
    private var turnL = false
    private var turnR = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastTime = System.nanoTime()
    private var zbuf = DoubleArray(1)
    private var rays = 1

    // Rectangles des boutons (recalcules selon la taille d'ecran)
    private val btnUp = RectF()
    private val btnDown = RectF()
    private val btnLeft = RectF()
    private val btnRight = RectF()
    private val btnProbe = RectF()
    private val btnDisarm = RectF()
    private val btnSword = RectF()
    private val btnReplay = RectF()
    private val answerRects = arrayOf(RectF(), RectF(), RectF())

    init {
        isFocusable = true
    }

    private fun reset() {
        world = World()
        px = 1.5; py = 1.5; angle = 0.4
        hp = 100.0
        gameOver = false; victory = false
        kills = 0; disarmedCount = 0
        activeRiddle = null; riddleCell = -1
        flash = 0.0; damageFlash = 0.0; attackAnim = 0.0
        showMsg("Nouvelle partie ! Bonne chance.")
    }

    private fun showMsg(m: String) {
        message = m
        msgTimer = 3.5
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        rays = (w / 3).coerceAtLeast(60)
        zbuf = DoubleArray(rays)
        val bs = h * 0.16f
        val m = h * 0.03f
        // Croix directionnelle a gauche
        val x0 = m
        val y0 = h - m - 2f * bs
        btnUp.set(x0 + bs, y0, x0 + 2 * bs, y0 + bs)
        btnLeft.set(x0, y0 + bs, x0 + bs, y0 + 2 * bs)
        btnRight.set(x0 + 2 * bs, y0 + bs, x0 + 3 * bs, y0 + 2 * bs)
        btnDown.set(x0 + bs, y0 + bs, x0 + 2 * bs, y0 + 2 * bs)
        // Boutons d'action a droite
        val bw = bs * 2.3f
        val bh = bs * 0.72f
        val xr = w - m - bw
        btnSword.set(xr, h - m - bh, xr + bw, h - m)
        btnDisarm.set(xr, h - m - 2.2f * bh, xr + bw, h - m - 1.2f * bh)
        btnProbe.set(xr, h - m - 3.4f * bh, xr + bw, h - m - 2.4f * bh)
        btnReplay.set(w / 2f - bw / 2, h * 0.62f, w / 2f + bw / 2, h * 0.62f + bh * 1.2f)
    }

    // ------------------------------------------------------------------ update

    private fun update(dt: Double) {
        if (gameOver || victory || activeRiddle != null) return

        if (turnL) angle -= 2.2 * dt
        if (turnR) angle += 2.2 * dt
        var sp = 0.0
        if (moveF) sp += 2.2
        if (moveB) sp -= 1.6
        if (sp != 0.0) {
            tryMove(px + cos(angle) * sp * dt, py + sin(angle) * sp * dt)
        }

        // Monstres : poursuite + degats de contact
        for (mo in world.monsters) {
            if (!mo.alive) continue
            mo.hitFlash = (mo.hitFlash - dt).coerceAtLeast(0.0)
            val dx = px - mo.x
            val dy = py - mo.y
            val d = hypot(dx, dy)
            if (d in 0.65..6.0) {
                val ms = 1.15 * dt
                val nx = mo.x + dx / d * ms
                val ny = mo.y + dy / d * ms
                if (world.cell(nx.toInt(), mo.y.toInt()) == World.FLOOR) mo.x = nx
                if (world.cell(mo.x.toInt(), ny.toInt()) == World.FLOOR) mo.y = ny
            }
            if (d < 0.85) {
                hp -= 14.0 * dt
                damageFlash = 0.35
            }
        }

        msgTimer -= dt
        flash = (flash - dt * 1.5).coerceAtLeast(0.0)
        damageFlash = (damageFlash - dt).coerceAtLeast(0.0)
        attackAnim = (attackAnim - dt * 3.5).coerceAtLeast(0.0)

        if (hp <= 0) {
            hp = 0.0
            gameOver = true
        }
        if (world.idx(px.toInt(), py.toInt()) == world.exitIdx) victory = true
    }

    private fun tryMove(nx: Double, ny: Double) {
        val ok = canEnter(nx.toInt(), ny.toInt())
        var fx = px
        var fy = py
        if (canEnter(nx.toInt(), py.toInt()) && (ok || nx.toInt() == px.toInt())) fx = nx
        if (canEnter(px.toInt(), ny.toInt()) && (ok || ny.toInt() == py.toInt())) fy = ny
        val oldCell = world.idx(px.toInt(), py.toInt())
        px = fx
        py = fy
        val newCell = world.idx(px.toInt(), py.toInt())
        if (newCell != oldCell) enterCell(px.toInt(), py.toInt())
    }

    private fun canEnter(cx: Int, cy: Int): Boolean {
        val c = world.cell(cx, cy)
        if (c == World.WALL) return false
        if (c == World.DOOR) {
            // Ouvre l'enigme quand on se cogne a la porte
            val i = world.idx(cx, cy)
            activeRiddle = world.riddles[i]
            riddleCell = i
            return false
        }
        val i = world.idx(cx, cy)
        if (i in world.flagged) {
            showMsg("Mine signalee ! Desamorcez-la d'abord.")
            return false
        }
        return true
    }

    private fun enterCell(cx: Int, cy: Int) {
        val i = world.idx(cx, cy)
        if (i in world.mines) {
            world.mines.remove(i)
            hp -= 25.0
            flash = 1.0
            showMsg("BOUM ! Vous avez marche sur une mine ! -25 PV")
        }
        world.revealed.add(i)
    }

    /** Case juste devant le joueur (direction cardinale). */
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
            showMsg("MINE DETECTEE devant vous !")
        } else {
            world.revealed.add(i)
            val n = world.mineCountAround(f.first, f.second)
            showMsg(if (n == 0) "Terrain sur. Aucune mine autour." else "Sur ici, mais $n mine(s) adjacente(s) !")
        }
    }

    private fun doDisarm() {
        val f = frontCell()
        val i = world.idx(f.first, f.second)
        if (i in world.flagged && i in world.mines) {
            world.mines.remove(i)
            world.flagged.remove(i)
            world.revealed.add(i)
            disarmedCount++
            showMsg("Mine desamorcee ! ($disarmedCount/${world.totalMines})")
        } else if (i in world.mines) {
            showMsg("Sondez d'abord pour localiser la mine !")
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
            if (d > 1.5) continue
            // Le monstre doit etre devant nous
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
            } else {
                showMsg("Coup d'epee ! Le monstre grogne...")
            }
        }
        if (!hit) showMsg("Votre epee fend l'air.")
    }

    private fun answerRiddle(choice: Int) {
        val r = activeRiddle ?: return
        if (choice == r.correct) {
            world.grid[riddleCell] = World.FLOOR
            world.revealed.add(riddleCell)
            showMsg("Bonne reponse ! La porte s'ouvre dans un grondement.")
        } else {
            hp -= 10.0
            showMsg("Mauvaise reponse ! La porte vous foudroie. -10 PV")
            if (hp <= 0) { hp = 0.0; gameOver = true }
        }
        activeRiddle = null
        riddleCell = -1
    }

    // ------------------------------------------------------------------ draw

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = ((now - lastTime) / 1e9).coerceIn(0.0, 0.05)
        lastTime = now
        update(dt)

        val w = width
        val h = height
        val cy = h / 2f

        // Ciel et sol
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(24, 26, 44)
        canvas.drawRect(0f, 0f, w.toFloat(), cy, paint)
        paint.color = Color.rgb(52, 44, 36)
        canvas.drawRect(0f, cy, w.toFloat(), h.toFloat(), paint)

        // Murs par lancer de rayons (DDA)
        val colW = w.toFloat() / rays
        for (r in 0 until rays) {
            val ra = angle - fov / 2 + fov * (r + 0.5) / rays
            val rdx = cos(ra)
            val rdy = sin(ra)
            var mapX = px.toInt()
            var mapY = py.toInt()
            val ddx = if (rdx == 0.0) 1e30 else abs(1.0 / rdx)
            val ddy = if (rdy == 0.0) 1e30 else abs(1.0 / rdy)
            val stepX = if (rdx < 0) -1 else 1
            val stepY = if (rdy < 0) -1 else 1
            var sideX = if (rdx < 0) (px - mapX) * ddx else (mapX + 1.0 - px) * ddx
            var sideY = if (rdy < 0) (py - mapY) * ddy else (mapY + 1.0 - py) * ddy
            var side = 0
            var tile = World.WALL
            var guard = 0
            while (guard++ < 200) {
                if (sideX < sideY) { sideX += ddx; mapX += stepX; side = 0 }
                else { sideY += ddy; mapY += stepY; side = 1 }
                tile = world.cell(mapX, mapY)
                if (tile == World.WALL || tile == World.DOOR) break
            }
            val dist = ((if (side == 0) sideX - ddx else sideY - ddy) *
                    cos(ra - angle)).coerceAtLeast(0.05)
            zbuf[r] = dist
            val lineH = (h / dist).coerceAtMost(h * 4.0).toFloat()
            val shade = (1.0 / (1.0 + dist * 0.22)).coerceIn(0.12, 1.0) *
                    (if (side == 1) 0.72 else 1.0)
            paint.color = if (tile == World.DOOR)
                Color.rgb((165 * shade).toInt(), (70 * shade).toInt(), (200 * shade).toInt())
            else
                Color.rgb((110 * shade).toInt(), (118 * shade).toInt(), (145 * shade).toInt())
            canvas.drawRect(r * colW, cy - lineH / 2, (r + 1) * colW + 1f, cy + lineH / 2, paint)
        }

        drawSprites(canvas, w, h, cy, colW)
        drawSword(canvas, w, h)
        drawMinimap(canvas, w, h)
        drawHud(canvas, w, h)

        if (flash > 0) {
            paint.color = Color.argb((flash * 190).toInt(), 255, 130, 20)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        }
        if (damageFlash > 0) {
            paint.color = Color.argb((damageFlash * 160).toInt(), 220, 20, 20)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        }

        activeRiddle?.let { drawRiddle(canvas, w, h, it) }
        if (gameOver || victory) drawEndScreen(canvas, w, h)

        postInvalidateOnAnimation()
    }

    /** Monstres, drapeaux de mines et sortie, dessines en "billboard". */
    private fun drawSprites(canvas: Canvas, w: Int, h: Int, cy: Float, colW: Float) {
        data class Spr(val x: Double, val y: Double, val type: Int, val mo: Monster?)

        val list = ArrayList<Spr>()
        for (mo in world.monsters) if (mo.alive) list.add(Spr(mo.x, mo.y, 0, mo))
        for (f in world.flagged) list.add(Spr(f % world.size + 0.5, f / world.size + 0.5, 1, null))
        list.add(Spr(world.exitIdx % world.size + 0.5, world.exitIdx / world.size + 0.5, 2, null))

        list.sortByDescending { hypot(it.x - px, it.y - py) }

        for (s in list) {
            val dx = s.x - px
            val dy = s.y - py
            val dist = hypot(dx, dy)
            if (dist < 0.25 || dist > 18) continue
            var sa = atan2(dy, dx) - angle
            while (sa > PI) sa -= 2 * PI
            while (sa < -PI) sa += 2 * PI
            if (abs(sa) > fov / 2 + 0.6) continue
            val sx = (((sa / fov) + 0.5) * w).toFloat()
            val col = (sx / colW).toInt().coerceIn(0, rays - 1)
            if (dist >= zbuf[col]) continue
            val sh = (h / dist).toFloat()
            val shade = (1.0 / (1.0 + dist * 0.18)).coerceIn(0.25, 1.0)

            when (s.type) {
                0 -> { // Monstre : gobelin rond avec yeux et cornes
                    val mo = s.mo!!
                    val bodyR = sh * 0.30f
                    val byc = cy + sh * 0.14f
                    val fl = mo.hitFlash > 0
                    paint.color = if (fl) Color.rgb(255, 230, 230)
                    else Color.rgb((150 * shade).toInt(), (35 * shade).toInt(), (40 * shade).toInt())
                    canvas.drawCircle(sx, byc, bodyR, paint)
                    paint.color = Color.rgb((90 * shade).toInt(), (20 * shade).toInt(), (25 * shade).toInt())
                    canvas.drawCircle(sx - bodyR * 0.7f, byc - bodyR * 0.9f, bodyR * 0.22f, paint)
                    canvas.drawCircle(sx + bodyR * 0.7f, byc - bodyR * 0.9f, bodyR * 0.22f, paint)
                    paint.color = Color.YELLOW
                    canvas.drawCircle(sx - bodyR * 0.35f, byc - bodyR * 0.25f, bodyR * 0.13f, paint)
                    canvas.drawCircle(sx + bodyR * 0.35f, byc - bodyR * 0.25f, bodyR * 0.13f, paint)
                    // Barre de vie
                    paint.color = Color.argb(190, 0, 0, 0)
                    canvas.drawRect(sx - bodyR, byc - bodyR * 1.7f, sx + bodyR, byc - bodyR * 1.5f, paint)
                    paint.color = Color.rgb(70, 210, 70)
                    val frac = (mo.hp / 50.0).coerceIn(0.0, 1.0).toFloat()
                    canvas.drawRect(sx - bodyR, byc - bodyR * 1.7f,
                        sx - bodyR + 2 * bodyR * frac, byc - bodyR * 1.5f, paint)
                }
                1 -> { // Drapeau rouge sur une mine detectee
                    val fh = sh * 0.5f
                    paint.color = Color.rgb((200 * shade).toInt(), (200 * shade).toInt(), (200 * shade).toInt())
                    canvas.drawRect(sx - fh * 0.03f, cy, sx + fh * 0.03f, cy + fh * 0.55f, paint)
                    paint.color = Color.rgb((230 * shade).toInt(), (40 * shade).toInt(), (40 * shade).toInt())
                    val p = Path()
                    p.moveTo(sx, cy)
                    p.lineTo(sx + fh * 0.35f, cy + fh * 0.13f)
                    p.lineTo(sx, cy + fh * 0.26f)
                    p.close()
                    canvas.drawPath(p, paint)
                }
                2 -> { // Portail de sortie vert
                    val ph = sh * 0.85f
                    paint.color = Color.argb(200, 40, (220 * shade).toInt(), 90)
                    canvas.drawRect(sx - ph * 0.16f, cy - ph * 0.5f, sx + ph * 0.16f, cy + ph * 0.5f, paint)
                    paint.color = Color.argb(90, 120, 255, 150)
                    canvas.drawRect(sx - ph * 0.26f, cy - ph * 0.55f, sx + ph * 0.26f, cy + ph * 0.55f, paint)
                }
            }
        }
    }

    /** Epee du heros en surimpression, animee lors d'une attaque. */
    private fun drawSword(canvas: Canvas, w: Int, h: Int) {
        val swing = (attackAnim * attackAnim).toFloat()
        val bx = w * 0.72f - swing * w * 0.18f
        val by = h * 0.98f - swing * h * 0.10f
        canvas.save()
        canvas.rotate(28f - swing * 55f, bx, by)
        val s = h * 0.0045f
        paint.color = Color.rgb(210, 214, 224) // lame
        val blade = Path()
        blade.moveTo(bx, by - 150 * s)
        blade.lineTo(bx + 16 * s, by - 130 * s)
        blade.lineTo(bx + 16 * s, by - 30 * s)
        blade.lineTo(bx - 16 * s, by - 30 * s)
        blade.lineTo(bx - 16 * s, by - 130 * s)
        blade.close()
        canvas.drawPath(blade, paint)
        paint.color = Color.rgb(150, 110, 40) // garde
        canvas.drawRect(bx - 40 * s, by - 34 * s, bx + 40 * s, by - 22 * s, paint)
        paint.color = Color.rgb(90, 60, 30) // poignee
        canvas.drawRect(bx - 10 * s, by - 22 * s, bx + 10 * s, by + 30 * s, paint)
        canvas.restore()
    }

    /** Mini-carte demineur : cases revelees avec leur chiffre, drapeaux, sortie. */
    private fun drawMinimap(canvas: Canvas, w: Int, h: Int) {
        val range = 4
        val cs = min(w, h) * 0.035f
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
                    c == World.WALL -> Color.argb(210, 30, 32, 48)
                    c == World.DOOR -> Color.argb(210, 140, 60, 180)
                    i == world.exitIdx -> Color.argb(220, 40, 190, 80)
                    i in world.flagged -> Color.argb(220, 190, 40, 40)
                    i in world.revealed -> Color.argb(210, 190, 190, 175)
                    else -> Color.argb(210, 95, 95, 95)
                }
                canvas.drawRect(l, t, l + cs - 1, t + cs - 1, paint)
                if (c == World.FLOOR && i in world.revealed && i != world.exitIdx) {
                    val n = world.mineCountAround(gx, gy)
                    if (n > 0) {
                        paint.color = when (n) {
                            1 -> Color.rgb(30, 80, 220)
                            2 -> Color.rgb(20, 130, 20)
                            else -> Color.rgb(190, 30, 30)
                        }
                        paint.textSize = cs * 0.72f
                        canvas.drawText("$n", l + cs / 2, t + cs * 0.75f, paint)
                    }
                }
            }
        }
        // Joueur : fleche orientee
        val pcx = ox + range * cs + cs / 2
        val pcy = oy + range * cs + cs / 2
        paint.color = Color.rgb(255, 200, 40)
        val a = angle.toFloat()
        val p = Path()
        p.moveTo(pcx + cos(a) * cs * 0.45f, pcy + sin(a) * cs * 0.45f)
        p.lineTo(pcx + cos(a + 2.5f) * cs * 0.32f, pcy + sin(a + 2.5f) * cs * 0.32f)
        p.lineTo(pcx + cos(a - 2.5f) * cs * 0.32f, pcy + sin(a - 2.5f) * cs * 0.32f)
        p.close()
        canvas.drawPath(p, paint)
    }

    private fun drawHud(canvas: Canvas, w: Int, h: Int) {
        val ts = h * 0.042f
        paint.textAlign = Paint.Align.LEFT
        // Barre de vie
        val bw = w * 0.26f
        paint.color = Color.argb(180, 0, 0, 0)
        canvas.drawRect(h * 0.03f, h * 0.03f, h * 0.03f + bw, h * 0.03f + ts, paint)
        paint.color = Color.rgb(200, 40, 40)
        canvas.drawRect(h * 0.03f, h * 0.03f,
            h * 0.03f + bw * (hp / 100.0).toFloat(), h * 0.03f + ts, paint)
        paint.color = Color.WHITE
        paint.textSize = ts * 0.8f
        canvas.drawText("PV ${hp.toInt()}", h * 0.045f, h * 0.03f + ts * 0.78f, paint)
        canvas.drawText(
            "Mines : ${world.mines.size} restantes   Desamorcees : $disarmedCount   Monstres : $kills",
            h * 0.03f, h * 0.03f + ts * 2.0f, paint
        )
        // Message
        if (msgTimer > 0) {
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = ts
            paint.color = Color.argb(160, 0, 0, 0)
            canvas.drawRect(w * 0.15f, h * 0.14f, w * 0.85f, h * 0.14f + ts * 1.5f, paint)
            paint.color = Color.rgb(255, 230, 150)
            canvas.drawText(message, w / 2f, h * 0.14f + ts * 1.08f, paint)
        }
        // Boutons directionnels
        drawBtn(canvas, btnUp, "▲")
        drawBtn(canvas, btnDown, "▼")
        drawBtn(canvas, btnLeft, "◀")
        drawBtn(canvas, btnRight, "▶")
        // Boutons d'action
        drawBtn(canvas, btnProbe, "SONDER")
        drawBtn(canvas, btnDisarm, "DESAMORCER")
        drawBtn(canvas, btnSword, "EPEE !")
    }

    private fun drawBtn(canvas: Canvas, r: RectF, label: String) {
        paint.color = Color.argb(120, 255, 255, 255)
        canvas.drawRoundRect(r, 14f, 14f, paint)
        paint.color = Color.argb(230, 20, 20, 30)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = r.height() * 0.4f
        canvas.drawText(label, r.centerX(), r.centerY() + r.height() * 0.14f, paint)
    }

    private fun drawRiddle(canvas: Canvas, w: Int, h: Int, r: Riddle) {
        paint.color = Color.argb(215, 12, 10, 26)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.color = Color.rgb(200, 160, 255)
        paint.textAlign = Paint.Align.CENTER
        val ts = h * 0.055f
        paint.textSize = ts
        canvas.drawText("~ La porte murmure une enigme ~", w / 2f, h * 0.14f, paint)
        paint.color = Color.WHITE
        var y = h * 0.26f
        for (line in r.question.split("\n")) {
            canvas.drawText(line, w / 2f, y, paint)
            y += ts * 1.25f
        }
        val bw = w * 0.55f
        val bh = h * 0.11f
        for (k in 0..2) {
            val t = h * 0.48f + k * bh * 1.25f
            answerRects[k].set(w / 2f - bw / 2, t, w / 2f + bw / 2, t + bh)
            paint.color = Color.argb(230, 60, 50, 110)
            canvas.drawRoundRect(answerRects[k], 18f, 18f, paint)
            paint.color = Color.WHITE
            paint.textSize = bh * 0.42f
            canvas.drawText(r.answers[k], w / 2f, t + bh * 0.63f, paint)
        }
    }

    private fun drawEndScreen(canvas: Canvas, w: Int, h: Int) {
        paint.color = Color.argb(210, 0, 0, 0)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = h * 0.1f
        paint.color = if (victory) Color.rgb(90, 230, 120) else Color.rgb(230, 60, 60)
        canvas.drawText(
            if (victory) "VICTOIRE !" else "GAME OVER",
            w / 2f, h * 0.3f, paint
        )
        paint.color = Color.WHITE
        paint.textSize = h * 0.045f
        canvas.drawText(
            "Mines desamorcees : $disarmedCount   Monstres vaincus : $kills",
            w / 2f, h * 0.44f, paint
        )
        paint.color = Color.argb(230, 60, 110, 200)
        canvas.drawRoundRect(btnReplay, 18f, 18f, paint)
        paint.color = Color.WHITE
        paint.textSize = btnReplay.height() * 0.45f
        canvas.drawText("REJOUER", btnReplay.centerX(), btnReplay.centerY() + btnReplay.height() * 0.16f, paint)
    }

    // ------------------------------------------------------------------ touch

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val down = e.actionMasked == MotionEvent.ACTION_DOWN ||
                e.actionMasked == MotionEvent.ACTION_POINTER_DOWN

        if (gameOver || victory) {
            if (down && btnReplay.contains(e.getX(e.actionIndex), e.getY(e.actionIndex))) reset()
            clearHolds()
            return true
        }
        activeRiddle?.let {
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

        // Etat maintenu des boutons de deplacement (multi-touch)
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
}
