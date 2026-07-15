package com.minelab.game

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader
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
    private var startAtVillage = false

    private val prefs = context.getSharedPreferences("minelab", Context.MODE_PRIVATE)

    /** Si une erreur survient, on l'affiche a l'ecran au lieu de fermer l'app. */
    private var crashLog: String? = null

    private fun crash(e: Throwable) {
        val sb = StringBuilder()
        var cur: Throwable? = e
        var depth = 0
        while (cur != null && depth < 4) {
            if (depth > 0) sb.append("CAUSE : ")
            sb.append(cur.toString()).append("\n")
            for (f in cur.stackTrace.take(8)) sb.append("  ").append(f).append("\n")
            cur = cur.cause
            depth++
        }
        crashLog = sb.toString()
        try { prefs.edit().putString("lastcrash", crashLog).commit() } catch (t: Throwable) { }
        invalidate()
    }

    private fun drawCrash(canvas: Canvas) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(20, 8, 10)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.color = Color.rgb(255, 90, 80)
        paint.textAlign = Paint.Align.LEFT
        paint.isFakeBoldText = true
        paint.textSize = height * 0.026f
        canvas.drawText("ERREUR — envoyez une capture de cet ecran", width * 0.04f, height * 0.055f, paint)
        paint.color = Color.rgb(255, 200, 120)
        paint.textSize = height * 0.016f
        canvas.drawText("(toucher l'ecran pour effacer et reessayer)", width * 0.04f, height * 0.082f, paint)
        paint.isFakeBoldText = false
        paint.color = Color.WHITE
        paint.textSize = height * 0.0135f
        var y = height * 0.11f
        for (raw in (crashLog ?: "").split("\n")) {
            var line = raw
            while (line.length > 64) {
                canvas.drawText(line.take(64), width * 0.04f, y, paint)
                line = line.drop(64)
                y += height * 0.021f
            }
            canvas.drawText(line, width * 0.04f, y, paint)
            y += height * 0.021f
            if (y > height * 0.96f) break
        }
    }
    private val audio = Audio(context)
    private var showSettings = false
    private var showSudoku = false
    private var showLights = false
    private var sudokuSel = -1
    private var sudokuShake = 0f
    private val sudokuCells = Array(16) { RectF() }
    private val sudokuPad = Array(5) { RectF() }
    private val lightCells = Array(9) { RectF() }
    private val setRows = Array(Audio.ZONES.size) { RectF() }
    private val setMusic = RectF()
    private val setSfx = RectF()
    private val setVolDown = RectF()
    private val setVolUp = RectF()

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
    private var pendingSecretChest = -1
    private var secretReturnCell = -1        // ou remonter apres une cache
    private var pendingDoor = false
    private var pendingChest2 = false
    private var pendingChest3 = false
    private var pendingAltar = false
    private var pendingDoor1 = false
    private var pendingDoor2 = false
    private var pendingMini = -1
    private var pendingTorch = -1
    private var pendingDoor3 = false
    private var pendingRune = false
    private var pendingVendor = -1

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
    private var sprayOwned = false
    private var sprayNext = 1
    private var shroomCount = 0
    private var spraysDone = 0
    private var metPierre = false
    private var rodOwned = false
    private var fishCasts = 0
    private var slipOwned = false
    // --- vrai mode peche ---
    private var fishing = false          // la ligne est a l'eau
    private var fishBobX = 0f            // flotteur, coordonnees monde (centre de case + derive)
    private var fishBobY = 0f
    private var fishWaitT = 0f           // compte a rebours avant la touche
    private var fishBiteT = 0f           // > 0 = CA MORD, fenetre de ferrage
    private var fishSplashT = 0f         // petit plouf a l'impact du lancer
    private var fishHx = 0               // position du heros au lancer (bouger = remonter la ligne)
    private var fishHy = 0
    private var fishCount = 0            // poissons frais dans la besace
    // --- la vraie discussion : reponses a choix multiples ---
    private var dlgChoices: List<String> = emptyList()
    private val dlgChoiceRects = ArrayList<RectF>()
    private var dlgWalkerIdx = -1        // a qui repond-on ?
    private var dlgReponses: List<VillagerAI.Reponse> = emptyList()   // effets des choix
    private var vmemRestore = true       // restaurer les souvenirs (pas en nouvelle partie)
    // --- les visites guidees : "Suis-moi !" ---
    private var guideWkIdx = -1          // le walker qui nous guide (-1 : personne)
    private var guideDest = -1           // sa destination (case)
    private var guideDoneLine = ""       // ce qu'il dit en arrivant
    private var guideRepathT = 0f        // recalcul du chemin
    private val guidedDone = HashSet<String>()   // visites deja faites (session)
    private var dlgKind = 0              // 0 papotage ; 1 menu du mage ; 2 question de cours
    private var dlgElems: List<Int> = emptyList()   // elements proposes au menu du mage
    private var dlgElem = -1             // element du cours en attente de reponse
    private var dlgCorrect = -1          // index de la bonne reponse
    // --- les magies elementaires ---
    private val spellKnown = BooleanArray(4)         // air, eau, terre, feu
    private var spellDemo = -1
    private var spellDemoT = 0f
    // --- LANCER de sorts en combat ---
    private val btnSpell = RectF()
    private var spellPicker = false                  // le selecteur d'element est ouvert
    private val spellPickRects = arrayOfNulls<RectF>(4)
    private var spellSel = -1                         // dernier element choisi (raccourci)
    private var spellCd = 0f
    private val ELEM_NAMES = arrayOf("AIR", "EAU", "TERRE", "FEU")
    private val ELEM_COLORS = intArrayOf(
        Color.rgb(120, 230, 200), Color.rgb(80, 170, 245),
        Color.rgb(210, 170, 70), Color.rgb(240, 120, 40)
    )
    private val ELEM_DMG = intArrayOf(35, 45, 60, 80)   // air rapide .. feu devastateur
    private class Bolt(
        var x: Float, var y: Float, val dx: Float, val dy: Float,
        val elem: Int, val dir: Int, var life: Float, var t: Float = 0f, var hit: Boolean = false
    )
    private val bolts = ArrayList<Bolt>()
    // --- systeme monetaire : les pieces d'or ---
    private var gold = 0
    private var goldAnim = 0f                          // petit flash a la recolte
    // --- caches secretes : trappe cachee -> antichambre (monstres) -> coffre ---
    private val secretFound = HashSet<Int>()           // caches deja decouvertes (cellule declencheur)
    private val secretLooted = HashSet<Int>()          // coffres deja pilles
    // --- LE COMMERCE : boutique, achat/vente, marchandage ---
    private var showShop = false
    private var shopMerchant = 0                        // 1 nomade, 2 jolie
    private var shopTab = 0                             // 0 acheter, 1 vendre
    private var shopDiscount = 0                        // reduction obtenue (%)
    private var shopHaggleTries = 0                     // tentatives de negoce ce passage
    private var shopMood = 0                            // humeur : <0 vexe, tarifs durcis
    private var shopMsg = ""
    private var shopMsgT = 0f
    private val shopRowRects = ArrayList<RectF>()
    private val shopBuyRect = RectF()
    private val shopSellRect = RectF()
    private val shopHaggleRect = RectF()
    private val shopCloseRect = RectF()
    private val sSpell = Array(4) { Array(4) { arrayOfNulls<Bitmap>(5) } }
    private val sTav: Array<Array<Bitmap>> = Array(7) { i ->
        val id = i + 1
        arrayOf(bmp("tav${id}d"), bmp("tav${id}u"), bmp("tav${id}l"), bmp("tav${id}r"))
    }
    private val sMerc: Array<Array<Bitmap>> = Array(2) { i ->
        val id = i + 1
        arrayOf(bmp("merc${id}d"), bmp("merc${id}u"), bmp("merc${id}l"), bmp("merc${id}r"))
    }
    private val sStall: Array<Bitmap> = arrayOf(
        bmp("stall_nomad"), bmp("stall_pretty")
    )
    private val sMage: Array<Bitmap> = arrayOf(
        BitmapFactory.decodeResource(resources, R.drawable.maged),
        BitmapFactory.decodeResource(resources, R.drawable.mageu),
        BitmapFactory.decodeResource(resources, R.drawable.magel),
        BitmapFactory.decodeResource(resources, R.drawable.mager)
    )
    // --- la barque ---
    private var onBoat = false           // le heros est en mer, a la rame
    private var boatCell = -1            // ou la barque attend (case de mer)
    // --- quetes farfelues des villageois : 0 pas proposee, 1 en cours, 2 accomplie ---
    private val vQuest = IntArray(11)    // indexe par l'id du villageois (1..10)
    private var fishTotal = 0            // poissons peches en tout (stat)
    private var bootCount = 0            // vieilles bottes remontees (Tomas les collectionne)
    private var algaeCount = 0           // paquets d'algues remontes (le pull de Nina)
    private var petCount = 0             // animaux caresses (pour Agathe)
    private var drinksDone = 0           // canettes bues (le defi de Milo)
    private var clubVisited = false      // on a danse au Punk Club (les poules de Rosa)
    private var swordSharp = false       // Bran a affute l'epee : 55 degats au lieu de 40
    private var ticketOwned = false
    private var tripT = 0f
    private val vendorsUsed = HashSet<Int>()
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

    /** Peintures a texture repetee : le sol est continu, sans aucune couture. */
    private val terPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val shaderCache = HashMap<Int, BitmapShader>()
    private val shMat = Matrix()

    private fun terShader(bmp: Bitmap, scale: Float, scrollX: Float, scrollY: Float): BitmapShader {
        val key = System.identityHashCode(bmp)
        val sh = shaderCache.getOrPut(key) {
            BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
        val originX = width / 2f - camX * tile
        val originY = (boardTop + boardBottom) / 2f - camY * tile
        shMat.reset()
        shMat.setScale(tile * scale / bmp.width, tile * scale / bmp.height)
        shMat.postTranslate(originX + scrollX, originY + scrollY)
        sh.setLocalMatrix(shMat)
        return sh
    }

    private fun useTer(bmp: Bitmap, scale: Float = 1f, alpha: Int = 255, scrollX: Float = 0f, scrollY: Float = 0f) {
        terPaint.color = Color.BLACK
        terPaint.shader = terShader(bmp, scale, scrollX, scrollY)
        terPaint.alpha = alpha
    }
    private var lastTime = System.nanoTime()

    // Sprites
    private val sCoin: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.coin)
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
    // L'ile
    private val sGrass: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.i_grass)
    private val sEarth: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.i_earth)
    private val sDirt: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.i_dirt)
    private val sSand: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.i_sand)
    private val sWater: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.i_water)
    private val sFloorWoodHouse: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.floor_wood)
    private val sGld: Array<Array<Bitmap>> = Array(10) { i ->
        val id = i + 1
        arrayOf(
            bmp("gld${id}d"), bmp("gld${id}u"), bmp("gld${id}l"), bmp("gld${id}r")
        )
    }
    private val sPunk: Array<Array<Bitmap>> = Array(7) { i ->
        val id = i + 1
        arrayOf(bmp("punk${id}d"), bmp("punk${id}u"), bmp("punk${id}l"), bmp("punk${id}r"))
    }
    private val sFisher: Array<Array<Bitmap>> = Array(2) { i ->
        val id = i + 1
        arrayOf(bmp("fisher${id}d"), bmp("fisher${id}u"), bmp("fisher${id}l"), bmp("fisher${id}r"))
    }
    private val sWallsIn: Array<Bitmap> = arrayOf(
        BitmapFactory.decodeResource(resources, R.drawable.wall_cottage),
        BitmapFactory.decodeResource(resources, R.drawable.wall_forge),
        BitmapFactory.decodeResource(resources, R.drawable.wall_squat),
        BitmapFactory.decodeResource(resources, R.drawable.wall_alchemist),
        BitmapFactory.decodeResource(resources, R.drawable.wall_club)
    )
    private val sWindow: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.o_window)
    private val sFire: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.o_fireplace)
    private val sStage: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.o_stage)
    private val sRugRed: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.o_rug_red)
    private val sRugPunk: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.o_rug_punk)
    private val sAmp: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.o_amp)
    private val sGuitar: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.o_guitar)
    private val sBass: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.o_bass)
    private val sDrums: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.o_drums)
    private val sMic: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.o_mic)
    private val sAntifa: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.o_antifa)
    private val sSlip: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.slip)
    private val sRod: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.rod)
    private val sBoat: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.boat1)
    private val sNpc: Array<Array<Bitmap>> = Array(10) { i ->
        val id = i + 1
        arrayOf(
            bmp("npc${id}d"), bmp("npc${id}u"), bmp("npc${id}l"), bmp("npc${id}r")
        )
    }
    private val sPet: Array<Array<Bitmap>> = Array(10) { i ->
        val id = i + 1
        arrayOf(
            bmp("pet${id}d"), bmp("pet${id}u"), bmp("pet${id}l"), bmp("pet${id}r")
        )
    }
    private val sProps: Array<Bitmap?> = arrayOfNulls(103)
    private val sTrees: Array<Bitmap?> = arrayOfNulls(30)
    private val sPlants: Array<Bitmap?> = arrayOfNulls(12)
    private val sRocks: Array<Bitmap?> = arrayOfNulls(10)
    private val sBoats: Array<Bitmap?> = arrayOfNulls(6)
    private val sHFloors: Array<Bitmap?> = arrayOfNulls(33)
    private val sTags: Array<Bitmap?> = arrayOfNulls(16)

    private fun tagBmp(n: Int): Bitmap {
        val k = n.coerceIn(1, 15)
        if (sTags[k] == null) sTags[k] = bmp("tag$k")
        return sTags[k]!!
    }

    private fun bmp(name: String): Bitmap {
        val id = resources.getIdentifier(name, "drawable", context.packageName)
        return BitmapFactory.decodeResource(resources, id)
    }

    private fun spellBmp(e: Int, d: Int, f: Int): Bitmap {
        if (sSpell[e][d][f] == null) {
            val en = arrayOf("a", "e", "t", "f")[e]
            val dn = arrayOf("d", "u", "l", "r")[d]
            sSpell[e][d][f] = bmp("sp$en$dn${f + 1}")
        }
        return sSpell[e][d][f]!!
    }

    private fun prop(n: Int): Bitmap {
        if (sProps[n] == null) sProps[n] = bmp("prop$n")
        return sProps[n]!!
    }

    private fun decorBmp(type: Int, n: Int): Bitmap = when (type) {
        0 -> { if (sTrees[n] == null) sTrees[n] = bmp("tree$n"); sTrees[n]!! }
        1 -> { if (sPlants[n] == null) sPlants[n] = bmp("plant$n"); sPlants[n]!! }
        2 -> { if (sRocks[n] == null) sRocks[n] = bmp("rock$n"); sRocks[n]!! }
        else -> { if (sBoats[n] == null) sBoats[n] = bmp("boat$n"); sBoats[n]!! }
    }

    private fun hfloor(n: Int): Bitmap {
        if (sHFloors[n] == null) sHFloors[n] = bmp("hfloor$n")
        return sHFloors[n]!!
    }

    private val sVendorGold: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.vendor_gold)
    private val sVendorBlue: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.vendor_blue)
    private val sSpray: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.spray)
    private val sShroom: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.shroom)
    private val sHouseNew: Array<Bitmap> = arrayOf(
        BitmapFactory.decodeResource(resources, R.drawable.house_cottage),
        BitmapFactory.decodeResource(resources, R.drawable.house_forge),
        BitmapFactory.decodeResource(resources, R.drawable.house_anarchist),
        BitmapFactory.decodeResource(resources, R.drawable.house_alchemist),
        BitmapFactory.decodeResource(resources, R.drawable.house_club),
        BitmapFactory.decodeResource(resources, R.drawable.house_guild),
        BitmapFactory.decodeResource(resources, R.drawable.house_tree),
        BitmapFactory.decodeResource(resources, R.drawable.house_tavern)
    )
    @Suppress("unused")
    private val sHouses: Array<Bitmap> = arrayOf(
        BitmapFactory.decodeResource(resources, R.drawable.house1),
        BitmapFactory.decodeResource(resources, R.drawable.house2),
        BitmapFactory.decodeResource(resources, R.drawable.house3),
        BitmapFactory.decodeResource(resources, R.drawable.house4),
        BitmapFactory.decodeResource(resources, R.drawable.house5),
        BitmapFactory.decodeResource(resources, R.drawable.house6),
        BitmapFactory.decodeResource(resources, R.drawable.house7),
        BitmapFactory.decodeResource(resources, R.drawable.house8),
        BitmapFactory.decodeResource(resources, R.drawable.house9),
        BitmapFactory.decodeResource(resources, R.drawable.house10)
    )
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
    private class Mob(
        var x: Float, var y: Float, var hp: Int, val sprite: Int,
        val maxHp: Int = 100, val scale: Float = 1f, val boss: Boolean = false
    ) {
        var hitT = 0f
        var stepT = 0f
        var spawnT = 0f
    }

    private val mobs = ArrayList<Mob>()

    /** Un villageois ou un animal qui deambule. */
    private class Walker(var x: Float, var y: Float, val kind: Int, val id: Int) {
        var dir = 0
        var tx = x
        var ty = y
        var wait = 0f
        var talk = 0f
    }

    private val walkers = ArrayList<Walker>()
    private var villagers: List<VillagerAI.Perso> = emptyList()
    private val npcRnd = Random(1234)
    private var dialogue = ""
    private var dialogueName = ""
    private var dialogueT = 0f
    private var dialogueX = 0f
    private var dialogueY = 0f

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
    private val btnReset = RectF()
    private val btnAct = RectF()
    private var actKind = 0        // 0 rien, 1 entrer, 2 sortir, 3 parler, 4 distributeur
    private var actData = 0
    private var actLabel = ""
    private val mMap = RectF()
    private val mSet = RectF()
    private var showMap = false

    private val tName = RectF()
    private val tDiff = arrayOf(RectF(), RectF(), RectF())
    private val tGod = RectF()
    private val tVillage = RectF()
    private val tNew = RectF()
    private val tCont = RectF()
    private val tHelp = RectF()
    private val tSet = RectF()

    private val mResume = RectF()
    private val mInv = RectF()
    private val mSave = RectF()
    private val mReset = RectF()
    private val mHelp = RectF()
    private val mRestart = RectF()
    private val mQuit = RectF()
    private val invJoyRect = RectF()
    private val invEnergyRect = RectF()
    private val invFishRect = RectF()
    private val linePath = Path()
    private val invShroomRect = RectF()

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
        crashLog = prefs.getString("lastcrash", null)
        playerName = prefs.getString("name", "Heros") ?: "Heros"
        difficulty = prefs.getInt("diff", 1)
        godMode = prefs.getBoolean("god", false)
        startAtVillage = prefs.getBoolean("village", false)
        loadAudioPrefs()
    }

    private fun loadAudioPrefs() {
        for (z in Audio.ZONES.indices) {
            // defaut = la piste prevue pour la zone (ex. la chanson punk au club)
            audio.zoneTrack[z] = prefs.getInt("z$z", audio.zoneTrack[z])
        }
        // Migration : l'ancien defaut de la zone concert etait une piste village
        if (Audio.ZONES.size > 10 && audio.zoneTrack[10] == 10 && !prefs.getBoolean("zfix", false)) {
            audio.zoneTrack[10] = 14
            prefs.edit().putBoolean("zfix", true).apply()
        }
        audio.musicOn = prefs.getBoolean("mus", true)
        audio.sfxOn = prefs.getBoolean("sfx", true)
        audio.setVolume(prefs.getFloat("vol", 0.55f))
    }

    private fun saveAudioPrefs() {
        val e = prefs.edit()
        for (z in Audio.ZONES.indices) e.putInt("z$z", audio.zoneTrack[z])
        e.putBoolean("mus", audio.musicOn)
        e.putBoolean("sfx", audio.sfxOn)
        e.putFloat("vol", audio.musicVol)
        e.apply()
        audio.refresh()
    }

    // Cycle de vie (appele par MainActivity)
    fun onPauseAudio() = audio.pause()
    fun onResumeAudio() = audio.resume()
    fun onDestroyAudio() = audio.release()

    /** La zone musicale courante. */
    private fun currentZone(): Int {
        if (state == TITLE) return 0
        if (world.isInterior(hx, hy)) return if (world.interiorOf(hx, hy) == 5) 10 else 9
        if (world.isIsland(hx, hy)) return if (world.inVillage(hx, hy)) 9 else 8
        if (mobs.any { it.hp > 0 }) return 7
        return when {
            hy >= world.uy0 -> when {
                hx < 12 -> 4
                hx <= 20 -> 5
                hx <= 34 -> 6
                else -> 7
            }
            hx <= world.hallW -> 1
            hx < world.hallW + 12 -> 2
            else -> 3
        }
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
        sprayOwned = false; sprayNext = 1; vendorsUsed.clear()
        shroomCount = 0; spraysDone = 0; tripT = 0f
        metPierre = false; rodOwned = false; fishCasts = 0; slipOwned = false; ticketOwned = false
        fishing = false; fishBiteT = 0f; fishCount = 0
        onBoat = false; boatCell = -1
        vmemRestore = false
        try { prefs.edit().remove("vmem").apply() } catch (t: Throwable) { }
        spellKnown.fill(false); spellDemo = -1; spellDemoT = 0f
        spellSel = -1; spellCd = 0f; spellPicker = false; bolts.clear()
        gold = 0; goldAnim = 0f; secretFound.clear(); secretLooted.clear()
        showShop = false; shopMerchant = 0; shopDiscount = 0; shopMood = 0
        dlgChoices = emptyList(); dlgKind = 0
        vQuest.fill(0); fishTotal = 0; bootCount = 0; algaeCount = 0
        petCount = 0; drinksDone = 0; clubVisited = false; swordSharp = false
        mobs.clear()
        miniPlate = -1
        simonState = 0; simonInput = 0
        gameOver = false; victory = false; flagMode = false
        showHelp = false; showMenu = false; showInv = false
        boomFlash = 0f; keyAnim = 0f
        heroDir = 0
        initWalkers()
        state = PLAYING
        if (startAtVillage) {
            // Raccourci de test : on demarre directement a la surface, joystick fourni
            world.teleportActive = true
            world.islandVisited = true
            joyOwned = true
            joyOn = true
            val px = world.cx(world.islandPortal)
            val py = world.cy(world.islandPortal)
            hx = px; hy = py + 1
            fx = hx + 0.5f; fy = hy + 0.5f
            camX = fx; camY = fy
            teleCd = 1.5f
            showMsg("Mode test : joystick actif, le portail ramene au donjon.")
        } else {
            showMsg("Traversez le champ de mines jusqu'au passage de droite !")
        }
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
        e.putBoolean("sud", world.sudokuSolved)
        e.putString("sudU", world.sudokuUser.joinToString(","))
        e.putBoolean("lgt", world.lightsSolved)
        e.putString("lights", world.lights.joinToString(",") { if (it) "1" else "0" })
        e.putInt("wave", world.wave)
        e.putBoolean("boss", world.bossDefeated)
        e.putBoolean("ile", world.islandVisited)
        e.putBoolean("spray", sprayOwned)
        e.putBoolean("sprayT", world.sprayTaken)
        e.putInt("sprayN", sprayNext)
        e.putString("vused", setToStr(vendorsUsed))
        e.putString("wtags", world.tags.entries.joinToString(";") { "${it.key}:${it.value}" })
        e.putInt("shrooms", shroomCount)
        e.putInt("sprayed", spraysDone)
        e.putBoolean("metP", metPierre)
        e.putBoolean("rod", rodOwned)
        e.putInt("casts", fishCasts)
        e.putInt("fish", fishCount)
        e.putString("vq", vQuest.joinToString(""))
        e.putInt("fishT", fishTotal)
        e.putInt("boots", bootCount)
        e.putInt("algae", algaeCount)
        e.putInt("pets", petCount)
        e.putInt("drinks", drinksDone)
        e.putBoolean("club", clubVisited)
        e.putBoolean("boat", onBoat)
        e.putInt("boatC", boatCell)
        e.putString("spells", spellKnown.joinToString("") { if (it) "1" else "0" })
        e.putInt("gold", gold)
        e.putInt("secR", secretReturnCell)
        e.putString("secF", secretFound.joinToString(","))
        e.putString("secL", secretLooted.joinToString(","))
        if (villagers.isNotEmpty()) {
            e.putString("vmem", try { VillagerAI.serialiser(villagers) } catch (t: Throwable) { "" })
        }
        e.putBoolean("sharp", swordSharp)
        e.putBoolean("slip", slipOwned)
        e.putBoolean("ticket", ticketOwned)
        e.putBoolean("shroomT", world.shroomTaken)
        e.putBoolean("d2r", world.dungeon2Revealed)
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
        try {
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
        world.sudokuSolved = prefs.getBoolean("sud", false)
        val su = prefs.getString("sudU", "") ?: ""
        if (su.isNotBlank()) {
            val parts = su.split(",")
            if (parts.size == 16) for (k in 0 until 16) world.sudokuUser[k] = parts[k].trim().toIntOrNull() ?: 0
        }
        val lg = prefs.getString("lights", "") ?: ""
        if (lg.isNotBlank()) {
            val parts = lg.split(",")
            if (parts.size == 9) for (k in 0 until 9) world.lights[k] = parts[k].trim() == "1"
        }
        if (prefs.getBoolean("lgt", false)) world.openRuneDoor()
        world.wave = prefs.getInt("wave", 0)
        if (prefs.getBoolean("boss", false)) world.bossVictory()
        world.islandVisited = prefs.getBoolean("ile", false)
        sprayOwned = prefs.getBoolean("spray", false)
        world.sprayTaken = prefs.getBoolean("sprayT", false)
        sprayNext = prefs.getInt("sprayN", 1)
        vendorsUsed.clear(); vendorsUsed.addAll(strToSet(prefs.getString("vused", "")))
        shroomCount = prefs.getInt("shrooms", 0)
        spraysDone = prefs.getInt("sprayed", 0)
        metPierre = prefs.getBoolean("metP", false)
        rodOwned = prefs.getBoolean("rod", false)
        fishCasts = prefs.getInt("casts", 0)
        fishCount = prefs.getInt("fish", 0)
        val vq = prefs.getString("vq", "") ?: ""
        for (k in vQuest.indices) vQuest[k] = if (k < vq.length) (vq[k] - '0').coerceIn(0, 2) else 0
        fishTotal = prefs.getInt("fishT", 0)
        bootCount = prefs.getInt("boots", 0)
        algaeCount = prefs.getInt("algae", 0)
        petCount = prefs.getInt("pets", 0)
        drinksDone = prefs.getInt("drinks", 0)
        clubVisited = prefs.getBoolean("club", false)
        onBoat = prefs.getBoolean("boat", false)
        boatCell = prefs.getInt("boatC", -1)
        val sp = prefs.getString("spells", "") ?: ""
        for (k in spellKnown.indices) spellKnown[k] = k < sp.length && sp[k] == '1'
        gold = prefs.getInt("gold", 0)
        secretReturnCell = prefs.getInt("secR", -1)
        (prefs.getString("secF", "") ?: "").split(",").forEach { it.toIntOrNull()?.let { c -> secretFound.add(c) } }
        (prefs.getString("secL", "") ?: "").split(",").forEach { it.toIntOrNull()?.let { c -> secretLooted.add(c) } }
        swordSharp = prefs.getBoolean("sharp", false)
        slipOwned = prefs.getBoolean("slip", false)
        ticketOwned = prefs.getBoolean("ticket", false)
        world.shroomTaken = prefs.getBoolean("shroomT", false)
        world.dungeon2Revealed = prefs.getBoolean("d2r", false)
        val wt = prefs.getString("wtags", "") ?: ""
        if (wt.isNotBlank()) {
            for (part in wt.split(";")) {
                val f = part.split(":")
                if (f.size == 2) {
                    val c = f[0].trim().toIntOrNull()
                    val v = f[1].trim().toIntOrNull()
                    if (c != null && v != null) world.tags[c] = v
                }
            }
        }
        if (prefs.getBoolean("d3o", false)) world.openDoor3()
        mobs.clear()
        val ms = prefs.getString("mobs", "") ?: ""
        if (ms.isNotBlank()) {
            for (part in ms.split(";")) {
                val f = part.split(",")
                if (f.size == 4) {
                    val sp = f[3].toInt().coerceIn(0, sMonsters.size - 1)
                    mobs.add(Mob(f[0].toFloat(), f[1].toFloat(), f[2].toInt(), sp))
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
        initWalkers()
        state = PLAYING
        showMsg("Partie chargee. Bon retour, $playerName !")
        return true
        } catch (@Suppress("UNUSED_PARAMETER") e: Throwable) {
            prefs.edit().putBoolean("has", false).apply()
            state = TITLE
            showMsg("Sauvegarde d'une ancienne version : faites NOUVELLE PARTIE.")
            return false
        }
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
        btnSpell.set(x - bh, y0, x, y0 + bh); x -= bh + gap
        btnReset.set(x - bh, y0, x, y0 + bh); x -= bh + gap
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
        tGod.set(cx0, hf * 0.515f, cx0 + cw, hf * 0.515f + rh)
        tVillage.set(cx0, hf * 0.585f, cx0 + cw, hf * 0.585f + rh)
        tNew.set(cx0, hf * 0.665f, cx0 + cw, hf * 0.665f + rh * 1.1f)
        tCont.set(cx0, hf * 0.745f, cx0 + cw, hf * 0.745f + rh * 1.1f)
        tHelp.set(cx0, hf * 0.825f, cx0 + cw, hf * 0.825f + rh * 1.1f)
        val sq = hf * 0.05f
        tSet.set(wf - sq * 1.5f, hf * 0.035f, wf - sq * 0.4f, hf * 0.035f + sq * 1.1f)

        val mw = wf * 0.74f
        val mx = (wf - mw) / 2f
        var my = hf * 0.205f
        val mh = hf * 0.057f
        val mg = hf * 0.011f
        mResume.set(mx, my, mx + mw, my + mh); my += mh + mg
        mInv.set(mx, my, mx + mw, my + mh); my += mh + mg
        mMap.set(mx, my, mx + mw, my + mh); my += mh + mg
        mSet.set(mx, my, mx + mw, my + mh); my += mh + mg
        mSave.set(mx, my, mx + mw, my + mh); my += mh + mg
        mReset.set(mx, my, mx + mw, my + mh); my += mh + mg
        mHelp.set(mx, my, mx + mw, my + mh); my += mh + mg
        mRestart.set(mx, my, mx + mw, my + mh); my += mh + mg
        mQuit.set(mx, my, mx + mw, my + mh)
    }

    // ============================================================ LOGIQUE

    // ---------------------------------------------------------- vrai mode peche

    /** Lance la ligne : le flotteur tombe sur la case visee, on attend la touche. */
    private fun startFishing(gx: Int, gy: Int) {
        fishing = true
        fishHx = hx; fishHy = hy
        val r = Random(System.nanoTime())
        fishBobX = gx + 0.3f + r.nextFloat() * 0.4f
        fishBobY = gy + 0.3f + r.nextFloat() * 0.4f
        fishWaitT = 1.6f + r.nextFloat() * 3.4f
        fishBiteT = 0f
        fishSplashT = 0.6f
        fishCasts++
        audio.play("splash")
        showMsg("Plouf ! La ligne est a l'eau... touchez l'ecran des que CA MORD !")
    }

    /** Remonte la ligne, avec ou sans commentaire. */
    private fun stopFishing(msg: String?) {
        fishing = false
        fishBiteT = 0f
        if (msg != null) showMsg(msg)
    }

    /** Ferrage reussi : qu'y a-t-il au bout de l'hamecon ? */
    private fun catchFish() {
        fishing = false
        fishBiteT = 0f
        val bGx = fishBobX.toInt()
        val bGy = fishBobY.toInt()
        // Le slip de Pierre flotte sur slipCell : ferrer a moins d'une case = prise garantie
        val sc = world.slipCell
        if (sc >= 0 && metPierre && !slipOwned && !ticketOwned &&
            abs(bGx - world.cx(sc)) <= 1 && abs(bGy - world.cy(sc)) <= 1
        ) {
            slipOwned = true
            audio.play("win")
            showMsg("ENORME PRISE !!! ... LE SLIP A PIERRE ! Rapportez-le-lui !")
            noteRumeur("slip", "le heros a repeche le fameux slip porte-bonheur de Pierre !", true)
            saveGame()
            return
        }
        val r = Random(System.nanoTime())
        val roll = r.nextFloat()
        when {
            roll < 0.62f -> {
                fishCount++
                fishTotal++
                audio.play("pickup")
                showMsg(
                    listOf(
                        "Un poisson frais ! (+1, inventaire pour le manger)",
                        "Une belle perche ! (+1 poisson dans la besace)",
                        "Un poisson bien dodu ! (+1, ca se mange !)"
                    )[r.nextInt(3)]
                )
            }
            roll < 0.78f -> {
                bootCount++
                audio.play("error")
                showMsg("Une vieille botte... (+1 botte - quelqu'un les collectionne, parait-il)")
            }
            roll < 0.90f -> {
                algaeCount++
                audio.play("error")
                showMsg("Des algues puantes. Beurk. (+1 paquet - ca interesse peut-etre quelqu'un ?)")
            }
            else -> {
                fishCount += 2
                fishTotal += 2
                audio.play("chest")
                showMsg("DOUBLE PRISE ! Deux poissons d'un coup ! (+2)")
            }
        }
        saveGame()
    }

    private fun update(dt: Float) {
        time += dt
        audio.setZone(currentZone())
        msgTimer -= dt
        boomFlash = (boomFlash - dt * 1.6f).coerceAtLeast(0f)
        damageT = (damageT - dt).coerceAtLeast(0f)
        keyAnim = (keyAnim - dt).coerceAtLeast(0f)
        if (dlgChoices.isEmpty()) dialogueT = (dialogueT - dt).coerceAtLeast(0f)
        else dialogueT = dialogueT.coerceAtLeast(0.5f)
        simonFlashT = (simonFlashT - dt).coerceAtLeast(0f)
        attackCd = (attackCd - dt).coerceAtLeast(0f)
        teleCd = (teleCd - dt).coerceAtLeast(0f)
        tripT = (tripT - dt).coerceAtLeast(0f)
        sudokuShake = (sudokuShake - dt).coerceAtLeast(0f)
        attackAnim = (attackAnim - dt * 3f).coerceAtLeast(0f)
        if (state != PLAYING) return

        if (spellDemoT > 0f) {
            spellDemoT -= dt
            if (spellDemoT <= 0f) spellDemo = -1
        }
        spellCd = (spellCd - dt).coerceAtLeast(0f)
        goldAnim = (goldAnim - dt).coerceAtLeast(0f)
        updateBolts(dt)
        // La peche : attente, touche ("CA MORD"), fenetre de ferrage
        if (fishing) {
            fishSplashT = (fishSplashT - dt).coerceAtLeast(0f)
            if (hx != fishHx || hy != fishHy) {
                stopFishing("Vous remontez la ligne en marchant.")
            } else if (fishBiteT > 0f) {
                fishBiteT -= dt
                if (fishBiteT <= 0f) {
                    fishBiteT = 0f
                    fishWaitT = 1.4f + npcRnd.nextFloat() * 3f
                    showMsg("Trop lent, ca ne mord plus... la ligne reste a l'eau.")
                }
            } else {
                fishWaitT -= dt
                if (fishWaitT <= 0f) {
                    fishBiteT = 0.85f
                    audio.play("bite")
                }
            }
        }

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
                    audio.play("simon${simonFlash}")
                    simonTimer = 0.68f
                }
            }
        }

        if (gameOver || victory || showMenu || showInv || showHelp || showMap || showSettings ||
            showSudoku || showLights || miniPlate >= 0 || showShop
        ) { if (showShop) shopMsgT = (shopMsgT - dt).coerceAtLeast(0f); return }

        updateMobs(dt)
        updateWalkers(dt)
        rumeurT += dt
        if (rumeurT > 8f) {
            rumeurT = 0f
            if (villagers.isNotEmpty()) try { VillagerAI.propagerRumeurs(villagers.size) } catch (t: Throwable) { }
        }
        // Les bavards marmonnent parfois tout seuls
        if (dialogueT <= 0f && time % 7f < dt && villagers.isNotEmpty()) {
            val near = walkers.filter { it.kind == 0 && hypot(it.x - fx, it.y - fy) < 6f && it.talk <= 0f }
            if (near.isNotEmpty()) {
                val w2 = near[npcRnd.nextInt(near.size)]
                val p2 = villagers[(w2.id - 1) % villagers.size]
                val line = try { VillagerAI.marmonner(p2, npcRnd) } catch (t: Throwable) { null }
                if (line != null) {
                    dialogue = line
                    dialogueName = p2.nom
                    dialogueX = w2.x
                    dialogueY = w2.y
                    val dur = (2f + line.length * 0.04f).coerceAtMost(4.5f)
                    dialogueT = dur
                    w2.talk = dur
                }
            }
        }

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
                } else if (if (onBoat) world.isNavigable(nx, ny) else world.isWalkable(nx, ny)) {
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
                showMsg("Porte scellee. Elle ne s'ouvrira que de l'autre cote...")
            } else if (pendingMini >= 0) {
                val p = pendingMini; pendingMini = -1
                openMini(p)
            } else if (pendingTorch >= 0) {
                val t = pendingTorch; pendingTorch = -1
                lightTorch(t)
            } else if (pendingVendor >= 0) {
                val v = pendingVendor; pendingVendor = -1
                useVendor(v)
            } else if (pendingRune) {
                pendingRune = false
                if (world.lightsSolved) showMsg("La porte est deja ouverte.")
                else { showLights = true; showMsg("Neuf runes... faites-les toutes briller !") }
            } else if (pendingDoor3) {
                pendingDoor3 = false
                if (!world.mobsDead) showMsg("Les 2 monstres gardent la porte : battez-vous !")
                else { world.openDoor3(); saveGame(); showMsg("La porte s'ouvre : l'etoile est la !") }
            }
        }
    }

    // ------------------------------------------------------------ combat

    /** Vague n de la salle du boss. */
    private fun spawnWave(n: Int) {
        mobs.clear()
        val sp = world.bossSpawns
        when (n) {
            1 -> {
                mobs.add(Mob(cxf(sp[0]), cyf(sp[0]), 100, 0, 100))
                mobs.add(Mob(cxf(sp[1]), cyf(sp[1]), 100, 3, 100))
                showMsg("VAGUE 1 / 3 : deux creatures surgissent !")
            }
            2 -> {
                mobs.add(Mob(cxf(sp[0]), cyf(sp[0]), 130, 1, 130))
                mobs.add(Mob(cxf(sp[1]), cyf(sp[1]), 130, 4, 130))
                mobs.add(Mob(cxf(sp[2]), cyf(sp[2]), 130, 6, 130))
                showMsg("VAGUE 2 / 3 : trois nouvelles creatures !")
            }
            else -> {
                mobs.add(Mob(cxf(sp[2]), cyf(sp[2]), 480, 7, 480, 2.1f, true))
                mobs.add(Mob(cxf(sp[3]), cyf(sp[3]), 110, 2, 110))
                mobs.add(Mob(cxf(sp[4]), cyf(sp[4]), 110, 5, 110))
                showMsg("VAGUE 3 / 3 : LE BOSS !")
            }
        }
        for (m in mobs) m.spawnT = 1f
        audio.play("hit")
    }

    private fun cxf(i: Int) = world.cx(i) + 0.5f
    private fun cyf(i: Int) = world.cy(i) + 0.5f

    private fun spawnMobs() {
        mobs.clear()
        val r = Random(world.seed + 77)
        for (k in 0..1) {
            val c = world.mobSpawn[k]
            val m = Mob(world.cx(c) + 0.5f, world.cy(c) + 0.5f, 100, r.nextInt(sMonsters.size))
            m.spawnT = 1f
            mobs.add(m)
        }
        audio.play("hit")
    }

    private fun updateMobs(dt: Float) {
        if (mobs.isEmpty()) return
        var alive = 0
        for (m in mobs) {
            m.hitT = (m.hitT - dt).coerceAtLeast(0f)
            m.spawnT = (m.spawnT - dt * 1.2f).coerceAtLeast(0f)
            if (m.hp <= 0) continue
            alive++
            if (m.spawnT > 0f) continue
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
            val reach = if (m.boss) 1.25f else 0.85f
            if (d < reach) {
                if (!godMode) {
                    hp -= ((if (m.boss) 26f else 16f) * dt).toInt().coerceAtLeast(0)
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
        if (alive == 0) {
            if (world.wave in 1..3 && !world.bossDefeated) {
                if (world.wave < 3) {
                    world.wave++
                    spawnWave(world.wave)
                    saveGame()
                } else {
                    world.bossVictory()
                    audio.play("win")
                    showMsg("LE BOSS EST VAINCU ! La porte scellee s'ouvre au nord...")
                    noteRumeur("boss", "le heros a terrasse le monstre du donjon a mains nues !", true)
                    saveGame()
                }
            } else if (world.mobsSpawned && !world.mobsDead) {
                world.mobsDead = true
                showMsg("Les deux monstres sont vaincus ! La porte se deverrouille.")
                saveGame()
            }
        }
    }

    /** A-t-on au moins un sort appris ? */
    private fun anySpell() = spellKnown.any { it }

    /** Touche du bouton SORT : ouvre le selecteur, ou relance le dernier element. */
    private fun onSpellButton() {
        if (!anySpell()) { showMsg("Le mage de l'arbre ne t'a encore rien appris !"); return }
        val known = (0..3).filter { spellKnown[it] }
        if (known.size == 1) { castSpell(known[0]); return }
        if (spellSel in known && spellCd <= 0f) { castSpell(spellSel); return }
        spellPicker = !spellPicker
    }

    /** Lance un sort de l'element e dans la direction du regard. */
    private fun castSpell(e: Int) {
        if (!spellKnown[e]) return
        spellPicker = false
        if (spellCd > 0f) return
        spellSel = e
        spellCd = when (e) { 0 -> 0.5f; 1 -> 0.65f; 2 -> 0.9f; else -> 1.1f }
        val d = heroDir.coerceIn(0, 3)
        val (dx, dy) = when (d) { 0 -> 0f to 1f; 1 -> 0f to -1f; 2 -> -1f to 0f; else -> 1f to 0f }
        val speed = when (e) { 0 -> 11f; 1 -> 9f; 2 -> 7f; else -> 8f }
        val range = when (e) { 0 -> 6.5f; 1 -> 6f; 2 -> 4.5f; else -> 7f }
        bolts.add(Bolt(fx + dx * 0.5f, fy + dy * 0.5f, dx * speed, dy * speed, e, d, range / speed))
        audio.play(if (e == 3) "win" else "flag")
        spellDemo = e; spellDemoT = 0.45f   // petite volute a la main du heros
    }

    /** Fait avancer les projectiles, gere l'impact elementaire sur les mobs. */
    private fun updateBolts(dt: Float) {
        if (bolts.isEmpty()) return
        val it = bolts.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.t += dt
            if (b.hit) { if (b.t > 0.28f) it.remove(); continue }
            val nx = b.x + b.dx * dt
            val ny = b.y + b.dy * dt
            // Mur : le sort s'ecrase (le feu laisse une marque de brulure via hitT visuel)
            if (!world.isFloor(nx.toInt(), ny.toInt())) {
                b.hit = true; b.t = 0f; b.life = 0f
                if (b.elem == 3) audio.play("error")
                continue
            }
            b.x = nx; b.y = ny
            b.life -= dt
            // Touche un monstre ?
            var touched = false
            for (m in mobs) {
                if (m.hp <= 0) continue
                val rad = if (m.boss) 1.5f else 1.1f
                if (hypot(m.x - b.x, m.y - b.y) <= rad) {
                    val base = ELEM_DMG[b.elem]
                    // Bonus elementaire : le feu ravage, la terre etourdit, l'eau ralentit
                    m.hp -= base
                    m.hitT = 0.3f
                    when (b.elem) {
                        2 -> m.stepT = 0.8f                       // terre : etourdi
                        1 -> m.stepT = m.stepT.coerceAtLeast(0.5f) // eau : ralenti
                    }
                    audio.play("hit")
                    if (m.hp <= 0) showMsg("Monstre pulverise par ${ELEM_NAMES[b.elem]} !")
                    touched = true
                    if (b.elem != 3) break   // le feu traverse (aoe), les autres s'arretent
                }
            }
            if (touched && b.elem != 3) { b.hit = true; b.t = 0f }
            else if (b.life <= 0f) { b.hit = true; b.t = 0f }
        }
    }

    private fun doAttack() {
        if (!swordOwned) { showMsg("Vous n'avez pas d'epee."); return }
        if (attackCd > 0f) return
        attackCd = 0.45f
        attackAnim = 1f
        audio.play("sword")
        var hit = false
        for (m in mobs) {
            if (m.hp <= 0) continue
            if (hypot(m.x - fx, m.y - fy) <= (if (m.boss) 1.9f else 1.45f)) {
                m.hp -= if (swordSharp) 55 else 40
                m.hitT = 0.28f
                hit = true
                audio.play("hit")
                if (m.hp <= 0) showMsg("Monstre terrasse !")
            }
        }
        if (!hit) showMsg("Votre epee fend l'air...")
    }

    /** Arrivee sur une nouvelle case : coeurs, trappe, dalles colorees. */
    private fun onArrive() {
        val i = world.idx(hx, hy)

        // L'entree secrete du prochain donjon
        if (i == world.dungeon2Cell && world.dungeon2Revealed) {
            showMsg("L'entree du prochain donjon... scellee pour l'instant. (A SUIVRE !)")
        }
        // Les champignons de Kaos (seulement pour les vrais taggeurs)
        if (i == world.shroomCell && !world.shroomTaken && spraysDone >= 3) {
            world.shroomTaken = true
            shroomCount += 3
            audio.play("pickup")
            showMsg("Des champignons psychedeliques ! (+3, inventaire pour gouter)")
            saveGame()
        }
        // La bombe Rebel Ink, dans le squat
        if (i == world.sprayCell && !world.sprayTaken) {
            world.sprayTaken = true
            sprayOwned = true
            audio.play("pickup")
            showMsg("Une bombe REBEL INK ! Touchez un mur proche pour taguer.")
            saveGame()
        }
        // Le briquet, au centre de la salle des torches
        if (i == world.lighter && !world.lighterTaken) {
            world.lighterTaken = true
            lighterOwned = true
            audio.play("pickup")
            showMsg("Vous ramassez un BRIQUET ! Allumez les 4 torches de la salle.")
            saveGame()
        }
        // Coeur ramasse
        if (i in world.hearts && i in world.revealed) {
            world.hearts.remove(i)
            heartsGot++
            audio.play("heart")
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
        // Une CACHE SECRETE ? Marcher sur la dalle suspecte revele la trappe
        if (world.secretTraps.containsKey(i)) {
            if (i !in secretFound) {
                secretFound.add(i)
                audio.play("push")
                showMsg("Cette dalle sonne creux... une TRAPPE CACHEE s'ouvre sous vos pieds !")
                saveGame()
            }
            // descente immediate vers l'antichambre
            val entry = world.secretTraps[i]!!
            enterSecretCache(entry)
            return
        }
        // Entree dans la salle du boss : la premiere vague se declenche
        if (world.inBossRoom(hx, hy) && world.wave == 0 && !world.bossDefeated) {
            world.wave = 1
            spawnWave(1)
            saveGame()
        }
        // Paillasson : on entre dans la maison
        val mat = world.houseMats[i]
        if (mat != null && world.isIsland(hx, hy) && teleCd <= 0f) {
            tryEnterHouse(mat)
            return
        }
        // Porte de sortie d'une maison
        val ex = world.houseExit[i]
        if (ex != null && teleCd <= 0f) {
            leaveHouse(ex)
            return
        }
        // Le portail du donjon -> l'ILE
        if (world.isTeleport(hx, hy) && teleCd <= 0f) {
            teleportTo(world.cx(world.islandPortal), world.cy(world.islandPortal))
            if (!world.islandVisited) {
                world.islandVisited = true
                showMsg("Vous emergez a la surface : une grande ILE vous accueille !")
            } else showMsg("Retour a la surface.")
            saveGame()
            return
        }
        // Le portail de l'ile -> retour au donjon
        if (world.isIslandPortal(hx, hy) && teleCd <= 0f) {
            teleportTo(world.exitX, world.exitY)
            showMsg("Vous replongez dans le donjon.")
            saveGame()
            return
        }
        // Saisie de l'enigme des couleurs (en arrivant sur une dalle)
        if (simonState == 2) {
            for (k in 0..3) if (i == world.simonTiles[k]) simonPress(k)
        }
    }

    /** Valide une couleur. Marche aussi si le heros est DEJA sur la dalle (couleur repetee). */
    private fun simonPress(k: Int) {
        if (simonState != 2 || world.simonSolved) return
        simonFlash = k
        simonFlashT = 0.32f
        audio.play("simon$k")
        if (k == world.simonSeq[simonInput]) {
            simonInput++
            if (simonInput >= world.simonSeq.size) {
                simonState = 0
                world.spawnAfterSimon()
                audio.play("win")
                showMsg("L'enigme est resolue ! Un coffre-fort et une porte apparaissent !")
                saveGame()
            } else {
                showMsg("Bonne couleur ! ($simonInput/${world.simonSeq.size})")
            }
        } else {
            simonState = 0
            simonInput = 0
            audio.play("error")
            hurt(5, "Mauvaise couleur ! Retouchez le socle pour reecouter.")
        }
    }

    /** Le heros est-il dans une salle a caisses ? */
    private fun inSokobanRoom(): Boolean {
        if (world.inTorchRoom(hx, hy)) return world.sokoban2Spawned
        return hy <= 11 && hx > world.hallW
    }

    private fun resetCurrentSokoban() {
        if (world.inTorchRoom(hx, hy)) {
            world.resetSokoban2()
            showMsg("Les 7 caisses sont revenues a leur place de depart.")
        } else {
            world.resetSokoban1()
            showMsg("Les blocs sont revenus a leur place de depart.")
        }
        clearPendings()
        path = emptyList(); pathStep = 0
        audio.play("push")
        saveGame()
    }

    private var teleCd = 0f

    private fun initWalkers() {
        if (boatCell < 0) boatCell = world.boatCell
        walkers.clear()
        villagers = try {
            VillagerAI.creerVillageois(world.seed)
        } catch (t: Throwable) {
            emptyList()
        }
        if (vmemRestore && villagers.isNotEmpty()) {
            try { VillagerAI.restaurer(villagers, prefs.getString("vmem", null)) } catch (t: Throwable) { }
        }
        for ((cell, id) in world.npcSpawns) {
            walkers.add(Walker(world.cx(cell) + 0.5f, world.cy(cell) + 0.5f, 0, id))
        }
        for ((cell, id) in world.petSpawns) {
            walkers.add(Walker(world.cx(cell) + 0.5f, world.cy(cell) + 0.5f, 1, id))
        }
        for ((cell, id) in world.punkSpawns) {
            walkers.add(Walker(world.cx(cell) + 0.5f, world.cy(cell) + 0.5f, 2, id))
        }
        for ((cell, id) in world.guildSpawns) {
            walkers.add(Walker(world.cx(cell) + 0.5f, world.cy(cell) + 0.5f, 4, id))
        }
        if (world.mageCell >= 0) {
            walkers.add(Walker(world.cx(world.mageCell) + 0.5f, world.cy(world.mageCell) + 0.5f, 5, 1))
        }
        for ((cell, id) in world.tavernCells) {
            walkers.add(Walker(world.cx(cell) + 0.5f, world.cy(cell) + 0.5f, 6, id))
        }
        for ((cell, id) in world.merchantCells) {
            walkers.add(Walker(world.cx(cell) + 0.5f, world.cy(cell) + 0.5f, 7, id))
        }
        if (world.pierreCell >= 0) {
            walkers.add(Walker(world.cx(world.pierreCell) + 0.5f, world.cy(world.pierreCell) + 0.5f, 3, 1))
        }
        if (world.frankiCell >= 0) {
            walkers.add(Walker(world.cx(world.frankiCell) + 0.5f, world.cy(world.frankiCell) + 0.5f, 3, 2))
        }
    }

    private fun updateWalkers(dt: Float) {
        if (walkers.isEmpty()) return
        // Bousculade : le heros traverse un villageois grognon -> il le retient
        for (w in walkers) {
            if (w.kind != 0 || villagers.isEmpty()) continue
            if (hypot(w.x - fx, w.y - fy) < 0.55f && w.talk <= 0f && dialogueT <= 0f) {
                val p = villagers[(w.id - 1) % villagers.size]
                VillagerAI.bousculer(p)
                if (p.grognon > 0.5f) talkTo(w)
            }
        }
        // Le GUIDE : il marche vers sa destination et attend le heros
        if (guideWkIdx >= 0) {
            val g = walkers.getOrNull(guideWkIdx)
            if (g == null || world.isInterior(hx, hy)) {
                guideWkIdx = -1   // visite annulee (guide disparu ou heros rentre)
            } else {
                val gx = g.x.toInt()
                val gy = g.y.toInt()
                val destX = world.cx(guideDest)
                val destY = world.cy(guideDest)
                if (abs(g.x - (destX + 0.5f)) < 0.35f && abs(g.y - (destY + 0.5f)) < 0.35f) {
                    // Arrive ! Il presente sa decouverte
                    dialogue = guideDoneLine
                    dialogueName = persoFor(g)?.nom ?: ""
                    setBubbleAt(g)
                    persoFor(g)?.nom?.let { guidedDone.add(it) }
                    audio.play("chest")
                    guideWkIdx = -1
                } else if (hypot(g.x - fx, g.y - fy) > 4.5f) {
                    g.tx = g.x; g.ty = g.y   // il vous attend
                } else {
                    guideRepathT -= dt
                    val atNode = hypot(g.tx - g.x, g.ty - g.y) < 0.08f
                    if (atNode && guideRepathT <= 0f) {
                        guideRepathT = 0.25f
                        val path2 = world.findPath(gx, gy, destX, destY)
                        val step = path2?.firstOrNull()
                        if (step != null) {
                            g.tx = step.first + 0.5f
                            g.ty = step.second + 0.5f
                            g.dir = if (step.first != gx) (if (step.first > gx) 3 else 2)
                                    else (if (step.second > gy) 0 else 1)
                            g.wait = 0f
                            g.talk = 0f
                        } else if (path2 != null && path2.isEmpty()) {
                            guideWkIdx = -1
                        }
                    }
                }
            }
        }
        // On n'anime que ce qui est proche du heros
        for (w in walkers) {
            w.talk = (w.talk - dt).coerceAtLeast(0f)
            if (abs(w.x - fx) > 16f || abs(w.y - fy) > 16f) continue
            if (w.talk > 0f) continue
            if (walkers.indexOf(w) == guideWkIdx) {
                // le guide avance sans flaner (pas d'errance)
                val dxg = w.tx - w.x
                val dyg = w.ty - w.y
                val spg = 1.6f * dt
                w.x += sign(dxg) * min(abs(dxg), spg)
                w.y += sign(dyg) * min(abs(dyg), spg)
                continue
            }
            val dx = w.tx - w.x
            val dy = w.ty - w.y
            val d = hypot(dx, dy)
            if (d < 0.06f) {
                w.wait -= dt
                if (w.wait <= 0f) {
                    // Nouvelle destination : une case voisine praticable
                    val cx0 = w.x.toInt()
                    val cy0 = w.y.toInt()
                    val dirs = listOf(Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1))
                    val opts = dirs.filter { (ddx, ddy) ->
                        val nx = cx0 + ddx
                        val ny = cy0 + ddy
                        world.isFloor(nx, ny) && !world.houses.containsKey(world.idx(nx, ny)) &&
                                !world.props.containsKey(world.idx(nx, ny)) &&
                                (world.isIsland(nx, ny) || world.isInterior(nx, ny))
                    }
                    if (opts.isNotEmpty()) {
                        val (ddx, ddy) = opts[(time * 13f + w.id * 7f).toInt() % opts.size]
                        w.tx = cx0 + ddx + 0.5f
                        w.ty = cy0 + ddy + 0.5f
                        w.dir = if (ddx != 0) (if (ddx > 0) 3 else 2) else (if (ddy > 0) 0 else 1)
                    }
                    w.wait = 0.7f + (w.id % 5) * 0.35f
                }
            } else {
                val sp = (if (w.kind == 0) 1.1f else 1.5f) * dt
                w.x += sign(dx) * min(abs(dx), sp)
                w.y += sign(dy) * min(abs(dy), sp)
            }
        }
    }

    /**
     * Les quetes farfelues des villageois. Premiere rencontre = proposition ;
     * ensuite rappel ou accomplissement. Renvoie null quand la quete est
     * accomplie : le villageois papote alors normalement (VillagerAI).
     */
    private fun questDialogue(id: Int): String? {
        if (id !in 1..10) return null
        if (vQuest[id] == 2) return null
        if (vQuest[id] == 0) {
            vQuest[id] = 1
            saveGame()
            return when (id) {
                1 -> "Mon auberge ouvrira bientot, mais ma SOUPE manque de corps... Apporte-moi 3 poissons frais, tu seras mon premier client !"
                2 -> "Une epee, ca s'entretient ! Desamorce 5 mines dans le donjon et rapporte-moi la ferraille : je t'AFFUTE la lame, promis."
                3 -> "Kaos refuse de vendre ses CHAMPIGNONS aux 'non-punks'. Toi il t'aime bien... apporte-m'en UN pour mes potions !"
                4 -> "Tu vois mes pots de fleurs ? Des BOTTES ! Peche-m'en 2 vieilles bottes. C'est ma passion secrete, n'en parle a personne."
                5 -> "Mes vieux genoux ne me portent plus... Caresse 3 animaux du village de ma part, veux-tu ? Ils me manquent tant."
                6 -> "T'es CAP de boire une 8.6 d'un coup ?! Moi maman veut pas. FAIS-LE, je te donne ma bille porte-bonheur !!"
                7 -> "Mes poules pondent MOU. Il leur faut des vibrations : va DANSER au Punk Club et reviens sentir le punk a plein nez !"
                8 -> "Halte ! Un heros, TOI ? Prouve-le : terrasse le SEIGNEUR du donjon, et je te ferai le salut militaire. Ca n'arrive jamais."
                9 -> "Je tisse un pull en fibres marines, l'avenir de la mode ! Peche-moi 2 paquets d'ALGUES bien puantes. L'odeur partira. Peut-etre."
                else -> "J'ai reve d'un SLIP qui volait au-dessus de la mer, majestueux, libre... Dis-moi que c'est VRAI, que je ne suis pas fou !"
            }
        }
        // vQuest[id] == 1 : la quete est-elle accomplie ?
        return when (id) {
            1 -> if (fishCount >= 3) {
                fishCount -= 3; vQuest[1] = 2; hp = 100
                audio.play("win"); saveGame()
                "TROIS beaux poissons ! Goute-moi cette soupe... ET VOILA ! Des forces pour cent ans ! (PV au maximum)"
            } else "Il me faut 3 poissons frais (tu en as $fishCount). La mer t'attend, la canne aussi !"
            2 -> if (disarmed >= 5) {
                vQuest[2] = 2; swordSharp = true
                audio.play("sword"); saveGame()
                "De la belle ferraille ! CLING, CLANG, TCHAK... Ton epee est AFFUTEE : elle frappe bien plus fort desormais !"
            } else "Il me faut la ferraille de 5 mines desamorcees ($disarmed/5). Le donjon en regorge !"
            3 -> if (shroomCount >= 1) {
                shroomCount--; vQuest[3] = 2; energyCount += 2
                audio.play("chest"); saveGame()
                "Un champignon de Kaos ! Fascinant... Tiens, 2 elixirs de ma reserve. (Bon, ce sont des canettes. Mais CHUT.)"
            } else "Un seul champignon de Kaos me suffit. Gagne d'abord sa confiance... a la punk."
            4 -> if (bootCount >= 2) {
                bootCount -= 2; vQuest[4] = 2; fishCount += 2
                audio.play("chest"); saveGame()
                "DEUX BOTTES ! Splendides ! Des geraniums dedans, ce sera un poeme. Tiens, 2 poissons de ma reserve, collegue !"
            } else "Encore ${2 - bootCount} vieille(s) botte(s) a remonter. Les poissons, garde-les : moi c'est les BOTTES."
            5 -> if (petCount >= 3) {
                vQuest[5] = 2; hp = (hp + 30).coerceAtMost(100)
                audio.play("heart"); saveGame()
                "Trois betes caressees... je le sens jusque dans mon vieux coeur. Recois la benediction de la doyenne ! (+30 PV)"
            } else "Caresse encore ${(3 - petCount).coerceAtLeast(1)} animal(aux) pour moi. Ils adorent, tu verras."
            6 -> if (drinksDone >= 1) {
                vQuest[6] = 2; hp = (hp + 15).coerceAtMost(100)
                audio.play("simon2"); saveGame()
                "T'AS OSE ?! T'ES TROP FORT !! Tiens, ma bille porte-bonheur. (+15 PV. Ne demande pas pourquoi, elle marche.)"
            } else "Une 8.6 du distributeur, CUL SEC ! Je te regarde. (Buvez-en une depuis l'inventaire.)"
            7 -> if (clubVisited) {
                vQuest[7] = 2; hp = (hp + 25).coerceAtMost(100)
                audio.play("chest"); saveGame()
                "Tu SENS le punk a plein nez, PARFAIT ! Mes poules pondent deja plus dur. Tiens, des oeufs tout frais ! (+25 PV)"
            } else "Le Punk Club, au bout du village ! Danse, transpire, impregne-toi, et reviens me voir."
            8 -> if (world.bossDefeated) {
                vQuest[8] = 2; energyCount++
                audio.play("win"); saveGame()
                "Le SEIGNEUR du donjon, terrasse... GARDE-A-VOUS ! *salut militaire* Recois ma ration de campagne. (+1 canette)"
            } else "Le SEIGNEUR du donjon vit toujours. Reviens quand ce sera regle, recrue."
            else -> if (metPierre || slipOwned || ticketOwned) {
                vQuest[10] = 2; hp = (hp + 15).coerceAtMost(100)
                audio.play("heart"); saveGame()
                "Il EXISTE ?! Un slip libre, flottant sur la mer infinie... Je vais ecrire un poeme-fleuve. MERCI ! (+15 PV d'emotion)"
            } else "Va voir les pecheurs au sud-ouest... si mon reve dit vrai, quelque chose de majestueux flotte la-bas."
        }
    }

    // ------------------------------------------------ la vraie discussion

    /** Ce que le village SAIT (ou saura bientot) des exploits du heros. */
    private var rumeurT = 0f            // horloge de propagation des rumeurs
    private var champiGiven = 0        // combien de champis distribues au concert
    private var lastConcertTrack = -1  // anti-repetition des chansons du concert
    private var tavernSongToggle = 0   // alterne les 2 chansons du bar

    /** Cree une rumeur dans le village (evenement notable du heros). */
    private fun noteRumeur(cle: String, texte: String, positif: Boolean) {
        if (villagers.isEmpty()) return
        try { VillagerAI.creerRumeur(cle, texte, positif, time) } catch (t: Throwable) { }
    }

    private fun heroFaits(): Set<String> {
        val f = HashSet<String>()
        if (world.bossDefeated) f.add("heros")
        if (ticketOwned) f.add("slip")
        if (fishTotal >= 3) f.add("peche")
        if (spraysDone >= 3) f.add("tags")
        if (spellKnown.any { it }) f.add("magie")
        if (boatCell >= 0 && world.boatCell >= 0 && boatCell != world.boatCell) f.add("marin")
        return f
    }

    /** Propose les reponses d'une Replique de l'IA (+ visite guidee eventuelle). */
    private fun offerReplique(rep: VillagerAI.Replique, w: Walker) {
        dlgWalkerIdx = walkers.indexOf(w)
        dlgKind = 0
        dlgReponses = rep.reponses
        val labels = rep.reponses.map { it.texte }.toMutableList()
        val offre = guideOfferFor(w)
        if (offre != null && labels.size < 4) labels.add("\uD83D\uDC63 " + offre.first)
        dlgChoices = labels
    }

    /**
     * La visite guidee que CE personnage peut proposer, selon l'etat du jeu :
     * (phrase d'invitation, case de destination, phrase d'arrivee), ou null.
     * Seuls les personnages dehors, sur l'ile, peuvent guider.
     */
    private fun guideOfferFor(w: Walker): Triple<String, Int, String>? {
        if (guideWkIdx >= 0) return null
        val wx = w.x.toInt()
        val wy = w.y.toInt()
        if (!world.isIsland(wx, wy) || world.isInterior(wx, wy)) return null
        val nom = persoFor(w)?.nom ?: return null
        if (nom in guidedDone) return null

        fun near(cell: Int): Int {   // une case praticable adjacente (ou la case elle-meme)
            if (cell < 0) return -1
            if (world.isWalkable(world.cx(cell), world.cy(cell))) return cell
            for ((dx, dy) in listOf(Pair(0, 1), Pair(0, -1), Pair(1, 0), Pair(-1, 0))) {
                val nx = world.cx(cell) + dx
                val ny = world.cy(cell) + dy
                if (world.isWalkable(nx, ny)) return world.idx(nx, ny)
            }
            return -1
        }
        val squatMat = world.houseMats.entries.firstOrNull { it.value == 3 }?.key ?: -1
        val vendorCell = world.vendors.entries.firstOrNull { it.value == 1 }?.key ?: -1

        val offre: Triple<String, Int, String>? = when {
            (nom == "Kaos" || (w.kind == 2)) && !world.sprayTaken -> Triple(
                "Viens, on va au squat : une bombe de peinture t'attend. On va TOUT taguer !",
                near(squatMat),
                "Voila le squat. La bombe est a l'interieur : sers-toi, l'artiste !"
            )
            w.kind == 2 && spraysDone >= 3 && !world.shroomTaken -> Triple(
                "Suis-moi... j'ai un pote qui a des CHAMPIS. Il t'aime bien, en plus.",
                near(squatMat),
                "C'est ici, chez Kaos. Ses champis sont pour les vrais. Entre !"
            )
            nom == "Tomas" && !metPierre -> Triple(
                "Suis-moi a la plage : Pierre a un GROS souci. Une histoire de... slip.",
                near(world.pierreCell),
                "Voila Pierre. Sois delicat, hein. C'est personnel."
            )
            nom == "Marthe" && energyCount == 0 && vendorsUsed.isEmpty() -> Triple(
                "Viens, je t'offre une 8.6 au distributeur ! Ca requinque.",
                near(vendorCell),
                "Voila la machine. La premiere est pour toi : touche-la !"
            )
            nom == "Ulric" && !world.dungeon2Revealed -> Triple(
                "Suis-moi. Au nord-ouest, il y a une chose que tu dois voir. Ouvre l'oeil.",
                near(world.dungeon2Cell),
                "La. Cette entree est scellee depuis toujours. Je la surveille. Toi aussi, maintenant."
            )
            nom == "Nina" && boatCell >= 0 && boatCell == world.boatCell -> Triple(
                "Suis-moi au rivage : une barque attend. La mer a des choses a te montrer...",
                if (boatCell >= 0) near(world.idx(world.cx(boatCell), world.cy(boatCell) - 1)) else -1,
                "La barque ! L'ile lointaine est au sud. Ramene-moi un coquillage !"
            )
            else -> null
        }
        return if (offre != null && offre.second >= 0) offre else null
    }

    /** Le personnage se met en route : le heros n'a plus qu'a suivre. */
    private fun startGuide(w: Walker, dest: Int, doneLine: String) {
        guideWkIdx = walkers.indexOf(w)
        guideDest = dest
        guideDoneLine = doneLine
        guideRepathT = 0f
        dlgChoices = emptyList()
        dlgReponses = emptyList()
        dialogue = listOf("Suis-moi !", "Par ici, reste derriere moi !", "C'est parti, suis-moi !")[npcRnd.nextInt(3)]
        dialogueName = persoFor(w)?.nom ?: ""
        setBubbleAt(w)
        audio.play("pickup")
    }

    /** Le Perso (VillagerAI) derriere un walker, s'il en a un. */
    private fun persoFor(w: Walker): VillagerAI.Perso? = villagers.getOrNull(persoIdxFor(w))

    /** Index du Perso dans la liste villagers (ou -1). */
    private fun persoIdxFor(w: Walker): Int = when (w.kind) {
        0 -> if (villagers.isEmpty()) -1 else (w.id - 1) % villagers.size
        2 -> 10 + w.id
        4 -> 17 + w.id
        5 -> 28
        6 -> 28 + w.id
        7 -> 35 + w.id
        else -> -1
    }

    /** Pose la bulle sur ce walker (position, duree, orientation). */
    private fun setBubbleAt(w: Walker) {
        dialogueX = w.x
        dialogueY = w.y
        val dur = (2.2f + dialogue.length * 0.045f).coerceAtMost(6.5f)
        w.talk = dur
        dialogueT = dur
        val ddx = fx - w.x
        val ddy = fy - w.y
        w.dir = if (abs(ddx) >= abs(ddy)) (if (ddx > 0) 3 else 2) else (if (ddy > 0) 0 else 1)
    }

    /** Des choix de papotage libres (quand le jeu a redige la replique). */
    private fun offerChatChoices(w: Walker) {
        val p = persoFor(w) ?: return
        dlgWalkerIdx = walkers.indexOf(w)
        dlgKind = 0
        dlgReponses = try { VillagerAI.choixLibres(p, npcRnd) } catch (t: Throwable) { emptyList() }
        dlgChoices = dlgReponses.map { it.texte }
    }

    private val LESSON_NAMES = arrayOf("L'AIR", "L'EAU", "LA TERRE", "LE FEU")

    /** Les cours de Maitre Zephyrin : lecon + question + 3 reponses (bonne). */
    private fun lessonFor(e: Int): Triple<String, List<String>, Int> = when (e) {
        0 -> Triple(
            "L'AIR ! Invisible, insaisissable... comme mes clefs ! Il porte les mouettes, les odeurs de soupe et mes eternuements. QUESTION : pour lever une tornade, on agite quoi ?",
            listOf("La langue", "Les bras, comme un poulet furieux", "Un sandwich"), 1
        )
        1 -> Triple(
            "L'EAU ! Elle mouille. C'est PROFOND, medite la-dessus. L'eau epouse toutes les formes, meme celle de ma theiere-mouette. QUESTION : la formule secrete de l'eau, c'est... ?",
            listOf("Abracadabra", "H2-Slip", "GLOU-GLOU-GLOUUU"), 2
        )
        2 -> Triple(
            "LA TERRE ! Solide ! Fiable ! Contrairement a mon dos. La terre porte tout : l'arbre, le village, et mes 3000 fioles. QUESTION : pour soulever un rocher par la pensee, on pense a... ?",
            listOf("Des racines profondes", "Une omelette", "Rien : c'est le rocher qui decide"), 0
        )
        else -> Triple(
            "LE FEU !! NE PAS REPETER A LA MAISON !! ... c'est ce que dit ma cicatrice. Le feu, c'est la passion, la colere, et les toasts trop cuits. QUESTION : on allume la flamme interieure avec... ?",
            listOf("Un briquet (TRICHEUR !)", "La colere des sourcils fronces", "Du fromage"), 1
        )
    }

    /** L'accueil du mage : menu des elements restants, ou papotage si diplome. */
    private fun magicianTalk(w: Walker) {
        dialogueName = "Maitre Zephyrin"
        val left = (0..3).filter { !spellKnown[it] }
        if (left.isEmpty()) {
            val p = persoFor(w)
            dialogue = try {
                if (p != null) {
                    val rep = VillagerAI.discuter(p, time, heroFaits(), npcRnd, persoIdxFor(w))
                    offerReplique(rep, w)
                    rep.texte
                } else "Mon meilleur eleve ! Snif. FIERTE."
            } catch (t: Throwable) { "Mon meilleur eleve ! Snif. FIERTE." }
            setBubbleAt(w)
            return
        }
        val n = 4 - left.size
        dialogue = when (n) {
            0 -> "AH ! Un cerveau tout frais ! Je suis Maitre Zephyrin, 400 ans, toutes mes dents (magiques). Quelle magie elementaire veux-tu apprendre ?"
            1 -> "Te revoila, apprenti ! L'arbre m'a dit du bien de toi. Il exagere toujours. La suite du programme ?"
            2 -> "Deja la MOITIE du programme ! A ce rythme tu me remplaceras. NE me remplace pas. Bon, la suite ?"
            else -> "PLUS QU'UNE ! Je sens l'emotion monter. Ou alors c'est la fiole verte. Alors ?"
        }
        setBubbleAt(w)
        dlgWalkerIdx = walkers.indexOf(w)
        dlgKind = 1
        dlgElems = left
        dlgChoices = left.map { LESSON_NAMES[it] } + "Juste discuter"
    }

    /** Le heros a touche une reponse. */
    private fun onDialogueChoice(k: Int) {
        val kind = dlgKind
        val w = walkers.getOrNull(dlgWalkerIdx)
        dlgChoices = emptyList()
        if (w == null) return
        when (kind) {
            0 -> {   // papotage generique, avec TOUT le monde
                val p = persoFor(w) ?: return
                if (k >= dlgReponses.size) {   // le choix "Suis-moi" ajoute en fin de liste
                    val offre = guideOfferFor(w)
                    if (offre != null) startGuide(w, offre.second, offre.third)
                    return
                }
                val effet = dlgReponses.getOrNull(k)?.effet ?: VillagerAI.EF_BYE
                val rep = try { VillagerAI.reagir(p, effet, npcRnd, time) } catch (t: Throwable) {
                    VillagerAI.Replique("...", emptyList())
                }
                // CONTAGION SOCIALE : la reaction rejaillit sur le cercle du PNJ
                val socialDelta = when (effet) {
                    VillagerAI.EF_MOQUE -> -2
                    VillagerAI.EF_TRAHISON -> -3
                    VillagerAI.EF_COMPLIMENT -> 1
                    VillagerAI.EF_PROMESSE -> 2
                    else -> 0
                }
                if (socialDelta != 0) {
                    try { VillagerAI.propagerAction(villagers, p.nom, socialDelta, time) } catch (t: Throwable) { }
                    // Le village jase de la maniere dont on traite les gens
                    try {
                        if (socialDelta <= -2) {
                            val ami = VillagerAI.amiDe(p.nom)
                            if (ami != null) noteRumeur(
                                "social_${p.nom}_${time.toInt() / 30}",
                                "le heros a mal traite ${p.nom}... ${ami} n'a pas apprecie du tout !",
                                false
                            )
                        } else if (socialDelta >= 2) {
                            noteRumeur(
                                "social_${p.nom}_${time.toInt() / 30}",
                                "le heros et ${p.nom} sont devenus tres proches, parait-il !",
                                true
                            )
                        }
                    } catch (t: Throwable) { }
                }
                dialogue = rep.texte
                dialogueName = p.nom
                setBubbleAt(w)
                if (rep.reponses.isNotEmpty()) {
                    dlgKind = 0
                    dlgReponses = rep.reponses
                    dlgChoices = rep.reponses.map { it.texte }
                }
            }
            1 -> {   // menu du mage
                if (k >= dlgElems.size) {   // "Juste discuter"
                    val p = persoFor(w) ?: return
                    dialogue = try {
                        val rep = VillagerAI.discuter(p, time, heroFaits(), npcRnd, persoIdxFor(w))
                        offerReplique(rep, w)
                        rep.texte
                    } catch (t: Throwable) { "Les runes, tout ca..." }
                    dialogueName = "Maitre Zephyrin"
                    setBubbleAt(w)
                    return
                }
                val e = dlgElems[k]
                val (cours, reponses, bonne) = lessonFor(e)
                dialogue = cours
                dialogueName = "Maitre Zephyrin"
                setBubbleAt(w)
                dlgKind = 2
                dlgElem = e
                dlgCorrect = bonne
                dlgChoices = reponses
            }
            2 -> {   // reponse au cours
                if (k == dlgCorrect) {
                    spellKnown[dlgElem] = true
                    noteRumeur("magie${dlgElem}", "le mage de l'arbre enseigne la magie au heros, il parait !", true)
                    spellDemo = dlgElem
                    spellDemoT = 1.2f
                    audio.play("win")
                    saveGame()
                    val fini = spellKnown.all { it }
                    dialogue = if (fini)
                        "TOUS LES ELEMENTS !!! Tu es... snif... MON CHEF-D'OEUVRE ! Sens cette puissance : bientot, tu la LANCERAS !"
                    else
                        "EXACT !! " + LESSON_NAMES[dlgElem] + " coule desormais dans tes veines ! Regarde-moi ce joli tourbillon ! Bon. La suite ?"
                    dialogueName = "Maitre Zephyrin"
                    setBubbleAt(w)
                    if (!fini) {
                        dlgKind = 1
                        dlgElems = (0..3).filter { !spellKnown[it] }
                        dlgChoices = dlgElems.map { LESSON_NAMES[it] } + "Juste discuter"
                    } else {
                        offerChatChoices(w)
                    }
                } else {
                    val p = persoFor(w)
                    if (p != null) p.memoire.vexe = (p.memoire.vexe + 1).coerceAtMost(4)
                    dialogue = listOf(
                        "NON NON NON ET NON !!! *il fume des oreilles* ... Pardon. Le calme. La respiration. ON REPREND ?",
                        "QUOI ?! *une fiole explose toute seule* ... Ce n'etait pas la verte, ca va. On reessaie ?",
                        "AAARGH ! 400 ans de pedagogie pour CA !! *inspire* ... L'arbre me dit d'etre patient. RECOMMENCE."
                    )[npcRnd.nextInt(3)]
                    dialogueName = "Maitre Zephyrin"
                    setBubbleAt(w)
                    val (_, reponses, _) = lessonFor(dlgElem)
                    dlgKind = 2
                    dlgChoices = reponses
                }
            }
        }
    }

    // ============================================================ LE COMMERCE

    /** Un article en vente : nom, prix de base, action a l'achat. */
    private class Ware(val key: String, val label: String, val price: Int)

    /** Ce que le marchand n vend (catalogue distinct par marchand). */
    private fun catalogue(n: Int): List<Ware> = if (n == 1) listOf(
        Ware("energy", "Canette de 8.6", 25),
        Ware("flags", "Lot de 5 drapeaux", 30),
        Ware("heal", "Potion de soin (+50 PV)", 40),
        Ware("boots", "Vieille botte (deco)", 8),
        Ware("shroom", "Champignon mystere", 55)
    ) else listOf(
        Ware("heal_big", "Grand elixir (+100 PV)", 70),
        Ware("energy", "Canette de 8.6", 22),
        Ware("fish", "Poisson frais", 15),
        Ware("flags", "Lot de 5 drapeaux", 28),
        Ware("charm", "Porte-bonheur (+2 coeurs)", 60)
    )

    /** Ce que le heros peut revendre, et a quel prix (avant reduction). */
    private fun sellable(): List<Ware> {
        val out = ArrayList<Ware>()
        if (fishCount > 0) out.add(Ware("s_fish", "Poisson frais x$fishCount", 8))
        if (bootCount > 0) out.add(Ware("s_boot", "Vieille botte x$bootCount", 5))
        if (algaeCount > 0) out.add(Ware("s_algae", "Paquet d'algues x$algaeCount", 4))
        if (shroomCount > 0) out.add(Ware("s_shroom", "Champignon x$shroomCount", 30))
        if (energyCount > 0) out.add(Ware("s_energy", "Canette de 8.6 x$energyCount", 12))
        return out
    }

    /** Prix effectif a l'achat (reduction du marchandage, humeur du marchand). */
    private fun buyPrice(base: Int): Int {
        val moodPenalty = if (shopMood < 0) 15 else 0
        val p = base * (100 - shopDiscount + moodPenalty) / 100
        return p.coerceAtLeast(1)
    }

    /** Prix de revente (le marchandage l'ameliore aussi, un peu). */
    private fun sellPrice(base: Int): Int {
        val bonus = (shopDiscount / 2)
        val moodPenalty = if (shopMood < 0) 15 else 0
        return (base * (100 + bonus - moodPenalty) / 100).coerceAtLeast(1)
    }

    private fun shopSay(msg: String) { shopMsg = msg; shopMsgT = 4f }

    private fun openShop(n: Int) {
        shopMerchant = n
        showShop = true
        shopTab = 0
        shopDiscount = 0
        shopHaggleTries = 0
        shopMood = 0
        val who = if (n == 1) "Le marchand nomade" else "La jolie marchande"
        shopSay("$who : \"Bonnes affaires et sourires garantis ! Que veux-tu ?\"")
    }

    /** Le heros tente de negocier : jet influence par l'or depense et le hasard. */
    private fun haggle() {
        if (shopHaggleTries >= 3) {
            shopSay("\"N'insiste pas trop, l'ami... mon dernier prix est mon dernier prix !\"")
            return
        }
        shopHaggleTries++
        val roll = npcRnd.nextFloat()
        // plus on a deja negocie, plus c'est dur ; un brin de chance
        val seuil = 0.35f + shopHaggleTries * 0.15f
        when {
            roll > seuil + 0.25f -> {
                val gain = 5 + npcRnd.nextInt(8)
                shopDiscount = (shopDiscount + gain).coerceAtMost(35)
                audio.play("chest")
                shopSay("\"Pfff... d'accord, $gain% de moins. Mais tu me ruines !\" (-$shopDiscount% au total)")
            }
            roll > seuil -> {
                shopSay("\"Hmmm... je reflechis. Propose encore, pour voir.\"")
            }
            else -> {
                shopMood--
                shopDiscount = (shopDiscount - 5).coerceAtLeast(0)
                audio.play("error")
                shopSay("\"Tu me VEXES ! Les prix remontent, tiens !\"")
                noteRumeur("vexeMarchand${shopMerchant}", "le heros a essaye d'arnaquer un pauvre marchand !", false)
            }
        }
    }

    private fun buyWare(wr: Ware) {
        val price = buyPrice(wr.price)
        if (gold < price) { shopSay("\"Reviens quand tu auras les moyens, tresor.\""); return }
        gold -= price
        when (wr.key) {
            "energy" -> energyCount++
            "flags" -> flagsLeft += 5
            "heal" -> hp = (hp + 50).coerceAtMost(100)
            "heal_big" -> hp = 100
            "boots" -> bootCount++
            "shroom" -> shroomCount++
            "fish" -> fishCount++
            "charm" -> { heartsGot += 2; hp = (hp + 20).coerceAtMost(100) }
        }
        audio.play("key")
        shopSay("Achat : ${wr.label} pour $price pieces. \"Excellent choix !\"")
        saveGame()
    }

    private fun sellWare(wr: Ware) {
        val price = sellPrice(wr.price)
        when (wr.key) {
            "s_fish" -> if (fishCount > 0) fishCount-- else return
            "s_boot" -> if (bootCount > 0) bootCount-- else return
            "s_algae" -> if (algaeCount > 0) algaeCount-- else return
            "s_shroom" -> if (shroomCount > 0) shroomCount-- else return
            "s_energy" -> if (energyCount > 0) energyCount-- else return
            else -> return
        }
        gold += price
        audio.play("chest")
        shopSay("Vendu pour $price pieces. \"Ca m'interesse, ca !\"")
        saveGame()
    }

    private fun talkTo(w: Walker) {
        dlgChoices = emptyList()   // toute nouvelle discussion repart de zero
        dialogueX = w.x
        dialogueY = w.y
        if (w.kind == 0 && villagers.isNotEmpty()) {
            val p = villagers[(w.id - 1) % villagers.size]
            dialogue = if (p.nom == "Kaos") {
                // Kaos ne donne pas ses champis a n'importe qui
                when {
                    !world.sprayTaken -> "Ma bombe Rebel Ink traine chez moi, sers-toi."
                    spraysDone < 3 -> "La bombe, c'est bien. Encore ${3 - spraysDone} tag(s) et t'es des notres."
                    !world.shroomTaken -> "Respect, l'artiste ! Mes champis t'attendent chez moi. Cadeau."
                    else -> try {
                        VillagerAI.parler(p, time, world.bossDefeated, npcRnd)
                    } catch (t: Throwable) { "No futur, l'ami." }
                }
            } else questDialogue(w.id) ?: (try {
                val rep = VillagerAI.discuter(p, time, heroFaits(), npcRnd, persoIdxFor(w))
                if (p.nom != "Kaos") offerReplique(rep, w)
                rep.texte
            } catch (t: Throwable) {
                "Belle journee, non ?"
            })
            dialogueName = p.nom
            // Kaos ne papote pas, mais il peut proposer LA visite du squat
            if (p.nom == "Kaos" && dlgChoices.isEmpty()) {
                guideOfferFor(w)?.let { off ->
                    dlgWalkerIdx = walkers.indexOf(w)
                    dlgKind = 0
                    dlgReponses = emptyList()
                    dlgChoices = listOf("\uD83D\uDC63 " + off.first)
                }
            }
        } else if (w.kind == 0) {
            dialogue = listOf(
                "Bonjour, voyageur !", "Belle journee !", "Bienvenue au village !"
            )[(w.id - 1) % 3]
            dialogueName = ""
        } else if (w.kind == 2) {
            // Les punks du club
            val idx2 = 10 + w.id
            if (idx2 < villagers.size) {
                val p2 = villagers[idx2]
                dialogue = try {
                    val rep = VillagerAI.discuter(p2, time, heroFaits(), npcRnd, persoIdxFor(w))
                    offerReplique(rep, w)
                    rep.texte
                } catch (t: Throwable) { "Oi ! Punk's not dead." }
                dialogueName = p2.nom
            } else {
                dialogue = "Oi !"
                dialogueName = ""
            }
        } else if (w.kind == 7) {
            // Un marchand ambulant : ouvre la boutique
            openShop(w.id)
            return
        } else if (w.kind == 6) {
            // Le tavernier et ses clients
            val p = persoFor(w)
            dialogue = try {
                if (p != null) {
                    val rep = VillagerAI.discuter(p, time, heroFaits(), npcRnd, persoIdxFor(w))
                    offerReplique(rep, w)
                    rep.texte
                } else "Sante !"
            } catch (t: Throwable) { "Sante !" }
            dialogueName = p?.nom ?: ""
            setBubbleAt(w)
            return
        } else if (w.kind == 5) {
            // Maitre Zephyrin : les cours de magie du Grand Arbre
            magicianTalk(w)
            return
        } else if (w.kind == 4) {
            // Les heros de la guilde
            val idx3 = 17 + w.id
            if (idx3 < villagers.size) {
                val p3 = villagers[idx3]
                dialogue = try {
                    val rep = VillagerAI.discuter(p3, time, heroFaits(), npcRnd, persoIdxFor(w))
                    offerReplique(rep, w)
                    rep.texte
                } catch (t: Throwable) { "Pour la guilde !" }
                dialogueName = p3.nom
            } else {
                dialogue = "Pour la guilde !"
                dialogueName = ""
            }
        } else if (w.kind == 3) {
            // Pierre et Franki : LA QUETE DU SLIP
            if (w.id == 1) {
                dialogueName = "Pierre"
                dialogue = when {
                    slipOwned -> {
                        slipOwned = false
                        ticketOwned = true
                        audio.play("win")
                        saveGame()
                        "MON SLIP PORTE-BONHEUR ! Tiens : une PLACE DE CONCERT au Punk Club !"
                    }
                    ticketOwned -> "Le concert va etre ENORME. Fonce au club !"
                    !metPierre -> {
                        metPierre = true
                        saveGame()
                        "Malheur ! Mon slip porte-bonheur est tombe a la mer ! Franki a une canne..."
                    }
                    !rodOwned -> "Va voir Franki pour la canne, je t'en prie !"
                    else -> "Regarde ! Il FLOTTE la-bas au large ! Lance ta ligne dessus !"
                }
            } else {
                dialogueName = "Franki"
                dialogue = when {
                    metPierre && !rodOwned -> {
                        rodOwned = true
                        audio.play("pickup")
                        saveGame()
                        "Tiens ma canne ! Touche la mer pour lancer, ferre quand CA MORD. Vise le slip qui flotte !"
                    }
                    rodOwned && !ticketOwned -> "Touche la mer pour lancer, et FERRE des que ca mord ! Le slip flotte au large de Pierre."
                    ticketOwned -> "Ha ! Le slip retrouve, ca se fete au club !"
                    else -> "Pierre a ENCORE perdu quelque chose. Va le voir."
                }
            }
        } else {
            dialogue = listOf("Ouaf !", "Miaou...", "Cot cot !", "Meuh...", "Beee !",
                "Groin groin !", "Hihan !", "Couac !", "Piou piou !", "Sniff sniff...")[(w.id - 1) % 10]
            dialogueName = ""
            petCount++
            saveGame()
        }
        // La bulle reste plus longtemps si la phrase est longue
        val dur = (2.2f + dialogue.length * 0.045f).coerceAtMost(5.5f)
        w.talk = dur
        dialogueT = dur
        // Le villageois se tourne vers le heros
        val dx = fx - w.x
        val dy = fy - w.y
        w.dir = if (abs(dx) >= abs(dy)) (if (dx > 0) 3 else 2) else (if (dy > 0) 0 else 1)
        audio.play("pickup")
    }

    /** Le distributeur : une canette la premiere fois, des histoires ensuite. */
    private fun useVendor(cell: Int) {
        if (cell !in vendorsUsed) {
            vendorsUsed.add(cell)
            energyCount++
            audio.play("chest")
            showMsg("CLONK ! Une canette bien fraiche tombe. (inventaire)")
            saveGame()
            return
        }
        val tavern = world.vendors[cell] == 4
        dialogue = try {
            if (tavern) VillagerAI.histoireTaverne(npcRnd) else VillagerAI.histoireDistributeur(npcRnd)
        } catch (t: Throwable) {
            "GRZZT... 2,50... GRZZT..."
        }
        dialogueName = if (tavern) "VIEUX DISTRIBUTEUR" else "DISTRIBUTEUR 8.6"
        dialogueX = world.cx(cell) + 0.5f
        dialogueY = world.cy(cell) + 0.15f
        dialogueT = (3f + dialogue.length * 0.05f).coerceAtMost(9f)
        audio.play("pickup")
    }

    /** Quelle interaction est possible ici ? (bouton contextuel) */
    private fun updateInteraction() {
        actKind = 0
        actLabel = ""
        // 1. Sortir d'un batiment
        if (world.isInterior(hx, hy)) {
            val n = world.interiorOf(hx, hy)
            val e = world.houseEntry[n]
            if (e != null && abs(world.cx(e) - hx) <= 1 && abs(world.cy(e) - hy) <= 2) {
                actKind = 2; actData = n; actLabel = "SORTIR"
                return
            }
        }
        // 2. Entrer : un paillasson sur ma case ou a cote
        for (dy in -1..1) for (dx in -1..1) {
            if (!world.inside(hx + dx, hy + dy)) continue
            val n = world.houseMats[world.idx(hx + dx, hy + dy)] ?: continue
            if (!world.isIsland(hx, hy)) continue
            actKind = 1; actData = n
            actLabel = if (n == 5) "ENTRER AU CLUB" else "ENTRER"
            return
        }
        // 2a. Remonter d'une cache secrete (on est dans une antichambre)
        if (world.secretRooms.containsKey(world.idx(hx, hy)) && secretReturnCell >= 0) {
            actKind = 7; actData = secretReturnCell
            actLabel = "REMONTER"
            return
        }
        // 2b. La barque : embarquer quand on est au bord, accoster quand on est en mer
        if (!onBoat && boatCell >= 0 &&
            abs(world.cx(boatCell) - hx) <= 1 && abs(world.cy(boatCell) - hy) <= 1
        ) {
            actKind = 5; actData = boatCell
            actLabel = "EMBARQUER"
            return
        }
        if (onBoat) {
            for ((dx, dy) in listOf(Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1))) {
                if (world.isWalkable(hx + dx, hy + dy)) {
                    actKind = 6; actData = world.idx(hx + dx, hy + dy)
                    actLabel = "ACCOSTER"
                    return
                }
            }
            return
        }
        // 3. Le distributeur
        for (dy in -1..1) for (dx in -1..1) {
            if (!world.inside(hx + dx, hy + dy)) continue
            val c = world.idx(hx + dx, hy + dy)
            if (world.vendors.containsKey(c)) {
                actKind = 4; actData = c
                actLabel = if (c in vendorsUsed) "HISTOIRE DU 8.6" else "8.6 — 2,50"
                return
            }
        }
        // 4. Parler au plus proche
        var best = -1
        var bd = 2.1f
        for ((k, wk) in walkers.withIndex()) {
            val d = hypot(wk.x - fx, wk.y - fy)
            if (d < bd) { bd = d; best = k }
        }
        if (best >= 0) {
            val wk = walkers[best]
            // Dans la salle de concert, offrir un champi a un punk lance la musique !
            val inClub = world.isInterior(hx, hy) && world.interiorOf(hx, hy) == 5
            if (inClub && shroomCount > 0 && (wk.kind == 2 || wk.kind == 0)) {
                actKind = 8; actData = best
                actLabel = "OFFRIR UN CHAMPI"
                return
            }
            actKind = 3; actData = best
            actLabel = when (wk.kind) {
                0 -> "PARLER"
                2 -> "PARLER"
                4 -> "PARLER"
                5 -> "PARLER AU MAGE"
                6 -> if (wk.id == 1) "PARLER AU TAVERNIER" else "PARLER"
                7 -> "MARCHANDER"
                3 -> if (wk.id == 1) "PARLER A PIERRE" else "PARLER A FRANKI"
                else -> "CARESSER"
            }
        }
    }

    /** Les reponses a choix multiples, empilees au-dessus du bouton d'action. */
    private fun drawDialogueChoices(canvas: Canvas, w: Float, h: Float) {
        dlgChoiceRects.clear()
        if (dlgChoices.isEmpty() || dialogueT <= 0f) return
        paint.textSize = h * 0.02f
        paint.isFakeBoldText = true
        val bh = h * 0.052f
        val gap = h * 0.012f
        var yb = boardBottom - h * 0.02f - bh
        for (k in dlgChoices.indices.reversed()) {
            val label = dlgChoices[k]
            val tw = paint.measureText(label)
            val bw = (tw + h * 0.06f).coerceAtLeast(w * 0.42f)
            val r = RectF(w / 2f - bw / 2f, yb, w / 2f + bw / 2f, yb + bh)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(235, 26, 30, 48)
            canvas.drawRoundRect(r, bh / 2f, bh / 2f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = h * 0.0035f
            paint.color = Color.rgb(210, 175, 90)
            canvas.drawRoundRect(r, bh / 2f, bh / 2f, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(240, 235, 220)
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(label, r.centerX(), r.centerY() + h * 0.0075f, paint)
            while (dlgChoiceRects.size <= k) dlgChoiceRects.add(RectF())
            dlgChoiceRects[k] = r
            yb -= bh + gap
        }
        paint.isFakeBoldText = false
    }

    /** La demonstration d'un sort fraichement appris, autour du heros. */
    private fun drawSpellDemo(canvas: Canvas, w: Float) {
        if (spellDemo < 0 || spellDemoT <= 0f) return
        val ph = (1f - spellDemoT / 1.2f).coerceIn(0f, 0.999f)
        val f = (ph * 5).toInt().coerceIn(0, 4)
        val b = spellBmp(spellDemo, heroDir.coerceIn(0, 3), f)
        drawSprite(canvas, b, sx(fx, w), sy(fy) - tile * 0.55f, tile * (1.6f + ph * 1.4f), 235)
    }

    /** Le bouton d'interaction contextuel. */
    private fun drawActionButton(canvas: Canvas, w: Float, h: Float) {
        updateInteraction()
        if (dlgChoices.isNotEmpty() && dialogueT > 0f) { btnAct.setEmpty(); return }
        if (actKind == 0) { btnAct.setEmpty(); return }
        paint.textSize = h * 0.021f
        paint.isFakeBoldText = true
        val tw = paint.measureText(actLabel)
        val bw = tw + h * 0.055f
        val bh = h * 0.052f
        val cxb = w / 2f
        val yb = boardBottom - bh - h * 0.015f
        btnAct.set(cxb - bw / 2f, yb, cxb + bw / 2f, yb + bh)
        val pulse = 0.5f + 0.5f * sin(time * 3.5f)
        paint.color = Color.argb(235, 34, 30, 26)
        canvas.drawRoundRect(btnAct, bh * 0.4f, bh * 0.4f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = h * 0.004f
        paint.color = Color.rgb((200 + 55 * pulse).toInt(), (170 + 40 * pulse).toInt(), 80)
        canvas.drawRoundRect(btnAct, bh * 0.4f, bh * 0.4f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(255, 235, 190)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(actLabel, cxb, yb + bh * 0.66f, paint)
        paint.isFakeBoldText = false
    }

    private fun doInteract() {
        when (actKind) {
            1 -> tryEnterHouse(actData)
            2 -> leaveHouse(actData)
            3 -> walkers.getOrNull(actData)?.let { if (hypot(it.x - fx, it.y - fy) <= 2.3f) talkTo(it) }
            4 -> useVendor(actData)
            5 -> boardBoat(actData)
            6 -> disembark(actData)
            7 -> leaveSecretCache(actData)
            8 -> offrirChampi(actData)
        }
    }

    /** Offrir un champi a quelqu'un dans la salle : le groupe joue ! */
    private fun offrirChampi(walkerIdx: Int) {
        if (shroomCount <= 0) return
        val wk = walkers.getOrNull(walkerIdx) ?: return
        shroomCount--
        champiGiven++
        // pioche une des 5 chansons du concert, jamais deux fois la meme d'affilee
        var track = Audio.CONCERT[npcRnd.nextInt(Audio.CONCERT.size)]
        var essais = 0
        while (track == lastConcertTrack && Audio.CONCERT.size > 1 && essais < 8) {
            track = Audio.CONCERT[npcRnd.nextInt(Audio.CONCERT.size)]; essais++
        }
        lastConcertTrack = track
        audio.playEvent(track)
        tripT = 6f                      // petit effet visuel psychedelique
        // le PNJ regarde le heros, tout le monde est content
        val p = persoFor(wk)
        p?.memoire?.pousserHumeur(0.6f, "tu m'as file un champi magique", time)
        val nom = p?.nom ?: "Le punk"
        showMsg("$nom goute le champi... LE GROUPE SE MET A JOUER ! Le concert commence !")
        saveGame()
    }

    /** Sauter dans la barque : on peut alors ramer sur toute la mer. */
    private fun boardBoat(cell: Int) {
        hx = world.cx(cell); hy = world.cy(cell)
        fx = hx + 0.5f; fy = hy + 0.5f
        onBoat = true
        stopFishing(null)
        path = emptyList(); pathStep = 0
        following = true
        audio.play("splash")
        showMsg("En mer ! Touchez l'eau pour ramer. ACCOSTER pres d'un rivage.")
        saveGame()
    }

    /** Poser pied a terre : la barque reste mouillee ici. */
    private fun disembark(cell: Int) {
        boatCell = world.idx(hx, hy)
        hx = world.cx(cell); hy = world.cy(cell)
        fx = hx + 0.5f; fy = hy + 0.5f
        onBoat = false
        path = emptyList(); pathStep = 0
        following = true
        audio.play("splash")
        showMsg("Vous accostez. La barque vous attend ici.")
        saveGame()
    }

    /** Entrer dans une maison (le videur du club controle les places). */
    private fun tryEnterHouse(n: Int) {
        if (n == 5 && !ticketOwned) {
            teleCd = 0.9f
            audio.play("error")
            showMsg("Le videur : \"Place de concert obligatoire.\" (voir Pierre le pecheur)")
            return
        }
        if (n == 5) {
            showMsg("LE PUNK CLUB ! Le son est ENORME !")
            if (!clubVisited) { clubVisited = true; saveGame() }
        }
        if (n == 6) showMsg("LA GUILDE ! \"La force du groupe, la gloire a tous !\"")
        if (n == 7) showMsg("LE GRAND ARBRE... Les runes murmurent doucement.")
        if (n == 8) {
            showMsg("LA TAVERNE ! Biere, vin, repas ET histoires !")
            tavernSongToggle++
            audio.playEvent(if (tavernSongToggle % 2 == 1) Audio.T_TAVERNE else Audio.T_TAVERNE2)
        }
        enterHouse(n)
    }

    /** Entrer dans une maison / en ressortir. */
    private fun enterHouse(n: Int) {
        val entry = world.houseEntry[n] ?: return
        teleportTo(world.cx(entry), world.cy(entry) - 1)
        audio.play("door")
        showMsg("Vous entrez dans la maison $n.")
    }

    private fun leaveHouse(n: Int) {
        audio.stopEvent()
        var mat = -1
        for ((cell, hn) in world.houseMats) if (hn == n && world.isIsland(world.cx(cell), world.cy(cell))) mat = cell
        if (mat < 0) return
        teleportTo(world.cx(mat), world.cy(mat))
        audio.play("door")
    }

    private fun teleportTo(tx: Int, ty: Int) {
        hx = tx; hy = ty
        fx = hx + 0.5f; fy = hy + 0.5f
        camX = fx; camY = fy
        following = true
        path = emptyList(); pathStep = 0
        clearPendings()
        teleCd = 1.2f
        audio.play("pickup")
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
        pendingChest = false
        pendingSecretChest = -1; pendingDoor = false
        pendingChest2 = false; pendingChest3 = false
        pendingAltar = false; pendingDoor1 = false; pendingDoor2 = false
        pendingMini = -1; pendingTorch = -1; pendingDoor3 = false; pendingRune = false
        pendingVendor = -1
    }

    private fun hurt(n: Int, why: String) {
        if (godMode) { showMsg("$why (mode test : aucun degat)"); return }
        hp -= n
        showMsg(why)
        if (hp <= 0) {
            hp = 0
            gameOver = true
            audio.play("lose")
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
            audio.play("boom")
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
            audio.play("disarm")
            showMsg("Mine desamorcee ! Drapeaux restants : $flagsLeft")
        } else {
            world.revealCascade(x, y)
            showMsg("Fausse alerte : drapeau gaspille ! Restants : $flagsLeft")
        }
    }

    /** Ajoute des pieces a la bourse, avec un petit flash. */
    /** Descend dans une antichambre secrete : monstres gardiens + coffre d'or. */
    private fun enterSecretCache(entry: Int) {
        secretReturnCell = world.idx(hx, hy)   // on remontera ici
        hx = world.cx(entry); hy = world.cy(entry)
        fx = hx + 0.5f; fy = hy + 0.5f
        camX = fx; camY = fy
        path = emptyList(); pathStep = 0
        clearPendings()
        // faire apparaitre les monstres gardiens (une seule fois, si coffre non pille)
        val chestC = world.secretChests[entry] ?: -1
        if (chestC >= 0 && chestC !in secretLooted) {
            val guards = world.secretMonsters[entry]
            if (guards != null && mobs.none { it.hp > 0 && world.secretRooms.containsKey(world.idx(it.x.toInt(), it.y.toInt())) }) {
                for ((k, gc) in guards.withIndex()) {
                    val hpv = 80 + k * 20
                    val spr = (world.cx(entry) + k * 3) % 8   // varie dans la serie rigolote
                    mobs.add(Mob(world.cx(gc) + 0.5f, world.cy(gc) + 0.5f, hpv, spr, hpv))
                }
            }
            showMsg("Une salle secrete ! Des gardiens veillent sur un coffre... a l'attaque !")
        } else {
            showMsg("La salle secrete est vide, le coffre a deja ete pille.")
        }
        saveGame()
    }

    private fun gainGold(n: Int) {
        gold += n
        goldAnim = 1.4f
        audio.play("key")
    }

    /** Remonte de l'antichambre secrete vers la dalle d'origine. */
    private fun leaveSecretCache(cell: Int) {
        hx = world.cx(cell); hy = world.cy(cell)
        fx = hx + 0.5f; fy = hy + 0.5f
        camX = fx; camY = fy
        path = emptyList(); pathStep = 0
        clearPendings()
        audio.play("push")
        showMsg("Vous remontez par la trappe.")
        saveGame()
    }

    /** Ouvre un coffre secret : gardiens d'abord, or ensuite. */
    private fun openSecretChest(chestC: Int) {
        if (chestC in secretLooted) { showMsg("Ce coffre est deja vide."); return }
        val rid = world.secretRooms[chestC]
        val guardsAlive = mobs.any { m ->
            m.hp > 0 && world.secretRooms[world.idx(m.x.toInt(), m.y.toInt())] == rid
        }
        if (guardsAlive) {
            showMsg("Les gardiens protegent le coffre ! Terrassez-les d'abord.")
            return
        }
        secretLooted.add(chestC)
        noteRumeur("cache${chestC}", "quelqu'un a trouve un tresor cache sous une dalle du donjon...", true)
        val loot = 80 + npcRnd.nextInt(70)
        gainGold(loot)
        audio.play("chest")
        if (npcRnd.nextFloat() < 0.4f) {
            hp = (hp + 30).coerceAtMost(100)
            showMsg("TRESOR SECRET ! $loot pieces d'or + un elixir (+30 PV) !")
        } else {
            showMsg("TRESOR SECRET ! $loot pieces d'or scintillent dans le coffre !")
        }
    }

    private fun openChest() {
        if (!world.platesSolved()) {
            showMsg("Le coffre est scelle... Resolvez l'enigme de cette salle pour l'ouvrir.")
            return
        }
        if (world.chestOpen) { showMsg("Le coffre est vide."); return }
        audio.play("chest")
        audio.play("key")
        world.chestOpen = true
        world.hasKey = true
        flagsLeft += 5
        keyAnim = 3f
        gainGold(50)
        showMsg("Le coffre s'ouvre... une CLE EN OR et 50 pieces ! (+5 drapeaux)")
    }

    private fun openChest2() {
        if (world.chest2Open) { showMsg("Ce coffre est vide."); return }
        audio.play("chest")
        world.chest2Open = true
        joyOwned = true
        showMsg("Un JOYSTICK ! Activez-le depuis l'inventaire (menu ☰).")
    }

    private fun openChest3() {
        if (world.chest3Open) { showMsg("Le coffre-fort est vide."); return }
        if (!world.sudokuSolved) {
            showSudoku = true
            sudokuSel = -1
            showMsg("Un cadenas a chiffres... resolvez la grille !")
            return
        }
        audio.play("chest")
        world.chest3Open = true
        swordOwned = true
        energyCount++
        flagsLeft += 5
        showMsg("Le coffre-fort s'ouvre : une EPEE, une canette d'energie et 5 drapeaux !")
    }

    private fun sudokuTap(c: Int) {
        if (world.sudokuGiven[c] != 0) return
        sudokuSel = c
        audio.play("flag")
    }

    private fun sudokuPut(v: Int) {
        val c = sudokuSel
        if (c < 0 || world.sudokuGiven[c] != 0) return
        world.sudokuUser[c] = v
        audio.play("flag")
        if (world.sudokuUser.all { it != 0 }) {
            if (world.sudokuOk()) {
                world.sudokuSolved = true
                showSudoku = false
                audio.play("win")
                showMsg("Clic ! Le cadenas cede. Retouchez le coffre-fort.")
                saveGame()
            } else {
                sudokuShake = 0.6f
                audio.play("error")
                for (i in 0 until 16) if (world.sudokuGiven[i] == 0) world.sudokuUser[i] = 0
                sudokuSel = -1
            }
        }
    }

    // ---------- PORTE A RUNES : lights out
    private fun lightsTap(i: Int) {
        world.toggleLight(i)
        audio.play("flag")
        if (world.lightsAllOn()) {
            world.openRuneDoor()
            showLights = false
            audio.play("win")
            showMsg("Les 9 runes brillent ! La porte s'ouvre sur la salle du BOSS.")
            saveGame()
        }
    }

    private fun openDoor() {
        if (!world.hasKey) { showMsg("Porte verrouillee. Il faut la cle du coffre."); return }
        audio.play("door")
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
        audio.play("torch")
        val n = world.torchLit.size
        if (n >= 4 && !world.sokoban2Spawned) {
            world.spawnSokoban2()
            showMsg("Les 4 torches brulent ! 7 caisses tombent du plafond...")
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
            audio.play("boom")
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
        audio.play("push")
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
                    world.sokoban2Spawned -> "Caisses rangees : $c2 / 7"
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

        // Un villageois ou un animal sur cette case ?
        for (w in walkers) {
            if (w.x.toInt() == gx && w.y.toInt() == gy) {
                if (hypot(w.x - fx, w.y - fy) <= 2.2f) { talkTo(w); return }
                // sinon on s'en approche
                val p2 = world.findPath(hx, hy, gx, gy)
                if (p2 != null) { clearPendings(); path = p2; pathStep = 0; return }
            }
        }
        // Une maison : on va sonner a la porte (tout le batiment est cliquable)
        val hn = world.houses[i] ?: world.houseBody[i]
        if (hn != null) {
            var matCell = -1
            for ((c, n) in world.houseMats) if (n == hn && world.isIsland(world.cx(c), world.cy(c))) matCell = c
            if (matCell >= 0) {
                val p2 = world.findPath(hx, hy, world.cx(matCell), world.cy(matCell))
                if (p2 != null) { clearPendings(); path = p2; pathStep = 0; return }
            }
            showMsg("Une jolie maison. Passez par la porte, en bas.")
            return
        }

        // Enigme des couleurs : si on retouche la dalle sur laquelle on se trouve
        // deja, la couleur est validee une nouvelle fois (utile si elle se repete).
        if (simonState == 2) {
            for (k in 0..3) {
                if (i == world.simonTiles[k] && gx == hx && gy == hy) {
                    simonPress(k)
                    return
                }
            }
        }

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
                world.runeDoor -> pendingRune = true
                else -> pendingDoor = true
            }
            return
        }
        if (world.isTorch(i)) {
            if (!walkNextTo(gx, gy)) { showMsg("Approchez-vous de la torche."); return }
            clearPendings(); pendingTorch = i
            return
        }
        // En barque : toucher l'eau = ramer jusque-la
        if (onBoat) {
            if (world.isNavigable(gx, gy)) {
                val p = world.findPath(hx, hy, gx, gy, boat = true)
                if (p == null) { showMsg("Impossible de ramer jusque-la."); return }
                clearPendings()
                path = p; pathStep = 0
            } else {
                showMsg("Approchez du rivage et touchez ACCOSTER pour debarquer.")
            }
            return
        }
        // Pecher avec la canne de Franki (vrai mode peche, garde pour toujours)
        val ter2 = if (world.inside(gx, gy)) world.terrain[i] else World.TER_NONE
        if (rodOwned && (ter2 == World.TER_WATER || ter2 == World.TER_SHALLOW)) {
            if (abs(gx - hx) <= 3 && abs(gy - hy) <= 3) {
                startFishing(gx, gy)
            } else {
                showMsg("Approchez-vous du bord pour lancer la ligne.")
            }
            return
        }
        // Un distributeur de 8.6 ?
        if (world.vendors.containsKey(i)) {
            if (!walkNextTo(gx, gy)) { showMsg("Approchez-vous du distributeur."); return }
            clearPendings(); pendingVendor = i
            return
        }
        if (!world.isFloor(gx, gy)) {
            // Taguer le mur avec la bombe (murs du donjon uniquement, a portee)
            if (sprayOwned && world.terrain[i] == World.TER_NONE &&
                abs(gx - hx) <= 1 && abs(gy - hy) <= 1
            ) {
                world.tags[i] = sprayNext
                sprayNext = sprayNext % 15 + 1
                spraysDone++
                audio.play("torch")
                showMsg(
                    when {
                        spraysDone < 3 -> "Pschhht ! ($spraysDone/3 pour impressionner Kaos)"
                        spraysDone == 3 -> "Pschhht ! Trois tags... Kaos va adorer. Passez le voir !"
                        else -> "Pschhht ! Kaos serait fier."
                    }
                )
                saveGame()
                return
            }
            showMsg(if (sprayOwned) "Trop loin pour taguer. Collez-vous au mur !" else "C'est un mur.")
            return
        }

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
        if (world.secretChests.containsValue(i)) {
            if (!walkNextTo(gx, gy)) { showMsg("Coffre inaccessible."); return }
            clearPendings(); pendingSecretChest = i
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
        if (world.isIsland(gx, gy)) return
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
            audio.play("flag")
            showMsg("Drapeau pose ($flagsLeft). Retouchez la dalle pour desamorcer.")
        }
        saveGame()
    }

    // ============================================================ RENDU

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = ((now - lastTime) / 1e9).toFloat().coerceIn(0f, 0.05f)
        lastTime = now
        if (crashLog != null) { drawCrash(canvas); return }
        try {
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

        drawDialogue(canvas, w, h)
        drawActionButton(canvas, w, h)
        drawDialogueChoices(canvas, w, h)
        drawHud(canvas, w, h)
        if (joyOn && joyOwned) drawJoystick(canvas)

        if (boomFlash > 0f) {
            paint.color = Color.argb((boomFlash * 170).toInt(), 255, 120, 30)
            canvas.drawRect(0f, 0f, w, h, paint)
        }
        if (tripT > 0f) {
            val a = (tripT.coerceAtMost(2f) / 2f)
            for (k in 0..4) {
                val ang = time * (0.7f + k * 0.23f) + k * 1.3f
                val rr = min(w, h) * (0.28f + 0.1f * sin(time * 1.7f + k))
                val cxk = w / 2f + cos(ang) * w * 0.3f
                val cyk = h / 2f + sin(ang * 1.3f) * h * 0.28f
                val cols = intArrayOf(
                    Color.argb((36 * a).toInt(), 255, 80, 200),
                    Color.argb((36 * a).toInt(), 90, 200, 255),
                    Color.argb((36 * a).toInt(), 255, 200, 60),
                    Color.argb((36 * a).toInt(), 140, 90, 255),
                    Color.argb((36 * a).toInt(), 80, 255, 160)
                )
                paint.color = cols[k]
                canvas.drawCircle(cxk, cyk, rr, paint)
            }
        }
        if (damageT > 0f) {
            paint.color = Color.argb((damageT * 200).toInt().coerceAtMost(90), 220, 30, 30)
            canvas.drawRect(0f, 0f, w, h, paint)
        }
        if (showMap) drawMap(canvas, w, h)
        if (showSettings) drawSettings(canvas, w, h)
        if (showSudoku) drawSudoku(canvas, w, h)
        if (showLights) drawLights(canvas, w, h)
        if (miniPlate >= 0) drawMini(canvas, w, h)
        if (showMenu) drawMenu(canvas, w, h)
        if (showInv) drawInventory(canvas, w, h)
        if (showShop) drawShop(canvas, w, h)
        if (showHelp) drawHelp(canvas, w, h)
        if (gameOver || victory) drawEnd(canvas, w, h)

        postInvalidateOnAnimation()
        } catch (e: Throwable) { crash(e) }
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
        drawPanelBtn(
            canvas, tVillage,
            if (startAtVillage) "DEPART : LE VILLAGE (test)" else "DEPART : LE DONJON",
            startAtVillage
        )
        drawPanelBtn(canvas, tNew, "NOUVELLE PARTIE", false)
        drawPanelBtn(canvas, tCont, if (hasSave()) "CONTINUER" else "AUCUNE SAUVEGARDE", false, hasSave())
        drawPanelBtn(canvas, tHelp, "COMMENT JOUER ?", false)
        drawPanelBtn(canvas, tSet, "♪", false)

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

        // L'ile : on peint le terrain en couches continues, avant tout le reste
        val islandVisible = cgy + ny >= world.iy0 && cgy - ny <= world.iy0 + 34
        if (islandVisible) drawIslandTerrain(canvas, w, cgx, cgy, nx, ny)

        for (gy in cgy - ny..cgy + ny) {
            for (gx in cgx - nx..cgx + nx) {
                if (!world.inside(gx, gy)) continue
                val l = sx(gx.toFloat(), w)
                val t = sy(gy.toFloat())
                if (l > w || t > boardBottom || l + tile < 0f || t + tile < boardTop) continue
                rect.set(l + gap, t + gap, l + tile - gap, t + tile - gap)
                val i = world.idx(gx, gy)

                // --- L'ILE (surface) : terrain deja peint, on ne pose que les objets
                val ter = world.terrain[i]
                if (ter != World.TER_NONE) { drawIslandObjects(canvas, gx, gy, i, ter); continue }

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
                        if (world.isTeleport(gx, gy)) drawTeleport(canvas)
                        if (isExit) drawStar(canvas)
                        if (i in world.hearts) drawHeart(canvas)
                        if (world.secretChests.containsValue(i))
                            drawChest(canvas, true, i in secretLooted, false)
                        if (world.secretTraps.containsKey(i) && i in secretFound)
                            drawSecretHatch(canvas)
                        else if (world.secretTraps.containsKey(i))
                            drawSuspiciousTile(canvas)
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
        drawMobs(canvas, w)
        drawBolts(canvas, w)
        drawStalls(canvas, w)
        drawWalkers(canvas, w)
        drawHero(canvas, w)
        drawIslandLife(canvas, w)
        drawInteriorMask(canvas, w)
    }


    /** La barque au mouillage, qui tangue en attendant le heros. */
    private fun drawMooredBoat(canvas: Canvas, w: Float) {
        if (onBoat || boatCell < 0) return
        val bx = world.cx(boatCell) + 0.5f
        val by = world.cy(boatCell) + 0.5f + sin(time * 1.4f) * 0.05f
        val sxp = sx(bx, w)
        val syp = sy(by)
        if (syp < boardTop - tile || syp > boardBottom + tile) return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = tile * 0.03f
        val ph = (time * 0.5f) % 1f
        paint.color = Color.argb(((1f - ph) * 80).toInt(), 235, 248, 255)
        canvas.drawOval(
            sxp - tile * (0.5f + ph * 0.4f), syp + tile * 0.2f - tile * (0.15f + ph * 0.13f),
            sxp + tile * (0.5f + ph * 0.4f), syp + tile * 0.2f + tile * (0.15f + ph * 0.13f), paint
        )
        paint.style = Paint.Style.FILL
        canvas.save()
        canvas.rotate(sin(time * 0.8f) * 4f, sxp, syp)
        drawSprite(canvas, sBoat, sxp, syp, tile * 1.55f)
        canvas.restore()
    }

    /** Le slip porte-bonheur de Pierre flotte sur la houle, au large de la plage. */
    private fun drawFloatingSlip(canvas: Canvas, w: Float) {
        val sc = world.slipCell
        if (sc < 0 || !metPierre || slipOwned || ticketOwned) return
        val bx = world.cx(sc) + 0.5f
        val by = world.cy(sc) + 0.5f + sin(time * 1.25f) * 0.07f
        val sxp = sx(bx, w)
        val syp = sy(by)
        if (syp < boardTop - tile || syp > boardBottom + tile) return
        // rides concentriques autour du slip
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = tile * 0.035f
        val ph = (time * 0.55f) % 1f
        for (k in 0..1) {
            val pk = (ph + k * 0.5f) % 1f
            paint.color = Color.argb(((1f - pk) * 90).toInt(), 235, 248, 255)
            canvas.drawOval(
                sxp - tile * (0.35f + pk * 0.5f), syp + tile * 0.18f - tile * (0.12f + pk * 0.17f),
                sxp + tile * (0.35f + pk * 0.5f), syp + tile * 0.18f + tile * (0.12f + pk * 0.17f), paint
            )
        }
        paint.style = Paint.Style.FILL
        // le slip, qui tangue doucement
        canvas.save()
        canvas.rotate(sin(time * 0.9f) * 8f, sxp, syp)
        drawSprite(canvas, sSlip, sxp, syp, tile * 0.95f, 242)
        canvas.restore()
        // reflet clair sous le tissu
        paint.color = Color.argb(40, 255, 255, 255)
        canvas.drawOval(sxp - tile * 0.3f, syp + tile * 0.3f, sxp + tile * 0.3f, syp + tile * 0.42f, paint)
    }

    /** La ligne, le flotteur et le "CA MORD !" pendant la peche. */
    private fun drawFishing(canvas: Canvas, w: Float) {
        if (!fishing) return
        val hsx = sx(fx, w)
        val hsy = sy(fy) - tile * 0.95f
        val bite = fishBiteT > 0f
        val dip = if (bite) tile * (0.14f + 0.06f * sin(time * 22f)) else tile * 0.03f * sin(time * 2.2f)
        val bxp = sx(fishBobX, w)
        val byp = sy(fishBobY) + dip
        // plouf du lancer : anneau qui s'etend
        if (fishSplashT > 0f) {
            val pr = (0.6f - fishSplashT) / 0.6f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = tile * 0.05f
            paint.color = Color.argb(((1f - pr) * 170).toInt(), 240, 250, 255)
            canvas.drawOval(
                bxp - tile * pr * 0.7f, byp - tile * pr * 0.28f,
                bxp + tile * pr * 0.7f, byp + tile * pr * 0.28f, paint
            )
        }
        // la ligne, du bout de la canne au flotteur, avec un peu de mou
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = tile * 0.028f
        paint.color = Color.argb(200, 245, 248, 252)
        linePath.reset()
        linePath.moveTo(hsx, hsy)
        val sag = if (bite) tile * 0.1f else tile * (0.4f + 0.05f * sin(time * 1.7f))
        linePath.quadTo((hsx + bxp) / 2f, (hsy + byp) / 2f + sag, bxp, byp - tile * 0.1f)
        canvas.drawPath(linePath, paint)
        // rides autour du flotteur
        val ph = (time * 0.7f) % 1f
        paint.color = Color.argb(((1f - ph) * 110).toInt(), 235, 248, 255)
        canvas.drawOval(
            bxp - tile * (0.16f + ph * 0.3f), byp - tile * (0.06f + ph * 0.11f),
            bxp + tile * (0.16f + ph * 0.3f), byp + tile * (0.06f + ph * 0.11f), paint
        )
        // le flotteur rouge et blanc
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(248, 246, 240)
        canvas.drawCircle(bxp, byp, tile * 0.085f, paint)
        paint.color = Color.rgb(225, 45, 40)
        canvas.drawArc(
            bxp - tile * 0.085f, byp - tile * 0.085f,
            bxp + tile * 0.085f, byp + tile * 0.085f, 180f, 180f, true, paint
        )
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = tile * 0.02f
        paint.color = Color.argb(160, 40, 30, 25)
        canvas.drawCircle(bxp, byp, tile * 0.085f, paint)
        paint.style = Paint.Style.FILL
        // CA MORD !
        if (bite) {
            val py2 = byp - tile * (0.55f + 0.07f * sin(time * 14f))
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = true
            paint.textSize = tile * 0.34f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = tile * 0.06f
            paint.color = Color.rgb(60, 30, 8)
            canvas.drawText("CA MORD !", bxp, py2, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(255, 210, 60)
            canvas.drawText("CA MORD !", bxp, py2, paint)
            paint.isFakeBoldText = false
        }
    }

    /** Mouettes qui planent au-dessus de la mer, papillons sur la place. */
    private fun drawIslandLife(canvas: Canvas, w: Float) {
        if (camY < world.iy0 - 6) return
        drawMooredBoat(canvas, w)
        drawFloatingSlip(canvas, w)
        drawFishing(canvas, w)
        drawSpellDemo(canvas, w)
        // Les mouettes
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = tile * 0.06f
        paint.strokeCap = Paint.Cap.ROUND
        for (j in 0..2) {
            val t2 = time * (0.09f + 0.02f * j) + j * 2.1f
            val bx = world.wid / 2f + cos(t2) * (world.wid / 2f - 5f + j * 1.5f)
            val by = world.iy0 + 17f + sin(t2 * 1.35f) * 12.5f
            val gxp = sx(bx, w)
            val gyp = sy(by)
            if (gyp < boardTop || gyp > boardBottom) continue
            val flap = sin(time * 7f + j * 2f) * tile * 0.11f
            // ombre de la mouette sur l'eau, un peu plus bas
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(35, 0, 0, 0)
            canvas.drawOval(gxp - tile * 0.12f, gyp + tile * 0.75f, gxp + tile * 0.12f, gyp + tile * 0.85f, paint)
            paint.style = Paint.Style.STROKE
            paint.color = Color.argb(225, 246, 249, 252)
            canvas.drawLine(gxp - tile * 0.17f, gyp - flap, gxp, gyp + tile * 0.05f, paint)
            canvas.drawLine(gxp, gyp + tile * 0.05f, gxp + tile * 0.17f, gyp - flap, paint)
        }
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
        // Les papillons
        for (j in 0..3) {
            val ax = world.cx(world.islandPortal) - 6.5f + j * 4.4f
            val ay = world.cy(world.islandPortal) + 3f + (j % 2) * 6.5f
            val bx = ax + sin(time * (0.5f + 0.1f * j) + j) * 2.3f
            val by = ay + sin(time * (0.7f + 0.13f * j) * 1.3f + j * 2f) * 1.7f
            val px2 = sx(bx, w)
            val py2 = sy(by)
            if (py2 < boardTop || py2 > boardBottom) continue
            val wing = 0.35f + 0.65f * abs(sin(time * 9f + j * 1.7f))
            paint.color = Color.argb(45, 0, 0, 0)
            canvas.drawOval(px2 - tile * 0.06f, py2 + tile * 0.22f, px2 + tile * 0.06f, py2 + tile * 0.28f, paint)
            val cols = intArrayOf(
                Color.rgb(255, 170, 60), Color.rgb(125, 185, 255),
                Color.rgb(255, 120, 175), Color.rgb(205, 230, 90)
            )
            paint.color = cols[j]
            canvas.drawOval(px2 - tile * 0.1f * wing, py2 - tile * 0.055f, px2, py2 + tile * 0.055f, paint)
            canvas.drawOval(px2, py2 - tile * 0.055f, px2 + tile * 0.1f * wing, py2 + tile * 0.055f, paint)
        }
    }

    /**
     * A l'interieur d'un batiment, tout ce qui depasse de la piece est plonge
     * dans le noir : on ne voit plus les pieces voisines ni l'ile au-dessus.
     * Une lumiere chaude baigne la piece.
     */
    private fun drawInteriorMask(canvas: Canvas, w: Float) {
        if (!world.isInterior(hx, hy)) return
        val n = world.interiorOf(hx, hy)
        val rx0 = 1 + ((n - 1) % 3) * 13
        val ry0 = world.hy0 + 1 + ((n - 1) / 3) * 9
        val l = sx((rx0 - 1).toFloat(), w)
        val r = sx((rx0 + 13).toFloat(), w)
        val t = sy((ry0 - 1).toFloat())
        val b = sy((ry0 + 9).toFloat())
        paint.color = Color.rgb(9, 8, 11)
        if (t > boardTop) canvas.drawRect(0f, boardTop, w, t, paint)
        if (b < boardBottom) canvas.drawRect(0f, b, w, boardBottom, paint)
        if (l > 0f) canvas.drawRect(0f, t, l, b, paint)
        if (r < w) canvas.drawRect(r, t, w, b, paint)
        // Lumiere chaude d'interieur
        paint.color = Color.argb(16, 255, 185, 95)
        canvas.drawRect(l, t, r, b, paint)
        // Vignette douce sur les bords de la piece
        paint.color = Color.argb(60, 15, 10, 8)
        val vw = tile * 0.55f
        canvas.drawRect(l, t, r, t + vw, paint)
        canvas.drawRect(l, b - vw, r, b, paint)
        canvas.drawRect(l, t, l + vw, b, paint)
        canvas.drawRect(r - vw, t, r, b, paint)
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

    /** Une case de l'ile : mer animee, plage, herbe, chemin, maison. */
    /**
     * Le terrain de l'ile, peint en couches successives avec des textures repetees.
     * Chaque couche est posee en "taches" rondes qui se chevauchent : les cotes
     * sont organiques, il n'y a ni couture ni escalier de carres.
     */
    /** Bruit deterministe 0..1 par case. */
    private fun hash01(a: Int, b: Int, c: Int): Float {
        var h = a * 374761393 + b * 668265263 + c * 974634341
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0x7FFFFFFF) % 1000 / 1000f
    }

    /**
     * Le terrain de l'ile : chaque couche est remplie PLEINEMENT (rectangles,
     * texture continue), puis sa frontiere est cassee par des lobes irreguliers
     * places au bruit deterministe. Ni couture, ni carre, ni festons reguliers.
     */
    private fun drawIslandTerrain(canvas: Canvas, w: Float, cgx: Int, cgy: Int, nx: Int, ny: Int) {
        val x0 = cgx - nx
        val x1 = cgx + nx
        val y0 = maxOf(cgy - ny, world.iy0)
        val y1 = minOf(cgy + ny, world.iy0 + 34)
        if (y0 > y1) return

        fun terOf(gx: Int, gy: Int): Int =
            if (!world.inside(gx, gy)) World.TER_WATER else world.terrain[world.idx(gx, gy)]

        // 1. La mer : un seul rectangle continu, motif large qui derive lentement
        useTer(
            sWater, 2.4f, 255,
            sin(time * 0.22f) * tile * 0.8f,
            cos(time * 0.17f) * tile * 0.6f
        )
        canvas.drawRect(
            sx(x0.toFloat(), w), sy(y0.toFloat()),
            sx((x1 + 1).toFloat(), w), sy((y1 + 1).toFloat()), terPaint
        )
        terPaint.shader = null
        val wv = 0.5f + 0.5f * sin(time * 0.7f)
        paint.color = Color.argb((6 + 8 * wv).toInt(), 200, 240, 255)
        canvas.drawRect(
            sx(x0.toFloat(), w), sy(y0.toFloat()),
            sx((x1 + 1).toFloat(), w), sy((y1 + 1).toFloat()), paint
        )

        /**
         * Peint une couche : remplissage plein de chaque case du groupe, puis
         * lobes irreguliers sur les bords exterieurs.
         * inSet : la case appartient-elle a la couche (ou a une couche superieure) ?
         */
        fun layer(inSet: (Int) -> Boolean, lobes: Int, lobeMin: Float, lobeMax: Float, salt: Int) {
            for (gy in y0..y1) for (gx in x0..x1) {
                if (!inSet(terOf(gx, gy))) continue
                // La case pleine (leger debord pour souder les cases entre elles)
                canvas.drawRect(
                    sx(gx.toFloat(), w) - 0.5f, sy(gy.toFloat()) - 0.5f,
                    sx(gx + 1f, w) + 0.5f, sy(gy + 1f) + 0.5f, terPaint
                )
                // Les bords : des lobes qui debordent vers l'exterieur
                for ((k, d) in listOf(Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1)).withIndex()) {
                    if (inSet(terOf(gx + d.first, gy + d.second))) continue
                    for (n in 0 until lobes) {
                        val t = 0.15f + 0.7f * hash01(gx, gy, salt + k * 7 + n * 13)
                        val rr = tile * (lobeMin + (lobeMax - lobeMin) * hash01(gx, gy, salt + 3 + k * 11 + n * 17))
                        val px: Float
                        val py: Float
                        if (d.first != 0) {
                            px = sx(gx + (if (d.first > 0) 1f else 0f), w)
                            py = sy(gy + t)
                        } else {
                            px = sx(gx + t, w)
                            py = sy(gy + (if (d.second > 0) 1f else 0f))
                        }
                        canvas.drawCircle(px, py, rr, terPaint)
                    }
                }
            }
        }

        // 2. Hauts-fonds : lagon turquoise. Toutes les formes sont dessinees
        // OPAQUES dans un calque unique dont l'alpha global est applique a la fin :
        // les chevauchements ne se voient plus, la zone est parfaitement unie.
        val lagoonLayer = canvas.saveLayerAlpha(
            sx(x0.toFloat(), w), sy(y0.toFloat()),
            sx((x1 + 1).toFloat(), w), sy((y1 + 1).toFloat()), 58
        )
        terPaint.shader = null
        terPaint.color = Color.rgb(190, 245, 235)
        layer({ it == World.TER_SHALLOW || it == World.TER_SHORE || it == World.TER_SAND ||
                it == World.TER_GRASS || it == World.TER_DIRT || it == World.TER_EARTH },
            3, 0.10f, 0.24f, 101)
        canvas.restoreToCount(lagoonLayer)
        terPaint.color = Color.BLACK

        // 2b. RELIEF : la silhouette de l'ile, decalee vers le sud-est, projetee
        // sur l'eau. Le sable la recouvre ensuite : seul un lisere d'ombre depasse
        // cote mer -> l'ile semble surelevee au-dessus des flots.
        val isLand = { t: Int ->
            t == World.TER_SAND || t == World.TER_SHORE || t == World.TER_GRASS ||
                    t == World.TER_DIRT || t == World.TER_EARTH
        }
        val reliefLayer = canvas.saveLayerAlpha(
            sx(x0.toFloat(), w), sy(y0.toFloat()),
            sx((x1 + 1).toFloat(), w), sy((y1 + 1).toFloat()), 42
        )
        canvas.save()
        canvas.translate(tile * 0.13f, tile * 0.19f)
        terPaint.shader = null
        terPaint.color = Color.rgb(4, 14, 26)
        layer(isLand, 2, 0.22f, 0.45f, 202)
        canvas.restore()
        canvas.restoreToCount(reliefLayer)
        terPaint.color = Color.BLACK

        // 3. Le sable (tout ce qui n'est pas eau)
        useTer(sSand, 1.4f)
        layer(isLand, 2, 0.22f, 0.45f, 202)
        terPaint.shader = null

        // 4. L'ecume du rivage (bande animee, par-dessus le sable)
        for (gy in y0..y1) for (gx in x0..x1) {
            if (terOf(gx, gy) != World.TER_SHORE) continue
            // seulement sur les bords qui touchent l'eau
            for (d in listOf(Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1))) {
                val t2 = terOf(gx + d.first, gy + d.second)
                if (t2 != World.TER_SHALLOW && t2 != World.TER_WATER) continue
                val foam = 0.5f + 0.5f * sin(time * 1.4f + gx * 0.9f + gy * 0.7f)
                paint.color = Color.argb((15 + 27 * foam).toInt(), 255, 255, 255)
                val px = sx(gx + 0.5f + d.first * (0.38f + 0.1f * foam), w)
                val py = sy(gy + 0.5f + d.second * (0.38f + 0.1f * foam))
                canvas.drawCircle(px, py, tile * (0.16f + 0.1f * foam), paint)
            }
        }

        // 5. L'herbe
        useTer(sGrass, 1.2f)
        layer({ it == World.TER_GRASS || it == World.TER_DIRT || it == World.TER_EARTH },
            2, 0.2f, 0.42f, 303)
        terPaint.shader = null

        // 5b. Nuances : des ombrages doux et quelques touches de lumiere
        for (gy in y0..y1) for (gx in x0..x1) {
            if (terOf(gx, gy) != World.TER_GRASS) continue
            val h1 = hash01(gx, gy, 777)
            if (h1 < 0.16f) {
                paint.color = Color.argb(20, 18, 42, 8)
                canvas.drawCircle(
                    sx(gx + 0.25f + 0.5f * hash01(gx, gy, 778), w),
                    sy(gy + 0.25f + 0.5f * hash01(gx, gy, 779)),
                    tile * (0.7f + 0.5f * h1), paint
                )
            } else if (h1 > 0.90f) {
                paint.color = Color.argb(13, 235, 240, 150)
                canvas.drawCircle(
                    sx(gx + 0.3f + 0.4f * hash01(gx, gy, 780), w),
                    sy(gy + 0.3f + 0.4f * hash01(gx, gy, 781)),
                    tile * 0.8f, paint
                )
            }
        }

        // 6. Les chemins de terre (lobes plus petits : sentier aux bords ronges)
        useTer(sEarth, 1.1f)
        layer({ it == World.TER_DIRT || it == World.TER_EARTH }, 2, 0.14f, 0.3f, 404)
        terPaint.shader = null

        // 7. Des etincelles de soleil scintillent sur l'eau
        for (gy in y0..y1) for (gx in x0..x1) {
            val t2 = terOf(gx, gy)
            if (t2 != World.TER_WATER && t2 != World.TER_SHALLOW) continue
            val k = hash01(gx, gy, 555)
            if (k > 0.30f) continue
            val ph = (time * (0.35f + k) + k * 11f) % 1f
            if (ph < 0.22f) {
                val a2 = sin(ph / 0.22f * 3.1416f).coerceIn(0f, 1f)
                paint.color = Color.argb((160 * a2).toInt(), 255, 255, 255)
                canvas.drawCircle(
                    sx(gx + 0.2f + 0.6f * hash01(gx, gy, 556), w),
                    sy(gy + 0.2f + 0.6f * hash01(gx, gy, 557)),
                    tile * (0.045f + 0.05f * a2), paint
                )
            }
        }

        // 8. Les ombres des nuages derivent lentement sur l'ile
        val clipL = sx(x0.toFloat(), w)
        val clipT = sy(y0.toFloat())
        val clipR = sx((x1 + 1).toFloat(), w)
        val clipB = sy((y1 + 1).toFloat())
        canvas.save()
        canvas.clipRect(clipL, clipT, clipR, clipB)
        for (j in 0..2) {
            val cwx = ((time * 0.45f + j * 19f) % (world.wid + 26f)) - 13f
            val cwy = world.iy0 + 6f + j * 10f + sin(time * 0.07f + j * 2f) * 2.5f
            paint.color = Color.argb(18, 8, 12, 6)
            canvas.drawCircle(sx(cwx, w), sy(cwy), tile * 4.6f, paint)
            canvas.drawCircle(sx(cwx + 2.6f, w), sy(cwy + 1.3f), tile * 3.1f, paint)
            canvas.drawCircle(sx(cwx - 2.2f, w), sy(cwy + 0.8f), tile * 2.6f, paint)
        }
        canvas.restore()
    }

    /** Ce qui se pose SUR le terrain de l'ile : decor, portes, portail. */
    private fun drawIslandObjects(canvas: Canvas, gx: Int, gy: Int, i: Int, ter: Int) {
        // Interieur d'un batiment (parquet)
        if (ter == World.TER_WOOD) {
            val hn = world.interiorOf(gx, gy)
            val fl = world.houseFloor[hn] ?: 1
            useTer(hfloor(fl), 1f)
            canvas.drawRect(
                rect.left - tile * 0.06f, rect.top - tile * 0.06f,
                rect.right + tile * 0.06f, rect.bottom + tile * 0.06f, terPaint
            )
            terPaint.shader = null
            val fx3 = world.fixtures[i]
            if (fx3 != null) drawFixture(canvas, fx3)
            val pn = world.props[i]
            if (pn != null) drawSprite(canvas, prop(pn), rect.centerX(), rect.centerY() - tile * 0.12f, tile * 1.35f)
            if (i == world.shroomCell && !world.shroomTaken && spraysDone >= 3) {
                val bob2 = sin(time * 2.6f) * tile * 0.05f
                val pulse2 = 0.5f + 0.5f * sin(time * 3.4f)
                paint.color = Color.argb((40 + 55 * pulse2).toInt(), 200, 90, 255)
                canvas.drawCircle(rect.centerX(), rect.centerY() + bob2, tile * 0.4f, paint)
                drawSprite(canvas, sShroom, rect.centerX(), rect.centerY() + bob2 - tile * 0.08f, tile * 0.8f)
            }
            if (i == world.sprayCell && !world.sprayTaken) {
                val bob = sin(time * 3f) * tile * 0.05f
                val pulse = 0.5f + 0.5f * sin(time * 4f)
                paint.color = Color.argb((45 + 60 * pulse).toInt(), 255, 80, 200)
                canvas.drawCircle(rect.centerX(), rect.centerY() + bob, tile * 0.42f, paint)
                drawSprite(canvas, sSpray, rect.centerX(), rect.centerY() + bob - tile * 0.1f, tile * 0.85f)
            }
            if (world.houseExit.containsKey(i)) drawHouseDoor(canvas)
            return
        }

        val dec = world.decor[i]
        if (dec != null) {
            val (dt, dn) = dec
            val size = when (dt) {
                0 -> tile * 2.0f
                1 -> tile * 0.9f
                2 -> tile * 1.15f
                else -> tile * 1.55f
            }
            val dyy = when (dt) {
                0 -> -tile * 0.45f
                3 -> -tile * 0.08f
                else -> -tile * 0.06f
            }
            // Ombre portee (soleil au nord-ouest), taille selon l'objet
            val shw = when (dt) { 0 -> 0.52f; 1 -> 0.22f; 2 -> 0.40f; else -> 0.58f } * tile
            val sha = when (dt) { 0 -> 70; 1 -> 42; 2 -> 62; else -> 66 }
            paint.color = Color.argb(sha, 0, 0, 0)
            canvas.drawOval(
                rect.centerX() - shw + tile * 0.12f, rect.centerY() + tile * 0.14f,
                rect.centerX() + shw + tile * 0.12f, rect.centerY() + tile * 0.34f, paint
            )
            if (dt == 0 || dt == 1) {
                // La brise : les arbres oscillent doucement, les plantes davantage
                val ang = sin(time * (if (dt == 0) 0.9f else 1.7f) + gx * 0.7f + gy * 0.9f) *
                        (if (dt == 0) 1.3f else 5f)
                canvas.save()
                canvas.rotate(ang, rect.centerX(), rect.centerY() + tile * 0.34f)
                drawSprite(canvas, decorBmp(dt, dn), rect.centerX(), rect.centerY() + dyy, size)
                canvas.restore()
            } else {
                drawSprite(canvas, decorBmp(dt, dn), rect.centerX(), rect.centerY() + dyy, size)
            }
        }

        // Les distributeurs de 8.6
        val vd = world.vendors[i]
        if (vd != null) {
            paint.color = Color.argb(70, 0, 0, 0)
            canvas.drawOval(
                rect.centerX() - tile * 0.4f, rect.centerY() + tile * 0.28f,
                rect.centerX() + tile * 0.4f, rect.centerY() + tile * 0.46f, paint
            )
            val glow = 0.5f + 0.5f * sin(time * 2.5f + vd)
            paint.color = if (vd == 1) Color.argb((30 + 30 * glow).toInt(), 255, 200, 60)
            else Color.argb((30 + 30 * glow).toInt(), 80, 140, 255)
            canvas.drawCircle(rect.centerX(), rect.centerY() - tile * 0.3f, tile * 0.85f, paint)
            drawSprite(
                canvas, if (vd == 1) sVendorGold else sVendorBlue,
                rect.centerX(), rect.centerY() - tile * 0.55f, tile * 1.75f
            )
        }
        // Les batiments : dessines sur leur case d'ancrage
        val hn = world.houses[i]
        if (hn != null) {
            paint.color = Color.argb(60, 0, 0, 0)
            canvas.drawOval(
                rect.centerX() - tile * 1.5f + tile * 0.15f, rect.centerY() + tile * 0.2f,
                rect.centerX() + tile * 1.5f + tile * 0.15f, rect.centerY() + tile * 0.66f, paint
            )
            paint.color = Color.argb(45, 0, 0, 0)
            canvas.drawOval(
                rect.centerX() - tile * 1.1f + tile * 0.2f, rect.centerY() + tile * 0.3f,
                rect.centerX() + tile * 1.1f + tile * 0.2f, rect.centerY() + tile * 0.58f, paint
            )
            val size2 = when (hn) {
                7 -> tile * 6.2f          // LE GRAND ARBRE, immense
                5 -> tile * 4.3f          // le club, imposant
                else -> tile * 3.6f
            }
            val lift2 = when (hn) {
                7 -> tile * 2.3f
                5 -> tile * 1.35f
                else -> tile * 1.05f
            }
            drawSprite(
                canvas, sHouseNew[(hn - 1) % sHouseNew.size],
                rect.centerX(), rect.centerY() - lift2, size2
            )
        }
        if (world.houseMats.containsKey(i) && world.isIsland(gx, gy)) drawHouseDoor(canvas)
        if (world.isIslandPortal(gx, gy)) drawTeleport(canvas)
        // L'entree secrete du prochain donjon (visible apres le champignon)
        if (i == world.dungeon2Cell && world.dungeon2Revealed) {
            val pulse = 0.5f + 0.5f * sin(time * 2.2f)
            paint.color = Color.argb((50 + 60 * pulse).toInt(), 170, 90, 255)
            canvas.drawCircle(rect.centerX(), rect.centerY(), tile * 0.62f, paint)
            paint.color = Color.rgb(16, 10, 24)
            canvas.drawOval(
                rect.left + tile * 0.12f, rect.top + tile * 0.22f,
                rect.right - tile * 0.12f, rect.bottom - tile * 0.06f, paint
            )
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = tile * 0.07f
            paint.color = Color.rgb(110, 80, 160)
            canvas.drawArc(
                rect.left + tile * 0.1f, rect.top + tile * 0.16f,
                rect.right - tile * 0.1f, rect.bottom, 180f, 180f, false, paint
            )
            paint.style = Paint.Style.FILL
            paint.color = Color.argb((120 + 100 * pulse).toInt(), 200, 140, 255)
            canvas.drawCircle(rect.centerX(), rect.centerY() + tile * 0.1f, tile * 0.06f, paint)
        }
    }

    /** Un objet fixe d'interieur : fenetre, cheminee, scene, tapis. */
    private fun drawFixture(canvas: Canvas, code: Int) {
        when (code) {
            1 -> {   // Fenetre : lumiere du jour qui entre
                val glow = 0.5f + 0.5f * sin(time * 0.8f)
                paint.color = Color.argb((28 + 18 * glow).toInt(), 190, 225, 255)
                canvas.drawCircle(rect.centerX(), rect.centerY() + tile * 0.35f, tile * 1.05f, paint)
                drawSprite(canvas, sWindow, rect.centerX(), rect.centerY(), tile * 1.05f)
            }
            2 -> {   // Cheminee : flamme et halo chaud qui vacillent
                val f = 0.5f + 0.5f * sin(time * 5.5f) * 0.6f + 0.2f * sin(time * 9f)
                paint.color = Color.argb((45 + 40 * f).toInt(), 255, 150, 45)
                canvas.drawCircle(rect.centerX(), rect.centerY() + tile * 0.5f, tile * (1.5f + 0.15f * f), paint)
                paint.color = Color.argb((30 + 30 * f).toInt(), 255, 200, 90)
                canvas.drawCircle(rect.centerX(), rect.centerY() + tile * 0.3f, tile * 0.7f, paint)
                drawSprite(canvas, sFire, rect.centerX(), rect.centerY() + tile * 0.12f, tile * 1.65f)
            }
            3 -> {   // LA SCENE du club : projecteurs qui balaient
                for (k in 0..3) {
                    val a = 0.5f + 0.5f * sin(time * 4f + k * 1.6f)
                    val cols = intArrayOf(
                        Color.argb((40 + 70 * a).toInt(), 255, 60, 60),
                        Color.argb((40 + 70 * a).toInt(), 70, 120, 255),
                        Color.argb((40 + 70 * a).toInt(), 255, 220, 90),
                        Color.argb((40 + 70 * a).toInt(), 200, 70, 255)
                    )
                    paint.color = cols[k]
                    canvas.drawCircle(
                        rect.centerX() + (k - 1.5f) * tile * 0.9f,
                        rect.centerY() + tile * 1.1f,
                        tile * (0.55f + 0.2f * a), paint
                    )
                }
                drawSprite(canvas, sStage, rect.centerX(), rect.centerY() + tile * 0.25f, tile * 4.2f)
            }
            4 -> drawSprite(canvas, sRugRed, rect.centerX(), rect.centerY(), tile * 2.6f)
            5 -> drawSprite(canvas, sRugPunk, rect.centerX(), rect.centerY(), tile * 2.6f)
            6 -> {   // Ampli 8.6 : le haut-parleur vibre au rythme
                val boom = 1f + 0.05f * sin(time * 8.5f)
                paint.color = Color.argb((25 + 25 * (0.5f + 0.5f * sin(time * 8.5f))).toInt(), 255, 170, 60)
                canvas.drawCircle(rect.centerX(), rect.centerY(), tile * 0.8f * boom, paint)
                drawSprite(canvas, sAmp, rect.centerX(), rect.centerY() - tile * 0.2f, tile * 1.5f * boom)
            }
            7 -> drawSprite(canvas, sGuitar, rect.centerX(), rect.centerY() - tile * 0.25f, tile * 1.4f)
            8 -> drawSprite(canvas, sBass, rect.centerX(), rect.centerY() - tile * 0.25f, tile * 1.4f)
            9 -> drawSprite(canvas, sDrums, rect.centerX(), rect.centerY() - tile * 0.15f, tile * 1.7f)
            10 -> drawSprite(canvas, sMic, rect.centerX(), rect.centerY() - tile * 0.3f, tile * 1.5f)
            11 -> {   // Drapeau antifa : ondule doucement, accroche au mur
                val sway = sin(time * 1.6f) * 3f
                canvas.save()
                canvas.rotate(sway, rect.centerX(), rect.centerY() - tile * 0.3f)
                drawSprite(canvas, sAntifa, rect.centerX(), rect.centerY() - tile * 0.2f, tile * 1.7f)
                canvas.restore()
            }
        }
    }

    /** Une porte de maison (entree / sortie). */
    private fun drawHouseDoor(canvas: Canvas) {
        val pulse = 0.5f + 0.5f * sin(time * 2.6f)
        // Halo dore au sol
        paint.color = Color.argb((26 + 34 * pulse).toInt(), 255, 215, 120)
        canvas.drawOval(
            rect.left + tile * 0.12f, rect.top + tile * 0.3f,
            rect.right - tile * 0.12f, rect.bottom - tile * 0.02f, paint
        )
        // Anneau fin
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = tile * 0.035f
        paint.color = Color.argb((90 + 90 * pulse).toInt(), 255, 225, 150)
        canvas.drawOval(
            rect.left + tile * 0.2f, rect.top + tile * 0.42f,
            rect.right - tile * 0.2f, rect.bottom - tile * 0.1f, paint
        )
        paint.style = Paint.Style.FILL
        // Petit chevron d'entree
        paint.strokeWidth = tile * 0.05f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.argb(230, 255, 240, 200)
        val cxx = rect.centerX()
        val cyy = rect.centerY() + tile * 0.1f
        canvas.drawLine(cxx - tile * 0.1f, cyy + tile * 0.06f, cxx, cyy - tile * 0.06f, paint)
        canvas.drawLine(cxx, cyy - tile * 0.06f, cxx + tile * 0.1f, cyy + tile * 0.06f, paint)
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
    }

    /** L'ecran de commerce : onglets acheter/vendre, marchandage, or. */
    private fun drawShop(canvas: Canvas, w: Float, h: Float) {
        drawStoneBg(canvas, w, h, 214)
        shopRowRects.clear()
        val who = if (shopMerchant == 1) "MARCHAND NOMADE" else "JOLIE MARCHANDE"
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(255, 210, 90)
        paint.textSize = h * 0.03f
        canvas.drawText(who, w / 2f, h * 0.075f, paint)
        // bourse
        paint.textSize = h * 0.022f
        paint.color = Color.rgb(255, 225, 120)
        canvas.drawText("Bourse : $gold pieces" + (if (shopDiscount > 0) "   (remise -$shopDiscount%)" else ""),
            w / 2f, h * 0.11f, paint)
        paint.isFakeBoldText = false

        // Onglets acheter / vendre
        val tabW = w * 0.34f
        val tabH = h * 0.05f
        val ty = h * 0.135f
        shopBuyRect.set(w / 2f - tabW - w * 0.01f, ty, w / 2f - w * 0.01f, ty + tabH)
        shopSellRect.set(w / 2f + w * 0.01f, ty, w / 2f + w * 0.01f + tabW, ty + tabH)
        for ((r, lab, idxTab) in listOf(
            Triple(shopBuyRect, "ACHETER", 0), Triple(shopSellRect, "VENDRE", 1)
        )) {
            paint.style = Paint.Style.FILL
            paint.color = if (shopTab == idxTab) Color.rgb(180, 130, 40) else Color.rgb(60, 55, 48)
            canvas.drawRoundRect(r, tabH * 0.3f, tabH * 0.3f, paint)
            paint.color = Color.WHITE
            paint.isFakeBoldText = true
            paint.textSize = h * 0.022f
            canvas.drawText(lab, r.centerX(), r.centerY() + h * 0.008f, paint)
            paint.isFakeBoldText = false
        }

        // Liste des articles
        val items = if (shopTab == 0) catalogue(shopMerchant) else sellable()
        var y = h * 0.21f
        val rh = h * 0.058f
        val rowW = w * 0.86f
        val rx = (w - rowW) / 2f
        if (items.isEmpty()) {
            paint.color = Color.rgb(200, 200, 200)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = h * 0.02f
            canvas.drawText("Tu n'as rien a me vendre, pour l'instant.", w / 2f, y + h * 0.05f, paint)
        }
        for (wr in items) {
            val r = RectF(rx, y, rx + rowW, y + rh)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(230, 40, 38, 32)
            canvas.drawRoundRect(r, rh * 0.2f, rh * 0.2f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = h * 0.002f
            paint.color = Color.rgb(150, 120, 60)
            canvas.drawRoundRect(r, rh * 0.2f, rh * 0.2f, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(235, 230, 220)
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = h * 0.021f
            canvas.drawText(wr.label, rx + w * 0.02f, r.centerY() + h * 0.007f, paint)
            val price = if (shopTab == 0) buyPrice(wr.price) else sellPrice(wr.price)
            paint.textAlign = Paint.Align.RIGHT
            paint.color = if (shopTab == 0 && gold < price) Color.rgb(220, 90, 80) else Color.rgb(255, 215, 100)
            paint.isFakeBoldText = true
            canvas.drawText("$price or", rx + rowW - w * 0.02f, r.centerY() + h * 0.007f, paint)
            paint.isFakeBoldText = false
            shopRowRects.add(r)
            y += rh + h * 0.01f
        }

        // Bouton MARCHANDER + fermer
        val bh = h * 0.055f
        shopHaggleRect.set(w * 0.1f, h * 0.83f, w * 0.55f, h * 0.83f + bh)
        paint.style = Paint.Style.FILL
        paint.color = if (shopHaggleTries >= 3) Color.rgb(70, 60, 50) else Color.rgb(70, 110, 90)
        canvas.drawRoundRect(shopHaggleRect, bh * 0.3f, bh * 0.3f, paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.textSize = h * 0.022f
        canvas.drawText("MARCHANDER (${3 - shopHaggleTries})", shopHaggleRect.centerX(), shopHaggleRect.centerY() + h * 0.008f, paint)

        shopCloseRect.set(w * 0.6f, h * 0.83f, w * 0.9f, h * 0.83f + bh)
        paint.color = Color.rgb(120, 60, 55)
        canvas.drawRoundRect(shopCloseRect, bh * 0.3f, bh * 0.3f, paint)
        paint.color = Color.WHITE
        canvas.drawText("FERMER", shopCloseRect.centerX(), shopCloseRect.centerY() + h * 0.008f, paint)
        paint.isFakeBoldText = false

        // Reparties du marchand
        if (shopMsgT > 0f && shopMsg.isNotEmpty()) {
            paint.color = Color.rgb(255, 235, 180)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = h * 0.019f
            val words = shopMsg.split(" ")
            val lines = ArrayList<String>()
            var cur = ""
            for (wd in words) {
                if ((cur + " " + wd).length > 46) { lines.add(cur); cur = wd } else cur = if (cur.isEmpty()) wd else "$cur $wd"
            }
            if (cur.isNotEmpty()) lines.add(cur)
            var yy = h * 0.9f
            for (ln in lines.take(3)) { canvas.drawText(ln, w / 2f, yy, paint); yy += h * 0.026f }
        }
    }

    /** Villageois et animaux -- (les stands sont dessines dans drawStalls) */
    private fun drawStalls(canvas: Canvas, w: Float) {
        for ((cell, id) in world.stallCells) {
            val cxx = sx(world.cx(cell) + 0.5f, w)
            val cyy = sy(world.cy(cell) + 0.5f)
            if (abs(world.cx(cell) + 0.5f - camX) > 12f || abs(world.cy(cell) + 0.5f - camY) > 12f) continue
            paint.color = Color.argb(70, 0, 0, 0)
            canvas.drawOval(cxx - tile * 1.2f, cyy + tile * 0.35f, cxx + tile * 1.2f, cyy + tile * 0.62f, paint)
            drawSprite(canvas, sStall[(id - 1) % 2], cxx, cyy - tile * 0.65f, tile * 3.1f)
        }
    }

    /** Villageois et animaux. */
    private fun drawWalkers(canvas: Canvas, w: Float) {
        for (wk in walkers) {
            if (abs(wk.x - camX) > 12f || abs(wk.y - camY) > 12f) continue
            val cxx = sx(wk.x, w)
            val cyy = sy(wk.y)
            val moving = hypot(wk.tx - wk.x, wk.ty - wk.y) > 0.06f
            val bob = if (moving) abs(sin(time * 8f + wk.id)) * tile * 0.05f else 0f
            paint.color = Color.argb(80, 0, 0, 0)
            val sw = if (wk.kind == 0) tile * 0.24f else tile * 0.2f
            canvas.drawOval(cxx - sw, cyy + tile * 0.16f, cxx + sw, cyy + tile * 0.3f, paint)
            val set = when (wk.kind) {
                0 -> sNpc[(wk.id - 1) % 10]
                2 -> sPunk[(wk.id - 1) % 7]
                3 -> sFisher[(wk.id - 1) % 2]
                4 -> sGld[(wk.id - 1) % 10]
                5 -> sMage
                6 -> sTav[(wk.id - 1) % 7]
                7 -> sMerc[(wk.id - 1) % 2]
                else -> sPet[(wk.id - 1) % 10]
            }
            val size = if (wk.kind == 1) tile * 1.25f else tile * 1.9f
            val lift = if (wk.kind == 1) tile * 0.14f else tile * 0.42f
            drawSprite(canvas, set[wk.dir.coerceIn(0, 3)], cxx, cyy - lift - bob, size)
            // Petite bulle d'humeur au-dessus des personnages a caractere
            if (wk.kind != 1 && wk.kind != 3) {
                val pIdx = persoIdxFor(wk)
                val p = villagers.getOrNull(pIdx)
                if (p != null) {
                    val hum = try { VillagerAI.humeurDe(p) } catch (t: Throwable) { 0f }
                    if (abs(hum) > 0.45f && abs(wk.x - camX) < 9f && abs(wk.y - camY) < 9f) {
                        drawMoodIcon(canvas, cxx + tile * 0.45f, cyy - lift - bob - tile * 0.75f, hum)
                    }
                }
            }
        }
    }

    /** Une pastille d'humeur : soleil (content) ou nuage grognon. */
    private fun drawMoodIcon(canvas: Canvas, cx: Float, cy: Float, hum: Float) {
        val r = tile * 0.16f
        val bob = sin(time * 3f) * tile * 0.03f
        val yy = cy + bob
        paint.style = Paint.Style.FILL
        if (hum > 0f) {
            // soleil jaune
            paint.color = Color.rgb(255, 210, 70)
            canvas.drawCircle(cx, yy, r, paint)
            paint.strokeWidth = tile * 0.025f
            paint.style = Paint.Style.STROKE
            for (k in 0 until 8) {
                val a = k * (Math.PI / 4).toFloat() + time
                canvas.drawLine(
                    cx + cos(a) * r * 1.3f, yy + sin(a) * r * 1.3f,
                    cx + cos(a) * r * 1.7f, yy + sin(a) * r * 1.7f, paint
                )
            }
            paint.style = Paint.Style.FILL
            // sourire
            paint.color = Color.rgb(120, 80, 20)
            canvas.drawArc(cx - r * 0.5f, yy - r * 0.3f, cx + r * 0.5f, yy + r * 0.5f, 20f, 140f, false, paint)
        } else {
            // nuage gris + eclair d'humeur
            paint.color = Color.rgb(120, 125, 135)
            canvas.drawCircle(cx - r * 0.4f, yy, r * 0.7f, paint)
            canvas.drawCircle(cx + r * 0.4f, yy, r * 0.7f, paint)
            canvas.drawCircle(cx, yy - r * 0.4f, r * 0.75f, paint)
            paint.color = Color.rgb(90, 95, 105)
            canvas.drawRect(cx - r, yy, cx + r, yy + r * 0.4f, paint)
            paint.color = Color.rgb(255, 220, 60)
            val p2 = Path()
            p2.moveTo(cx - r * 0.15f, yy + r * 0.3f)
            p2.lineTo(cx + r * 0.2f, yy + r * 0.3f)
            p2.lineTo(cx - r * 0.05f, yy + r * 0.95f)
            p2.close()
            canvas.drawPath(p2, paint)
        }
        paint.style = Paint.Style.FILL
    }

    /** La bulle de dialogue. */
    private fun drawDialogue(canvas: Canvas, w: Float, h: Float) {
        if (dialogueT <= 0f) return
        val anchorX = sx(dialogueX, w)
        val cyy = sy(dialogueY) - tile * 0.95f
        paint.textSize = h * 0.019f
        paint.isFakeBoldText = false
        // Decoupage en lignes : les histoires du distributeur sont longues !
        val maxW = w * 0.72f
        val lines = ArrayList<String>()
        var cur = ""
        for (word in dialogue.split(" ")) {
            val t = if (cur.isEmpty()) word else "$cur $word"
            if (paint.measureText(t) > maxW && cur.isNotEmpty()) {
                lines.add(cur)
                cur = word
            } else cur = t
        }
        if (cur.isNotEmpty()) lines.add(cur)
        val hasName = dialogueName.isNotEmpty()
        val lineH = h * 0.0255f
        var tw = 0f
        for (l in lines) tw = maxOf(tw, paint.measureText(l))
        if (hasName) {
            paint.textSize = h * 0.0145f
            tw = maxOf(tw, paint.measureText(dialogueName))
            paint.textSize = h * 0.019f
        }
        tw += h * 0.034f
        val headH = if (hasName) h * 0.03f else h * 0.008f
        val th = lineH * lines.size + headH + h * 0.016f
        val cxx = anchorX.coerceIn(tw / 2f + w * 0.012f, w - tw / 2f - w * 0.012f)
        tmpRect.set(cxx - tw / 2f, cyy - th, cxx + tw / 2f, cyy)
        paint.color = Color.argb(242, 250, 246, 235)
        canvas.drawRoundRect(tmpRect, h * 0.014f, h * 0.014f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = h * 0.003f
        paint.color = Color.rgb(120, 90, 50)
        canvas.drawRoundRect(tmpRect, h * 0.014f, h * 0.014f, paint)
        paint.style = Paint.Style.FILL
        val p = Path()
        p.moveTo(anchorX - h * 0.012f, cyy - h * 0.001f)
        p.lineTo(anchorX + h * 0.012f, cyy - h * 0.001f)
        p.lineTo(anchorX, cyy + h * 0.014f)
        p.close()
        paint.color = Color.argb(242, 250, 246, 235)
        canvas.drawPath(p, paint)
        paint.textAlign = Paint.Align.CENTER
        var ty = cyy - th + headH + lineH * 0.62f
        if (hasName) {
            paint.color = Color.rgb(150, 100, 40)
            paint.isFakeBoldText = true
            paint.textSize = h * 0.0145f
            canvas.drawText(dialogueName, cxx, cyy - th + h * 0.021f, paint)
            paint.isFakeBoldText = false
            paint.textSize = h * 0.019f
        }
        paint.color = Color.rgb(40, 34, 28)
        for (l in lines) {
            canvas.drawText(l, cxx, ty, paint)
            ty += lineH
        }
    }

    private fun drawWall(canvas: Canvas, gx: Int, gy: Int) {
        val under = gy >= world.uy0
        // Un mur de maison : la texture de SON batiment
        var roomN = 0
        for (d in listOf(Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1))) {
            val n = world.interiorOf(gx + d.first, gy + d.second)
            if (n > 0) { roomN = n; break }
        }
        tmpRect.set(rect.left - tile * 0.045f, rect.top - tile * 0.045f, rect.right + tile * 0.045f, rect.bottom + tile * 0.045f)
        if (roomN > 0) {
            useTer(sWallsIn[(roomN - 1).coerceIn(0, 4)], 1.5f)
            canvas.drawRect(tmpRect, terPaint)
            terPaint.shader = null
            paint.color = Color.argb(45, 10, 6, 4)
            canvas.drawRect(tmpRect, paint)
            // Un objet accroche au mur (fenetre)
            val fx2 = world.fixtures[world.idx(gx, gy)]
            if (fx2 != null) drawFixture(canvas, fx2)
            return
        }
        drawTex(canvas, if (under) sWallMossy else sWall, tmpRect)
        paint.color = Color.argb(if (under) 95 else 75, 0, 0, 0)
        canvas.drawRect(tmpRect, paint)
        // Graffiti bombe sur le mur
        val tg = world.tags[world.idx(gx, gy)]
        if (tg != null) drawSprite(canvas, tagBmp(tg), rect.centerX(), rect.centerY(), tile * 0.85f, 235)

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

    /** Le bouton SORT et, s'il est ouvert, le selecteur d'element. */
    private fun drawSpellButton(canvas: Canvas) {
        val e = if (spellSel in 0..3 && spellKnown[spellSel]) spellSel
                else (0..3).firstOrNull { spellKnown[it] } ?: 0
        paint.style = Paint.Style.FILL
        paint.color = if (spellCd > 0f) Color.rgb(50, 55, 70) else ELEM_COLORS[e]
        canvas.drawRoundRect(btnSpell, btnSpell.height() * 0.28f, btnSpell.height() * 0.28f, paint)
        // petite etincelle centrale
        paint.color = Color.argb(220, 255, 255, 255)
        canvas.drawCircle(btnSpell.centerX(), btnSpell.centerY(), btnSpell.height() * 0.16f, paint)
        paint.color = ELEM_COLORS[e]
        canvas.drawCircle(btnSpell.centerX(), btnSpell.centerY(), btnSpell.height() * 0.09f, paint)
        // jauge de recharge
        if (spellCd > 0f) {
            paint.color = Color.argb(120, 0, 0, 0)
            val cdMax = when (e) { 0 -> 0.5f; 1 -> 0.65f; 2 -> 0.9f; else -> 1.1f }
            canvas.drawRect(
                btnSpell.left, btnSpell.top,
                btnSpell.right, btnSpell.top + btnSpell.height() * (spellCd / cdMax), paint
            )
        }

        spellPickRects.fill(null)
        if (!spellPicker) return
        // Le selecteur : les elements appris, empiles au-dessus du bouton
        val known = (0..3).filter { spellKnown[it] }
        val sz = btnSpell.height() * 1.05f
        val gap = btnSpell.height() * 0.2f
        var yb = btnSpell.top - gap - sz
        for (el in known.reversed()) {
            val r = RectF(btnSpell.centerX() - sz / 2f, yb, btnSpell.centerX() + sz / 2f, yb + sz)
            paint.style = Paint.Style.FILL
            paint.color = ELEM_COLORS[el]
            canvas.drawRoundRect(r, sz * 0.22f, sz * 0.22f, paint)
            paint.color = Color.argb(70, 0, 0, 0)
            canvas.drawRoundRect(r, sz * 0.22f, sz * 0.22f, paint)
            drawSprite(canvas, spellBmp(el, 0, 3), r.centerX(), r.centerY(), sz * 0.92f)
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = true
            paint.textSize = sz * 0.2f
            canvas.drawText(ELEM_NAMES[el], r.centerX(), r.bottom - sz * 0.06f, paint)
            paint.isFakeBoldText = false
            spellPickRects[el] = r
            yb -= sz + gap * 0.6f
        }
    }

    /** Les projectiles de sort : la volute animee file vers sa cible. */
    private fun drawBolts(canvas: Canvas, w: Float) {
        for (b in bolts) {
            val cxx = sx(b.x, w)
            val cyy = sy(b.y) - tile * 0.35f
            val f = if (b.hit) 4 else (2 + (sin(b.t * 30f) * 1.4f).toInt()).coerceIn(1, 4)
            val bmp = spellBmp(b.elem, b.dir, f)
            val sz = if (b.hit) tile * 2.2f else tile * (1.5f + 0.2f * sin(b.t * 18f))
            val alpha = if (b.hit) (b.t.let { (1f - it / 0.28f) * 255 }).toInt().coerceIn(0, 255) else 255
            drawSprite(canvas, bmp, cxx, cyy, sz, alpha)
        }
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
            val grow = if (m.spawnT > 0f) 0.4f + 0.6f * (1f - m.spawnT) else 1f
            if (m.spawnT > 0f) {
                paint.color = Color.argb((160 * m.spawnT).toInt(), 190, 70, 220)
                canvas.drawCircle(cxx, cyy - tile * 0.1f, tile * (0.3f + m.spawnT * 0.6f), paint)
            }
            drawSprite(canvas, sMonsters[m.sprite], cxx, cyy - tile * 0.14f * m.scale - bounce, tile * 1.15f * m.scale * grow, alpha)
            if (m.hitT > 0f) {
                paint.color = Color.argb((150 * (m.hitT / 0.28f)).toInt(), 255, 70, 60)
                canvas.drawCircle(cxx, cyy - tile * 0.15f, tile * 0.5f, paint)
            }
            // Barre de vie
            val bw = tile * 0.6f * m.scale
            paint.color = Color.argb(200, 25, 25, 30)
            canvas.drawRect(cxx - bw, cyy - tile * 0.78f, cxx + bw, cyy - tile * 0.68f, paint)
            paint.color = if (m.boss) Color.rgb(255, 140, 40) else Color.rgb(220, 60, 55)
            canvas.drawRect(cxx - bw, cyy - tile * 0.78f, cxx - bw + 2 * bw * (m.hp.toFloat() / m.maxHp), cyy - tile * 0.68f, paint)
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

    /** Portail de teleportation : apparait au centre apres le boss. */
    private fun drawTeleport(canvas: Canvas) {
        val cxx = rect.centerX()
        val cyy = rect.centerY()
        for (k in 0..2) {
            val r = tile * (0.45f - k * 0.11f) * (0.85f + 0.15f * sin(time * 3f + k))
            paint.color = Color.argb(70 + k * 45, 120 + k * 40, 90 + k * 50, 255)
            canvas.drawCircle(cxx, cyy, r, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = tile * 0.05f
        for (k in 0..1) {
            val a = time * (60f + k * 40f) % 360f
            canvas.save()
            canvas.rotate(a, cxx, cyy)
            paint.color = Color.argb(200, 190, 160, 255)
            tmpRect.set(cxx - tile * 0.33f, cyy - tile * 0.33f, cxx + tile * 0.33f, cyy + tile * 0.33f)
            canvas.drawArc(tmpRect, 0f, 90f, false, paint)
            canvas.drawArc(tmpRect, 180f, 90f, false, paint)
            canvas.restore()
        }
        paint.style = Paint.Style.FILL
        for (k in 0..5) {
            val a = time * 2.2f + k * 1.05f
            val rr = tile * (0.12f + 0.22f * ((time * 0.7f + k * 0.17f) % 1f))
            paint.color = Color.argb(180, 220, 200, 255)
            canvas.drawCircle(cxx + cos(a) * rr, cyy + sin(a) * rr, tile * 0.03f, paint)
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

    /** Un indice DISCRET sur une dalle piegee : 4 rivets et une fissure. */
    private fun drawSuspiciousTile(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(70, 40, 35, 28)
        val o = tile * 0.32f
        for (dx in intArrayOf(-1, 1)) for (dy in intArrayOf(-1, 1)) {
            canvas.drawCircle(rect.centerX() + dx * o, rect.centerY() + dy * o, tile * 0.035f, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = tile * 0.02f
        paint.color = Color.argb(55, 30, 25, 20)
        canvas.drawLine(rect.centerX() - o * 0.6f, rect.top + tile * 0.3f,
                        rect.centerX() + o * 0.4f, rect.bottom - tile * 0.3f, paint)
        paint.style = Paint.Style.FILL
    }

    /** La trappe cachee, une fois revelee : un trou beant borde de bois. */
    private fun drawSecretHatch(canvas: Canvas) {
        val rad = tile * 0.12f
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(20, 14, 10)
        canvas.drawRoundRect(rect, rad, rad, paint)
        paint.color = Color.rgb(84, 60, 38)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = tile * 0.08f
        canvas.drawRoundRect(rect, rad, rad, paint)
        // battant ouvert
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(110, 82, 52)
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + tile * 0.18f, paint)
        val pulse = 0.5f + 0.5f * sin(time * 4f)
        paint.color = Color.argb((60 + 60 * pulse).toInt(), 240, 200, 90)
        canvas.drawCircle(rect.centerX(), rect.centerY() + tile * 0.05f, tile * 0.06f, paint)
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
        val bob = if (onBoat) sin(time * 1.6f) * tile * 0.03f
                  else if (walking) abs(sin(walkPhase * 0.5f)) * tile * 0.06f else 0f
        val cyy = sy(fy) - tile * 0.12f - bob
        if (onBoat) {
            // Sillage derriere la barque quand on rame
            if (walking) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = tile * 0.035f
                for (k in 0..1) {
                    val ph = (time * 1.1f + k * 0.5f) % 1f
                    paint.color = Color.argb(((1f - ph) * 120).toInt(), 235, 248, 255)
                    canvas.drawOval(
                        cxx - tile * (0.3f + ph * 0.55f), sy(fy) + tile * 0.1f - tile * (0.1f + ph * 0.16f),
                        cxx + tile * (0.3f + ph * 0.55f), sy(fy) + tile * 0.1f + tile * (0.1f + ph * 0.16f), paint
                    )
                }
                paint.style = Paint.Style.FILL
            }
            // La barque, sous le heros
            drawSprite(canvas, sBoat, cxx, sy(fy) + tile * 0.05f - bob, tile * 1.6f)
        } else {
            paint.color = Color.argb(95, 0, 0, 0)
            canvas.drawOval(
                cxx - tile * 0.26f, sy(fy) + tile * 0.22f,
                cxx + tile * 0.26f, sy(fy) + tile * 0.36f, paint
            )
        }
        val bmp = when (heroDir) {
            1 -> sHeroUp
            2 -> sHeroLeft
            3 -> sHeroRight
            else -> sHeroDown
        }
        drawSprite(canvas, bmp, cxx, cyy - tile * 0.08f, tile * 1.5f)
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
            guideWkIdx >= 0 -> "Suivez votre guide, il connait le chemin !"
            onBoat -> "En mer ! Touchez l'eau pour ramer. L'ile lointaine est au sud..."
            !world.isInterior(hx, hy) && world.isIsland(hx, hy) && hy >= world.iy0 + 38 ->
                "L'ILE LOINTAINE. Le Grand Arbre veille... et il a une porte."
            world.isInterior(hx, hy) && world.interiorOf(hx, hy) == 5 -> "LE CONCERT ! Le slip a Pierre resonne !"
            world.isInterior(hx, hy) -> "Vous etes a l'interieur. La porte du bas pour sortir."
            metPierre && !rodOwned -> "Quete : demander la canne a Franki (plage sud-ouest)."
            rodOwned && !slipOwned && !ticketOwned -> "Quete : pecher le SLIP qui flotte au large (touchez l'eau) !"
            slipOwned -> "Quete : rapporter le slip a Pierre !"
            world.isIsland(hx, hy) && world.inVillage(hx, hy) && vQuest.count { it == 1 } > 0 ->
                "Quetes du village : ${vQuest.count { it == 1 }} en cours. Reparlez aux villageois !"
            world.isIsland(hx, hy) && world.inVillage(hx, hy) -> "Le village : parlez aux habitants, ils ont tous besoin de vous !"
            world.isIsland(hx, hy) -> "L'ile ! Explorez la plage, les bois et la place au sud."
            world.bossDefeated -> "Objectif : le PORTAIL au centre de la salle des couleurs !"
            world.wave in 1..3 -> "BOSS : vague ${world.wave} / 3 - battez-vous !"
            world.lightsSolved -> "Objectif : entrer dans la salle du BOSS."
            world.door3Open -> "Objectif : suivre le couloir jusqu'a la porte a runes."
            world.mobsSpawned && !world.mobsDead -> "Objectif : battre les 2 monstres (bouton epee) !"
            world.sokoban2Spawned -> "Objectif : ranger les 7 caisses ($c2/7). Attention a l'ordre !"
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
        if (anySpell()) drawSpellButton(canvas)
        if (inSokobanRoom()) drawBtn(canvas, btnReset, "↺", false)
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
        drawPanelBtn(canvas, mSet, "REGLAGES / MUSIQUE", false)
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
            arrayOf(sCoin, "Pieces d'or", "$gold", Color.rgb(255, 205, 70)),
            arrayOf(null, "Drapeaux", "$flagsLeft", Color.rgb(230, 55, 50)),
            arrayOf(sKey, "Cle en or", if (world.hasKey) "1" else "0", Color.rgb(255, 216, 92)),
            arrayOf(sSwordV, "Epee", if (swordOwned) "1 (combat a venir)" else "0", Color.rgb(180, 195, 220)),
            arrayOf(sLighter, "Briquet", if (lighterOwned) "1" else "0", Color.rgb(200, 210, 225)),
            arrayOf(sSpray, "Bombe Rebel Ink", if (sprayOwned) "taguez les murs !" else "0", Color.rgb(240, 90, 200)),
            arrayOf(sShroom, "Champignon de Kaos", if (shroomCount > 0) "$shroomCount - touchez pour gouter" else "0", Color.rgb(200, 90, 255)),
            arrayOf(sRod, "Canne de Franki", if (rodOwned) "touchez la mer pour pecher" else "0", Color.rgb(120, 190, 240)),
            arrayOf(sSlip, "Slip de Pierre", if (slipOwned) "rapportez-le a Pierre !" else "0", Color.rgb(240, 230, 210)),
            arrayOf(sEnergy, "Canette de 8.6", if (energyCount > 0) "$energyCount - touchez pour boire" else "0", Color.rgb(90, 160, 240)),
            arrayOf(null, "Poisson frais", if (fishCount > 0) "$fishCount - touchez pour manger" else "0", Color.rgb(110, 220, 190)),
            arrayOf(null, "Bottes / Algues (peche)", "$bootCount / $algaeCount", Color.rgb(180, 150, 110)),
            arrayOf(null, "Coeurs ramasses", "$heartsGot", Color.rgb(230, 60, 80)),
            arrayOf(null, "Mines desamorcees", "$disarmed", Color.rgb(90, 200, 130)),
            arrayOf(null, "Points de vie", if (godMode) "illimites" else "$hp / 100", Color.rgb(215, 90, 85)),
            arrayOf(null, "Joystick", joyLabel, Color.rgb(120, 190, 240))
        )
        var y = h * 0.135f
        val bw = w * 0.84f
        val bx = (w - bw) / 2f
        val rh = h * 0.046f
        for (r in rows) {
            val icon = r[0] as Bitmap?
            val label = r[1] as String
            val value = r[2] as String
            val col = r[3] as Int
            tmpRect.set(bx, y, bx + bw, y + rh)
            if (label == "Joystick") invJoyRect.set(tmpRect)
            if (label == "Canette de 8.6") invEnergyRect.set(tmpRect)
            if (label == "Poisson frais") invFishRect.set(tmpRect)
            if (label == "Champignon de Kaos") invShroomRect.set(tmpRect)
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
            y += rh + h * 0.007f
        }
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.rgb(150, 160, 185)
        paint.textSize = h * 0.018f
        canvas.drawText("Touchez ailleurs pour fermer", w / 2f, h * 0.965f, paint)
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
            "• 7 caisses tombent : sokoban 2. PIEGE : l'emplacement tout",
            "  a droite doit etre rempli EN PREMIER, sinon il devient",
            "  inatteignable ! Bouton ↺ pour tout recommencer.",
            "• Resolu -> une porte apparait + 2 MONSTRES.",
            "• Equipez l'epee et utilisez le bouton epee pour frapper !",
            "• Monstres vaincus -> la porte s'ouvre sur un long couloir.",
            "",
            "6) LE COFFRE-FORT : un cadenas a SUDOKU 4x4 (1 a 4 par ligne,",
            "   colonne et bloc 2x2). Resolu -> l'EPEE !",
            "",
            "7) LA PORTE AUX 9 RUNES (bout du couloir)",
            "• Toucher une rune l'inverse ainsi que ses 4 voisines.",
            "• Faites-les toutes briller -> la salle du BOSS s'ouvre.",
            "",
            "8) LA SALLE DU BOSS : 3 vagues de monstres, puis le BOSS.",
            "• Victoire -> la porte scellee s'ouvre et un PORTAIL",
            "  apparait au centre de la salle des couleurs.",
            "",
            "9) L'ILE : le portail vous emmene a la SURFACE.",
            "• Mer, plages, bois, rochers et barques echouees.",
            "• Une place ou vivent les habitants : touchez-les !",
            "• Le portail de l'ile vous ramene au donjon.",
            "• (Les batiments seront ajoutes un par un.)",
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
        val visH = world.hy0                    // on n'affiche pas les interieurs
        val cs = min(mw / world.wid, mh / visH)
        val ox = (w - cs * world.wid) / 2f
        val oy = h * 0.13f
        for (gy in 0 until visH) {
            for (gx in 0 until world.wid) {
                val i = world.idx(gx, gy)
                val g = world.grid[i]
                val ter = world.terrain[i]
                if (ter != World.TER_NONE) {
                    val l2 = ox + gx * cs
                    val t2 = oy + gy * cs
                    paint.color = when (ter) {
                        World.TER_WATER -> Color.rgb(30, 80, 130)
                        World.TER_SHALLOW -> Color.rgb(60, 130, 175)
                        World.TER_SHORE -> Color.rgb(150, 190, 195)
                        World.TER_SAND -> Color.rgb(225, 205, 160)
                        World.TER_DIRT -> Color.rgb(120, 95, 70)
                        World.TER_EARTH -> Color.rgb(160, 130, 95)
                        else -> Color.rgb(85, 140, 55)
                    }
                    if (world.houses.containsKey(i)) paint.color = Color.rgb(200, 80, 60)
                    if (world.isIslandPortal(gx, gy)) paint.color = Color.rgb(190, 150, 255)
                    canvas.drawRect(l2, t2, l2 + cs - 0.6f, t2 + cs - 0.6f, paint)
                    continue
                }
                if (g == World.WALL) continue
                val known = i in world.revealed
                val l = ox + gx * cs
                val t = oy + gy * cs
                paint.color = when {
                    world.isIslandPortal(gx, gy) || (world.isTeleport(gx, gy)) -> Color.rgb(180, 140, 255)
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
        if (hy < visH) {
            paint.color = Color.rgb(255, 220, 60)
            canvas.drawCircle(ox + (hx + 0.5f) * cs, oy + (hy + 0.5f) * cs, cs * 1.3f, paint)
        }

        paint.textAlign = Paint.Align.LEFT
        paint.textSize = h * 0.017f
        var y = oy + cs * visH + h * 0.045f
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

    /** Ecran de reglages : une musique par salle, volume, bruitages. */
    /** Le cadenas du coffre-fort : sudoku 4x4 (chiffres 1 a 4). */
    private fun drawSudoku(canvas: Canvas, w: Float, h: Float) {
        drawStoneBg(canvas, w, h, 214)
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.textSize = h * 0.03f
        paint.color = Color.rgb(255, 205, 90)
        canvas.drawText("CADENAS DU COFFRE-FORT", w / 2f, h * 0.1f, paint)
        paint.isFakeBoldText = false
        paint.color = Color.rgb(190, 175, 140)
        paint.textSize = h * 0.016f
        canvas.drawText("Chaque ligne, chaque colonne et chaque bloc 2x2", w / 2f, h * 0.135f, paint)
        canvas.drawText("doivent contenir 1, 2, 3 et 4.", w / 2f, h * 0.158f, paint)

        val shake = if (sudokuShake > 0f) sin(time * 60f) * w * 0.012f * sudokuShake else 0f
        val gs = min(w * 0.8f, h * 0.4f)
        val cs = gs / 4f
        val gx0 = (w - gs) / 2f + shake
        val gy0 = h * 0.2f

        for (c in 0 until 16) {
            val cxi = c % 4
            val cyi = c / 4
            val r = sudokuCells[c]
            r.set(gx0 + cxi * cs, gy0 + cyi * cs, gx0 + cxi * cs + cs, gy0 + cyi * cs + cs)
            tmpRect.set(r.left + cs * 0.04f, r.top + cs * 0.04f, r.right - cs * 0.04f, r.bottom - cs * 0.04f)
            val given = world.sudokuGiven[c] != 0
            val sel = c == sudokuSel
            paint.color = when {
                given -> Color.rgb(58, 54, 48)
                sel -> Color.rgb(96, 78, 36)
                else -> Color.rgb(38, 40, 52)
            }
            canvas.drawRoundRect(tmpRect, cs * 0.1f, cs * 0.1f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = cs * 0.035f
            paint.color = if (sel) Color.rgb(255, 205, 90) else Color.rgb(120, 100, 60)
            canvas.drawRoundRect(tmpRect, cs * 0.1f, cs * 0.1f, paint)
            paint.style = Paint.Style.FILL
            val v = world.sudokuUser[c]
            if (v != 0) {
                paint.color = if (given) Color.rgb(230, 220, 195) else Color.rgb(120, 200, 240)
                paint.isFakeBoldText = true
                paint.textSize = cs * 0.55f
                canvas.drawText("$v", r.centerX(), r.centerY() + cs * 0.2f, paint)
                paint.isFakeBoldText = false
            }
        }
        // Separations des blocs 2x2
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = gs * 0.014f
        paint.color = Color.rgb(255, 205, 90)
        canvas.drawLine(gx0 + gs / 2f, gy0, gx0 + gs / 2f, gy0 + gs, paint)
        canvas.drawLine(gx0, gy0 + gs / 2f, gx0 + gs, gy0 + gs / 2f, paint)
        canvas.drawRect(gx0, gy0, gx0 + gs, gy0 + gs, paint)
        paint.style = Paint.Style.FILL

        // Pave numerique
        val pw = gs / 5.2f
        val py = gy0 + gs + h * 0.05f
        val px0 = (w - pw * 5f - w * 0.02f * 4f) / 2f
        for (k in 0 until 5) {
            val r = sudokuPad[k]
            r.set(px0 + k * (pw + w * 0.02f), py, px0 + k * (pw + w * 0.02f) + pw, py + pw)
            drawFrame(canvas, r, if (k == 4) Color.rgb(70, 40, 40) else Color.rgb(46, 44, 54),
                if (k == 4) Color.rgb(200, 100, 90) else Color.rgb(168, 136, 72))
            paint.color = Color.rgb(248, 238, 214)
            paint.isFakeBoldText = true
            paint.textSize = pw * 0.45f
            canvas.drawText(if (k == 4) "X" else "${k + 1}", r.centerX(), r.centerY() + pw * 0.16f, paint)
            paint.isFakeBoldText = false
        }
        paint.color = Color.rgb(150, 160, 185)
        paint.textSize = h * 0.017f
        canvas.drawText("Touchez une case, puis un chiffre.  (X = effacer)", w / 2f, py + pw + h * 0.05f, paint)
        canvas.drawText("Touchez tout en bas pour fermer", w / 2f, h * 0.95f, paint)
        drawEmbers(canvas, w, h)
    }

    /** La porte a runes : faire briller les 9 runes (chaque rune bascule ses voisines). */
    private fun drawLights(canvas: Canvas, w: Float, h: Float) {
        drawStoneBg(canvas, w, h, 214)
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.textSize = h * 0.03f
        paint.color = Color.rgb(255, 205, 90)
        canvas.drawText("LA PORTE AUX NEUF RUNES", w / 2f, h * 0.11f, paint)
        paint.isFakeBoldText = false
        paint.color = Color.rgb(190, 175, 140)
        paint.textSize = h * 0.016f
        canvas.drawText("Toucher une rune l'inverse, ainsi que ses 4 voisines.", w / 2f, h * 0.15f, paint)
        canvas.drawText("Faites-les TOUTES briller.", w / 2f, h * 0.175f, paint)

        val gs = min(w * 0.76f, h * 0.4f)
        val cs = gs / 3f
        val gx0 = (w - gs) / 2f
        val gy0 = h * 0.23f
        for (k in 0 until 9) {
            val cxi = k % 3
            val cyi = k / 3
            val r = lightCells[k]
            r.set(gx0 + cxi * cs, gy0 + cyi * cs, gx0 + cxi * cs + cs, gy0 + cyi * cs + cs)
            tmpRect.set(r.left + cs * 0.06f, r.top + cs * 0.06f, r.right - cs * 0.06f, r.bottom - cs * 0.06f)
            val on = world.lights[k]
            if (on) {
                val pulse = 0.5f + 0.5f * sin(time * 3f + k)
                paint.color = Color.argb((60 + 60 * pulse).toInt(), 255, 190, 80)
                canvas.drawCircle(r.centerX(), r.centerY(), cs * 0.52f, paint)
            }
            paint.color = if (on) Color.rgb(126, 88, 30) else Color.rgb(36, 38, 48)
            canvas.drawRoundRect(tmpRect, cs * 0.14f, cs * 0.14f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = cs * 0.05f
            paint.color = if (on) Color.rgb(255, 210, 100) else Color.rgb(90, 92, 104)
            canvas.drawRoundRect(tmpRect, cs * 0.14f, cs * 0.14f, paint)
            // La rune
            paint.strokeWidth = cs * 0.07f
            paint.color = if (on) Color.rgb(255, 240, 190) else Color.rgb(70, 74, 88)
            val cxr = r.centerX()
            val cyr = r.centerY()
            val s2 = cs * 0.22f
            canvas.drawLine(cxr, cyr - s2, cxr, cyr + s2, paint)
            canvas.drawLine(cxr - s2 * 0.8f, cyr - s2 * 0.3f, cxr, cyr - s2, paint)
            canvas.drawLine(cxr + s2 * 0.8f, cyr + s2 * 0.3f, cxr, cyr + s2, paint)
            paint.style = Paint.Style.FILL
        }
        val n = world.lights.count { it }
        paint.color = Color.rgb(255, 225, 140)
        paint.textSize = h * 0.022f
        canvas.drawText("$n / 9 runes allumees", w / 2f, gy0 + gs + h * 0.06f, paint)
        paint.color = Color.rgb(150, 160, 185)
        paint.textSize = h * 0.017f
        canvas.drawText("Touchez tout en bas pour fermer", w / 2f, h * 0.95f, paint)
        drawEmbers(canvas, w, h)
    }

    private fun drawSettings(canvas: Canvas, w: Float, h: Float) {
        drawStoneBg(canvas, w, h, 210)
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.textSize = h * 0.03f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = h * 0.006f
        paint.color = Color.rgb(60, 40, 12)
        canvas.drawText("REGLAGES", w / 2f, h * 0.065f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(255, 205, 90)
        canvas.drawText("REGLAGES", w / 2f, h * 0.065f, paint)
        paint.isFakeBoldText = false
        paint.color = Color.rgb(180, 165, 130)
        paint.textSize = h * 0.015f
        canvas.drawText("Touchez une salle pour changer sa musique", w / 2f, h * 0.093f, paint)

        val bw = w * 0.9f
        val bx = (w - bw) / 2f
        val rh = h * 0.047f
        var y = h * 0.112f

        // Musique ON/OFF + volume
        setMusic.set(bx, y, bx + bw * 0.52f, y + rh)
        drawFrame(canvas, setMusic,
            if (audio.musicOn) Color.rgb(48, 62, 44) else Color.rgb(46, 34, 34),
            if (audio.musicOn) Color.rgb(120, 200, 120) else Color.rgb(180, 90, 80))
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.WHITE
        paint.isFakeBoldText = true
        paint.textSize = rh * 0.34f
        canvas.drawText(if (audio.musicOn) "MUSIQUE : ON" else "MUSIQUE : OFF", setMusic.centerX(), setMusic.centerY() + rh * 0.12f, paint)

        val vw = bw * 0.44f
        setVolDown.set(bx + bw - vw, y, bx + bw - vw + vw * 0.28f, y + rh)
        setVolUp.set(bx + bw - vw * 0.28f, y, bx + bw, y + rh)
        drawFrame(canvas, setVolDown, Color.rgb(40, 38, 48), Color.rgb(150, 122, 66))
        drawFrame(canvas, setVolUp, Color.rgb(40, 38, 48), Color.rgb(150, 122, 66))
        canvas.drawText("−", setVolDown.centerX(), setVolDown.centerY() + rh * 0.14f, paint)
        canvas.drawText("+", setVolUp.centerX(), setVolUp.centerY() + rh * 0.14f, paint)
        paint.color = Color.rgb(255, 210, 100)
        canvas.drawText(
            "VOL ${(audio.musicVol * 100).toInt()}%",
            (setVolDown.right + setVolUp.left) / 2f, setVolDown.centerY() + rh * 0.12f, paint
        )
        y += rh + h * 0.01f

        setSfx.set(bx, y, bx + bw, y + rh)
        drawFrame(canvas, setSfx,
            if (audio.sfxOn) Color.rgb(48, 62, 44) else Color.rgb(46, 34, 34),
            if (audio.sfxOn) Color.rgb(120, 200, 120) else Color.rgb(180, 90, 80))
        paint.color = Color.WHITE
        canvas.drawText(if (audio.sfxOn) "BRUITAGES : ON" else "BRUITAGES : OFF", setSfx.centerX(), setSfx.centerY() + rh * 0.12f, paint)
        paint.isFakeBoldText = false
        y += rh + h * 0.018f

        // Une ligne par salle
        val zone = currentZone()
        for (z in Audio.ZONES.indices) {
            setRows[z].set(bx, y, bx + bw, y + rh)
            val here = z == zone
            drawFrame(canvas, setRows[z],
                if (here) Color.rgb(58, 50, 34) else Color.rgb(34, 32, 42),
                if (here) Color.rgb(255, 205, 90) else Color.rgb(140, 114, 62))
            paint.textAlign = Paint.Align.LEFT
            paint.color = if (here) Color.rgb(255, 225, 150) else Color.rgb(226, 218, 200)
            paint.textSize = rh * 0.32f
            canvas.drawText(Audio.ZONES[z], bx + rh * 0.35f, setRows[z].centerY() + rh * 0.11f, paint)
            paint.textAlign = Paint.Align.RIGHT
            paint.isFakeBoldText = true
            val t = audio.zoneTrack[z]
            paint.color = if (t == Audio.NONE) Color.rgb(140, 140, 150) else Color.rgb(120, 200, 240)
            canvas.drawText(
                if (t == Audio.NONE) "aucune" else "${Audio.TRACK_NAMES[t]}  ▶",
                bx + bw - rh * 0.35f, setRows[z].centerY() + rh * 0.11f, paint
            )
            paint.isFakeBoldText = false
            y += rh + h * 0.008f
        }

        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.rgb(150, 160, 185)
        paint.textSize = h * 0.017f
        canvas.drawText("Touchez en bas de l'ecran pour fermer", w / 2f, h * 0.965f, paint)
        drawEmbers(canvas, w, h)
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
        if (crashLog != null) {
            // toucher l'ecran d'erreur l'efface (pour reessayer)
            if (e.actionMasked == MotionEvent.ACTION_UP) {
                crashLog = null
                prefs.edit().remove("lastcrash").apply()
                state = TITLE
                invalidate()
            }
            return true
        }
        return try {
            handleTouch(e)
        } catch (t: Throwable) {
            crash(t)
            true
        }
    }

    private fun handleTouch(e: MotionEvent): Boolean {
        val am = e.actionMasked
        // La BOUTIQUE capte tout le toucher quand elle est ouverte
        if (showShop) {
            if (am != MotionEvent.ACTION_UP) return true
            if (shopBuyRect.contains(e.x, e.y)) { shopTab = 0; return true }
            if (shopSellRect.contains(e.x, e.y)) { shopTab = 1; return true }
            if (shopHaggleRect.contains(e.x, e.y)) { haggle(); return true }
            if (shopCloseRect.contains(e.x, e.y)) {
                showShop = false
                val bye = if (shopMerchant == 1) "\"Bonne route, l'ami ! Reviens quand tu veux.\""
                          else "\"A bientot, tresor ! Achete bien, vis mieux !\""
                showMsg(bye)
                return true
            }
            val items = if (shopTab == 0) catalogue(shopMerchant) else sellable()
            for (k in items.indices) {
                if (k < shopRowRects.size && shopRowRects[k].contains(e.x, e.y)) {
                    if (shopTab == 0) buyWare(items[k]) else sellWare(items[k])
                    return true
                }
            }
            return true
        }
        // Gestion multi-touch du joystick
        if (joyOn && joyOwned && state == PLAYING && miniPlate < 0 &&
            !showMenu && !showInv && !showHelp && !showMap && !showSettings &&
            !showSudoku && !showLights && !gameOver && !victory && !showShop
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
                if (state != PLAYING || showMenu || showInv || showHelp || showMap || showSettings ||
                    showSudoku || showLights || miniPlate >= 0
                ) return true
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
        if (showSudoku) {
            for (c in 0 until 16) if (sudokuCells[c].contains(e.x, e.y)) { sudokuTap(c); return true }
            for (k in 0 until 5) if (sudokuPad[k].contains(e.x, e.y)) {
                sudokuPut(if (k == 4) 0 else k + 1)
                return true
            }
            if (e.y > height * 0.9f) showSudoku = false
            return true
        }
        if (showLights) {
            for (k in 0 until 9) if (lightCells[k].contains(e.x, e.y)) { lightsTap(k); return true }
            if (e.y > height * 0.9f) showLights = false
            return true
        }
        if (showSettings) {
            when {
                setMusic.contains(e.x, e.y) -> { audio.musicOn = !audio.musicOn; saveAudioPrefs() }
                setSfx.contains(e.x, e.y) -> { audio.sfxOn = !audio.sfxOn; saveAudioPrefs(); audio.play("flag") }
                setVolDown.contains(e.x, e.y) -> { audio.setVolume(audio.musicVol - 0.1f); saveAudioPrefs() }
                setVolUp.contains(e.x, e.y) -> { audio.setVolume(audio.musicVol + 0.1f); saveAudioPrefs() }
                else -> {
                    var hit = false
                    for (z in Audio.ZONES.indices) {
                        if (setRows[z].contains(e.x, e.y)) {
                            hit = true
                            var t = audio.zoneTrack[z] + 1
                            if (t >= Audio.TRACKS.size) t = Audio.NONE
                            audio.zoneTrack[z] = t
                            saveAudioPrefs()
                            audio.play("flag")
                        }
                    }
                    if (!hit) showSettings = false
                }
            }
            return true
        }
        if (showMap) { showMap = false; return true }
        if (showHelp) { showHelp = false; return true }
        if (showInv) {
            if (invJoyRect.contains(e.x, e.y) && joyOwned) {
                joyOn = !joyOn
                saveGame()
                showMsg(if (joyOn) "Joystick active !" else "Joystick desactive.")
            } else if (invEnergyRect.contains(e.x, e.y) && energyCount > 0) {
                energyCount--
                drinksDone++
                hp = (hp + 30).coerceAtMost(100)
                saveGame()
                showMsg("Glouglou ! +30 PV")
            } else if (invFishRect.contains(e.x, e.y) && fishCount > 0) {
                fishCount--
                hp = (hp + 25).coerceAtMost(100)
                audio.play("pickup")
                saveGame()
                showMsg("Miam, grille sur un feu de plage ! +25 PV")
            } else if (invShroomRect.contains(e.x, e.y) && shroomCount > 0) {
                shroomCount--
                tripT = 9f
                showInv = false
                audio.play("simon2")
                if (!world.dungeon2Revealed) {
                    world.dungeon2Revealed = true
                    showMsg("Vos yeux s'ouvrent... une ENTREE SECRETE apparait au nord-ouest !")
                } else {
                    showMsg("Les couleurs dansent... Kaos avait raison.")
                }
                saveGame()
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
                tVillage.contains(e.x, e.y) -> {
                    startAtVillage = !startAtVillage
                    prefs.edit().putBoolean("village", startAtVillage).apply()
                }
                tNew.contains(e.x, e.y) -> newGame()
                tCont.contains(e.x, e.y) -> if (hasSave()) loadGame()
                tHelp.contains(e.x, e.y) -> showHelp = true
                tSet.contains(e.x, e.y) -> showSettings = true
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
                mSet.contains(e.x, e.y) -> { showSettings = true; showMenu = false }
                mSave.contains(e.x, e.y) -> { saveGame(); showMenu = false; showMsg("Partie sauvegardee.") }
                mReset.contains(e.x, e.y) -> {
                    showMenu = false
                    resetCurrentSokoban()
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

        if (actKind != 0 && btnAct.contains(e.x, e.y)) { doInteract(); return true }
        if (btnFlag.contains(e.x, e.y)) {
            flagMode = !flagMode
            showMsg(if (flagMode) "Mode drapeau : touchez une dalle pour la marquer." else "Mode normal.")
            return true
        }
        // Le selecteur d'element ouvert : capte le toucher en priorite
        if (spellPicker) {
            for (el in 0..3) {
                val r = spellPickRects[el]
                if (r != null && r.contains(e.x, e.y)) { castSpell(el); return true }
            }
            spellPicker = false
            if (btnSpell.contains(e.x, e.y)) return true
        }
        if (anySpell() && btnSpell.contains(e.x, e.y)) { onSpellButton(); return true }
        if (swordOwned && btnSword.contains(e.x, e.y)) { doAttack(); return true }
        if (inSokobanRoom() && btnReset.contains(e.x, e.y)) { resetCurrentSokoban(); return true }
        if (btnZoomOut.contains(e.x, e.y)) { tile = (tile * 0.82f).coerceAtLeast(34f); clampCam(); return true }
        if (btnZoomIn.contains(e.x, e.y)) { tile = (tile * 1.22f).coerceAtMost(240f); clampCam(); return true }
        if (btnCenter.contains(e.x, e.y)) { following = true; return true }
        if (btnMenu.contains(e.x, e.y)) { showMenu = true; return true }

        // Les reponses a choix multiples : priorite absolue
        if (dlgChoices.isNotEmpty() && dialogueT > 0f) {
            for (k in dlgChoices.indices) {
                if (k < dlgChoiceRects.size && dlgChoiceRects[k].contains(e.x, e.y)) {
                    audio.play("pickup")
                    onDialogueChoice(k)
                    return true
                }
            }
            // toucher ailleurs : on met fin poliment a la discussion
            dlgChoices = emptyList()
            dialogueT = dialogueT.coerceAtMost(1.2f)
            return true
        }
        // Pendant la peche : toucher le plateau = ferrer (ou remonter la ligne)
        if (fishing && e.y in boardTop..boardBottom) {
            if (fishBiteT > 0f) catchFish() else stopFishing("Vous remontez la ligne. Rien au bout.")
            return true
        }

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
