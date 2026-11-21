package com.cristal.abismo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class GameView(ctx: Context) : View(ctx) {

    private class Spark(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, val max: Float, val spr: Bitmap, val size: Float
    )

    private class Star(var ang: Float, var dist: Float, val z: Float)
    /** Estrella del viaje de la pantalla de carga (coordenadas 3D). */
    private class TStar(var x: Float, var y: Float, var z: Float, var pz: Float, val b: Float)

    /** Objeto cósmico: 0 asteroide, 1 cometa, 2 luna, 3 satélite. */
    private class Cosmic(val kind: Int, var ang: Float, var dist: Float, var z: Float, var rot: Float, var rotSp: Float, var spr: Int, var size: Float)

    /** Explosión en una ciudad: t < 0 indica retraso; size escala todo el efecto. */
    private class Blast(val city: Int, var t: Float, val size: Float)

    /** Explosión en pantalla (línea completada). */
    private class Boom(val x: Float, val y: Float, var t: Float, val size: Float, val plasma: Boolean = false)

    private class Meteor(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Float,
                         val ev: Boolean = false, val size: Float = 1f, var rot: Float = 0f, val spr: Int = 0)
    /** Partícula de efecto: 0 humo, 1 gas incandescente, 2 escape de motor, 3 brasa. */
    private class Puff(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Float, val max: Float,
                       val size: Float, val grow: Float, var rot: Float, val spin: Float, val kind: Int, val tex: Int)

    /** Fragmento de hierro que vuela desde una nave hasta la barra. */
    private class Chip(
        val x0: Float, val y0: Float, val delay: Float, val dur: Float, val amount: Int, val side: Float
    ) { var t = 0f }

    private companion object {
        const val COLS = 17
        const val LIFT_LOCKED = 0.30f
        const val LIFT_PIECE = 0.55f
        const val EXPLOSION_TIME = 4.6f
        const val CELEB = 1.7f
        const val HOLD = 1.7f      // la cámara se queda cerca mientras explota
        const val PULL = 2.5f      // duración del alejamiento
        const val MENU_SC = 1.9f
        const val APPROACH = 1.6f
        /** Tamaño relativo del planeta al empezar la llegada (igual al último cuadro del viaje). */
        const val APPROACH_SC0 = 0.12f
        const val T_GAL = 2.3f
        const val T_THRU = 4.0f
        const val T_SYS = 6.4f
        const val ESC0 = 1.4f
        /** Orden visual de las pestañas (de izquierda a derecha). */
        val TAB_ORDER = intArrayOf(3, 0, 1, 2)
        const val PRICE_REGEN = 150
        const val PRICE_SHIELD = 90
        const val EV_DUR = 26f
        const val LAUNCH = 0.75f
        const val ESC1 = 0.7f
        val BASE = intArrayOf(
            Color.rgb(0, 229, 255), Color.rgb(255, 196, 0), Color.rgb(176, 64, 255),
            Color.rgb(0, 255, 140), Color.rgb(255, 40, 150), Color.rgb(50, 110, 255),
            Color.rgb(255, 110, 20)
        )
        val MOON_TINTS = intArrayOf(Color.rgb(176, 176, 182), Color.rgb(196, 134, 104), Color.rgb(172, 202, 232))
    }

    private val skins = Skins.all
    private val factions = Factions.all
    private val tips = arrayOf(
        "Cada nave aparece junto a la línea central y viaja hacia arriba o hacia abajo, al azar.",
        "Arrastra la nave en cualquiera de las 8 direcciones; siempre encaja en la cuadrícula.",
        "Una nave nunca puede regresar hacia el centro: solo avanzar o moverse a los lados.",
        "Toca la pantalla para girar la nave antes de que se coloque.",
        "Desliza rápido en la dirección en que viaja la nave para lanzarla de golpe.",
        "Completa una fila o columna y las naves que la tocan explotan; las demás se recorren hacia afuera.",
        "Las naves bomba parpadean en rojo: al colocarse destruyen todo lo que tengan cerca.",
        "Las rachas de líneas seguidas dan medallas y las anuncia la voz del comandante.",
        "El hierro de cada nave llena la barra; al completarla ganas lingotes.",
        "La franja roja cruza todo el centro: si una nave se queda ahí, pierdes.",
        "Las alertas de partida (meteoros, eclipses, tormentas) son solo visuales: no afectan el juego.",
        "En Personalizar compras superficies, naves, fondos y música, y diseñas tu emblema.",
        "En Perfil ves tus estadísticas, medallas y logros, y puedes cambiar tu nombre de piloto.",
        "Las naves bomba brillan en azul: estallan como plasma y limpian lo que tengan cerca.",
        "En MEJORAS compras Regeneración (seguir tras perder) y Sobre-escudo (salva la franja roja)."
    )
    private val tip = tips[Random.nextInt(tips.size)]
    private var game: Game? = null
    private var s = 0f
    private var u = 0f
    private var ox = 0f
    private var oy = 0f
    private var ccx = 0f
    private var ccy = 0f
    private var planetR = 0f
    private var planetSc = 1.5f
    private var topInset = 0

    // fases: 0 = menú, 1 = explosión inicial, 2 = partida
    private var phase = 0
    private var phaseT = 0f
    private var tab = 0

    // recursos que se generan en segundo plano
    @Volatile private var ships: Array<Bitmap>? = null
    @Volatile private var rocks: Array<Bitmap>? = null
    @Volatile private var moons: Array<Bitmap>? = null
    private val cache = arrayOfNulls<PlanetData>(skins.size)
    private val thumbs = arrayOfNulls<Bitmap>(skins.size)
    private val skinLoading = BooleanArray(skins.size)
    private var loading = false
    @Volatile private var loaded = false
    @Volatile private var progress = 0f
    private var dispProgress = 0f
    private var glows: Array<Bitmap>? = null
    private var sprWhite: Bitmap? = null
    private var sprFire: Bitmap? = null
    private var sprRed: Bitmap? = null
    private var sprSteel: Bitmap? = null
    private var haze: Bitmap? = null
    private var bgShader: Shader? = null
    private var nebA: Shader? = null
    private var nebB: Shader? = null
    private val atmoShaders = arrayOfNulls<Shader>(skins.size)
    private val edgeShaders = arrayOfNulls<Shader>(4)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val hazePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val objPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val addPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val dark1 = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(0.30f, 0.30f, 0.36f, 1f) })
    }
    private val dark2 = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(0.55f, 0.55f, 0.62f, 1f) })
    }
    private val path = Path()
    private val rect = RectF()
    private val srcRect = android.graphics.Rect()
    private val sparks = ArrayList<Spark>()
    private val stars = ArrayList<Star>()
    private val cosmic = ArrayList<Cosmic>()
    private val meteors = ArrayList<Meteor>()
    private val puffs = ArrayList<Puff>()
    private var frameDt = 0f
    private val dustPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = android.graphics.LightingColorFilter(0xFFB8C4E8.toInt(), 0) }
    private val smokePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = android.graphics.LightingColorFilter(0xFF8C847C.toInt(), 0) }
    private val hotPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD); colorFilter = android.graphics.LightingColorFilter(0xFFFF9A40.toInt(), 0) }
    private val bluePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD); colorFilter = android.graphics.LightingColorFilter(0xFF8FB8FF.toInt(), 0) }
    private val blasts = ArrayList<Blast>()
    private val booms = ArrayList<Boom>()
    private val rowFlash = ArrayList<FloatArray>()
    private var blastTimer = 1.5f
    private var soloT = 0f
    private var sprSmoke: Bitmap? = null
    private val chips = ArrayList<Chip>()
    private var last = 0L
    private var lastMusic = System.nanoTime()
    private val gearC = RectF()
    private val panelRect = RectF()
    private val musicSw = RectF()
    private val sfxSw = RectF()
    private val closeRect = RectF()
    private var settingsOpen = false
    private val fpsRect = Array(3) { RectF() }
    private val gfxRect = Array(2) { RectF() }
    private var framePosted = false
    private var lastFrameNs = 0L
    private val frameCb = object : android.view.Choreographer.FrameCallback {
        override fun doFrame(t: Long) {
            framePosted = false
            val interval = when (fpsMode) { 0 -> 8_000_000L; 1 -> 16_000_000L; else -> 32_500_000L }
            if (t - lastFrameNs >= interval) { lastFrameNs = t; invalidate() } else scheduleFrame()
        }
    }

    /** Pide el siguiente cuadro alineado a la pantalla, respetando el límite de FPS elegido. */
    private fun scheduleFrame() {
        if (framePosted) return
        framePosted = true
        try { android.view.Choreographer.getInstance().postFrameCallback(frameCb) } catch (e: Throwable) { framePosted = false; postInvalidateOnAnimation() }
    }

    /** Pide a la pantalla la frecuencia adecuada (120 Hz si la hay, o 60 Hz para ahorrar). */
    private fun applyRefresh() {
        try {
            val act = context as? android.app.Activity ?: return
            val win = act.window
            val lp = win.attributes
            val target = if (fpsMode == 0) 120f else 60f
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                val d = act.windowManager.defaultDisplay
                val curMode = d.mode
                val modes = d.supportedModes.filter { it.physicalWidth == curMode.physicalWidth && it.physicalHeight == curMode.physicalHeight }
                val best = modes.minByOrNull { kotlin.math.abs(it.refreshRate - target) + (if (it.refreshRate < target - 1f && fpsMode == 0) 1000f else 0f) }
                if (best != null) lp.preferredDisplayModeId = best.modeId
            }
            lp.preferredRefreshRate = target
            win.attributes = lp
        } catch (e: Throwable) { }
    }

    // indicador de batería y hora
    private var battPct = -1
    private var battCharging = false
    private var battReadT = 0f
    private val clockFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    private var settingsAnim = 0f
    private var pauseAnim = 0f
    private var prevTab = 0
    private var tabT = 1f
    private var tabDir = 1f
    private var celebT = 0f
    private var time = 0f
    private var shake = 0f
    private var starSpeed = 0.12f
    private var alert = 0f
    private var flashWhite = 0f

    private val prefs = ctx.getSharedPreferences("abismo", Context.MODE_PRIVATE)
    private val secure = Secure(ctx, prefs).also { it.verify() }
    private val purchases: PurchaseProvider = NoPurchases
    private var puRegen = try { prefs.getInt("pu_regen", 0) } catch (e: Exception) { 0 }
    private var puShield = try { prefs.getInt("pu_shield", 0) } catch (e: Exception) { 0 }
    private val puCard = Array(2) { RectF() }
    private val puBuy = Array(2) { RectF() }
    private val soonRect = RectF()
    private val tabRect3 = RectF()
    private val gameGear = RectF()
    private var settingsFromGame = false
    /** Dificultad: 0 Normal, 1 Heroico, 2 Legendario. */
    private var diff = try { prefs.getInt("diff", 0).coerceIn(0, 2) } catch (e: Exception) { 0 }
    private val diffRect = Array(3) { RectF() }
    private var occl = 0f
    private var occlX = 0f
    private var occlY = 0f
    private val overRegen = RectF()
    private val overMenu = RectF()
    private var overAnim = 0f
    private var reviveHold = 0f
    private var shieldSeen = 0
    private var shieldFx = 0f

    private fun saveWallet() = secure.put("ingots" to totalIngots, "pu_regen" to puRegen, "pu_shield" to puShield)
    /** Cuadros por segundo: 0 = 120, 1 = 60, 2 = ahorro (30). */
    private var fpsMode = try { prefs.getInt("fps", 1).coerceIn(0, 2) } catch (e: Exception) { 1 }
    /** Gráficos: 0 = calidad (texturas más nítidas y todos los efectos), 1 = rendimiento. */
    private var gfxMode = try { prefs.getInt("gfx", 0).coerceIn(0, 1) } catch (e: Exception) { 0 }
    private val gfxAtStart = gfxMode
    private val sfx = Sfx(ctx).also { it.enabled = try { prefs.getBoolean("sfx", true) } catch (e: Exception) { true } }
    private var seenLocks = 0
    private val announcer = Announcer(ctx).also { it.enabled = try { prefs.getBoolean("voice", true) } catch (e: Exception) { true } }
    init { announcer.start() }
    private val music = MusicPlayer(ctx).also { it.enabled = try { prefs.getBoolean("music", true) } catch (e: Exception) { true } }
    private var totalIngots = try { prefs.getInt("ingots", 0) } catch (e: Exception) { 0 }
    private var best = try { prefs.getInt("best", 0) } catch (e: Exception) { 0 }
    private var skinSel = try { prefs.getInt("skin", 0).coerceIn(0, skins.size - 1) } catch (e: Exception) { 0 }
    private var owned = try { prefs.getInt("owned", 1) or 1 } catch (e: Exception) { 1 }
    private var preview = skinSel
    private var shipSel = try { prefs.getInt("ship", 0).coerceIn(0, Factions.all.size - 1) } catch (e: Exception) { 0 }
    private var shipOwned = try { prefs.getInt("shipOwned", 1) or 1 } catch (e: Exception) { 1 }
    private var previewShip = shipSel
    private var subTab = 0
    private val subRects = Array(5) { RectF() }
    private val tabRect2 = RectF()
    private val voiceSw = RectF()
    private val nameRect = RectF()
    private val emRows = Array(5) { RectF() }
    private val stats by lazy { Stats(prefs) }
    private var bgSel = try { prefs.getInt("bg", 0).coerceIn(0, Backgrounds.all.size - 1) } catch (e: Exception) { 0 }
    private var bgOwned = try { prefs.getInt("bgOwned", 1) or 1 } catch (e: Exception) { 1 }
    private var previewBg = bgSel
    private var curBg = -1
    private var themeSel = try { prefs.getInt("theme", 0).coerceIn(0, MusicThemes.all.size - 1) } catch (e: Exception) { 0 }
    private var themeOwned = try { prefs.getInt("themeOwned", 1) or 1 } catch (e: Exception) { 1 }
    private var previewTheme = themeSel
    private var emShape = try { prefs.getInt("em_s", 1) } catch (e: Exception) { 1 }
    private var emSym = try { prefs.getInt("em_y", 0) } catch (e: Exception) { 0 }
    private var emBg = try { prefs.getInt("em_b", 10) } catch (e: Exception) { 10 }
    private var emC1 = try { prefs.getInt("em_c1", 5) } catch (e: Exception) { 5 }
    private var emC2 = try { prefs.getInt("em_c2", 1) } catch (e: Exception) { 1 }
    /** Fila de color cuya cuadrícula de paleta está abierta (2, 3 o 4), o -1. */
    private var palPick = -1
    private var palAnim = 0f
    private val palRect = RectF()
    private var pilot = try { prefs.getString("pilot", "Piloto") ?: "Piloto" } catch (e: Exception) { "Piloto" }
    private var profScroll = 0f
    private var profMax = 0f
    private val voiceQueue = ArrayList<Int>()
    private var voiceT = 0f
    private class MedalPop(val idx: Int, var t: Float)
    private val medalPops = ArrayList<MedalPop>()
    private val achToasts = ArrayList<Ach>()
    private var achT = 0f
    private var evKind = -1
    private var evT = 0f
    private var evTimer = 45f
    private val evFleet = ArrayList<FloatArray>()
    private var evX = 0f
    private var evY = 0f
    private var gameTime = 0f
    @Volatile private var galaxyBmp: Bitmap? = null
    private var clusterPts: FloatArray? = null
    @Volatile private var shipBusy = false
    private val shipThumbs = arrayOfNulls<Bitmap>(Factions.all.size)
    private var flameA: Bitmap? = null
    private var flameB: Bitmap? = null
    private var totalBars = try { prefs.getInt("tbars", 0) } catch (e: Exception) { 0 }
    private var rankStart = 0
    private var rankSaid = 0
    private var carouselOff = 0f
    private var msg = ""
    private var msgT = 0f
    private var newRecord = false
    private var overHandled = false
    private var seenIngots = 0
    private var seenBars = 0
    private var dispFrac = 0f
    private var barFlash = 0f
    private var chipTx = 0f
    private var chipTy = 0f

    // geometría de la interfaz
    private var barX0 = 0f
    private var barX1 = 0f
    private var barY0 = 0f
    private var barY1 = 0f
    private val pauseRect = RectF()
    private val resumeRect = RectF()
    private val escapeRect = RectF()
    private val tabRect0 = RectF()
    private val tabRect1 = RectF()
    private val buyRect = RectF()
    private val menuRect = RectF()
    private var confirmExit = false
    private var gridLines = FloatArray(0)
    private var lkX = IntArray(0)
    private var lkY = IntArray(0)
    private var lkT = IntArray(0)
    private var lkM = IntArray(0)
    private var barShader: Shader? = null
    private var ingotBmp: Bitmap? = null
    private var cardW = 0f
    private var cardH = 0f
    private var cardsTop = 0f
    private var gap = 0f
    private var paused = false

    // entrada
    private var sx = 0f
    private var sy = 0f
    private var lx = 0f
    private var ly = 0f
    private var t0 = 0L
    private var moved = false
    private var downOnPause = false

    fun pause() { if (phase == 2) paused = true }
    fun hostPause() { pause(); music.pause() }
    fun hostResume() { music.resume() }
    fun hostDestroy() { music.release(); sfx.release(); announcer.release() }

    @Suppress("DEPRECATION")
    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        var t = insets.systemWindowInsetTop
        if (Build.VERSION.SDK_INT >= 28) {
            val cut = insets.displayCutout
            if (cut != null) t = max(t, cut.safeInsetTop)
        }
        if (t != topInset) {
            topInset = t
            if (width > 0) layoutGame()
        }
        return super.onApplyWindowInsets(insets)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        layoutGame()
    }

    private fun layoutGame() {
        val w = width
        val h = height
        if (w == 0 || h == 0) return
        s = w / COLS.toFloat()
        u = w / 15f
        val topPad = topInset + u * 2.7f
        val botPad = u * 0.9f
        var rows = ((h - topPad - botPad) / s).toInt()
        if (rows % 2 == 0) rows--
        // Heroico y Legendario: cuadrícula más corta (2 filas menos por lado), centrada igual
        if (diff > 0) rows -= 4
        val g = Game(COLS, rows, diff)
        g.baseSpeed = w * 0.105f / s
        game = g
        ox = 0f
        oy = topPad + (h - topPad - botPad - rows * s) / 2f
        ccx = ox + (COLS / 2 + 0.5f) * s
        ccy = oy + (rows / 2 + 0.5f) * s
        planetR = w * 0.27f
        planetSc = MENU_SC
        confirmExit = false
        phase = if (loaded) 0 else -1; phaseT = 0f; tab = 0
        preview = skinSel
        seenIngots = 0; seenBars = 0; dispFrac = 0f; seenLocks = 0
        rankStart = Ranks.indexFor(totalBars)
        rankSaid = rankStart
        settingsOpen = false
        settingsAnim = 0f; pauseAnim = 0f; tabT = 1f; celebT = 0f; overAnim = 0f; reviveHold = 0f; shieldSeen = 0; shieldFx = 0f
        game?.shields = if (puShield > 0) 1 else 0
        newRecord = false; overHandled = false
        paused = false
        alert = 0f; flashWhite = 0f
        chips.clear()
        blasts.clear()
        booms.clear()
        rowFlash.clear()
        blastTimer = 1.5f

        val top = topInset + u * 0.95f
        barX0 = u * 0.4f
        barX1 = w - u * 0.4f
        barY0 = top + u * 0.35f
        barY1 = barY0 + u * 0.42f
        pauseRect.set(w / 2f - u * 0.7f, top - u * 0.75f, w / 2f + u * 0.7f, top + u * 0.3f)
        gameGear.set(w / 2f + u * 0.75f, top - u * 0.75f, w / 2f + u * 1.85f, top + u * 0.35f)
        resumeRect.set(w / 2f - u * 3f, h / 2f + u * 0.2f, w / 2f + u * 3f, h / 2f + u * 1.6f)
        menuRect.set(w / 2f - u * 3.6f, h / 2f + u * 2.0f, w / 2f + u * 3.6f, h / 2f + u * 3.3f)
        panelRect.set(u * 0.9f, h / 2f - u * 4.8f, w - u * 0.9f, h / 2f + u * 6.0f)
        musicSw.set(panelRect.left + u * 0.6f, panelRect.top + u * 1.9f, panelRect.right - u * 0.6f, panelRect.top + u * 3.0f)
        sfxSw.set(panelRect.left + u * 0.6f, panelRect.top + u * 3.3f, panelRect.right - u * 0.6f, panelRect.top + u * 4.4f)
        voiceSw.set(panelRect.left + u * 0.6f, panelRect.top + u * 4.7f, panelRect.right - u * 0.6f, panelRect.top + u * 5.8f)
        closeRect.set(w / 2f - u * 2.2f, panelRect.bottom - u * 1.6f, w / 2f + u * 2.2f, panelRect.bottom - u * 0.5f)
        run {
            val l = panelRect.left + u * 0.6f; val r = panelRect.right - u * 0.6f
            val fy = panelRect.top + u * 7.0f; val gy = panelRect.top + u * 8.75f
            val g3 = (r - l - u * 0.3f) / 3f
            for (i in 0 until 3) fpsRect[i].set(l + i * (g3 + u * 0.15f), fy, l + i * (g3 + u * 0.15f) + g3, fy + u * 1.0f)
            val g2 = (r - l - u * 0.15f) / 2f
            for (i in 0 until 2) gfxRect[i].set(l + i * (g2 + u * 0.15f), gy, l + i * (g2 + u * 0.15f) + g2, gy + u * 1.0f)
        }
        applyRefresh()
        escapeRect.set(w / 2f - u * 4.4f, h * 0.765f, w / 2f + u * 4.4f, h * 0.765f + u * 2.0f)
        run {
            val bw = u * 2.75f; val gp = u * 0.2f
            val x0 = w / 2f - (3 * bw + 2 * gp) / 2f
            for (i in 0 until 3) diffRect[i].set(x0 + i * (bw + gp), escapeRect.bottom + u * 0.45f, x0 + i * (bw + gp) + bw, escapeRect.bottom + u * 1.3f)
        }
        val tb = topInset + u * 0.9f
        val sw5 = (w - u * 0.8f - 4 * u * 0.12f) / 5f
        for (i in 0 until 5) {
            val x0 = u * 0.4f + i * (sw5 + u * 0.12f)
            subRects[i].set(x0, mtop + u * 4.1f, x0 + sw5, mtop + u * 4.9f)
        }
        // cuatro pestañas: MEJORAS | MENÚ | PERSONALIZAR | PERFIL (más ancha la de PERSONALIZAR)
        val avail = (w - u * 1.65f) - u * 0.4f - 3 * u * 0.12f
        val tws = floatArrayOf(0.23f, 0.21f, 0.32f, 0.24f).map { it * avail }
        tabRect3.set(u * 0.4f, tb, u * 0.4f + tws[0], tb + u * 1.3f)
        tabRect0.set(tabRect3.right + u * 0.12f, tb, tabRect3.right + u * 0.12f + tws[1], tb + u * 1.3f)
        tabRect1.set(tabRect0.right + u * 0.12f, tb, tabRect0.right + u * 0.12f + tws[2], tb + u * 1.3f)
        tabRect2.set(tabRect1.right + u * 0.12f, tb, tabRect1.right + u * 0.12f + tws[3], tb + u * 1.3f)
        run {
            val cw2 = (w - u * 1.2f - u * 0.4f) / 2f
            for (i in 0 until 2) {
                puCard[i].set(u * 0.6f + i * (cw2 + u * 0.4f), mtop + u * 3.2f, u * 0.6f + i * (cw2 + u * 0.4f) + cw2, mtop + u * 10.6f)
                puBuy[i].set(puCard[i].left + u * 0.4f, puCard[i].bottom - u * 1.5f, puCard[i].right - u * 0.4f, puCard[i].bottom - u * 0.4f)
            }
            soonRect.set(u * 0.6f, mtop + u * 11.2f, w - u * 0.6f, mtop + u * 14.6f)
        }
        gearC.set(w - u * 1.35f, tb, w - u * 0.4f, tb + u * 1.3f)
        cardW = u * 3.4f
        cardH = u * 4.3f
        gap = u * 0.3f
        cardsTop = h * 0.735f
        buyRect.set(w / 2f - u * 3.4f, cardsTop + cardH + u * 0.35f, w / 2f + u * 3.4f, cardsTop + cardH + u * 1.45f)
        val emTop = h * 0.56f
        for (i in 0 until 5) emRows[i].set(u * 0.45f, emTop + i * u * 1.5f, w - u * 0.45f, emTop + i * u * 1.5f + u * 1.3f)
        clampCarousel()
        medalPops.clear(); voiceQueue.clear(); achToasts.clear()
        evKind = -1; evTimer = 18f + Random.nextFloat() * 15f; gameTime = 0f

        for (i in skins.indices) {
            val a = skins[i].atmo
            val r = Color.red(a); val gg = Color.green(a); val b = Color.blue(a)
            atmoShaders[i] = RadialGradient(ccx, ccy, planetR * 1.5f,
                intArrayOf(Color.argb(0, r, gg, b), Color.argb(0, r, gg, b), Color.argb(200, min(255, r + 20), min(255, gg + 15), min(255, b)),
                    Color.argb(70, r, gg, b), Color.argb(0, r, gg, b)),
                floatArrayOf(0f, 0.655f, 0.68f, 0.8f, 1f), Shader.TileMode.CLAMP)
        }
        val th = u * 1.1f
        val red = Color.argb(255, 255, 30, 40)
        val none = Color.argb(0, 255, 30, 40)
        edgeShaders[0] = LinearGradient(0f, 0f, 0f, th, red, none, Shader.TileMode.CLAMP)
        edgeShaders[1] = LinearGradient(0f, h.toFloat(), 0f, h - th, red, none, Shader.TileMode.CLAMP)
        edgeShaders[2] = LinearGradient(0f, 0f, th, 0f, red, none, Shader.TileMode.CLAMP)
        edgeShaders[3] = LinearGradient(w.toFloat(), 0f, w - th, 0f, red, none, Shader.TileMode.CLAMP)

        if (glows == null) {
            val f0 = Factions.all[shipSel]
            glows = Array(7) { Textures.makeGlow(f0.accent(BASE[it])) }
            flameA = Textures.makeGlow(f0.flameA)
            flameB = Textures.makeGlow(f0.flameB)
            sprWhite = Textures.makeGlow(Color.rgb(255, 245, 225))
            sprFire = Textures.makeGlow(Color.rgb(255, 150, 50))
            sprRed = Textures.makeGlow(Color.rgb(255, 60, 40))
            sprSmoke = Textures.makeGlow(Color.rgb(58, 50, 46))
            sprSteel = Textures.makeGlow(Color.rgb(170, 200, 255))
        }
        if (haze == null) {
            val hw = 120
            val hh = (hw * h / w.toFloat()).toInt()
            haze = Textures.makeHaze(hw, hh)
        }
        startLoading()

        val gl = ArrayList<Float>()
        for (i in 0..COLS) { gl.add(ox + i * s); gl.add(oy); gl.add(ox + i * s); gl.add(oy + rows * s) }
        for (j in 0..rows) { gl.add(ox); gl.add(oy + j * s); gl.add(ox + COLS * s); gl.add(oy + j * s) }
        gridLines = gl.toFloatArray()
        barShader = LinearGradient(barX0, 0f, barX1, 0f,
            intArrayOf(Color.rgb(90, 110, 140), Color.rgb(170, 190, 215), Color.rgb(255, 235, 150)), null, Shader.TileMode.CLAMP)
        if (ingotBmp == null) ingotBmp = makeIngot()
        meteors.clear()
        applyBg(bgSel)
        sparks.clear()
        last = 0L
    }

    /** Aplica un fondo espacial: degradado, nebulosas, estrellas, asteroides y adornos. */
    private fun applyBg(i: Int) {
        val w = width
        val h = height
        if (w == 0) return
        curBg = i
        val b = Backgrounds.all[i]
        bgShader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(b.top, b.mid, b.bottom), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        nebA = RadialGradient(w * 0.2f, h * 0.22f, w * 0.9f, intArrayOf(b.nebA, Color.argb(0, 0, 0, 0)), null, Shader.TileMode.CLAMP)
        nebB = RadialGradient(w * 0.85f, h * 0.8f, w * 0.9f, intArrayOf(b.nebB, Color.argb(0, 0, 0, 0)), null, Shader.TileMode.CLAMP)
        stars.clear()
        val maxD = hypot(w.toFloat(), h.toFloat()) / 2f
        repeat(b.stars) {
            stars.add(Star(Random.nextFloat() * 6.2832f, planetR + Random.nextFloat() * (maxD - planetR), 0.2f + Random.nextFloat() * 0.8f))
        }
        cosmic.clear()
        for (k in 0..b.rocks) {
            val c = Cosmic(if (k == b.rocks) 1 else 0, 0f, 0f, 0f, 0f, 0f, 0, 0f)
            respawn(c, maxD, true)
            cosmic.add(c)
        }
        if (b.cluster && clusterPts == null) {
            val n = 90
            clusterPts = FloatArray(n * 3) {
                when (it % 3) {
                    0 -> w * 0.28f + (Random.nextFloat() - 0.5f) * w * 0.35f * Random.nextFloat()
                    1 -> h * 0.22f + (Random.nextFloat() - 0.5f) * w * 0.3f * Random.nextFloat()
                    else -> 0.3f + Random.nextFloat() * 0.7f
                }
            }
        }
    }

    private fun respawn(c: Cosmic, maxD: Float, initial: Boolean) {
        c.ang = Random.nextFloat() * 6.2832f
        c.dist = if (initial) planetR * 1.2f + Random.nextFloat() * (maxD - planetR) else maxD * (0.85f + Random.nextFloat() * 0.3f)
        c.z = 0.4f + Random.nextFloat() * 0.6f
        c.rot = Random.nextFloat() * 360f
        c.rotSp = (Random.nextFloat() - 0.5f) * 60f
        c.spr = Random.nextInt(5)
        c.size = when (c.kind) {
            0 -> s * (0.55f + Random.nextFloat() * 1.1f)
            1 -> s * 0.9f
            2 -> s * (1.4f + Random.nextFloat() * 1.1f)
            else -> s * (0.8f + Random.nextFloat() * 0.5f)
        }
    }

    private fun saveAsync(block: () -> Unit) { Thread { try { block() } catch (e: Throwable) { } }.start() }

    private val fullRes = BooleanArray(Skins.all.size)

    // texturas de eventos (con ruido; se generan una vez y se guardan en disco)
    @Volatile private var puffTex: Array<Bitmap>? = null
    @Volatile private var plasmaTex: Bitmap? = null
    @Volatile private var auroraTex: Bitmap? = null
    @Volatile private var swirlTex: Bitmap? = null
    @Volatile private var shadowTex: Bitmap? = null
    @Volatile private var ringTex: Bitmap? = null
    @Volatile private var plateBmp: Bitmap? = null
    @Volatile private var cloudTex: Bitmap? = null
    private val cloudPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    /** Resolución del planeta equipado (más píxeles que en pantalla para el acercamiento), con tope de 2048 px. */
    private fun planetRes() = if (gfxAtStart == 0) min((planetR * 2.7f).toInt(), 1200) else min((planetR * 1.7f).toInt(), 760)
    private fun previewRes() = (planetR * (if (gfxAtStart == 0) 1.1f else 0.85f)).toInt()
    /** Píxeles por celda de las naves: ~2× el tamaño en pantalla, entre 96 y 128. */
    private fun spriteCp() = if (gfxAtStart == 0) ((s * 2.3f).toInt() / 8 * 8).coerceIn(112, 144) else 96

    private fun cachedTex(dir: java.io.File, name: String, make: () -> Bitmap): Bitmap =
        DiskCache.loadNamed(dir, name) ?: make().also { b -> saveAsync { DiskCache.saveNamed(dir, name, b) } }

    /**
     * Carga todo en la pantalla inicial con un grupo de hilos (núcleos − 1): primero el planeta y las naves
     * equipadas; en paralelo rocas, galaxia, texturas de eventos, vistas previas de todas las superficies y
     * vistas de todas las facciones; al final deja en disco las superficies y facciones compradas.
     * Lo ya generado se lee del disco en formato binario (sin decodificar PNG).
     */
    private fun startLoading() {
        if (loading) return
        loading = true
        for (i in skins.indices) skinLoading[i] = true
        val pr = planetRes()
        val prv = previewRes()
        val cp = spriteCp()
        DiskCache.spriteTag = cp
        val thumbR = (u * 1.3f).toInt()
        val plW = (u * 10f).toInt(); val plH = (u * 1.5f).toInt()
        val dir = context.cacheDir
        Thread {
            DiskCache.purgeOld(dir)
            val ownedSkins = skins.indices.filter { it != skinSel && (owned shr it) and 1 == 1 && !skins[it].classified }
            val ownedFac = Factions.all.indices.filter { it != shipSel && (shipOwned shr it) and 1 == 1 && !Factions.all[it].classified }
            val previews = skins.indices.filter { it != skinSel && !skins[it].classified }
            val facs = Factions.all.indices.filter { !Factions.all[it].classified }
            val tasks = ArrayList<Pair<Float, () -> Unit>>()
            val sel = skinSel
            val fs = shipSel
            tasks.add(3f to {
                val raw = DiskCache.planetIsRaw(dir, sel, pr)
                val pd = DiskCache.loadPlanet(dir, sel, pr)
                    ?: Textures.makePlanet(pr, skins[sel]).also { saveAsync { DiskCache.savePlanet(dir, sel, pr, it) } }
                if (!raw && cache[sel] == null) saveAsync { DiskCache.savePlanet(dir, sel, pr, pd) }
                pd.bmp.setHasMipMap(true)
                cache[sel] = pd
                fullRes[sel] = true
                thumbs[sel] = Bitmap.createScaledBitmap(pd.bmp, thumbR * 2, thumbR * 2, true)
            })
            tasks.add(1.5f to { prepareSpinner(dir, sel) })
            tasks.add(3f to {
                ships = DiskCache.loadSprites(dir, fs, 28)
                    ?: Textures.makeShipSprites(BASE, Factions.all[fs], cp).also { t -> saveAsync { DiskCache.saveSprites(dir, fs, t) } }
            })
            tasks.add(1f to {
                rocks = Array(5) { k -> cachedTex(dir, "rock_$k") { Textures.makeRock(96, k * 7 + 1) } }
                if (galaxyBmp == null) galaxyBmp = cachedTex(dir, "galaxy") { Textures.makeGalaxy(256) }
            })
            tasks.add(1f to {
                puffTex = Array(3) { k -> cachedTex(dir, "ev_puff_$k") { Textures.makePuff(128, 3.1f + k * 7.7f) } }
                shadowTex = cachedTex(dir, "ev_shadow") { Textures.makeShadow(256) }
                ringTex = cachedTex(dir, "ev_ring") { Textures.makeRing(512) }
                cloudTex = cachedTex(dir, "clouds") { Textures.makeClouds(512) }
            })
            tasks.add(1f to {
                plasmaTex = cachedTex(dir, "ev_plasma") { Textures.makePlasma(384, Color.rgb(255, 235, 190), Color.rgb(255, 90, 20), 4.4f) }
                swirlTex = cachedTex(dir, "ev_swirl") { Textures.makeSwirl(384) }
            })
            tasks.add(1f to {
                auroraTex = cachedTex(dir, "ev_aurora") { Textures.makeAurora(512, 160) }
                plateBmp = cachedTex(dir, "ev_plate_${plW}x$plH") { Textures.makePlate(plW, plH) }
            })
            for (i in previews) tasks.add(0.6f to {
                val p = DiskCache.loadPlanet(dir, i, prv)
                    ?: Textures.makePlanet(prv, skins[i], lite = true).also { saveAsync { DiskCache.savePlanet(dir, i, prv, it) } }
                p.bmp.setHasMipMap(true)
                if (cache[i] == null) cache[i] = p
                thumbs[i] = Bitmap.createScaledBitmap(p.bmp, thumbR * 2, thumbR * 2, true)
            })
            for (i in facs) tasks.add(0.15f to {
                shipThumbs[i] = cachedTex(dir, "fthumb_$i") { Textures.makeShipSprite(2, 0, Factions.all[i].accent(BASE[2]), Factions.all[i], cp) }
            })
            // superficies y facciones compradas: se dejan listas en disco, una por una al final (menos memoria a la vez)
            val later = ArrayList<Pair<Float, () -> Unit>>()
            for (i in ownedSkins) later.add(1f to {
                if (!DiskCache.hasPlanet(dir, i, pr)) DiskCache.savePlanet(dir, i, pr, Textures.makePlanet(pr, skins[i]), raw = false)
            })
            for (i in ownedFac) later.add(1f to {
                if (!DiskCache.hasSprites(dir, i)) DiskCache.saveSprites(dir, i, Textures.makeShipSprites(BASE, Factions.all[i], cp))
            })
            val total = (tasks.sumOf { it.first.toDouble() } + later.sumOf { it.first.toDouble() }).toFloat()
            var done = 0f
            val lock = Any()
            val nt = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 3)
            val pool = java.util.concurrent.Executors.newFixedThreadPool(nt)
            val futures = tasks.map { (w, job) ->
                pool.submit {
                    try { job() } catch (e: Throwable) { }
                    synchronized(lock) { done += w; progress = min(0.999f, done / total) }
                    postInvalidate()
                }
            }
            for (f in futures) try { f.get() } catch (e: Exception) { }
            pool.shutdown()
            // lo indispensable para jugar se verifica y, si algo falló, se reintenta aquí antes de abrir el menú
            if (cache[sel] == null) try {
                cache[sel] = DiskCache.loadPlanet(dir, sel, pr) ?: Textures.makePlanet(pr, skins[sel]).also { DiskCache.savePlanet(dir, sel, pr, it) }
                fullRes[sel] = true
            } catch (e: Throwable) { try { cache[sel] = Textures.makePlanet(prv, skins[sel], lite = true) } catch (x: Throwable) { } }
            if (ships == null) try {
                ships = Textures.makeShipSprites(BASE, Factions.all[fs], cp).also { t -> DiskCache.saveSprites(dir, fs, t) }
            } catch (e: Throwable) { try { System.gc(); ships = Textures.makeShipSprites(BASE, Factions.all[fs], 96) } catch (x: Throwable) { } }
            for ((w, job) in later) {
                try { job() } catch (e: Throwable) { }
                done += w; progress = min(0.999f, done / total); postInvalidate()
            }
            progress = 1f
            loaded = true
            postInvalidate()
        }.start()
    }

    /** Al equipar una superficie se muestra al instante la vista previa y se cambia a la completa cuando está lista. */
    private fun requestSkin(i: Int) {
        if (skins[i].classified) return
        if (fullRes[i]) { if (spinSkin != i) Thread { try { prepareSpinner(context.cacheDir, i) } catch (e: Throwable) { } }.start(); return }
        fullRes[i] = true
        val pr = planetRes()
        val dir = context.cacheDir
        Thread {
            val raw = DiskCache.planetIsRaw(dir, i, pr)
            val pd = DiskCache.loadPlanet(dir, i, pr)
                ?: Textures.makePlanet(pr, skins[i]).also { saveAsync { DiskCache.savePlanet(dir, i, pr, it) } }
            // la equipada se guarda en binario para que el próximo inicio sea más rápido
            if (!raw) saveAsync { DiskCache.savePlanet(dir, i, pr, pd) }
            pd.bmp.setHasMipMap(true)
            cache[i] = pd
            try { prepareSpinner(dir, i) } catch (e: Throwable) { }
            postInvalidate()
        }.start()
    }

    private fun ready() = ships != null && cache[skinSel] != null

    // ---------- ciclo ----------
    override fun onDraw(canvas: Canvas) {
        val sc = canvas.saveCount
        try { drawFrame(canvas) } catch (e: Throwable) {
            // un fallo de un cuadro no cierra el juego: se descarta y se sigue con el siguiente
            try { canvas.restoreToCount(sc) } catch (x: Throwable) { }
            android.util.Log.e("FugaOrbital", "cuadro", e)
            scheduleFrame()
        }
    }

    private fun drawFrame(canvas: Canvas) {
        val g = game ?: return
        val now = System.nanoTime()
        val dt = if (last == 0L || paused) 0f else min(0.05f, (now - last) / 1e9f)
        last = now
        time += dt
        msgT = max(0f, msgT - dt)
        if (phase == -1) {
            travelT += dt
            if (loaded && travelT >= T_SYS && approachT < 0f) approachT = 0f
            if (approachT >= 0f) approachT += dt
            if (approachT >= APPROACH) { phase = 0; phaseT = 0f; loadFade = 1f }
        }
        loadFade = max(0f, loadFade - dt * 1.6f)
        dispProgress += (progress - dispProgress) * min(1f, dt * 6f)

        if (phase == 1) {
            phaseT += dt
            if (phaseT >= EXPLOSION_TIME) { phase = 2; phaseT = 0f }
        } else phaseT += dt

        // música: calmada en carga y menú, épica en partida (vuelve a la calmada tras perder)
        val shopMusic = phase == 0 && tab == 1 && subTab == 4
        val coral = MusicThemes.all.indexOfFirst { it.name == "Épica coral" }
        music.play(if (shopMusic) 1 + previewTheme else if (phase <= 0 || (phase == 2 && g.over && g.overTime > 1.5f)) (if (themeSel == coral) 1 + coral else 0)
            else if (themeSel == coral) 1 else 1 + themeSel)
        val wantBg = if (phase == 0 && tab == 1 && subTab == 3) previewBg else bgSel
        if (wantBg != curBg) applyBg(wantBg)
        music.duck = if (paused) 0.35f else if (soloT > 0f) 0.4f else 1f
        val rdt = min(0.05f, (now - lastMusic) / 1e9f)
        frameDt = rdt
        soloT = max(0f, soloT - rdt)
        music.update(rdt)
        lastMusic = now
        pauseAnim += ((if (paused) 1f else 0f) - pauseAnim) * min(1f, rdt * 11f)
        settingsAnim += ((if (settingsOpen) 1f else 0f) - settingsAnim) * min(1f, rdt * 11f)
        tabT = min(1f, tabT + rdt / 0.32f)
        voiceT -= rdt
        if (voiceT <= 0f && voiceQueue.isNotEmpty()) { announcer.say(voiceQueue.removeAt(0)); voiceT = 1.1f }
        val mp = medalPops.iterator()
        while (mp.hasNext()) { val m = mp.next(); m.t += rdt; if (m.t > 2.8f) mp.remove() }
        if (achToasts.isNotEmpty()) { achT += rdt; if (achT > 3.2f) { achToasts.removeAt(0); achT = 0f } }
        celebT = max(0f, celebT - dt)

        val near = g.nearestFilled()
        if (phase == 2 && !paused) {
            if (reviveHold > 0f) reviveHold = max(0f, reviveHold - dt)
            else if (celebT <= 0f && spawnT >= LAUNCH) g.update(dt)
            if (g.shieldEvents > shieldSeen) { shieldSeen = g.shieldEvents; onShield(g) }
            consumeBursts(g)
            consumeIron(g)
            if (g.locks > seenLocks) { stats.add("ships", g.locks - seenLocks); seenLocks = g.locks; sfx.place(Factions.all[shipSel].sfx) }
            processGameEvents(g)
            if (!g.over) gameTime += dt
            // eventos visuales al azar
            if (!g.over) {
                evTimer -= dt
                if (evKind < 0 && evTimer <= 0f) startEvent()
            }
            if (evKind >= 0) { evT += dt; if (evT > EV_DUR) evKind = -1 }
            if (celebT <= 0f) emitThrust(g)
            if (celebT > 0f) emitWeld(dt)
            updateBar(g, dt)
            if (g.over && !overHandled) finishGame(g)
        }
        updateParticles(dt, g)
        // disturbios: explosiones esporádicas en ciudades durante la partida (más seguidas si hay peligro)
        if (phase == 2 && !g.over && !paused) {
            blastTimer -= dt
            val pdx = cache[skinSel]
            if (blastTimer <= 0f && pdx != null && pdx.cities.size > 2) {
                blasts.add(Blast(1 + Random.nextInt(pdx.cities.size / 2 - 1), 0f, 0.6f + Random.nextFloat() * 0.6f))
                val danger = (1f - ((near - 1) / 8f)).coerceIn(0f, 1f)
                blastTimer = (0.9f + Random.nextFloat() * 1.8f) * (1f - 0.5f * danger)
            }
        }
        val bo = booms.iterator()
        while (bo.hasNext()) { val b = bo.next(); b.t += dt; if (b.t > 2.6f) bo.remove() }
        val rf = rowFlash.iterator()
        while (rf.hasNext()) { val r = rf.next(); r[1] += dt; if (r[1] > 0.9f) rf.remove() }
        val bi = blasts.iterator()
        while (bi.hasNext()) { val b = bi.next(); b.t += dt; if (b.t > 4.5f) bi.remove() }

        // cámara: cerca en el menú; al explotar se queda cerca un momento y luego se aleja suave hasta la vista de juego
        val pull = ((phaseT - HOLD) / PULL).coerceIn(0f, 1f)
        if (phase == 1) {
            val e = pull * pull * (3f - 2f * pull)
            planetSc = MENU_SC + (ESC0 - MENU_SC) * e
        } else {
            val target = when {
                phase == 0 -> (if (tab != 0) 1.45f else MENU_SC) + 0.02f * sin(time * 0.6f)
                g.over -> 1.45f
                // escape: el planeta empieza cerca y se aleja despacio durante la partida (~6 min)
                else -> ESC0 - (ESC0 - ESC1) * (gameTime / 360f).coerceIn(0f, 1f) + 0.18f * (1f - ((near - 1) / 8f)).coerceIn(0f, 1f)
            }
            planetSc += (target - planetSc) * min(1f, dt * 2.5f)
        }

        val starTarget = when {
            phase == 0 -> 0.12f
            phase == 1 -> 0.12f + 0.88f * pull
            g.over -> 0.15f
            else -> ((near - 1) / 8f).coerceIn(0.10f, 1f)
        }
        starSpeed += (starTarget - starSpeed) * min(1f, dt * 2f)

        val alertT = if (phase != 2 || g.over) 0f else when {
            near <= 2 -> 1f
            near == 3 -> 0.7f
            near == 4 -> 0.4f
            else -> 0f
        }
        alert += (alertT - alert) * min(1f, dt * 3f)
        shake = max(0f, shake - dt * 2.5f)
        flashWhite = max(0f, flashWhite - dt * 1.6f)

        canvas.save()
        if (shake > 0f) canvas.translate((Random.nextFloat() - 0.5f) * shake * s * 0.35f, (Random.nextFloat() - 0.5f) * shake * s * 0.35f)
        drawSpace(canvas)
        if (phase == 2) drawEventBack(canvas)
        if (phase == -1 && approachT >= 0f) {
            val e = (approachT / APPROACH).coerceIn(0f, 1f)
            planetSc = MENU_SC * (APPROACH_SC0 + (1f - APPROACH_SC0) * e * e * (3f - 2f * e))
        }
        if (phase >= 0 || approachT >= 0f) drawPlanet(canvas, g)
        if (phase == 2 && evKind == 3) drawEclipse(canvas)
        drawHaze(canvas)
        if (phase == 2) {
            drawGrid(canvas)
            drawDanger(canvas, near)
            drawGhost(canvas, g)
            drawShadow(canvas, g)
            drawLocked(canvas, g)
            drawPiece(canvas, g)
        }
        drawBooms(canvas)
        drawSparks(canvas)
        canvas.restore()
        if (alert > 0.01f) drawAlert(canvas)
        if (phase == 2 && evKind == 1) drawStorm(canvas)
        if (phase == 2) {
            drawChips(canvas)
            drawHud(canvas, g)
            if (!g.over) drawGear(canvas, 0.9f, gameGear)
            drawMedals(canvas)
            drawEventBanner(canvas)
        } else if (phase == -1) drawLoading(canvas)
        else drawMenu(canvas)
        if (loadFade > 0f && phase == 0) { paint.color = Color.argb((90 * loadFade).toInt(), 0, 0, 0); paint.style = Paint.Style.FILL; canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint) }
        drawAchToast(canvas)
        if (flashWhite > 0f) {
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = Color.argb((235 * min(1f, flashWhite)).toInt(), 255, 245, 225)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
        if (pauseAnim > 0.01f) drawPause(canvas)
        if (phase == 2 && settingsAnim > 0.01f) drawSettings(canvas)
        if (phase == 2 && diff == 2 && !g.over) drawOcclusion(canvas)
        if (phase >= 0) drawStatusBar(canvas)
        scheduleFrame()
    }

    /** Legendario: ráfagas rápidas que tapan parte de la pantalla (humo, polvo o destellos), sobre todo en eventos. */
    private fun drawOcclusion(c: Canvas) {
        val rate = if (evKind >= 0) 0.35f else 0.08f
        if (occl <= 0f && !paused && Random.nextFloat() < frameDt * rate) {
            occl = 1.1f; occlX = width * (0.15f + Random.nextFloat() * 0.7f); occlY = height * (0.2f + Random.nextFloat() * 0.6f)
        }
        if (occl <= 0f) return
        occl = max(0f, occl - frameDt)
        val k = kotlin.math.sin((1.1f - occl) / 1.1f * PI.toFloat())
        val pt = puffTex ?: return
        for (j in 0 until 4) {
            val r = width * (0.28f + 0.1f * j) * (0.7f + 0.5f * k)
            val dx = cos(j * 1.7f + time) * width * 0.08f; val dy = sin(j * 2.3f) * width * 0.08f
            rect.set(occlX + dx - r, occlY + dy - r, occlX + dx + r, occlY + dy + r)
            val pnt = if (evKind == 1) hotPaint else smokePaint
            pnt.alpha = (200 * k).toInt().coerceIn(0, 255)
            c.save(); c.rotate(j * 60f + time * 20f, occlX, occlY); c.drawBitmap(pt[j % pt.size], null, rect, pnt); c.restore()
        }
    }

    /** Hora y batería, pequeñas, en la orilla superior de la pantalla. */
    private fun drawStatusBar(c: Canvas) {
        battReadT -= frameDt
        if (battReadT <= 0f) {
            battReadT = 20f
            try {
                val it2 = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                if (it2 != null) {
                    val lv = it2.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                    val sc = it2.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
                    battPct = if (lv >= 0 && sc > 0) lv * 100 / sc else -1
                    val st = it2.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                    battCharging = st == android.os.BatteryManager.BATTERY_STATUS_CHARGING || st == android.os.BatteryManager.BATTERY_STATUS_FULL
                }
            } catch (e: Exception) { }
        }
        val y = max(u * 0.42f, topInset * 0.55f + u * 0.12f)
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = u * 0.3f
        paint.color = Color.argb(175, 220, 228, 245)
        paint.textAlign = Paint.Align.LEFT
        c.drawText(clockFmt.format(java.util.Date()), u * 0.35f, y, paint)
        if (battPct >= 0) {
            val bw = u * 0.62f; val bh = u * 0.3f
            val bx = width - u * 0.45f - bw; val by = y - bh * 0.85f
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
            rect.set(bx, by, bx + bw, by + bh)
            c.drawRoundRect(rect, bh * 0.2f, bh * 0.2f, paint)
            paint.style = Paint.Style.FILL
            c.drawRect(bx + bw, by + bh * 0.3f, bx + bw + u * 0.06f, by + bh * 0.7f, paint)
            paint.color = when { battCharging -> Color.argb(210, 90, 210, 120); battPct <= 15 -> Color.argb(220, 240, 70, 60); battPct <= 35 -> Color.argb(210, 240, 190, 60); else -> Color.argb(200, 220, 228, 245) }
            rect.set(bx + 3f, by + 3f, bx + 3f + (bw - 6f) * battPct / 100f, by + bh - 3f)
            c.drawRect(rect, paint)
            paint.textAlign = Paint.Align.RIGHT
            paint.color = Color.argb(175, 220, 228, 245)
            c.drawText((if (battCharging) "⚡" else "") + "$battPct%", bx - u * 0.12f, y, paint)
        }
        paint.textAlign = Paint.Align.CENTER
    }

    /** Explosión dentro de la ciudad epicentro; el planeta no se rompe. */
    private fun spawnExplosion(big: Boolean) {
        val pd = cache[skinSel] ?: return
        shake = if (big) 2.2f else 2.0f
        flashWhite = if (big) 1.5f else 1.4f
        val ra = Math.toRadians(planetAng.toDouble())
        val cxr = (pd.cities[0] * kotlin.math.cos(ra) - pd.cities[1] * kotlin.math.sin(ra)).toFloat()
        val cyr = (pd.cities[0] * kotlin.math.sin(ra) + pd.cities[1] * kotlin.math.cos(ra)).toFloat()
        val ex = ccx + cxr * planetR * planetSc
        val ey = ccy + cyr * planetR * planetSc
        val n = if (big) 220 else 160
        val sp = sprWhite ?: return
        val fi = sprFire ?: return
        val rd = sprRed ?: return
        repeat(n) {
            val a = Random.nextFloat() * 6.283f
            val v = s * (3f + Random.nextFloat() * (if (big) 20f else 13f))
            val spr = when (Random.nextInt(3)) { 0 -> sp; 1 -> fi; else -> rd }
            sparks.add(Spark(ex, ey, cos(a) * v, sin(a) * v, 1.6f, 1.6f, spr, s * (0.18f + Random.nextFloat() * 0.35f)))
        }
        // nubes de humo en el epicentro y explosiones en cadena en ciudades cercanas
        for (k in 0 until 3) blasts.add(Blast(0, -0.15f - k * 0.25f, if (big) 2.8f else 2.0f))
        val nc = pd.cities.size / 2
        val order = (1 until nc).sortedBy {
            val dx = pd.cities[it * 2] - pd.cities[0]; val dy = pd.cities[it * 2 + 1] - pd.cities[1]
            dx * dx + dy * dy
        }
        val count = if (big) 12 else 9
        for ((j, ci) in order.take(count).withIndex()) blasts.add(Blast(ci, -0.35f - j * 0.22f, if (big) 1.5f else 1.1f))
    }

    /** El sobre-escudo se activó: explosión azul a lo largo de la franja y se gasta un power-up. */
    private fun onShield(g: Game) {
        puShield = max(0, puShield - 1); saveWallet()
        shieldFx = 1.6f; shake = 2.4f; flashWhite = max(flashWhite, 0.5f)
        val yc = oy + (g.cy + 0.5f) * s
        for (k in 0 until 9) booms.add(Boom(ox + (k + 0.5f) * COLS * s / 9f, yc + (Random.nextFloat() - 0.5f) * s * 4f, -k * 0.04f, 2.2f, true))
        sfx.boom()
    }

    private fun startEscape() {
        if (!ready()) return
        phase = 1; phaseT = 0f
        spawnExplosion(false)
        sfx.bigBoom()
    }

    private fun finishGame(g: Game) {
        overHandled = true
        stats.add("games")
        stats.add("time", gameTime.toInt())
        stats.max("level", g.level)
        stats.max("best", g.score)
        stats.max("streak", g.bestStreak)
        if (g.score > best) voice(Announcer.RECORD)
        checkAch()
        if (g.score > best) {
            best = g.score
            newRecord = true
            try { prefs.edit().putInt("best", best).apply() } catch (e: Exception) { }
        }
        spawnExplosion(true)
    }

    private fun updateBar(g: Game, dt: Float) {
        if (g.ingots > seenIngots) {
            totalIngots += g.ingots - seenIngots
            seenIngots = g.ingots
            saveWallet()
        }
        if (g.barsFilled > seenBars) {
            totalBars += g.barsFilled - seenBars
            stats.add("bars", g.barsFilled - seenBars)
            stats.add("ingots", 3 * (g.barsFilled - seenBars))
            checkAch()
            try { prefs.edit().putInt("tbars", totalBars).apply() } catch (e: Exception) { }
            seenBars = g.barsFilled
            barFlash = 1f
            // ascenso de rango durante la partida: el narrador lo anuncia con su nombre
            val ri = Ranks.indexFor(totalBars)
            if (ri > rankSaid) { rankSaid = ri; voice(Announcer.RANK_BASE + ri) }
            celebT = CELEB
            sfx.recharge()
            if (music.enabled) { sfx.solo(); soloT = 4.5f }
            dispFrac = 0f
        }
        val frac = g.barProgress / g.barNeed().toFloat()
        dispFrac += (frac - dispFrac) * min(1f, dt * 6f)
        barFlash = max(0f, barFlash - dt * 1.5f)
    }

    private fun consumeBursts(g: Game) {
        if (g.bursts.isEmpty()) return
        val gl = glows ?: return
        val wh = sprWhite ?: return
        for (b in g.bursts) {
            val type = (b[2] - 1).coerceIn(0, 6)
            val px = ox + (b[0] + 0.5f) * s
            val py = oy + (b[1] + 0.5f) * s
            booms.add(Boom(px, py, -Random.nextFloat() * 0.3f, 0.8f + Random.nextFloat() * 0.7f))
            repeat(7) {
                val a = Random.nextFloat() * 6.283f
                val v = s * (1.5f + Random.nextFloat() * 4f)
                sparks.add(Spark(px, py, cos(a) * v, sin(a) * v, 0.9f, 0.9f, if (it % 3 == 0) wh else gl[type], s * (0.15f + Random.nextFloat() * 0.25f)))
            }
        }
        g.bursts.clear()
        shake = 1.8f
        // explosión dramática: destello en la fila y estruendo
        for (row in g.clearedRows) rowFlash.add(floatArrayOf(row.toFloat(), 0f))
        g.clearedRows.clear()
        sfx.boom()
        flashWhite = max(flashWhite, 0.35f)
    }

    /** Convierte el hierro soltado por las naves en fragmentos que vuelan hacia la barra. */
    private fun consumeIron(g: Game) {
        if (g.ironDrops.isEmpty()) return
        for (d in g.ironDrops) {
            chips.add(Chip(ox + (d[0] + 0.5f) * s, oy + (d[1] + 0.5f) * s, Random.nextFloat() * 0.25f,
                0.7f + Random.nextFloat() * 0.4f, d[2], if (Random.nextBoolean()) 1f else -1f))
        }
        g.ironDrops.clear()
    }

    /** Estela de motor detrás de cada nave (hacia el planeta). */
    private fun emitThrust(g: Game) {
        val p = g.piece ?: return
        val fi = flameA ?: return
        val rd = flameB ?: return
        for (c in p.cells) {
            var covered = false
            for (o in p.cells) if (o[0] == c[0] - g.gx && o[1] == c[1] - g.gy) covered = true
            if (covered || Random.nextFloat() > 0.6f) continue
            val cxp = ox + (p.fx + c[0] + 0.5f) * s - g.gx * s * 0.5f
            val cyp = oy + (p.fy + c[1] + 0.5f) * s - g.gy * s * 0.5f
            sparks.add(Spark(cxp + (Random.nextFloat() - 0.5f) * s * 0.4f, cyp + (Random.nextFloat() - 0.5f) * s * 0.4f,
                -g.gx * s * (2.5f + Random.nextFloat() * 2f) + (Random.nextFloat() - 0.5f) * s,
                -g.gy * s * (2.5f + Random.nextFloat() * 2f) + (Random.nextFloat() - 0.5f) * s,
                0.4f, 0.4f, if (Random.nextBoolean()) fi else rd, s * (0.22f + Random.nextFloat() * 0.2f)))
        }
    }

    private fun updateParticles(dt: Float, g: Game) {
        val it = sparks.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) { it.remove(); continue }
            p.x += p.vx * dt; p.y += p.vy * dt
            p.vx *= 0.96f; p.vy *= 0.96f
        }
        val maxD = hypot(width.toFloat(), height.toFloat()) / 2f + 20f
        for (st in stars) {
            st.dist -= st.dist * (0.45f + 0.9f * st.z) * starSpeed * dt
            st.ang += (0.006f + 0.01f * st.z) * dt
            if (st.dist < planetR * 0.9f) { st.dist = maxD * (0.75f + Random.nextFloat() * 0.25f); st.ang = Random.nextFloat() * 6.2832f }
        }
        for (c in cosmic) {
            c.dist -= c.dist * (0.28f + 0.5f * c.z) * starSpeed * dt
            c.rot += c.rotSp * dt
            if (c.dist < planetR * 0.95f) respawn(c, maxD, false)
        }
        // estrellas fugaces ocasionales
        if (starSpeed > 0.3f && Random.nextFloat() < dt * 0.35f && meteors.size < 3) {
            val w = width.toFloat()
            val x0 = Random.nextFloat() * w
            val y0 = Random.nextFloat() * height * 0.5f
            val a = 0.5f + Random.nextFloat() * 0.9f
            val v = w * (1.1f + Random.nextFloat() * 0.8f)
            meteors.add(Meteor(x0, y0, cos(a) * v, sin(a) * v, 0.9f))
        }
        val mi = meteors.iterator()
        while (mi.hasNext()) {
            val m = mi.next()
            m.life -= dt
            m.x += m.vx * dt; m.y += m.vy * dt
            if (m.life <= 0f) mi.remove()
        }
        chipTx = barX0 + (barX1 - barX0) * dispFrac.coerceIn(0.03f, 1f)
        chipTy = (barY0 + barY1) / 2f
        val ci = chips.iterator()
        while (ci.hasNext()) {
            val c = ci.next()
            c.t += dt
            if (c.t >= c.delay + c.dur) {
                g.addIron(c.amount)
                ci.remove()
            }
        }
    }

    // ---------- dibujo del espacio ----------
    private fun drawSpace(c: Canvas) {
        paint.style = Paint.Style.FILL
        paint.shader = bgShader
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = nebA
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = nebB
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        // adornos del fondo elegido
        val bgd = Backgrounds.all[max(0, curBg)]
        val gb = galaxyBmp
        if (bgd.galaxy && gb != null) {
            c.save()
            c.translate(width * 0.72f, height * 0.17f)
            c.rotate(-25f + time * 0.5f)
            c.scale(1f, 0.55f)
            val gr = width * 0.36f
            rect.set(-gr, -gr, gr, gr)
            addPaint.alpha = 210
            c.drawBitmap(gb, null, rect, addPaint)
            c.restore()
        }
        val cp = clusterPts
        val wsp = sprWhite
        if (bgd.cluster && cp != null && wsp != null) {
            for (k in 0 until cp.size / 3) {
                val r = s * 0.35f * cp[k * 3 + 2]
                rect.set(cp[k * 3] - r, cp[k * 3 + 1] - r, cp[k * 3] + r, cp[k * 3 + 1] + r)
                addPaint.alpha = (140 * cp[k * 3 + 2] * (0.75f + 0.25f * sin(time * 2f + k))).toInt()
                c.drawBitmap(wsp, null, rect, addPaint)
            }
        }
        if (bgd.aurora) drawAurora(c, 0.55f)
        paint.strokeCap = Paint.Cap.ROUND
        val wh = sprWhite
        for (st in stars) {
            val fade = ((st.dist - planetR) / (s * 3f)).coerceIn(0f, 1f)
            val speedPx = st.dist * (0.45f + 0.9f * st.z) * starSpeed
            val len = 2f + speedPx * 0.055f
            val ca = cos(st.ang); val sa = sin(st.ang)
            val px = ccx + ca * st.dist
            val py = ccy + sa * st.dist
            val tw = 0.7f + 0.18f * sin(time * (2.2f + st.z * 3f) + st.ang * 40f) + 0.12f * sin(time * (7.3f + st.ang * 5f) + st.dist)
            val tint = (st.ang * 100f).toInt() and 3
            val tr = if (tint == 0) 255 else if (tint == 1) 185 else 235
            val tg = if (tint == 0) 232 else if (tint == 1) 212 else 240
            val tb = if (tint == 0) 195 else 255
            paint.strokeWidth = 0.9f + 1.6f * st.z
            paint.color = Color.argb((fade * tw * (70f + 185f * st.z)).toInt().coerceIn(0, 255), tr, tg, tb)
            c.drawLine(px, py, ccx + ca * (st.dist + len), ccy + sa * (st.dist + len), paint)
            if (wh != null && st.z > 0.62f && gfxMode == 0) {
                val r = s * (0.22f + 0.55f * (st.z - 0.62f) / 0.38f) * (0.85f + 0.3f * tw)
                rect.set(px - r, py - r, px + r, py + r)
                addPaint.alpha = (fade * tw * 210f).toInt().coerceIn(0, 255)
                c.drawBitmap(wh, null, rect, addPaint)
            }
        }
        drawCosmic(c)
        drawMeteors(c)
    }

    private fun drawCosmic(c: Canvas) {
        val maxD = hypot(width.toFloat(), height.toFloat()) / 2f
        val rk = rocks
        val mn = moons
        val wh = sprWhite
        val st = sprSteel
        val rd = sprRed
        for (o in cosmic) {
            val fade = ((o.dist - planetR) / (s * 4f)).coerceIn(0f, 1f)
            val px = ccx + cos(o.ang) * o.dist
            val py = ccy + sin(o.ang) * o.dist
            val sz = o.size * (0.35f + 0.9f * (o.dist / maxD))
            objPaint.alpha = (255 * fade).toInt()
            when (o.kind) {
                0 -> if (rk != null) {
                    c.save(); c.translate(px, py); c.rotate(o.rot)
                    rect.set(-sz, -sz, sz, sz)
                    c.drawBitmap(rk[o.spr % rk.size], null, rect, objPaint)
                    c.restore()
                }
                2 -> if (mn != null) {
                    rect.set(px - sz, py - sz, px + sz, py + sz)
                    c.drawBitmap(mn[o.spr % mn.size], null, rect, objPaint)
                }
                3 -> {
                    c.save(); c.translate(px, py); c.rotate(o.rot * 0.3f)
                    paint.style = Paint.Style.FILL
                    val a = (255 * fade).toInt()
                    paint.color = Color.argb(a, 40, 70, 150)
                    c.drawRect(-sz * 1.1f, -sz * 0.22f, -sz * 0.3f, sz * 0.22f, paint)
                    c.drawRect(sz * 0.3f, -sz * 0.22f, sz * 1.1f, sz * 0.22f, paint)
                    paint.color = Color.argb(a, 120, 160, 230)
                    paint.strokeWidth = 1f
                    c.drawLine(-sz * 0.7f, -sz * 0.22f, -sz * 0.7f, sz * 0.22f, paint)
                    c.drawLine(sz * 0.7f, -sz * 0.22f, sz * 0.7f, sz * 0.22f, paint)
                    paint.color = Color.argb(a, 170, 176, 188)
                    c.drawRoundRect(-sz * 0.3f, -sz * 0.28f, sz * 0.3f, sz * 0.28f, sz * 0.08f, sz * 0.08f, paint)
                    c.restore()
                    if (rd != null && ((time * 1.6f + o.rot) % 1f) < 0.18f) {
                        val r = sz * 0.7f
                        rect.set(px - r, py - r, px + r, py + r)
                        addPaint.alpha = (255 * fade).toInt()
                        c.drawBitmap(rd, null, rect, addPaint)
                    }
                }
                1 -> if (wh != null && st != null) {
                    // cometa: cabeza brillante y cola hacia afuera (lejos del planeta)
                    val dx = cos(o.ang); val dy = sin(o.ang)
                    for (k in 12 downTo 1) {
                        val f = 1f - k / 13f
                        val r = sz * (0.35f + 0.5f * f)
                        val tx = px + dx * k * sz * 0.7f
                        val ty = py + dy * k * sz * 0.7f
                        rect.set(tx - r, ty - r, tx + r, ty + r)
                        addPaint.alpha = (150f * f * fade).toInt()
                        c.drawBitmap(st, null, rect, addPaint)
                    }
                    var r = sz * 1.0f
                    rect.set(px - r, py - r, px + r, py + r)
                    addPaint.alpha = (255 * fade).toInt()
                    c.drawBitmap(wh, null, rect, addPaint)
                }
            }
        }
        objPaint.alpha = 255
    }

    private fun drawMeteors(c: Canvas) {
        val wh = sprWhite ?: return
        for (m in meteors) {
            if (m.ev) continue
            val f = (m.life / 0.9f).coerceIn(0f, 1f)
            val sp = hypot(m.vx, m.vy)
            val ux = m.vx / sp; val uy = m.vy / sp
            for (k in 0 until 14) {
                val r = if (k == 0) s * 0.22f else s * (0.16f - k * 0.009f)
                val x = m.x - ux * k * s * 0.7f
                val y = m.y - uy * k * s * 0.7f
                rect.set(x - r, y - r, x + r, y + r)
                addPaint.alpha = (if (k == 0) 200f * f else 60f * f * (1f - k / 14f)).toInt().coerceIn(0, 255)
                c.drawBitmap(wh, null, rect, addPaint)
            }
        }
    }

    private fun displayedSkin(): Int =
        if (phase == 0 && tab == 1 && !skins[preview].classified && cache[preview] != null) preview else skinSel

    /** Giro del planeta: lento en el menú; se congela al explotar y queda fijo toda la partida. */
    private var planetAng = 0f
    /** Desplazamiento del contenido del menú (más abajo y con pestañas más altas). */
    private val mtop get() = topInset + u * 1.25f
    @Volatile private var spinner: Textures.Spinner? = null
    @Volatile private var spinSkin = -1
    private var spinThread: Thread? = null

    /** Prepara el giro del planeta equipado (mapa de superficie en disco) y lanza el hilo que dibuja los cuadros. */
    private fun prepareSpinner(dir: java.io.File, skin: Int) {
        if (skins[skin].classified) { spinner = null; return }
        val mh = 512
        val mapBmp = cachedTex(dir, "map_$skin") { Bitmap.createBitmap(Textures.makeSurfaceMap(mh, skins[skin]), mh * 2, mh, Bitmap.Config.ARGB_8888) }
        val px = IntArray(mh * 2 * mh)
        mapBmp.getPixels(px, 0, mh * 2, 0, 0, mh * 2, mh)
        val sp = Textures.Spinner(px, mh, (planetR * 1.25f).toInt().coerceIn(200, 700), skins[skin])
        sp.render(0f)
        spinner = sp; spinSkin = skin
        if (spinThread == null) {
            spinThread = Thread {
                var ang = 0f
                var last = System.nanoTime()
                while (true) {
                    try {
                        val now = System.nanoTime()
                        val dt = ((now - last) / 1e9f).coerceAtMost(0.2f); last = now
                        val cur = spinner
                        if (cur != null && phase <= 0 && !paused) {
                            ang += dt * 0.045f  // una vuelta cada ~2 min 20 s
                            cur.render(ang)
                            postInvalidate()
                        }
                        Thread.sleep(45)
                    } catch (e: InterruptedException) { break } catch (e: Throwable) { }
                }
            }.apply { isDaemon = true; start() }
        }
    }

    private fun drawPlanet(c: Canvas, g: Game) {
        val si = displayedSkin()
        val pd = cache[si]
        c.save()
        c.scale(planetSc, planetSc, ccx, ccy)
        c.rotate(planetAng, ccx, ccy)
        paint.shader = atmoShaders[si]
        c.drawRect(ccx - planetR * 1.5f, ccy - planetR * 1.5f, ccx + planetR * 1.5f, ccy + planetR * 1.5f, paint)
        paint.shader = null
        if (pd != null) {
            rect.set(ccx - planetR, ccy - planetR, ccx + planetR, ccy + planetR)
            c.drawBitmap(pd.bmp, null, rect, bmpPaint)
            // en el menú la superficie gira sobre su eje vertical con la luz fija; al explotar queda la textura completa
            val spn = spinner
            if (spn != null && phase == 0 && si == spinSkin) {
                val dr = planetR * 0.93f
                rect.set(ccx - dr, ccy - dr, ccx + dr, ccy + dr)
                // aparece poco a poco al terminar la llegada, para que no haya salto
                objPaint.alpha = (255 * (1f - loadFade)).toInt().coerceIn(0, 255)
                synchronized(spn.bmp) { c.drawBitmap(spn.bmp, null, rect, objPaint) }
                objPaint.alpha = 255
                rect.set(ccx - planetR, ccy - planetR, ccx + planetR, ccy + planetR)
            }
            // nubes que giran despacio (dos capas en sentidos opuestos), solo sobre el disco del planeta
            val ct = cloudTex
            if (ct != null && !skins[si].classified) {
                val dr = planetR * 0.93f
                path.reset(); path.addCircle(ccx, ccy, dr, Path.Direction.CW)
                c.save(); c.clipPath(path)
                for (kk in 0 until (if (gfxMode == 0) 2 else 1)) {
                    c.save(); c.rotate(time * (if (kk == 0) 1.2f else -0.7f) + kk * 140f, ccx, ccy)
                    rect.set(ccx - dr, ccy - dr, ccx + dr, ccy + dr)
                    cloudPaint.alpha = if (kk == 0) 60 else 38
                    c.drawBitmap(ct, null, rect, cloudPaint)
                    c.restore()
                }
                c.restore()
            }
            drawDisturbances(c, pd, g)
        }
        c.restore()
    }

    /** Ciudades con destellos y fuego; fogonazo y onda expansiva al iniciar y mucho más grandes al perder. */
    private fun drawDisturbances(c: Canvas, pd: PlanetData, g: Game) {
        val gf = sprFire ?: return
        val gw = sprWhite ?: return
        if (phase == 0) return
        val n = pd.cities.size / 2
        val ex = ccx + pd.cities[0] * planetR
        val ey = ccy + pd.cities[1] * planetR
        val intensity = if (phase == 1) (phaseT / 1.2f).coerceIn(0f, 1f) else 1f
        val over = phase == 2 && g.over
        val boost = if (over) 1.6f else 1f
        for (i in 1 until n) {
            val cxp = ccx + pd.cities[i * 2] * planetR
            val cyp = ccy + pd.cities[i * 2 + 1] * planetR
            val v = sin(time * (5f + (i % 5)) + i * 3.1f) * sin(time * 2.3f + i * 1.7f)
            val flash = ((v - 0.55f) / 0.45f).coerceIn(0f, 1f)
            val base = 0.35f + 0.25f * sin(time * 9f + i * 2f)
            val rad = planetR * (0.025f + 0.05f * flash) * boost
            rect.set(cxp - rad, cyp - rad, cxp + rad, cyp + rad)
            addPaint.alpha = ((60f * base + 255f * flash) * intensity).toInt().coerceIn(0, 255)
            c.drawBitmap(if (flash > 0.5f) gw else gf, null, rect, addPaint)
        }
        drawBlasts(c, pd)
        val burn = 0.7f + 0.3f * sin(time * 11f)
        var rad = planetR * 0.13f * (0.6f + 0.4f * intensity) * boost
        rect.set(ex - rad, ey - rad, ex + rad, ey + rad)
        addPaint.alpha = (200 * burn * intensity).toInt().coerceIn(0, 255)
        c.drawBitmap(gf, null, rect, addPaint)
        rad = planetR * 0.045f * boost
        rect.set(ex - rad, ey - rad, ex + rad, ey + rad)
        addPaint.alpha = (255 * burn).toInt().coerceIn(0, 255)
        c.drawBitmap(gw, null, rect, addPaint)

        val et = when {
            phase == 1 -> phaseT
            over -> g.overTime
            else -> -1f
        }
        if (et < 0f) return
        val dur = if (over) 3.2f else 1.6f
        val k = if (over) 1.6f else 1f
        val fl = (1f - et / dur).coerceIn(0f, 1f)
        if (fl > 0f) {
            val r1 = planetR * k * (0.08f + 0.5f * (1f - kotlin.math.exp(-et * 4f)))
            rect.set(ex - r1, ey - r1, ex + r1, ey + r1)
            addPaint.alpha = (255 * fl).toInt()
            c.drawBitmap(gw, null, rect, addPaint)
            val r2 = r1 * 1.8f
            rect.set(ex - r2, ey - r2, ex + r2, ey + r2)
            addPaint.alpha = (190 * fl).toInt()
            c.drawBitmap(gf, null, rect, addPaint)
            val rd = sprRed
            if (rd != null) {
                val r3 = r1 * 2.8f
                rect.set(ex - r3, ey - r3, ex + r3, ey + r3)
                addPaint.alpha = (110 * fl).toInt()
                c.drawBitmap(rd, null, rect, addPaint)
            }
        }
        // bola de fuego con textura de plasma (nítida y con filamentos) que gira y se expande
        val pl = plasmaTex
        if (pl != null && et < 2.4f) {
            val q = et / 2.4f
            val r = planetR * k * (0.12f + 0.45f * (1f - (1f - q) * (1f - q)))
            for (kk in 0 until 2) {
                c.save(); c.rotate(et * (if (kk == 0) 25f else -40f) + kk * 90f, ex, ey)
                rect.set(ex - r * (1f - 0.3f * kk), ey - r * (1f - 0.3f * kk), ex + r * (1f - 0.3f * kk), ey + r * (1f - 0.3f * kk))
                addPaint.alpha = (255 * (1f - q) * (if (kk == 0) 1f else 0.8f)).toInt().coerceIn(0, 255)
                c.drawBitmap(pl, null, rect, addPaint)
                c.restore()
            }
        }
        // onda de choque que recorre todo el planeta (anillo con textura, recortado al disco)
        val rg = ringTex
        if (rg != null) {
            for (w in 0..(if (over) 2 else 1)) {
                val t2 = et - w * 0.35f
                val q = (t2 / 2.2f).coerceIn(0f, 1f)
                if (t2 <= 0f || q >= 1f) continue
                path.reset(); path.addCircle(ccx, ccy, planetR * 0.93f, Path.Direction.CW)
                c.save(); c.clipPath(path)
                val rr = planetR * 2.1f * (1f - (1f - q) * (1f - q)) / 0.9f
                rect.set(ex - rr, ey - rr, ex + rr, ey + rr)
                addPaint.alpha = ((if (w == 0) 255 else 150) * (1f - q)).toInt().coerceIn(0, 255)
                c.drawBitmap(rg, null, rect, addPaint)
                // la superficie se ilumina justo detrás del frente de la onda
                val gl = sprFire
                if (gl != null) {
                    val r2 = rr * 0.9f
                    rect.set(ex - r2, ey - r2, ex + r2, ey + r2)
                    addPaint.alpha = (70 * (1f - q)).toInt()
                    c.drawBitmap(gl, null, rect, addPaint)
                }
                c.restore()
            }
        }
        for (w in 0..(if (over) 1 else 0)) {
            val t2 = et - w * 0.7f
            val ring = (t2 / 1.8f).coerceIn(0f, 1f)
            if (t2 > 0f && ring < 1f) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = planetR * 0.03f * (1f - ring) + 2f
                paint.color = Color.argb((220 * (1f - ring)).toInt(), 255, 220, 170)
                c.drawCircle(ex, ey, planetR * 1.1f * k * ring, paint)
                paint.style = Paint.Style.FILL
            }
        }
    }

    /** Fogonazo, bola de fuego, onda sobre la superficie (en perspectiva) y humo que se eleva. */
    private fun drawBlasts(c: Canvas, pd: PlanetData) {
        val gf = sprFire ?: return
        val gw = sprWhite ?: return
        val sm = sprSmoke ?: return
        val rd = sprRed ?: return
        for (b in blasts) {
            if (b.t < 0f) continue
            val nxp = pd.cities[b.city * 2]
            val nyp = pd.cities[b.city * 2 + 1]
            val px = ccx + nxp * planetR
            val py = ccy + nyp * planetR
            val t = b.t
            val sz = b.size
            if (t > 0.25f) {
                val st = ((t - 0.25f) / 4.2f).coerceIn(0f, 1f)
                for (k in 0 until 3) {
                    val r = planetR * (0.04f + 0.13f * st) * sz * (0.8f + 0.2f * k)
                    val ox2 = (k - 1) * r * 0.45f
                    val oy2 = -planetR * 0.05f * st * sz * (1f + k * 0.4f)
                    rect.set(px + ox2 - r, py + oy2 - r, px + ox2 + r, py + oy2 + r)
                    objPaint.alpha = (150f * (1f - st) * min(1f, (t - 0.25f) * 3f)).toInt().coerceIn(0, 255)
                    c.drawBitmap(sm, null, rect, objPaint)
                }
            }
            if (t < 1.4f) {
                val q = t / 1.4f
                val nz = kotlin.math.sqrt(max(0.05f, 1f - nxp * nxp - nyp * nyp))
                val rr = planetR * 0.22f * sz * q
                c.save()
                c.rotate(Math.toDegrees(kotlin.math.atan2(nyp, nxp).toDouble()).toFloat(), px, py)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = planetR * 0.012f * sz * (1f - q) + 1f
                paint.color = Color.argb((200 * (1f - q)).toInt(), 255, 210, 150)
                rect.set(px - rr * max(0.3f, nz), py - rr, px + rr * max(0.3f, nz), py + rr)
                c.drawOval(rect, paint)
                paint.style = Paint.Style.FILL
                c.restore()
            }
            if (t < 1.8f) {
                val q = t / 1.8f
                val grow = 1f - (1f - q) * (1f - q)
                var r = planetR * (0.03f + 0.09f * grow) * sz
                rect.set(px - r * 1.6f, py - r * 1.6f, px + r * 1.6f, py + r * 1.6f)
                addPaint.alpha = (150 * (1f - q)).toInt().coerceIn(0, 255)
                c.drawBitmap(rd, null, rect, addPaint)
                rect.set(px - r, py - r, px + r, py + r)
                addPaint.alpha = (240 * (1f - q)).toInt().coerceIn(0, 255)
                c.drawBitmap(gf, null, rect, addPaint)
                r *= 0.45f
                rect.set(px - r, py - r, px + r, py + r)
                addPaint.alpha = (255 * (1f - q) * (1f - q)).toInt().coerceIn(0, 255)
                c.drawBitmap(gw, null, rect, addPaint)
            }
            val plx = plasmaTex
            if (plx != null && t < 1.2f) {
                val q = t / 1.2f
                val r = planetR * (0.04f + 0.12f * (1f - (1f - q) * (1f - q))) * sz
                c.save(); c.rotate(t * 60f + b.city * 40f, px, py)
                rect.set(px - r, py - r, px + r, py + r)
                addPaint.alpha = (230 * (1f - q)).toInt().coerceIn(0, 255)
                c.drawBitmap(plx, null, rect, addPaint)
                c.restore()
            }
            if (t < 0.35f) {
                val q = t / 0.35f
                val r = planetR * 0.13f * sz * (0.5f + q)
                rect.set(px - r, py - r, px + r, py + r)
                addPaint.alpha = (255 * (1f - q)).toInt().coerceIn(0, 255)
                c.drawBitmap(gw, null, rect, addPaint)
            }
        }
    }

    private fun drawHaze(c: Canvas) {
        if (gfxMode == 1) return
        val hb = haze ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in 0..1) {
            val sc = if (i == 0) 1.5f else 2.1f
            val dx = sin(time * 0.05f * (i + 1) + i * 2f) * w * 0.12f
            val dy = cos(time * 0.04f * (i + 1)) * h * 0.08f
            val ex = w * (sc - 1f) / 2f
            val ey = h * (sc - 1f) / 2f
            rect.set(-ex + dx, -ey + dy, w + ex + dx, h + ey + dy)
            hazePaint.alpha = if (i == 0) 110 else 70
            c.drawBitmap(hb, null, rect, hazePaint)
        }
    }

    private fun drawAlert(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val th = u * 1.1f
        val pulse = 0.35f + 0.65f * (0.5f + 0.5f * sin(time * (6f + 5f * alert)))
        val a = (230f * alert * pulse).toInt().coerceIn(0, 255)
        paint.style = Paint.Style.FILL
        paint.shader = edgeShaders[0]; paint.alpha = a; c.drawRect(0f, 0f, w, th, paint)
        paint.shader = edgeShaders[1]; paint.alpha = a; c.drawRect(0f, h - th, w, h, paint)
        paint.shader = edgeShaders[2]; paint.alpha = a; c.drawRect(0f, 0f, th, h, paint)
        paint.shader = edgeShaders[3]; paint.alpha = a; c.drawRect(w - th, 0f, w, h, paint)
        paint.shader = null
        paint.alpha = 255
    }

    private fun drawDanger(c: Canvas, near: Int) {
        val g = game ?: return
        val pulse = 0.5f + 0.5f * sin(time * (if (near <= 3) 9f else 3f))
        val alpha = if (near <= 3) (60 + 80 * pulse).toInt() else (14 + 12 * pulse).toInt()
        val x0 = ox
        val y0 = oy + (g.cy - ZONE) * s
        val x1 = ox + COLS * s
        val y1 = oy + (g.cy + ZONE + 1) * s
        // segunda franja tenue: el alcance del sobre-escudo si está activo
        if (g.shields > 0) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
            paint.color = Color.argb((60 + 40 * pulse).toInt(), 90, 180, 255)
            c.drawRect(x0, y0 - 3 * s, x1, y1 + 3 * s, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(alpha, 255, 50, 60)
        c.drawRoundRect(x0, y0, x1, y1, s * 0.3f, s * 0.3f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.argb(min(255, alpha + 60), 255, 110, 120)
        c.drawRoundRect(x0, y0, x1, y1, s * 0.3f, s * 0.3f, paint)
        paint.style = Paint.Style.FILL
    }

    /** Cuadrícula del campo: muestra en qué celdas se colocan realmente las naves. */
    private fun drawGrid(c: Canvas) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.argb(38, 170, 205, 255)
        c.drawLines(gridLines, paint)
        paint.style = Paint.Style.FILL
    }

    /** Contorno de las celdas que ocuparía la nave si se fijara ahora (a nivel del suelo). */
    private fun drawGhost(c: Canvas, g: Game) {
        val p = g.piece ?: return
        val col = BASE[p.type]
        val bx = p.px
        val by = p.py
        for (cell in p.cells) {
            val x = ox + (bx + cell[0]) * s
            val y = oy + (by + cell[1]) * s
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(46, Color.red(col), Color.green(col), Color.blue(col))
            c.drawRect(x, y, x + s, y + s, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = Color.argb(170, Color.red(col), Color.green(col), Color.blue(col))
            c.drawRect(x + 1.5f, y + 1.5f, x + s - 1.5f, y + s - 1.5f, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawShadow(c: Canvas, g: Game) {
        val p = g.piece ?: return
        paint.color = Color.argb(100, 0, 0, 0)
        val o = s * 0.14f
        for (cell in p.cells) {
            val x = ox + (p.fx + cell[0]) * s + o
            val y = oy + (p.fy + cell[1]) * s + o
            c.drawRoundRect(x, y, x + s, y + s, s * 0.08f, s * 0.08f, paint)
        }
    }

    /** Naves colocadas dibujadas enteras (una imagen por nave), con su desfase de recorrido animado. */
    private fun drawLocked(c: Canvas, g: Game) {
        val sp = ships ?: return
        for (layer in 0..2) for (sh in g.placed.values) {
            drawShipSprite(c, sp, sh.type, sh.rot, sh.cells, sh.px.toFloat(), sh.py + sh.vy, layer, LIFT_LOCKED)
        }
    }

    private var lastPiece: Any? = null
    private var spawnT = 9f

    /** Despegue: la nave sale del planeta como un destello que sube hasta su casilla y crece al llegar. */
    private fun drawLaunch(c: Canvas, bcx: Float, bcy: Float) {
        val k = (spawnT / LAUNCH).coerceIn(0f, 1f)
        if (k >= 1f) return
        val wh = sprSteel ?: return
        val fire = sprFire
        // destello en la superficie al despegar
        if (fire != null && k < 0.45f) {
            val r = s * (1.2f + 2f * k) * planetSc
            rect.set(ccx - r, ccy - r, ccx + r, ccy + r)
            addPaint.alpha = (200 * (1f - k / 0.45f)).toInt().coerceIn(0, 255)
            c.drawBitmap(fire, null, rect, addPaint)
        }
        // estela del recorrido planeta → casilla
        for (q in 0 until 14) {
            val lq = k * (q / 13f)
            val pe = 1f - lq
            val x = bcx + (ccx - bcx) * pe * pe + (-(bcy - ccy)) * 0.25f * sin(lq * PI.toFloat())
            val y = bcy + (ccy - bcy) * pe * pe + (bcx - ccx) * 0.25f * sin(lq * PI.toFloat())
            val r = s * (0.2f + 0.4f * q / 13f)
            rect.set(x - r, y - r, x + r, y + r)
            addPaint.alpha = (130 * (1f - k * 0.8f) * (q / 13f)).toInt().coerceIn(0, 255)
            c.drawBitmap(wh, null, rect, addPaint)
        }
    }

    private fun drawPiece(c: Canvas, g: Game) {
        val p = g.piece ?: return
        if (p !== lastPiece) { lastPiece = p; spawnT = 0f }
        if (!paused) spawnT += frameDt
        var avgx = 0f; var avgy = 0f
        for (cell in p.cells) { avgx += cell[0]; avgy += cell[1] }
        val gl = hypot(g.gx.toFloat(), g.gy.toFloat())
        val ux = g.gx / gl
        val uy = g.gy / gl
        val mx = p.fx + avgx / 4f + 0.5f + ux * 2.8f
        val my = p.fy + avgy / 4f + 0.5f + uy * 2.8f
        val ax = ox + mx * s
        val ay = oy + my * s
        paint.color = Color.argb(150, 255, 255, 255)
        path.reset()
        val a = s * 0.4f
        path.moveTo(ax + ux * a, ay + uy * a)
        path.lineTo(ax - ux * a + uy * a, ay - uy * a + ux * a)
        path.lineTo(ax - ux * a - uy * a, ay - uy * a - ux * a)
        path.close()
        c.drawPath(path, paint)
        val sp = ships ?: return
        val bcx = ox + (p.fx + avgx / 4f + 0.5f) * s
        val bcy = oy + (p.fy + avgy / 4f + 0.5f) * s
        if (p.bomb) {
            val rd = sprSteel
            val pulse = 0.6f + 0.4f * sin(time * 9f)
            if (rd != null) {
                val r = s * 2.6f * (0.85f + 0.15f * pulse)
                rect.set(bcx - r, bcy - r, bcx + r, bcy + r)
                addPaint.alpha = (190 * pulse).toInt()
                c.drawBitmap(rd, null, rect, addPaint)
            }
        }
        val fa = flameA
        if (fa != null) {
            val gl = hypot(g.gx.toFloat(), g.gy.toFloat()).coerceAtLeast(1f)
            val ex = bcx - g.gx / gl * s * 1.4f; val ey = bcy - g.gy / gl * s * 1.4f
            val fl = 0.75f + 0.15f * sin(time * 31f) + 0.1f * sin(time * 17.3f)
            val r = s * 0.9f * fl
            rect.set(ex - r, ey - r, ex + r, ey + r)
            addPaint.alpha = (150 * fl).toInt().coerceIn(0, 255)
            c.drawBitmap(fa, null, rect, addPaint)
        }
        val hover = LIFT_PIECE * (1f + 0.18f * sin(time * 2.6f))
        drawLaunch(c, bcx, bcy)
        val lk = (spawnT / LAUNCH).coerceIn(0f, 1f)
        val grow = if (lk < 1f) 0.25f + 0.75f * (lk * lk * (3f - 2f * lk)) else 1f
        c.save()
        if (grow < 1f) {
            // sube desde el planeta: empieza pequeña y cerca del centro del planeta
            val pe = 1f - lk
            val ox2 = (ccx - bcx) * pe * pe + (-(bcy - ccy)) * 0.25f * sin(lk * PI.toFloat())
            val oy2 = (ccy - bcy) * pe * pe + (bcx - ccx) * 0.25f * sin(lk * PI.toFloat())
            c.translate(ox2, oy2); c.scale(grow, grow, bcx, bcy)
        }
        for (layer in 0..2) drawShipSprite(c, sp, p.type, p.rot, p.cells, p.fx, p.fy, layer, hover)
        c.restore()
        if (p.bomb) drawPlasmaCore(c, bcx, bcy)
        for (fp in g.free) drawFreePiece(c, sp, fp)
    }

    /** Granada de plasma adherida: núcleo azul que late, filamentos girando y chispas eléctricas. */
    private fun drawPlasmaCore(c: Canvas, x: Float, y: Float) {
        val beat = 0.5f + 0.5f * sin(time * 14f)
        val pl = plasmaTex
        if (pl != null) {
            for (k in 0 until 2) {
                val r = s * (1.1f + 0.25f * beat) * (1f - 0.3f * k)
                c.save(); c.rotate(time * (if (k == 0) 220f else -300f), x, y)
                rect.set(x - r, y - r, x + r, y + r)
                bluePaint.alpha = (230 - 60 * k).coerceIn(0, 255)
                c.drawBitmap(pl, null, rect, bluePaint)
                c.restore()
            }
        }
        val st = sprSteel; val wh = sprWhite
        if (st != null && wh != null) {
            var r = s * (0.9f + 0.4f * beat)
            rect.set(x - r, y - r, x + r, y + r); addPaint.alpha = 210; c.drawBitmap(st, null, rect, addPaint)
            r = s * (0.28f + 0.12f * beat)
            rect.set(x - r, y - r, x + r, y + r); addPaint.alpha = 255; c.drawBitmap(wh, null, rect, addPaint)
        }
        // arcos eléctricos cortos alrededor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.05f
        paint.color = Color.argb(200, 170, 220, 255)
        for (k in 0 until 3) {
            val a0 = time * 9f + k * 2.1f + (Random.nextFloat() - 0.5f) * 0.6f
            val r0 = s * 0.4f; val r1 = s * (0.9f + Random.nextFloat() * 0.4f)
            path.reset()
            path.moveTo(x + cos(a0) * r0, y + sin(a0) * r0)
            val am = a0 + (Random.nextFloat() - 0.5f) * 0.5f
            path.lineTo(x + cos(am) * (r0 + r1) / 2f, y + sin(am) * (r0 + r1) / 2f)
            path.lineTo(x + cos(a0 + 0.2f) * r1, y + sin(a0 + 0.2f) * r1)
            c.drawPath(path, paint)
        }
        paint.style = Paint.Style.FILL
    }

    /** Naves que siguen solas (modo Legendario): se ven igual pero sin control ni guía. */
    private fun drawFreePiece(c: Canvas, sp: Array<Bitmap>, fp: Piece) {
        for (layer in 0..2) drawShipSprite(c, sp, fp.type, fp.rot, fp.cells, fp.fx, fp.fy, layer, LIFT_PIECE)
        val fa = flameA ?: return
        var ax = 0f; var ay = 0f
        for (cl in fp.cells) { ax += cl[0]; ay += cl[1] }
        val bx = ox + (fp.fx + ax / 4f + 0.5f) * s
        val by = oy + (fp.fy + ay / 4f + 0.5f) * s - fp.dirY * s * 1.4f
        val r = s * 0.8f * (0.8f + 0.2f * sin(time * 29f))
        rect.set(bx - r, by - r, bx + r, by + r); addPaint.alpha = 140; c.drawBitmap(fa, null, rect, addPaint)
    }

    private fun proj(v: Float, center: Float, lift: Float) =
        v + lift * s * ((v - center) / (4f * s)).coerceIn(-1f, 1f)

    /** Una nave completa: capas 0 y 1 son el casco inferior oscuro (relieve hacia el centro), capa 2 la cubierta. */
    private fun drawShipSprite(c: Canvas, sp: Array<Bitmap>, type: Int, rot: Int, cells: Array<IntArray>, fx: Float, fy: Float, layer: Int, lift: Float) {
        var mnx = 9; var mny = 9; var mxx = -9; var mxy = -9
        for (cl in cells) { mnx = min(mnx, cl[0]); mny = min(mny, cl[1]); mxx = max(mxx, cl[0]); mxy = max(mxy, cl[1]) }
        val x0 = ox + (fx + mnx) * s
        val y0 = oy + (fy + mny) * s
        val x1 = x0 + (mxx - mnx + 1) * s
        val y1 = y0 + (mxy - mny + 1) * s
        val f = when (layer) { 0 -> -lift; 1 -> -lift * 0.5f; else -> 0f }
        val pt = when (layer) { 0 -> dark1; 1 -> dark2; else -> bmpPaint }
        rect.set(proj(x0, ccx, f), proj(y0, ccy, f), proj(x1, ccx, f), proj(y1, ccy, f))
        c.drawBitmap(sp[type * 4 + (rot and 3)], null, rect, pt)
    }

    private fun voice(i: Int) { if (voiceQueue.size < 3) voiceQueue.add(i) }

    private fun awardMedal(i: Int, speak: Boolean = true) {
        medalPops.add(MedalPop(i, 0f))
        if (medalPops.size > 4) medalPops.removeAt(0)
        stats.add("md_$i")
        if (speak) voice(i)
    }

    private fun ownedCount() = Integer.bitCount(owned) + Integer.bitCount(shipOwned) + Integer.bitCount(bgOwned) + Integer.bitCount(themeOwned) - 4

    private fun checkAch() {
        for (a in stats.check()) {
            achToasts.add(a)
            totalIngots += a.reward
            saveWallet()
        }
    }

    /** Medallas, voz y estadísticas a partir de lo que pasó en el juego (líneas, rachas, bombas). */
    private fun processGameEvents(g: Game) {
        for (n in g.clearEvents) {
            stats.add("lines", n)
            when {
                n == 2 -> stats.add("double")
                n == 3 -> stats.add("triple")
                n >= 4 -> stats.add("quad")
            }
            if (n >= 2) awardMedal(min(n, 4) - 1, speak = false)
            // el narrador cuenta las líneas de la partida, como las rachas de Halo: 2 doble, 3 triple, 5 exterminador…
            val total = g.lines
            voice(when {
                total <= 1 -> Announcer.LINE
                total <= 10 -> Announcer.KILL_BASE + total - 2
                else -> intArrayOf(Announcer.UNSTOP, Announcer.RAMPAGE, Announcer.UNTOUCH, Announcer.INVINC, Announcer.BRUTAL)[total % 5]
            })
            val si = when (g.streak) { 3 -> 4; 5 -> 5; 8 -> 6; 12 -> 7; 16 -> 8; 20 -> 9; else -> -1 }
            if (si >= 0) awardMedal(si, speak = false)
            if (alert > 0.55f) { awardMedal(12); stats.add("limit") }
        }
        val hadEvents = g.clearEvents.isNotEmpty() || g.bombEvents.isNotEmpty()
        g.clearEvents.clear()
        for (b in g.bombEvents) {
            stats.add("bombs")
            if (b[2] >= 3) { awardMedal(11); stats.add("demol") } else awardMedal(10)
            val px = ox + (b[0] + 0.5f) * s
            val py = oy + (b[1] + 0.5f) * s
            // granada de plasma: destello azul intenso y estallido de plasma
            booms.add(Boom(px, py, 0f, 3.4f, true))
            repeat(8) { booms.add(Boom(px + (Random.nextFloat() - 0.5f) * s * 5f, py + (Random.nextFloat() - 0.5f) * s * 5f, -Random.nextFloat() * 0.35f, 1.5f, true)) }
            repeat(40) {
                val a = Random.nextFloat() * 6.283f; val v = s * (4f + Random.nextFloat() * 10f)
                sparks.add(Spark(px, py, cos(a) * v, sin(a) * v, 1.1f, 1.1f, sprSteel ?: return@repeat, s * (0.15f + Random.nextFloat() * 0.3f)))
            }
            shake = 2.6f
            flashWhite = max(flashWhite, 0.6f)
            sfx.boom()
        }
        g.bombEvents.clear()
        if (hadEvents) { stats.max("streak", g.bestStreak); checkAch() }
    }

    private val lasers = ArrayList<FloatArray>()
    private val evNames = arrayOf("LLUVIA DE METEOROS", "TORMENTA SOLAR", "FLOTA ALIADA", "ECLIPSE", "AURORA CÓSMICA", "AGUJERO DE GUSANO", "VENTISCA DE POLVO ESTELAR")

    private fun startEvent() {
        evKind = Random.nextInt(evNames.size)
        evT = 0f
        evTimer = if (diff == 2) EV_DUR + 5f + Random.nextFloat() * 8f else EV_DUR + 20f + Random.nextFloat() * 25f
        evX = width * (0.2f + Random.nextFloat() * 0.6f)
        evY = height * (0.15f + Random.nextFloat() * 0.2f)
        evFleet.clear()
        puffs.clear()
        shake = max(shake, 0.7f)
        // flota aliada: cada nave sigue una curva amplia (bezier) de un lado al otro, sin dar vueltas
        if (evKind == 2) repeat(8) {
            val dir = if (it % 2 == 0) 1f else -1f
            val sc = 0.45f + Random.nextFloat() * 0.6f
            val w = width.toFloat(); val h = height.toFloat()
            val y0 = h * (0.08f + Random.nextFloat() * 0.84f)
            val y1 = h * (0.08f + Random.nextFloat() * 0.84f)
            val cy2 = h * (0.05f + Random.nextFloat() * 0.9f)
            // [x0, y0, cx, cy, x1, y1, t, velocidad, tipo, escala, recarga de disparo]
            evFleet.add(floatArrayOf(if (dir > 0) -s * 4f else w + s * 4f, y0, w * (0.3f + Random.nextFloat() * 0.4f), cy2,
                if (dir > 0) w + s * 4f else -s * 4f, y1, -it * 0.09f, 0.055f + 0.05f * sc, Random.nextInt(7).toFloat(), sc, Random.nextFloat()))
        }
        lasers.clear()
    }

    private fun evEnv(): Float = (evT / 3f).coerceIn(0f, 1f) * ((EV_DUR - evT) / 4f).coerceIn(0f, 1f)

    private fun addPuff(x: Float, y: Float, vx: Float, vy: Float, life: Float, size: Float, grow: Float, kind: Int) {
        if (puffs.size >= (if (gfxMode == 0) 240 else 110)) return
        puffs.add(Puff(x, y, vx, vy, life, life, size, grow, Random.nextFloat() * 360f, (Random.nextFloat() - 0.5f) * 90f, kind, Random.nextInt(3)))
    }

    /** Humo, gas incandescente, escapes y brasas de los eventos (texturas con ruido, nada sólido). */
    private fun updateDrawPuffs(c: Canvas, dt: Float) {
        val pt = puffTex ?: return
        val fire = sprFire
        val it = puffs.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) { it.remove(); continue }
            p.x += p.vx * dt; p.y += p.vy * dt; p.rot += p.spin * dt
            val k = p.life / p.max
            val age = 1f - k
            val r = p.size * (1f + p.grow * age)
            rect.set(p.x - r, p.y - r, p.x + r, p.y + r)
            when (p.kind) {
                0 -> { smokePaint.alpha = (150f * k * k.coerceAtMost(1f) * smoothIn(age, 0.08f)).toInt().coerceIn(0, 255); drawRot(c, pt[p.tex], p, smokePaint) }
                1 -> { hotPaint.alpha = (230f * k * k).toInt().coerceIn(0, 255); drawRot(c, pt[p.tex], p, hotPaint) }
                4 -> { dustPaint.alpha = (95f * k * smoothIn(age, 0.15f)).toInt().coerceIn(0, 255); drawRot(c, pt[p.tex], p, dustPaint) }
                2 -> { bluePaint.alpha = (170f * k * k).toInt().coerceIn(0, 255); drawRot(c, pt[p.tex], p, bluePaint) }
                else -> if (fire != null) { addPaint.alpha = (255f * k).toInt().coerceIn(0, 255); c.drawBitmap(fire, null, rect, addPaint) }
            }
        }
    }

    private fun smoothIn(t: Float, d: Float) = (t / d).coerceIn(0f, 1f)

    private fun drawRot(c: Canvas, b: Bitmap, p: Puff, pnt: Paint) {
        c.save(); c.rotate(p.rot, p.x, p.y); c.drawBitmap(b, null, rect, pnt); c.restore()
    }

    /** Eventos que ocurren detrás del planeta: meteoros, flota aliada, aurora y agujero de gusano. */
    private fun drawEventBack(c: Canvas) {
        val dt = if (paused) 0f else frameDt
        if (evKind < 0) { if (puffs.isNotEmpty()) updateDrawPuffs(c, dt); return }
        val env = evEnv()
        when (evKind) {
            0 -> {
                val w = width.toFloat()
                if (dt > 0f && evT < EV_DUR - 3f && Random.nextFloat() < dt * 1.6f * evEnv() && meteors.count { it.ev } < 5) {
                    val a = 0.55f + Random.nextFloat() * 0.7f
                    val sp = w * (0.45f + Random.nextFloat() * 0.35f)
                    meteors.add(Meteor(Random.nextFloat() * w * 0.8f - w * 0.1f, -s * 2f + Random.nextFloat() * height * 0.25f,
                        cos(a) * sp, sin(a) * sp, 2.6f, true, 0.6f + Random.nextFloat() * 0.8f, Random.nextFloat() * 360f, Random.nextInt(5)))
                }
                // estelas: humo detrás de la cabeza y gas incandescente junto a ella
                for (m in meteors) if (m.ev && dt > 0f) {
                    val sp = hypot(m.vx, m.vy)
                    val ux = m.vx / sp; val uy = m.vy / sp
                    val sz = s * 0.5f * m.size
                    for (q in 0 until 2) addPuff(m.x - ux * sz * (0.6f + Random.nextFloat()), m.y - uy * sz * (0.6f + Random.nextFloat()),
                        -ux * sp * 0.05f + (Random.nextFloat() - 0.5f) * s * 0.4f, -uy * sp * 0.05f + (Random.nextFloat() - 0.5f) * s * 0.4f,
                        1.4f + Random.nextFloat() * 1.2f, sz * 0.75f, 2.4f, 0)
                    addPuff(m.x - ux * sz * 0.5f, m.y - uy * sz * 0.5f, -ux * sp * 0.15f, -uy * sp * 0.15f, 0.35f + Random.nextFloat() * 0.2f, sz * 0.9f, 0.8f, 1)
                    if (Random.nextFloat() < 0.3f) addPuff(m.x, m.y, -ux * sp * 0.3f + (Random.nextFloat() - 0.5f) * s * 3f,
                        -uy * sp * 0.3f + (Random.nextFloat() - 0.5f) * s * 3f, 0.5f + Random.nextFloat() * 0.5f, s * 0.12f, 0f, 3)
                }
                updateDrawPuffs(c, dt)
                val rk = rocks
                val fire = sprFire
                val wh = sprWhite
                for (m in meteors) {
                    if (!m.ev) continue
                    m.rot += dt * 120f
                    val sz = s * 0.5f * m.size
                    val sp = hypot(m.vx, m.vy)
                    val ux = m.vx / sp; val uy = m.vy / sp
                    if (fire != null) {
                        val r = sz * 2.2f
                        rect.set(m.x + ux * sz * 0.3f - r, m.y + uy * sz * 0.3f - r, m.x + ux * sz * 0.3f + r, m.y + uy * sz * 0.3f + r)
                        addPaint.alpha = 150; c.drawBitmap(fire, null, rect, addPaint)
                    }
                    if (rk != null) {
                        rect.set(m.x - sz, m.y - sz, m.x + sz, m.y + sz)
                        objPaint.alpha = 255
                        c.save(); c.rotate(m.rot, m.x, m.y); c.drawBitmap(rk[m.spr % rk.size], null, rect, objPaint); c.restore()
                    }
                    if (wh != null) {
                        val r = sz * 0.8f
                        rect.set(m.x + ux * sz * 0.55f - r, m.y + uy * sz * 0.55f - r, m.x + ux * sz * 0.55f + r, m.y + uy * sz * 0.55f + r)
                        addPaint.alpha = 210; c.drawBitmap(wh, null, rect, addPaint)
                    }
                }
            }
            2 -> {
                val sp = ships ?: return
                val fl = flameA
                for (f in evFleet) {
                    f[6] += f[7] * dt
                    val tt = f[6]
                    if (tt <= 0f || tt >= 1f) continue
                    val u1 = 1f - tt
                    val x = u1 * u1 * f[0] + 2 * u1 * tt * f[2] + tt * tt * f[4]
                    val y = u1 * u1 * f[1] + 2 * u1 * tt * f[3] + tt * tt * f[5]
                    val dx = 2 * u1 * (f[2] - f[0]) + 2 * tt * (f[4] - f[2])
                    val dy = 2 * u1 * (f[3] - f[1]) + 2 * tt * (f[5] - f[3])
                    val ang = Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()).toFloat()
                    val t = f[8].toInt()
                    val sc = f[9]
                    val cells = Shapes.cells(t, 0)
                    var mnx = 9; var mny = 9; var mxx = -9; var mxy = -9
                    for (cl in cells) { mnx = min(mnx, cl[0]); mny = min(mny, cl[1]); mxx = max(mxx, cl[0]); mxy = max(mxy, cl[1]) }
                    val cs = s * 0.8f * sc
                    val hw = (mxx - mnx + 1) * cs / 2f; val hh = (mxy - mny + 1) * cs / 2f
                    val dl = hypot(dx, dy).coerceAtLeast(1f)
                    val ux = dx / dl; val uy = dy / dl
                    if (dt > 0f) addPuff(x - ux * hw, y - uy * hw, -ux * s * 2f, -uy * s * 2f, 0.6f + 0.5f * sc, cs * 0.35f, 1.6f, 2)
                    // disparos: ráfagas cortas hacia adelante
                    f[10] -= dt
                    if (f[10] <= 0f && dt > 0f) {
                        f[10] = 0.5f + Random.nextFloat() * 1.2f
                        lasers.add(floatArrayOf(x + ux * hw, y + uy * hw, ux * width * 1.3f, uy * width * 1.3f, 0.7f, sc))
                    }
                    c.save(); c.rotate(ang, x, y)
                    rect.set(x - hw, y - hh, x + hw, y + hh)
                    objPaint.alpha = ((110 + 140 * sc) * env).toInt().coerceIn(0, 255)
                    // los sprites miran a la derecha en la rotación 0
                    c.drawBitmap(sp[t * 4], null, rect, objPaint)
                    c.restore()
                    if (fl != null) {
                        val r = cs * 0.7f
                        rect.set(x - ux * hw - r, y - uy * hw - r, x - ux * hw + r, y - uy * hw + r)
                        addPaint.alpha = (170 * env).toInt()
                        c.drawBitmap(fl, null, rect, addPaint)
                    }
                }
                objPaint.alpha = 255
                // rayos de energía de la flota
                paint.strokeCap = Paint.Cap.ROUND
                val li = lasers.iterator()
                while (li.hasNext()) {
                    val l = li.next()
                    l[4] -= dt
                    if (l[4] <= 0f) { li.remove(); continue }
                    l[0] += l[2] * dt; l[1] += l[3] * dt
                    val len = s * 1.6f * l[5]
                    val n2 = hypot(l[2], l[3]).coerceAtLeast(1f)
                    val ex = l[0] - l[2] / n2 * len; val ey = l[1] - l[3] / n2 * len
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = s * 0.28f * l[5]
                    paint.color = Color.argb((90 * env).toInt(), 90, 200, 255)
                    c.drawLine(ex, ey, l[0], l[1], paint)
                    paint.strokeWidth = s * 0.09f * l[5]
                    paint.color = Color.argb((240 * env).toInt(), 225, 245, 255)
                    c.drawLine(ex, ey, l[0], l[1], paint)
                }
                paint.style = Paint.Style.FILL
                updateDrawPuffs(c, dt)
            }
            4 -> drawAurora(c, env)
            6 -> {
                // ventisca: ráfagas de polvo estelar que cruzan la pantalla en diagonal con fuerza variable
                val gust = 0.6f + 0.4f * sin(time * 0.7f) * sin(time * 1.9f + 1f)
                val w = width.toFloat()
                if (dt > 0f) repeat((dt * 70f * env * gust).toInt() + if (Random.nextFloat() < dt * 30f * env) 1 else 0) {
                    val v = w * (0.5f + 0.6f * gust) * (0.7f + Random.nextFloat() * 0.6f)
                    addPuff(-s * 3f, Random.nextFloat() * height * 1.1f - height * 0.05f, v, v * 0.28f + (Random.nextFloat() - 0.5f) * s * 4f,
                        w / v * 1.4f, s * (0.8f + Random.nextFloat() * 2.2f), 1.5f, if (Random.nextFloat() < 0.75f) 4 else 2)
                }
                updateDrawPuffs(c, dt)
                val hz = haze
                if (hz != null) {
                    for (kk in 0 until 2) {
                        val off = ((time * (0.25f + kk * 0.15f) * gust) % 1f) * w
                        rect.set(off - w, 0f, off, height.toFloat()); addPaint.alpha = (110 * env).toInt()
                        c.drawBitmap(hz, null, rect, addPaint)
                        rect.set(off, 0f, off + w, height.toFloat()); c.drawBitmap(hz, null, rect, addPaint)
                    }
                }
            }
            5 -> {
                val sw = swirlTex
                if (sw != null) {
                    for (k in 0 until 2) {
                        val r = u * (3.4f - k * 1.1f) * (0.96f + 0.04f * sin(time * 2f + k))
                        rect.set(evX - r, evY - r * 0.62f, evX + r, evY + r * 0.62f)
                        addPaint.alpha = ((200 - k * 50) * env).toInt()
                        c.save(); c.rotate(-12f, evX, evY); c.rotate(time * (if (k == 0) 38f else -60f), evX, evY)
                        c.drawBitmap(sw, null, rect, addPaint); c.restore()
                    }
                }
                val sh = shadowTex
                if (sh != null) {
                    val r = u * 0.9f
                    rect.set(evX - r, evY - r * 0.75f, evX + r, evY + r * 0.75f)
                    objPaint.alpha = (255 * env).toInt()
                    c.drawBitmap(sh, null, rect, objPaint)
                    objPaint.alpha = 255
                }
                val gw = sprSteel
                if (gw != null) {
                    val r = u * (0.5f + 0.15f * sin(time * 9f))
                    rect.set(evX - r, evY - r, evX + r, evY + r)
                    addPaint.alpha = (120 * env).toInt()
                    c.drawBitmap(gw, null, rect, addPaint)
                }
            }
        }
    }

    /** Aurora: cortinas con vetas de ruido que ondulan (la textura se dibuja en tiras verticales desplazadas). */
    private fun drawAurora(c: Canvas, env: Float) {
        val tex = auroraTex ?: return
        val w = width.toFloat()
        val n = 36
        val tw = tex.width
        val slice = tw / n
        for (band in 0 until 2) {
            val base = height * (0.08f + band * 0.13f)
            val bh = height * (0.24f - band * 0.05f)
            addPaint.alpha = ((if (band == 0) 210 else 150) * env).toInt()
            for (i in 0 until n) {
                val x0 = i * w / n
                val x1 = (i + 1) * w / n + 1f
                val xm = (x0 + x1) / 2f
                val off = sin(xm * 0.0045f + time * (0.5f + band * 0.25f) + band * 2f) * u * 1.3f +
                    sin(xm * 0.011f - time * 0.8f) * u * 0.4f
                val sx = ((i * slice + (time * (18f + band * 9f)).toInt() + band * 97) % (tw - slice) + (tw - slice)) % (tw - slice)
                srcRect.set(sx, 0, sx + slice, tex.height)
                rect.set(x0, base + off, x1, base + off + bh)
                c.drawBitmap(tex, srcRect, rect, addPaint)
            }
        }
    }

    /** Eclipse: sombra con penumbra amplia que cruza el planeta y anillo de atmósfera retroiluminado. */
    private fun drawEclipse(c: Canvas) {
        val env = evEnv()
        val pr = planetR * planetSc
        val sx2 = ccx - pr * 2.4f + pr * 4.8f * (0.5f - 0.5f * cos((evT / EV_DUR) * PI.toFloat()))
        val cover = (1f - abs(sx2 - ccx) / (pr * 1.3f)).coerceIn(0f, 1f)
        val sh = shadowTex
        if (sh != null) {
            path.reset()
            path.addCircle(ccx, ccy, pr * 1.01f, Path.Direction.CW)
            c.save()
            c.clipPath(path)
            val r = pr * 1.35f
            rect.set(sx2 - r, ccy - pr * 0.08f - r, sx2 + r, ccy - pr * 0.08f + r)
            objPaint.alpha = (255 * env).toInt()
            c.drawBitmap(sh, null, rect, objPaint)
            objPaint.alpha = 255
            c.restore()
        }
        paint.style = Paint.Style.FILL; paint.shader = null
        paint.color = Color.argb((110 * env * cover).toInt(), 0, 0, 6)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        val rg = ringTex
        if (rg != null && cover > 0f) {
            val r = pr / 0.9f
            rect.set(ccx - r, ccy - r, ccx + r, ccy + r)
            addPaint.alpha = (200 * env * cover * cover).toInt()
            c.save(); c.rotate(time * 4f, ccx, ccy); c.drawBitmap(rg, null, rect, addPaint); c.restore()
        }
    }

    /** Tormenta solar: plasma con filamentos que entra desde una esquina, destello de lente, brasas y parpadeo de luz. */
    private fun drawStorm(c: Canvas) {
        val env = evEnv()
        val dt = if (paused) 0f else frameDt
        val fl = 0.75f + 0.25f * (Noise.fbm(time * 3f, 1.7f, 0.3f, 3) * 2f - 0.5f).coerceIn(0f, 1.2f)
        val pl = plasmaTex
        if (pl != null) {
            for (k in 0 until 2) {
                val r = width * (1.25f - k * 0.35f)
                rect.set(-r, -r, r, r)
                addPaint.alpha = ((150 - k * 40) * env * fl).toInt().coerceIn(0, 255)
                c.save(); c.rotate(time * (if (k == 0) 4f else -7f)); c.drawBitmap(pl, null, rect, addPaint); c.restore()
            }
        }
        // destello de lente a lo largo de la diagonal
        val wh = sprWhite
        val fire = sprFire
        if (wh != null && fire != null) {
            val dx = ccx; val dy = ccy
            val fls = floatArrayOf(0.35f, 0.9f, 0.6f, 0.5f, 1.25f, 1.1f)
            for (k in 0 until 3) {
                val t = fls[k * 2]
                val r = u * fls[k * 2 + 1]
                rect.set(dx * t - r, dy * t - r, dx * t + r, dy * t + r)
                addPaint.alpha = (60 * env * fl).toInt()
                c.drawBitmap(if (k == 1) fire else wh, null, rect, addPaint)
            }
        }
        // brasas arrastradas por el viento solar
        if (dt > 0f && Random.nextFloat() < dt * 25f * env) {
            val a = 0.35f + Random.nextFloat() * 0.8f
            val v = width * (0.25f + Random.nextFloat() * 0.35f)
            addPuff(Random.nextFloat() * width * 0.3f, Random.nextFloat() * height * 0.2f, cos(a) * v, sin(a) * v, 1.2f + Random.nextFloat(), s * (0.06f + Random.nextFloat() * 0.08f), 0f, 3)
        }
        updateDrawPuffs(c, dt)
        // la luz de la escena parpadea levemente
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((22 * env * fl).toInt(), 255, 150, 60)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.xfermode = null
    }

    /** Aviso de alerta: placa metálica oscura con franjas de peligro, interferencia y texto ámbar con sombra. */
    private fun drawEventBanner(c: Canvas) {
        if (evKind < 0) return
        // aviso de borde: tinte suave de color del evento todo el tiempo que dura
        val envA = evEnv()
        val edge = sprRed
        if (edge != null && envA > 0f) {
            val tint = when (evKind) { 1 -> hotPaint; 3 -> smokePaint; else -> bluePaint }
            tint.alpha = (70 * envA * (0.75f + 0.25f * sin(time * 2.5f))).toInt()
            for ((ex, ey) in listOf(0f to 0f, width.toFloat() to 0f, 0f to height.toFloat(), width.toFloat() to height.toFloat())) {
                val r = width * 0.55f
                rect.set(ex - r, ey - r, ex + r, ey + r); c.drawBitmap(edge, null, rect, tint)
            }
        }
        if (evT > 6f) return
        val k = (evT / 0.3f).coerceIn(0f, 1f) * ((6f - evT) / 0.5f).coerceIn(0f, 1f)
        val glitch = (time * 6.3f) % 1f < 0.07f || evT < 0.45f && Random.nextFloat() < 0.35f
        val jx = if (glitch) (Random.nextFloat() - 0.5f) * u * 0.35f else 0f
        val y = height * 0.5f
        val x = width / 2f + jx
        val hw = u * 6.2f; val hh = u * 1.0f
        rect.set(x - hw, y - hh, x + hw, y + hh)
        val al = (205 * k * (if (glitch) 0.7f else 1f)).toInt()
        val pb = plateBmp
        if (pb != null) { objPaint.alpha = al; c.drawBitmap(pb, null, rect, objPaint); objPaint.alpha = 255 }
        else { paint.style = Paint.Style.FILL; paint.color = Color.argb(al * 230 / 255, 28, 28, 30); c.drawRect(rect, paint) }
        // franjas de peligro en los extremos
        val sw = u * 0.85f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = u * 0.16f
        for (side in 0 until 2) {
            val l = if (side == 0) rect.left + u * 0.18f else rect.right - u * 0.18f - sw
            c.save()
            c.clipRect(l, rect.top + u * 0.2f, l + sw, rect.bottom - u * 0.2f)
            paint.color = Color.argb(al * 190 / 255, 214, 150, 40)
            var q = l - hh * 2
            while (q < l + sw + hh) { c.drawLine(q, rect.bottom, q + hh * 2, rect.top, paint); q += u * 0.38f }
            c.restore()
        }
        // líneas de barrido muy tenues
        paint.strokeWidth = 1f
        paint.color = Color.argb(al * 28 / 255, 0, 0, 0)
        var ly = rect.top + 2f
        while (ly < rect.bottom) { c.drawLine(rect.left + sw, ly, rect.right - sw, ly, paint); ly += 3f }
        // lámparas de aviso giratorias
        val fire = sprFire
        if (fire != null) {
            val beat = 0.5f + 0.5f * sin(time * 9f)
            for (side in intArrayOf(-1, 1)) {
                val lx = x + side * (hw - sw * 0.5f - u * 0.18f)
                val r = u * (0.5f + 0.35f * beat)
                rect.set(lx - r, y - hh - r * 0.3f, lx + r, y - hh + r * 0.7f)
                addPaint.alpha = (110 * k * beat).toInt()
                c.drawBitmap(fire, null, rect, addPaint)
            }
        }
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.setShadowLayer(u * 0.12f, 0f, u * 0.04f, Color.argb(al, 0, 0, 0))
        paint.textSize = u * 0.4f
        paint.letterSpacing = 0.3f
        paint.color = Color.argb((al * (0.75f + 0.25f * sin(time * 9f))).toInt(), 255, 178, 60)
        c.drawText("ALERTA", x, y - u * 0.18f, paint)
        paint.letterSpacing = 0.06f
        paint.textSize = u * 0.56f
        paint.color = Color.argb(al, 236, 232, 222)
        c.drawText(evNames[evKind], x, y + u * 0.42f, paint)
        paint.letterSpacing = 0f
        paint.clearShadowLayer()
    }

    /** Insignia de medalla estilo Halo: forma y color según el tipo. */
    private fun drawMedal(c: Canvas, cx: Float, cy: Float, sz: Float, idx: Int, a: Float = 1f) {
        val al = (255 * a).toInt().coerceIn(0, 255)
        val col = when {
            idx <= 3 -> intArrayOf(180 + idx * 20, 190 + idx * 10, 210 - idx * 50)
            idx <= 9 -> intArrayOf(255, 120 + (9 - idx) * 15, 40)
            idx <= 11 -> intArrayOf(230, 50, 40)
            else -> intArrayOf(255, 210, 60)
        }
        paint.style = Paint.Style.FILL
        val r = sz / 2f
        path.reset()
        when {
            idx <= 3 -> path.addCircle(cx, cy, r, Path.Direction.CW)
            idx <= 9 -> {
                path.moveTo(cx - r * 0.85f, cy - r * 0.8f); path.lineTo(cx + r * 0.85f, cy - r * 0.8f)
                path.lineTo(cx + r * 0.85f, cy + r * 0.1f); path.quadTo(cx + r * 0.7f, cy + r * 0.75f, cx, cy + r)
                path.quadTo(cx - r * 0.7f, cy + r * 0.75f, cx - r * 0.85f, cy + r * 0.1f); path.close()
            }
            idx <= 11 -> {
                for (i in 0 until 8) {
                    val ang = PI.toFloat() / 8f + i * PI.toFloat() / 4f
                    if (i == 0) path.moveTo(cx + cos(ang) * r, cy + sin(ang) * r) else path.lineTo(cx + cos(ang) * r, cy + sin(ang) * r)
                }
                path.close()
            }
            else -> { path.moveTo(cx, cy - r); path.lineTo(cx + r, cy + r * 0.8f); path.lineTo(cx - r, cy + r * 0.8f); path.close() }
        }
        paint.color = Color.argb(al, 20, 22, 30)
        c.drawPath(path, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sz * 0.07f
        paint.color = Color.argb(al, col[0], col[1], col[2])
        c.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        when {
            idx in 1..3 -> for (i in 0..idx) chevron(c, cx, cy - r * 0.35f + i * r * 0.22f - idx * r * 0.08f, r * 0.45f, r * 0.25f, r * 0.1f)
            idx == 0 -> c.drawCircle(cx, cy, r * 0.25f, paint)
            idx <= 9 -> for (i in 0 until idx - 3) star(c, cx + ((i % 3) - (min(idx - 3, 3) - 1) / 2f) * r * 0.45f, cy - r * 0.15f + (i / 3) * r * 0.42f, r * 0.2f)
            idx <= 11 -> { star(c, cx, cy, r * 0.5f); if (idx == 11) { paint.style = Paint.Style.STROKE; c.drawCircle(cx, cy, r * 0.62f, paint); paint.style = Paint.Style.FILL } }
            else -> { rect.set(cx - r * 0.07f, cy - r * 0.35f, cx + r * 0.07f, cy + r * 0.25f); c.drawRect(rect, paint); c.drawCircle(cx, cy + r * 0.45f, r * 0.08f, paint) }
        }
    }

    private fun drawMedals(c: Canvas) {
        var y = barY1 + u * 3.4f
        for (m in medalPops) {
            val inK = (m.t / 0.25f).coerceIn(0f, 1f)
            val outK = ((2.8f - m.t) / 0.5f).coerceIn(0f, 1f)
            val a = inK * outK
            val sc = 1.6f - 0.6f * inK
            val sz = u * 1.3f * sc
            drawMedal(c, u * 1.3f, y, sz, m.idx, a)
            paint.textAlign = Paint.Align.LEFT
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = u * 0.42f
            paint.color = Color.argb((255 * a).toInt(), 255, 255, 255)
            c.drawText(Medals.names[m.idx], u * 2.3f, y + u * 0.15f, paint)
            y += u * 1.5f
        }
    }

    private fun drawAchToast(c: Canvas) {
        if (achToasts.isEmpty()) return
        val a = achToasts[0]
        val k = (achT / 0.3f).coerceIn(0f, 1f) * ((3.2f - achT) / 0.4f).coerceIn(0f, 1f)
        val y = topInset + u * 0.4f + (k - 1f) * u * 2f + u * 0.9f
        rect.set(u * 1.2f, y - u * 0.8f, width - u * 1.2f, y + u * 0.8f)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((235 * k).toInt(), 18, 26, 50)
        c.drawRoundRect(rect, u * 0.35f, u * 0.35f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.argb((255 * k).toInt(), 255, 210, 90)
        c.drawRoundRect(rect, u * 0.35f, u * 0.35f, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = u * 0.32f
        paint.color = Color.argb((255 * k).toInt(), 255, 210, 90)
        c.drawText("LOGRO DESBLOQUEADO  ·  +${a.reward} lingotes", width / 2f, y - u * 0.2f, paint)
        paint.textSize = u * 0.48f
        paint.color = Color.argb((255 * k).toInt(), 255, 255, 255)
        c.drawText(a.name, width / 2f, y + u * 0.42f, paint)
    }

    /** Destello que barre la fila completada, ondas de choque, bolas de fuego en capas y humo. */
    private fun drawBooms(c: Canvas) {
        val gf = sprFire ?: return
        val gw = sprWhite ?: return
        val rd = sprRed ?: return
        val sm = sprSmoke ?: return
        for (r in rowFlash) {
            val t = r[1]
            val yc = oy + (r[0] + 0.5f) * s
            val q = (t / 0.9f).coerceIn(0f, 1f)
            rect.set(-s * 2f, yc - s * (0.8f + 1.5f * q), width + s * 2f, yc + s * (0.8f + 1.5f * q))
            addPaint.alpha = (255 * (1f - q) * (1f - q)).toInt()
            c.drawBitmap(gw, null, rect, addPaint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = s * 0.12f * (1f - q) + 1f
            paint.color = Color.argb((220 * (1f - q)).toInt(), 255, 220, 170)
            val dy = s * 7f * q
            c.drawLine(0f, yc - dy, width.toFloat(), yc - dy, paint)
            c.drawLine(0f, yc + dy, width.toFloat(), yc + dy, paint)
            paint.style = Paint.Style.FILL
        }
        for (b in booms) {
            if (b.t < 0f) continue
            if (b.plasma) { drawPlasmaBoom(c, b); continue }
            val t = b.t
            val base = s * 1.5f * b.size
            if (t > 0.2f) {
                val st = ((t - 0.2f) / 2.4f).coerceIn(0f, 1f)
                val r = base * (0.6f + 1.4f * st)
                rect.set(b.x - r, b.y - r - base * st, b.x + r, b.y + r - base * st)
                objPaint.alpha = (170f * (1f - st) * min(1f, (t - 0.2f) * 4f)).toInt().coerceIn(0, 255)
                c.drawBitmap(sm, null, rect, objPaint)
            }
            if (t < 1.2f) {
                val q = t / 1.2f
                val grow = 1f - (1f - q) * (1f - q)
                var r = base * (0.3f + 1.1f * grow)
                rect.set(b.x - r * 1.6f, b.y - r * 1.6f, b.x + r * 1.6f, b.y + r * 1.6f)
                addPaint.alpha = (170 * (1f - q)).toInt().coerceIn(0, 255)
                c.drawBitmap(rd, null, rect, addPaint)
                rect.set(b.x - r, b.y - r, b.x + r, b.y + r)
                addPaint.alpha = (255 * (1f - q)).toInt().coerceIn(0, 255)
                c.drawBitmap(gf, null, rect, addPaint)
                r *= 0.45f
                rect.set(b.x - r, b.y - r, b.x + r, b.y + r)
                addPaint.alpha = (255 * (1f - q) * (1f - q)).toInt().coerceIn(0, 255)
                c.drawBitmap(gw, null, rect, addPaint)
            }
            if (t < 0.7f) {
                val q = t / 0.7f
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = s * 0.08f * (1f - q) + 1f
                paint.color = Color.argb((200 * (1f - q)).toInt(), 255, 210, 150)
                c.drawCircle(b.x, b.y, base * 1.8f * q, paint)
                paint.style = Paint.Style.FILL
            }
        }
    }

    /** Explosión de plasma (como la granada de plasma): núcleo blanco-azul cegador, bola de plasma con filamentos y anillo eléctrico. */
    private fun drawPlasmaBoom(c: Canvas, b: Boom) {
        val t = b.t
        val base = s * 1.5f * b.size
        val st = sprSteel ?: return
        val wh = sprWhite ?: return
        if (t < 0.18f) {  // destello inicial azul que ciega
            val q = t / 0.18f
            val r = base * (2.5f + 2f * q)
            rect.set(b.x - r, b.y - r, b.x + r, b.y + r)
            addPaint.alpha = (255 * (1f - q * 0.4f)).toInt(); c.drawBitmap(st, null, rect, addPaint)
        }
        if (t < 1.4f) {
            val q = t / 1.4f
            val grow = 1f - (1f - q) * (1f - q)
            val pl = plasmaTex
            if (pl != null) {
                val r = base * (0.4f + 1.3f * grow)
                for (k in 0 until 2) {
                    c.save(); c.rotate(t * (if (k == 0) 140f else -200f), b.x, b.y)
                    rect.set(b.x - r, b.y - r, b.x + r, b.y + r)
                    bluePaint.alpha = (255 * (1f - q) * (if (k == 0) 1f else 0.7f)).toInt().coerceIn(0, 255)
                    c.drawBitmap(pl, null, rect, bluePaint)
                    c.restore()
                }
            }
            var r = base * (0.5f + 1.2f * grow)
            rect.set(b.x - r, b.y - r, b.x + r, b.y + r)
            addPaint.alpha = (220 * (1f - q)).toInt().coerceIn(0, 255); c.drawBitmap(st, null, rect, addPaint)
            r *= 0.35f
            rect.set(b.x - r, b.y - r, b.x + r, b.y + r)
            addPaint.alpha = (255 * (1f - q) * (1f - q)).toInt().coerceIn(0, 255); c.drawBitmap(wh, null, rect, addPaint)
        }
        if (t < 0.8f) {  // anillo eléctrico que se expande, tembloroso
            val q = t / 0.8f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = s * 0.12f * (1f - q) + 1f
            paint.color = Color.argb((230 * (1f - q)).toInt(), 140, 200, 255)
            c.drawCircle(b.x, b.y, base * 2.2f * q * (1f + 0.04f * sin(t * 90f)), paint)
            paint.style = Paint.Style.FILL
        }
    }

    /** Partículas como sprites suaves con mezcla aditiva (sin cuadrados). */
    private fun drawSparks(c: Canvas) {
        for (p in sparks) {
            val f = p.life / p.max
            val r = p.size * (0.6f + f) * 1.2f
            rect.set(p.x - r, p.y - r, p.x + r, p.y + r)
            addPaint.alpha = (255 * f).toInt().coerceIn(0, 255)
            c.drawBitmap(p.spr, null, rect, addPaint)
        }
    }

    private fun drawChips(c: Canvas) {
        val st = sprSteel
        paint.style = Paint.Style.FILL
        for (ch in chips) {
            val p = ch.t - ch.delay
            val sz = u * 0.15f
            var x = ch.x0
            var y = ch.y0
            var rot = 0f
            if (p >= 0f) {
                val f0 = (p / ch.dur).coerceIn(0f, 1f)
                val f = f0 * f0 * (3f - 2f * f0)
                x = ch.x0 + (chipTx - ch.x0) * f + sin(f * PI.toFloat()) * u * 2.2f * ch.side
                y = ch.y0 + (chipTy - ch.y0) * f
                rot = 45f + f * 360f
                if (st != null) {
                    val r = sz * 3.2f
                    rect.set(x - r, y - r, x + r, y + r)
                    addPaint.alpha = 140
                    c.drawBitmap(st, null, rect, addPaint)
                }
            }
            c.save()
            c.rotate(rot, x, y)
            paint.color = Color.rgb(120, 135, 160)
            rect.set(x - sz, y - sz * 0.7f, x + sz, y + sz * 0.7f)
            c.drawRoundRect(rect, sz * 0.35f, sz * 0.35f, paint)
            paint.color = Color.rgb(228, 238, 250)
            rect.set(x - sz * 0.8f, y - sz * 0.55f, x + sz * 0.2f, y - sz * 0.05f)
            c.drawRoundRect(rect, sz * 0.2f, sz * 0.2f, paint)
            c.restore()
        }
    }

    private fun perimeterPoint(l: Float, t: Float, r: Float, b: Float, k: Float, out: FloatArray) {
        val w = r - l
        val h = b - t
        val d = (k - floor(k)) * 2f * (w + h)
        if (d < w) { out[0] = l + d; out[1] = t }
        else if (d < w + h) { out[0] = r; out[1] = t + d - w }
        else if (d < 2f * w + h) { out[0] = r - (d - w - h); out[1] = b }
        else { out[0] = l; out[1] = b - (d - 2f * w - h) }
    }

    private val weldPt = FloatArray(2)

    /** Chispas del punto de soldadura que recorre la barra mientras el juego está congelado. */
    private fun emitWeld(dt: Float) {
        val wh = sprWhite ?: return
        val fi = sprFire ?: return
        val ck = 1f - celebT / CELEB
        val cy = (barY0 + barY1) / 2f
        val hh = (barY1 - barY0) / 2f * (1f + 0.22f * sin(PI.toFloat() * min(1f, ck * 1.5f)))
        for (i in 0 until 3) {
            perimeterPoint(barX0, cy - hh, barX1, cy + hh, ck * 1.6f + i / 3f, weldPt)
            repeat(3) {
                val a = Random.nextFloat() * 6.283f
                val v = u * (0.8f + Random.nextFloat() * 3.2f)
                sparks.add(Spark(weldPt[0], weldPt[1], cos(a) * v, sin(a) * v - u * 0.6f, 0.35f, 0.35f,
                    if (it == 0) wh else fi, u * (0.05f + Random.nextFloat() * 0.06f)))
            }
        }
    }

    /** Contorno cálido tenue y puntos de soldadura alrededor de la barra al completarse. */
    /**
     * Recarga de la barra al completarse, al estilo de la recarga de escudos: destello, la barra se vacía con
     * un parpadeo y luego se llena desde el centro hacia los lados con un frente de energía brillante.
     */
    private fun drawShieldRecharge(c: Canvas, ck: Float) {
        val cx = (barX0 + barX1) / 2f
        val half = (barX1 - barX0) / 2f
        val r = u * 0.21f
        rect.set(barX0, barY0, barX1, barY1)
        when {
            ck < 0.12f -> {  // destello completo
                paint.color = Color.argb((255 * (1f - ck / 0.12f * 0.3f)).toInt(), 235, 250, 255)
                c.drawRoundRect(rect, r, r, paint)
            }
            ck < 0.3f -> {  // vacía, con parpadeo de alerta
                val on = ((ck * 60f).toInt() % 2 == 0)
                paint.color = if (on) Color.argb(200, 255, 70, 60) else Color.argb(120, 40, 10, 10)
                c.drawRoundRect(rect, r, r, paint)
            }
            else -> {
                val q = ((ck - 0.3f) / 0.55f).coerceIn(0f, 1f)
                val e = 1f - (1f - q) * (1f - q) * (1f - q)
                val w = half * e
                rect.set(cx - w, barY0, cx + w, barY1)
                paint.shader = LinearGradient(barX0, 0f, barX1, 0f,
                    intArrayOf(Color.rgb(70, 170, 255), Color.rgb(200, 240, 255), Color.rgb(70, 170, 255)), null, Shader.TileMode.CLAMP)
                c.drawRoundRect(rect, r, r, paint)
                paint.shader = null
                // segmentos de la barra
                paint.color = Color.argb(70, 0, 20, 50)
                var x = barX0 + u * 0.5f
                while (x < barX1) { if (abs(x - cx) < w) c.drawRect(x, barY0, x + 2f, barY1, paint); x += u * 0.5f }
                // frentes de energía en los extremos que avanzan
                val st = sprSteel; val wh = sprWhite
                if (st != null && wh != null && q < 1f) {
                    for (sgn in intArrayOf(-1, 1)) {
                        val fx = cx + sgn * w
                        val rr = u * (0.7f + 0.3f * sin(time * 50f))
                        rect.set(fx - rr, (barY0 + barY1) / 2f - rr, fx + rr, (barY0 + barY1) / 2f + rr)
                        addPaint.alpha = 230; c.drawBitmap(st, null, rect, addPaint)
                        rect.set(fx - rr * 0.4f, (barY0 + barY1) / 2f - rr * 0.4f, fx + rr * 0.4f, (barY0 + barY1) / 2f + rr * 0.4f)
                        addPaint.alpha = 255; c.drawBitmap(wh, null, rect, addPaint)
                    }
                }
                // brillo general al quedar llena
                if (ck > 0.85f) {
                    val g2 = 1f - (ck - 0.85f) / 0.15f
                    val st2 = sprSteel
                    if (st2 != null) {
                        rect.set(barX0 - u, barY0 - u * 0.8f, barX1 + u, barY1 + u * 0.8f)
                        addPaint.alpha = (160 * g2).toInt().coerceIn(0, 255)
                        c.drawBitmap(st2, null, rect, addPaint)
                    }
                }
            }
        }
    }

    private fun drawWeld(c: Canvas, ck: Float, pulseE: Float, bsx: Float, bsy: Float) {
        val wh = sprWhite ?: return
        val fi = sprFire ?: return
        val fade0 = (1f - ck).coerceIn(0f, 1f)
        // destello radial con rayos y ola de luz que recorre la barra
        val mx = (barX0 + barX1) / 2f
        val my = (barY0 + barY1) / 2f
        val fl = sin(PI.toFloat() * min(1f, ck * 1.3f)).coerceAtLeast(0f)
        var rr = u * 4.2f * (0.5f + 0.5f * fl)
        rect.set(mx - rr, my - rr, mx + rr, my + rr)
        addPaint.alpha = (150 * fl).toInt().coerceIn(0, 255)
        c.drawBitmap(wh, null, rect, addPaint)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = u * 0.06f
        for (i in 0 until 8) {
            val ang = i * PI.toFloat() / 4f + ck * 1.2f
            paint.color = Color.argb((130 * fl).toInt().coerceIn(0, 255), 255, 235, 190)
            c.drawLine(mx + cos(ang) * u * 0.8f, my + sin(ang) * u * 0.8f * 0.5f, mx + cos(ang) * rr * 0.9f, my + sin(ang) * rr * 0.9f * 0.5f, paint)
        }
        paint.style = Paint.Style.FILL
        val sweepX = barX0 + (barX1 - barX0) * (ck * 1.7f).coerceIn(0f, 1f)
        if (ck < 0.6f) {
            rr = u * 1.0f
            rect.set(sweepX - rr, barY0 - u * 0.3f, sweepX + rr, barY1 + u * 0.3f)
            addPaint.alpha = (200 * (1f - ck / 0.6f)).toInt().coerceIn(0, 255)
            c.drawBitmap(wh, null, rect, addPaint)
        }
        val cx = (barX0 + barX1) / 2f
        val cy = (barY0 + barY1) / 2f
        val hw = (barX1 - barX0) / 2f * bsx
        val hh = (barY1 - barY0) / 2f * bsy
        val fade = (1f - ck).coerceIn(0f, 1f)
        val flick = 0.6f + 0.4f * sin(time * 40f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.argb((110 * pulseE * flick).toInt().coerceIn(0, 255), 255, 205, 130)
        rect.set(cx - hw, cy - hh, cx + hw, cy + hh)
        c.drawRoundRect(rect, u * 0.25f, u * 0.25f, paint)
        paint.style = Paint.Style.FILL
        for (i in 0 until 3) {
            perimeterPoint(cx - hw, cy - hh, cx + hw, cy + hh, ck * 1.6f + i / 3f, weldPt)
            var r = u * 0.55f
            rect.set(weldPt[0] - r, weldPt[1] - r, weldPt[0] + r, weldPt[1] + r)
            addPaint.alpha = (140 * fade * flick).toInt().coerceIn(0, 255)
            c.drawBitmap(fi, null, rect, addPaint)
            r = u * 0.26f
            rect.set(weldPt[0] - r, weldPt[1] - r, weldPt[0] + r, weldPt[1] + r)
            addPaint.alpha = (235 * fade).toInt().coerceIn(0, 255)
            c.drawBitmap(wh, null, rect, addPaint)
        }
    }

    private fun switchTab(n: Int) {
        achOpen = false
        palPick = -1
        if (n == tab) return
        prevTab = tab
        tabDir = if (TAB_ORDER.indexOf(n) > TAB_ORDER.indexOf(tab)) 1f else -1f
        tab = n
        tabT = 0f
        if (n == 1) { preview = skinSel }
    }

    /** Lingote dibujado una vez en un bitmap (evita crear gradientes en cada fotograma). */
    private fun makeIngot(): Bitmap {
        val w = 128f
        val h = w * 0.55f
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt() + 2, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = w / 2f
        val cy = h / 2f + 1f
        val topW = w * 0.72f
        val pth = Path()
        pth.moveTo(cx - topW / 2f, cy - h / 2f)
        pth.lineTo(cx + topW / 2f, cy - h / 2f)
        pth.lineTo(cx + w / 2f, cy + h / 2f)
        pth.lineTo(cx - w / 2f, cy + h / 2f)
        pth.close()
        p.shader = LinearGradient(cx, cy - h / 2f, cx, cy + h / 2f,
            intArrayOf(Color.rgb(225, 232, 245), Color.rgb(130, 145, 170), Color.rgb(70, 82, 105)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        cv.drawPath(pth, p)
        p.shader = null
        p.color = Color.argb(200, 255, 255, 255)
        cv.drawRoundRect(cx - topW * 0.38f, cy - h * 0.36f, cx + topW * 0.2f, cy - h * 0.2f, h * 0.08f, h * 0.08f, p)
        return bmp
    }

    private fun drawIngot(c: Canvas, cx: Float, cy: Float, w: Float) {
        val b = ingotBmp ?: return
        val h = w * b.height / b.width
        rect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        c.drawBitmap(b, null, rect, bmpPaint)
    }

    private fun wrap(text: String, maxW: Float): List<String> {
        val lines = ArrayList<String>()
        var cur = ""
        for (w in text.split(" ")) {
            val t = if (cur.isEmpty()) w else "$cur $w"
            if (paint.measureText(t) > maxW && cur.isNotEmpty()) { lines.add(cur); cur = w } else cur = t
        }
        if (cur.isNotEmpty()) lines.add(cur)
        return lines
    }

    private var travelT = 0f
    private var approachT = -1f
    private var loadFade = 0f
    private var travelStars: Array<TStar>? = null
    private var travelGalaxy: Bitmap? = null
    private var travelZ = 0f

    /**
     * Viaje de la pantalla de carga: desde una galaxia lejana que crece y nos envuelve, pasando entre estrellas
     * en 3D, nubes de neblina cósmica y soles lejanos, hasta llegar al planeta (que aparece cuando todo cargó).
     */
    private fun drawTravel(c: Canvas, alpha: Float) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = ccx; val cy = ccy
        val dt = frameDt
        val al = alpha.coerceIn(0f, 1f)
        val arrive = if (approachT >= 0f) (approachT / APPROACH).coerceIn(0f, 1f) else 0f
        // etapas: 1 acercarse a la galaxia, 2 atravesarla a toda velocidad, 3 entrar al sistema solar, 4 llegar al planeta
        val s1 = (travelT / T_GAL).coerceIn(0f, 1f)
        val s2 = ((travelT - T_GAL) / (T_THRU - T_GAL)).coerceIn(0f, 1f)
        val s3 = ((travelT - T_THRU) / (T_SYS - T_THRU)).coerceIn(0f, 1f)
        val warp = if (s2 in 0.001f..0.999f) kotlin.math.sin(s2 * PI.toFloat()) else 0f
        val v = (0.35f + 0.5f * s1 + 2.6f * warp) * (1f - 0.7f * s3) * (1f - 0.85f * arrive)
        travelZ += v * dt
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = Color.argb((255 * al * (1f - arrive * 0.85f)).toInt(), 2, 3, 10)
        c.drawRect(0f, 0f, w, h, paint)
        // durante la llegada el planeta se dibuja aquí también, así no queda tapado por el fondo del viaje
        if (approachT >= 0f) {
            val pd0 = cache[skinSel]
            if (pd0 != null) {
                val r0 = planetR * planetSc
                rect.set(ccx - r0, ccy - r0, ccx + r0, ccy + r0)
                c.drawBitmap(pd0.bmp, null, rect, bmpPaint)
            }
        }
        if (travelGalaxy == null) travelGalaxy = try {
            val dir = context.cacheDir
            DiskCache.loadNamed(dir, "galaxy_travel") ?: Textures.makeTravelGalaxy(448).also { b -> saveAsync { DiskCache.saveNamed(dir, "galaxy_travel", b) } }
        } catch (e: Throwable) { galaxyBmp }
        // 1-2: la galaxia crece, se inclina hacia nosotros y la atravesamos (se abre y se desvanece alrededor)
        val gb = travelGalaxy
        if (gb != null && s2 < 1f) {
            val e1 = s1 * s1 * (3f - 2f * s1)
            val gs = w * (0.22f + 1.6f * e1 + 6f * s2 * s2)
            val ga = (1f - s2 * 1.15f).coerceIn(0f, 1f)
            val tilt = 0.38f + 0.5f * e1 + 0.12f * s2
            for (layer in 0 until 2) {
                c.save(); c.translate(cx, cy); c.rotate(-24f + travelT * (2.5f + layer)); c.scale(1f, tilt * (1f + layer * 0.08f))
                val gl = gs * (1f + layer * 0.12f)
                rect.set(-gl, -gl, gl, gl)
                addPaint.alpha = ((if (layer == 0) 235 else 90) * ga * al).toInt().coerceIn(0, 255)
                c.drawBitmap(gb, null, rect, addPaint)
                c.restore()
            }
            val wh0 = sprWhite
            if (wh0 != null) {
                val cr = gs * 0.3f
                rect.set(cx - cr, cy - cr * tilt, cx + cr, cy + cr * tilt)
                addPaint.alpha = (150 * ga * al).toInt().coerceIn(0, 255)
                c.drawBitmap(wh0, null, rect, addPaint)
            }
        }
        // neblina y nebulosas de color: sobre todo al atravesar la galaxia
        val hz = haze
        val dust = (0.25f + 0.75f * warp + 0.2f * s1 * (1f - s3)) * (1f - 0.7f * s3)
        if (hz != null && dust > 0.02f) {
            for (k in 0 until 4) {
                val ph = ((travelZ * 0.45f + k / 4f) % 1f)
                val sc = 0.4f + ph * ph * 4f
                val a = kotlin.math.sin(ph * PI.toFloat()) * 0.9f
                c.save(); c.translate(cx, cy); c.rotate(k * 77f + travelT * 2f)
                rect.set(-w * sc, -h * sc, w * sc, h * sc)
                addPaint.alpha = (200 * a * al * dust).toInt().coerceIn(0, 255)
                c.drawBitmap(hz, null, rect, addPaint)
                c.restore()
            }
            for (k in 0 until 3) {
                val ph = ((travelZ * 0.33f + k * 0.31f) % 1f)
                val zz = 1f - ph
                if (zz < 0.05f) continue
                val ang = k * 2.4f + 1f
                val px0 = cx + cos(ang) * 0.6f / zz * w * 0.25f
                val py0 = cy + sin(ang) * 0.5f / zz * w * 0.25f
                val rr = w * 0.35f / zz
                rect.set(px0 - rr, py0 - rr * 0.7f, px0 + rr, py0 + rr * 0.7f)
                val pnt = if (k % 2 == 0) hotPaint else bluePaint
                pnt.alpha = (al * 120 * dust * kotlin.math.sin(ph * PI.toFloat())).toInt().coerceIn(0, 255)
                c.save(); c.rotate(k * 50f, px0, py0); c.drawBitmap(hz, null, rect, pnt); c.restore()
            }
        }
        // estrellas en 3D: estelas largas en el salto, puntos tranquilos en el sistema solar
        val ts = travelStars ?: Array(320) {
            TStar((Random.nextFloat() - 0.5f) * 2f, (Random.nextFloat() - 0.5f) * 2f, 0.05f + Random.nextFloat(), 0f, 0.4f + Random.nextFloat() * 0.6f)
        }.also { travelStars = it }
        val f = w * 0.5f
        paint.strokeCap = Paint.Cap.ROUND
        paint.style = Paint.Style.STROKE
        for (st in ts) {
            st.pz = st.z
            st.z -= v * dt * 0.55f
            if (st.z <= 0.02f) { st.x = (Random.nextFloat() - 0.5f) * 2f; st.y = (Random.nextFloat() - 0.5f) * 2f; st.z = 1f; st.pz = 1f }
            val x1 = cx + st.x / st.z * f * 0.5f; val y1 = cy + st.y / st.z * f * 0.5f
            val x0 = cx + st.x / st.pz * f * 0.5f; val y0 = cy + st.y / st.pz * f * 0.5f
            if (x1 < -20 || x1 > w + 20 || y1 < -20 || y1 > h + 20) continue
            val near = (1f - st.z).coerceIn(0f, 1f)
            paint.strokeWidth = 1f + 2.2f * near * st.b
            paint.color = Color.argb((al * 255 * st.b * (0.25f + 0.75f * near)).toInt().coerceIn(0, 255), 225, 232, 255)
            c.drawLine(x0, y0, x1, y1, paint)
        }
        paint.style = Paint.Style.FILL
        // 3: sistema solar — el sol aparece al frente, crece y queda a un lado mientras cruzamos las órbitas
        val wh = sprWhite
        val fire = sprFire
        if (s3 > 0f && wh != null && fire != null) {
            val e3 = s3 * s3 * (3f - 2f * s3)
            val sunX = cx - w * 0.05f - w * 0.75f * e3 * e3
            val sunY = cy - h * 0.05f - h * 0.12f * e3
            val sunR = u * (0.6f + 7f * e3)
            val sa = (s3 / 0.15f).coerceIn(0f, 1f) * (1f - arrive)
            // órbitas tenues alrededor del sol
            for (k in 1..4) {
                val orx = w * (0.18f + 0.22f * k) * (0.6f + 1.8f * e3)
                // un planeta pequeño en cada órbita
                val pa = k * 1.7f + travelT * 0.15f / k
                val ppx = sunX + cos(pa) * orx; val ppy = sunY + sin(pa) * orx * 0.32f
                paint.style = Paint.Style.FILL
                paint.color = Color.argb((al * 200 * sa).toInt().coerceIn(0, 255), 150 + 25 * k, 140 + 10 * k, 130)
                c.drawCircle(ppx, ppy, u * (0.1f + 0.04f * k) * (0.7f + e3), paint)
            }
            paint.style = Paint.Style.FILL
            for ((bm, sc, a) in listOf(Triple(fire, 4.2f, 120), Triple(fire, 2.2f, 210), Triple(wh, 1.1f, 255))) {
                val r = sunR * sc
                rect.set(sunX - r, sunY - r, sunX + r, sunY + r)
                addPaint.alpha = (a * al * sa).toInt().coerceIn(0, 255)
                c.drawBitmap(bm, null, rect, addPaint)
            }
            // destello de lente en línea con el centro de la pantalla
            for (k in 1..3) {
                val t = k * 0.35f
                val fx = sunX + (cx - sunX) * t * 2f; val fy = sunY + (cy - sunY) * t * 2f
                val r = u * (0.3f + 0.25f * k)
                rect.set(fx - r, fy - r, fx + r, fy + r)
                addPaint.alpha = (45 * al * sa).toInt().coerceIn(0, 255)
                c.drawBitmap(if (k == 2) fire else wh, null, rect, addPaint)
            }
            // nuestro planeta: un punto azulado al centro que crece hasta que empieza la llegada
            if (approachT < 0f) {
                // nuestro planeta con su textura real (si ya cargó), pequeño y creciendo
                val pd = cache[skinSel]
                val pr = planetR * MENU_SC * APPROACH_SC0 * (0.15f + 0.85f * e3)
                val pa = (al * 255 * (s3 / 0.4f).coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
                if (pd != null) {
                    rect.set(cx - pr, cy - pr, cx + pr, cy + pr)
                    objPaint.alpha = pa
                    c.drawBitmap(pd.bmp, null, rect, objPaint)
                    objPaint.alpha = 255
                } else {
                    paint.color = Color.argb(pa, 120, 150, 200)
                    c.drawCircle(cx, cy, pr * 0.9f, paint)
                }
            }
        }
    }

    // ---------- logros cercanos (icono en el menú) ----------
    private val achIcon = RectF()
    private var achOpen = false
    private var achAnim = 0f

    /** Los 3 logros sin desbloquear con mayor avance. */
    private fun nearAch(): List<Pair<Ach, Float>> = Achievements.all.filter { !stats.unlocked(it.id) }
        .map { it to (stats.get(it.stat).toFloat() / it.goal).coerceIn(0f, 0.999f) }
        .sortedByDescending { it.second }.take(3)

    /** Icono de lista con un signo "!" que da un saltito cada pocos segundos; al tocarlo muestra los 3 logros. */
    private fun drawAchWidget(c: Canvas, a: Float) {
        val sz = u * 1.5f
        achIcon.set(u * 0.45f, tabRect0.bottom + u * 0.4f, u * 0.45f + sz, tabRect0.bottom + u * 0.45f + sz)
        val near = nearAch()
        achAnim += ((if (achOpen) 1f else 0f) - achAnim) * min(1f, frameDt * 10f)
        val al = (255 * a).toInt()
        // leve balanceo lento del icono
        val sway = sin(time * 1.3f) * 2.2f
        c.save(); c.rotate(sway, achIcon.centerX(), achIcon.centerY())
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(al * 170 / 255, 10, 14, 34)
        c.drawRoundRect(achIcon, u * 0.25f, u * 0.25f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
        paint.color = Color.argb(al * 120 / 255, 170, 200, 255)
        c.drawRoundRect(achIcon, u * 0.25f, u * 0.25f, paint)
        // renglones de la lista
        paint.strokeWidth = u * 0.07f; paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.argb(al * 220 / 255, 200, 220, 255)
        for (i in 0 until 3) {
            val y = achIcon.top + sz * (0.3f + i * 0.2f)
            c.drawPoint(achIcon.left + sz * 0.22f, y, paint)
            c.drawLine(achIcon.left + sz * 0.34f, y, achIcon.left + sz * 0.62f, y, paint)
        }
        c.restore()
        if (near.isNotEmpty()) {
            // signo de admiración que salta suave cada ~3.5 s
            val ph = time % 3.5f
            val hop = if (ph < 0.5f) sin(ph / 0.5f * PI.toFloat()) else 0f
            val ex = achIcon.right - sz * 0.2f
            val ey = achIcon.top + sz * 0.5f - hop * sz * 0.18f
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(al, 255, 190, 60)
            c.drawCircle(ex, ey, sz * 0.2f, paint)
            paint.color = Color.argb(al, 30, 18, 0)
            paint.textAlign = Paint.Align.CENTER; paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = sz * 0.3f
            c.drawText("!", ex, ey + sz * 0.105f, paint)
        }
        if (achAnim < 0.02f || near.isEmpty()) return
        val k = achAnim
        val pw = width - u * 0.9f; val rowH = u * 1.6f
        rect.set(achIcon.left, achIcon.bottom + u * 0.2f, achIcon.left + pw, achIcon.bottom + u * 0.2f + rowH * near.size + u * 1.1f)
        val pa = (al * k).toInt()
        c.save(); c.translate(0f, (1f - k) * -u * 0.5f)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(pa * 225 / 255, 8, 12, 28)
        c.drawRoundRect(rect, u * 0.3f, u * 0.3f, paint)
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = u * 0.48f
        paint.color = Color.argb(pa, 255, 210, 120)
        c.drawText("Logros más cercanos", rect.left + u * 0.35f, rect.top + u * 0.7f, paint)
        for ((i, pr) in near.withIndex()) {
            val (ach, f) = pr
            val y = rect.top + u * 1.05f + i * rowH
            paint.color = Color.argb(pa, 255, 255, 255); paint.textSize = u * 0.46f
            c.drawText(ach.name, rect.left + u * 0.35f, y + u * 0.48f, paint)
            paint.color = Color.argb(pa * 180 / 255, 190, 210, 240); paint.textSize = u * 0.36f
            c.drawText(ach.desc, rect.left + u * 0.35f, y + u * 0.98f, paint)
            val bx0 = rect.right - u * 3.2f; val bx1 = rect.right - u * 0.35f; val by = y + u * 0.2f
            paint.color = Color.argb(pa * 150 / 255, 40, 50, 80)
            c.drawRoundRect(bx0, by, bx1, by + u * 0.28f, u * 0.14f, u * 0.14f, paint)
            paint.color = Color.argb(pa, 90, 170, 255)
            c.drawRoundRect(bx0, by, bx0 + (bx1 - bx0) * max(0.04f, f), by + u * 0.28f, u * 0.14f, u * 0.14f, paint)
            paint.textAlign = Paint.Align.RIGHT; paint.textSize = u * 0.34f
            paint.color = Color.argb(pa * 200 / 255, 220, 230, 255)
            c.drawText("${min(stats.get(ach.stat), ach.goal)}/${ach.goal}", bx1, by + u * 0.75f, paint)
            paint.textAlign = Paint.Align.LEFT
        }
        c.restore()
    }

    private fun drawLoading(c: Canvas) {
        drawTravel(c, if (approachT >= 0f) 1f - (approachT / APPROACH).coerceIn(0f, 1f) else 1f)
        if (approachT >= 0f) return
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.WHITE
        paint.textSize = u * 1.15f
        c.drawText("FUGA ORBITAL", width / 2f, height * 0.38f, paint)
        val x0 = u * 2.5f
        val x1 = width - u * 2.5f
        val y0 = height * 0.50f
        rect.set(x0, y0, x1, y0 + u * 0.4f)
        paint.color = Color.argb(150, 10, 12, 30)
        c.drawRoundRect(rect, u * 0.2f, u * 0.2f, paint)
        val fw = (x1 - x0) * dispProgress.coerceIn(0f, 1f)
        if (fw > u * 0.2f) {
            rect.set(x0, y0, x0 + fw, y0 + u * 0.4f)
            paint.shader = LinearGradient(x0, 0f, x1, 0f, Color.rgb(60, 140, 255), Color.rgb(0, 229, 255), Shader.TileMode.CLAMP)
            c.drawRoundRect(rect, u * 0.2f, u * 0.2f, paint)
            paint.shader = null
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(140, 170, 200, 255)
        rect.set(x0, y0, x1, y0 + u * 0.4f)
        c.drawRoundRect(rect, u * 0.2f, u * 0.2f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(230, 255, 255, 255)
        paint.textSize = u * 0.42f
        c.drawText("Preparando texturas... ${(dispProgress * 100).toInt()}%", width / 2f, y0 + u * 1.3f, paint)
        paint.textSize = u * 0.32f
        paint.color = Color.argb(170, 190, 215, 255)
        c.drawText("La primera vez tarda más; después será casi instantáneo", width / 2f, y0 + u * 2.0f, paint)
        paint.textSize = u * 0.4f
        paint.color = Color.argb(240, 255, 225, 150)
        c.drawText("Consejo", width / 2f, y0 + u * 3.6f, paint)
        paint.color = Color.argb(230, 255, 255, 255)
        paint.textSize = u * 0.42f
        var ty = y0 + u * 4.3f
        for (line in wrap(tip, width - u * 3f)) { c.drawText(line, width / 2f, ty, paint); ty += u * 0.62f }
    }

    // ---------- menú ----------
    private fun drawMenu(c: Canvas) {
        val a = if (phase == 1) (1f - phaseT / 0.7f).coerceIn(0f, 1f) else 1f
        if (a <= 0f) return
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        for (i in 0..3) {
            val r = when (i) { 0 -> tabRect0; 1 -> tabRect1; 2 -> tabRect2; else -> tabRect3 }
            val sel = tab == i
            paint.style = Paint.Style.FILL
            paint.color = if (sel) Color.argb((235 * a).toInt(), 56, 92, 176) else Color.argb((150 * a).toInt(), 10, 14, 34)
            c.drawRoundRect(r, u * 0.45f, u * 0.45f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = Color.argb(((if (sel) 220 else 110) * a).toInt(), 170, 200, 255)
            c.drawRoundRect(r, u * 0.45f, u * 0.45f, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb((255 * a).toInt(), 255, 255, 255)
            val label = when (i) { 0 -> "MENÚ"; 1 -> "PERSONALIZAR"; 2 -> "PERFIL"; else -> "MEJORAS" }
            paint.textSize = u * 0.4f
            while (paint.measureText(label) > r.width() - u * 0.25f && paint.textSize > u * 0.22f) paint.textSize *= 0.94f
            c.drawText(label, r.centerX(), r.centerY() + paint.textSize * 0.35f, paint)
        }
        val e = tabT * tabT * (3f - 2f * tabT)
        if (e < 1f) {
            c.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (255 * (1f - e)).toInt())
            c.translate(-tabDir * width * 0.18f * e, 0f)
            drawTabContent(c, prevTab, a)
            c.restore()
            c.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (255 * e).toInt())
            c.translate(tabDir * width * 0.18f * (1f - e), 0f)
            drawTabContent(c, tab, a)
            c.restore()
        } else {
            drawTabContent(c, tab, a)
        }
        drawGear(c, a)
        drawPalette(c)
        if (settingsAnim > 0.01f) drawSettings(c)
    }

    private fun buyPower(i: Int) {
        val price = if (i == 0) PRICE_REGEN else PRICE_SHIELD
        if (totalIngots < price) { say("Te faltan ${price - totalIngots} lingotes"); return }
        totalIngots -= price
        if (i == 0) puRegen++ else puShield++
        saveWallet()
        say(if (i == 0) "Regeneración lista ($puRegen)" else "Sobre-escudo listo ($puShield)")
        sfx.recharge()
    }

    /** Pestaña MEJORAS: power-ups con lingotes y, más adelante, la tienda (aún sin productos). */
    private fun drawPowerTab(c: Canvas, a: Float) {
        val al = (255 * a).toInt()
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.color = Color.argb(al, 255, 255, 255)
        paint.textSize = u * 0.7f
        c.drawText("MEJORAS", width / 2f, mtop + u * 2.1f, paint)
        paint.textSize = u * 0.42f
        val label = "Tienes $totalIngots"
        val tw = paint.measureText(label)
        drawIngot(c, width / 2f - tw / 2f - u * 0.5f, mtop + u * 2.55f, u * 0.6f)
        paint.color = Color.argb(al, 255, 230, 160)
        c.drawText(label, width / 2f + u * 0.2f, mtop + u * 2.75f, paint)
        val names = arrayOf("Regeneración", "Sobre-escudo")
        val desc = arrayOf(arrayOf("Al perder, sigue la", "partida donde quedó", "(limpia la franja roja)"),
            arrayOf("Si una nave cae en la", "franja roja, explota esa", "zona y 3 filas más"))
        val owned = intArrayOf(puRegen, puShield)
        val price = intArrayOf(PRICE_REGEN, PRICE_SHIELD)
        for (i in 0 until 2) {
            val r = puCard[i]
            val bob = sin(time * 1.6f + i * 1.3f) * u * 0.06f
            c.save(); c.translate(0f, bob)
            paint.shader = LinearGradient(0f, r.top, 0f, r.bottom,
                if (i == 0) Color.argb(al * 235 / 255, 20, 60, 45) else Color.argb(al * 235 / 255, 18, 40, 80), Color.argb(al * 235 / 255, 8, 10, 24), Shader.TileMode.CLAMP)
            c.drawRoundRect(r, u * 0.45f, u * 0.45f, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f
            paint.color = if (i == 0) Color.argb(al * 200 / 255, 110, 230, 160) else Color.argb(al * 200 / 255, 120, 190, 255)
            c.drawRoundRect(r, u * 0.45f, u * 0.45f, paint)
            paint.style = Paint.Style.FILL
            // icono animado
            val icx = r.centerX(); val icy = r.top + u * 1.6f
            val glow = if (i == 0) sprFire else sprSteel
            if (glow != null) {
                val gr = u * (1.1f + 0.1f * sin(time * 3f + i))
                rect.set(icx - gr, icy - gr, icx + gr, icy + gr)
                addPaint.alpha = (140 * a).toInt(); c.drawBitmap(glow, null, rect, addPaint)
            }
            if (i == 0) {  // cruz de regeneración
                paint.color = Color.argb(al, 120, 255, 170)
                rect.set(icx - u * 0.18f, icy - u * 0.6f, icx + u * 0.18f, icy + u * 0.6f); c.drawRoundRect(rect, u * 0.08f, u * 0.08f, paint)
                rect.set(icx - u * 0.6f, icy - u * 0.18f, icx + u * 0.6f, icy + u * 0.18f); c.drawRoundRect(rect, u * 0.08f, u * 0.08f, paint)
            } else {  // escudo
                path.reset()
                path.moveTo(icx - u * 0.55f, icy - u * 0.55f); path.lineTo(icx + u * 0.55f, icy - u * 0.55f)
                path.lineTo(icx + u * 0.55f, icy); path.quadTo(icx + u * 0.45f, icy + u * 0.45f, icx, icy + u * 0.65f)
                path.quadTo(icx - u * 0.45f, icy + u * 0.45f, icx - u * 0.55f, icy); path.close()
                paint.color = Color.argb(al, 130, 200, 255); c.drawPath(path, paint)
            }
            paint.color = Color.argb(al, 255, 255, 255)
            paint.textSize = u * 0.45f
            c.drawText(names[i], icx, r.top + u * 3.3f, paint)
            paint.textSize = u * 0.3f
            paint.color = Color.argb(al * 200 / 255, 200, 215, 240)
            for ((k, line) in desc[i].withIndex()) c.drawText(line, icx, r.top + u * (3.95f + k * 0.42f), paint)
            paint.textSize = u * 0.36f
            paint.color = Color.argb(al, 255, 225, 140)
            c.drawText("Tienes: ${owned[i]}", icx, r.top + u * 5.55f, paint)
            val b = puBuy[i]
            val ok = totalIngots >= price[i]
            paint.color = if (ok) Color.argb(al, 56, 110, 230) else Color.argb(al * 160 / 255, 50, 56, 80)
            c.drawRoundRect(b, b.height() / 2f, b.height() / 2f, paint)
            paint.color = Color.argb(al, 255, 255, 255)
            paint.textSize = u * 0.38f
            val pl = "Comprar  ${price[i]}"
            val pw = paint.measureText(pl)
            c.drawText(pl, b.centerX() - u * 0.25f, b.centerY() + u * 0.13f, paint)
            drawIngot(c, b.centerX() - u * 0.25f + pw / 2f + u * 0.35f, b.centerY() - u * 0.08f, u * 0.45f)
            c.restore()
        }
        // espacio reservado para la tienda futura (sin productos todavía)
        paint.color = Color.argb(al * 150 / 255, 12, 14, 30)
        c.drawRoundRect(soonRect, u * 0.45f, u * 0.45f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
        paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(u * 0.3f, u * 0.2f), 0f)
        paint.color = Color.argb(al * 120 / 255, 170, 190, 230)
        c.drawRoundRect(soonRect, u * 0.45f, u * 0.45f, paint)
        paint.pathEffect = null
        paint.style = Paint.Style.FILL
        paint.textSize = u * 0.45f
        paint.color = Color.argb(al * 190 / 255, 210, 220, 245)
        c.drawText(if (purchases.available) "Tienda" else "Próximamente", width / 2f, soonRect.centerY() + u * 0.15f, paint)
    }

    private fun drawTabContent(c: Canvas, t: Int, a: Float) {
        when (t) { 0 -> { drawPlayTab(c, a); drawAchWidget(c, a) }; 1 -> drawSkinTab(c, a); 3 -> drawPowerTab(c, a); else -> drawProfileTab(c, a) }
    }

    private fun drawGear(c: Canvas, a: Float, at: RectF = gearC) {
        val cx = at.centerX()
        val cy = at.centerY()
        val r = u * 0.42f
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((150 * a).toInt(), 10, 14, 34)
        c.drawCircle(cx, cy, u * 0.47f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb((120 * a).toInt(), 170, 200, 255)
        c.drawCircle(cx, cy, u * 0.47f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((240 * a).toInt(), 255, 255, 255)
        for (i in 0 until 8) {
            c.save()
            c.rotate(i * 45f, cx, cy)
            rect.set(cx - r * 0.2f, cy - r * 1.0f, cx + r * 0.2f, cy - r * 0.55f)
            c.drawRoundRect(rect, r * 0.06f, r * 0.06f, paint)
            c.restore()
        }
        c.drawCircle(cx, cy, r * 0.72f, paint)
        paint.color = Color.argb((255 * a).toInt(), 20, 26, 54)
        c.drawCircle(cx, cy, r * 0.3f, paint)
    }

    private fun drawSwitch(c: Canvas, r: RectF, label: String, on: Boolean) {
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.WHITE
        paint.textSize = u * 0.5f
        c.drawText(label, r.left, r.centerY() + u * 0.18f, paint)
        val sw = RectF(r.right - u * 1.9f, r.centerY() - u * 0.4f, r.right, r.centerY() + u * 0.4f)
        paint.color = if (on) Color.rgb(50, 160, 100) else Color.rgb(90, 96, 120)
        c.drawRoundRect(sw, u * 0.4f, u * 0.4f, paint)
        paint.color = Color.WHITE
        val kx = if (on) sw.right - u * 0.4f else sw.left + u * 0.4f
        c.drawCircle(kx, sw.centerY(), u * 0.3f, paint)
        paint.color = Color.argb(230, 255, 255, 255)
        paint.textSize = u * 0.3f
        paint.textAlign = Paint.Align.CENTER
        c.drawText(if (on) "SÍ" else "NO", if (on) sw.left + u * 0.6f else sw.right - u * 0.6f, sw.centerY() + u * 0.11f, paint)
    }

    private fun drawSegments(c: Canvas, rs: Array<RectF>, labels: Array<String>, sel: Int) {
        for (i in rs.indices) {
            val r = rs[i]
            paint.style = Paint.Style.FILL
            paint.color = if (i == sel) Color.rgb(56, 110, 230) else Color.argb(200, 30, 38, 70)
            c.drawRoundRect(r, u * 0.35f, u * 0.35f, paint)
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = u * 0.4f
            c.drawText(labels[i], r.centerX(), r.centerY() + u * 0.14f, paint)
        }
    }

    private fun drawSettings(c: Canvas) {
        val an = settingsAnim.coerceIn(0f, 1f)
        c.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (255 * an).toInt())
        val e = 1f + 2.0f * (an - 1f) * (an - 1f) * (an - 1f) + 1.0f * (an - 1f) * (an - 1f)
        val sc = 0.82f + 0.18f * e
        c.translate(0f, (1f - an) * u * 4f)
        c.scale(sc, sc, width / 2f, height / 2f)
        drawSettingsBody(c)
        c.restore()
    }

    private fun drawSettingsBody(c: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(190, 0, 0, 10)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.color = Color.argb(245, 12, 16, 38)
        c.drawRoundRect(panelRect, u * 0.5f, u * 0.5f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(150, 170, 200, 255)
        c.drawRoundRect(panelRect, u * 0.5f, u * 0.5f, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.WHITE
        paint.textSize = u * 0.8f
        c.drawText("Configuración", width / 2f, panelRect.top + u * 1.3f, paint)
        drawSwitch(c, musicSw, "Música", music.enabled)
        drawSwitch(c, sfxSw, "Efectos de sonido", sfx.enabled)
        drawSwitch(c, voiceSw, "Voz del anunciador", announcer.enabled)
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.WHITE
        paint.textSize = u * 0.45f
        c.drawText("Cuadros por segundo", panelRect.left + u * 0.6f, fpsRect[0].top - u * 0.25f, paint)
        c.drawText("Gráficos", panelRect.left + u * 0.6f, gfxRect[0].top - u * 0.25f, paint)
        drawSegments(c, fpsRect, arrayOf("120", "60", "Ahorro 30"), fpsMode)
        drawSegments(c, gfxRect, arrayOf("Calidad", "Rendimiento"), gfxMode)
        if (gfxMode != gfxAtStart) {
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = u * 0.3f
            paint.color = Color.argb(220, 255, 210, 120)
            c.drawText("La calidad de texturas se aplica al volver a abrir el juego", width / 2f, gfxRect[0].bottom + u * 0.55f, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(255, 40, 60, 110)
        c.drawRoundRect(closeRect, u * 0.4f, u * 0.4f, paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = u * 0.55f
        c.drawText("Cerrar", closeRect.centerX(), closeRect.centerY() + u * 0.2f, paint)
        paint.textSize = u * 0.24f
        paint.color = Color.argb(150, 190, 205, 235)
        c.drawText("Sonidos: Salamander Grand Piano (A. Holm, CC-BY), VSCO 2 CE (Versilian Studios),", width / 2f, panelRect.bottom + u * 0.5f, paint)
        c.drawText("órgano de Ivy Audio, coro de Sonatina (CC Sampling+) y bancos CC0 de Karoryfer Lecolds", width / 2f, panelRect.bottom + u * 0.85f, paint)
    }

    private fun drawPlayTab(c: Canvas, a: Float) {
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.argb((255 * a).toInt(), 255, 255, 255)
        paint.textSize = u * 1.15f
        c.drawText("FUGA ORBITAL", width / 2f, mtop + u * 3.1f, paint)
        paint.textSize = u * 0.42f
        paint.color = Color.argb((210 * a).toInt(), 190, 215, 255)
        c.drawText("Aleja las naves del centro antes de que lleguen", width / 2f, mtop + u * 3.9f, paint)
        drawRankBlock(c, a)
        Emblem.draw(c, paint, width - u * 1.4f, mtop + u * 6.4f, u * 1.6f, emShape, emSym, Emblem.palette[emBg], Emblem.palette[emC1], Emblem.palette[emC2], a)
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.argb((220 * a).toInt(), 255, 255, 255)
        c.drawText("Récord: $best      Lingotes: $totalIngots", width / 2f, escapeRect.top - u * 0.7f, paint)
        val pulse = 0.5f + 0.5f * sin(time * 3f)
        val ok = ready()
        val al = (255 * a).toInt()
        val er = escapeRect
        val rad = er.height() * 0.42f
        // resplandor que respira detrás del botón
        val rg = sprRed
        if (ok && rg != null) {
            rect.set(er.left - u * 1.2f, er.top - u * 1.2f, er.right + u * 1.2f, er.bottom + u * 1.2f)
            addPaint.alpha = ((70 + 60 * pulse) * a).toInt()
            c.drawBitmap(rg, null, rect, addPaint)
        }
        paint.shader = if (ok) LinearGradient(0f, er.top, 0f, er.bottom, intArrayOf(Color.argb(al, 255, 96, 80), Color.argb(al, 200, 30, 30), Color.argb(al, 120, 10, 14)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP) else null
        if (!ok) paint.color = Color.argb((200 * a).toInt(), 60, 70, 100)
        c.drawRoundRect(er, rad, rad, paint)
        paint.shader = null
        if (ok) {
            // brillo que recorre el botón cada pocos segundos (efecto reluciente)
            val ph = (time % 3.2f) / 1.1f
            if (ph < 1f) {
                path.reset(); path.addRoundRect(er, rad, rad, Path.Direction.CW)
                c.save(); c.clipPath(path)
                val bx = er.left - er.width() * 0.3f + er.width() * 1.6f * ph
                paint.shader = LinearGradient(bx - u * 1.2f, 0f, bx + u * 1.2f, 0f,
                    intArrayOf(Color.argb(0, 255, 255, 255), Color.argb((150 * a).toInt(), 255, 245, 230), Color.argb(0, 255, 255, 255)), null, Shader.TileMode.CLAMP)
                c.save(); c.skew(-0.35f, 0f)
                c.drawRect(bx - u * 1.2f + er.top * 0.35f, er.top, bx + u * 1.2f + er.bottom * 0.35f, er.bottom, paint)
                c.restore()
                paint.shader = null
                c.restore()
            }
            // reflejo superior suave
            paint.color = Color.argb((55 * a).toInt(), 255, 255, 255)
            rect.set(er.left + u * 0.3f, er.top + u * 0.12f, er.right - u * 0.3f, er.centerY() - u * 0.1f)
            c.drawRoundRect(rect, rad * 0.6f, rad * 0.6f, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.argb(al, 255, (170 + 60 * pulse).toInt(), 150)
        c.drawRoundRect(er, rad, rad, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(al, 255, 255, 255)
        paint.textSize = u * 0.95f
        paint.setShadowLayer(u * 0.12f, 0f, u * 0.05f, Color.argb((160 * a).toInt(), 60, 0, 0))
        c.drawText(if (ok) "ESCAPAR" else "Cargando...", width / 2f, er.centerY() + u * 0.33f, paint)
        paint.clearShadowLayer()
        // burbujas de dificultad
        val dn = arrayOf("Normal", "Heroico", "Legendario")
        val dc = intArrayOf(Color.rgb(60, 140, 230), Color.rgb(230, 150, 40), Color.rgb(200, 40, 60))
        for (i in 0 until 3) {
            val r = diffRect[i]
            val sel = i == diff
            val rr = r.height() / 2f
            paint.style = Paint.Style.FILL
            paint.color = if (sel) Color.argb(al, Color.red(dc[i]), Color.green(dc[i]), Color.blue(dc[i])) else Color.argb((150 * a).toInt(), 14, 18, 40)
            c.drawRoundRect(r, rr, rr, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = if (sel) 3f else 2f
            paint.color = Color.argb(((if (sel) 255 else 140) * a).toInt(), Color.red(dc[i]), Color.green(dc[i]), Color.blue(dc[i]))
            c.drawRoundRect(r, rr, rr, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(al, 255, 255, 255)
            paint.textSize = u * 0.36f
            c.drawText(dn[i], r.centerX(), r.centerY() + u * 0.13f, paint)
        }
    }

    private fun cardX(i: Int) = u * 0.4f + carouselOff + i * (cardW + gap)

    private fun shopCount() = when (subTab) { 0 -> skins.size; 1 -> factions.size; 3 -> Backgrounds.all.size; 4 -> MusicThemes.all.size; else -> 0 }
    private fun shopOwned(i: Int) = when (subTab) {
        0 -> (owned shr i) and 1 == 1; 1 -> (shipOwned shr i) and 1 == 1
        3 -> (bgOwned shr i) and 1 == 1; else -> (themeOwned shr i) and 1 == 1
    }
    private fun shopSel() = when (subTab) { 0 -> skinSel; 1 -> shipSel; 3 -> bgSel; else -> themeSel }
    private fun shopPreview() = when (subTab) { 0 -> preview; 1 -> previewShip; 3 -> previewBg; else -> previewTheme }
    private fun shopName(i: Int) = when (subTab) { 0 -> skins[i].name; 1 -> factions[i].name; 3 -> Backgrounds.all[i].name; else -> MusicThemes.all[i].name }
    private fun shopPrice(i: Int) = when (subTab) { 0 -> skins[i].price; 1 -> factions[i].price; 3 -> Backgrounds.all[i].price; else -> MusicThemes.all[i].price }
    private fun shopClassified(i: Int) = when (subTab) { 0 -> skins[i].classified; 1 -> factions[i].classified; else -> false }
    private fun shopThumb(i: Int): Bitmap? = when (subTab) { 0 -> thumbs[i]; 1 -> shipThumbs[i]; else -> null }

    private fun clampCarousel() {
        val total = shopCount() * (cardW + gap) - gap + u * 0.8f
        val minOff = min(0f, width - total)
        carouselOff = carouselOff.coerceIn(minOff, 0f)
    }

    private fun switchSub(n: Int) {
        palPick = -1
        if (n == subTab) return
        subTab = n
        previewBg = bgSel
        previewTheme = themeSel
        carouselOff = 0f
        clampCarousel()
    }

    private fun drawSkinTab(c: Canvas, a: Float) {
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.argb((255 * a).toInt(), 255, 255, 255)
        paint.textSize = u * 0.62f
        c.drawText(arrayOf("Superficies de planeta", "Modelos de nave", "Emblema del piloto", "Fondos espaciales", "Temas musicales")[subTab], width / 2f, mtop + u * 2.6f, paint)
        paint.textSize = u * 0.5f
        val label = "$totalIngots"
        val tw = paint.measureText(label)
        drawIngot(c, width / 2f - tw / 2f - u * 0.5f, mtop + u * 3.35f, u * 0.7f)
        c.drawText(label, width / 2f + u * 0.2f, mtop + u * 3.55f, paint)
        // sub-pestañas
        val subNames = arrayOf("Superficies", "Naves", "Emblema", "Fondos", "Música")
        for (i in 0 until 5) {
            val r = subRects[i]
            val sel = subTab == i
            paint.style = Paint.Style.FILL
            paint.color = if (sel) Color.argb(235, 56, 92, 176) else Color.argb(150, 10, 14, 34)
            c.drawRoundRect(r, u * 0.4f, u * 0.4f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = Color.argb(if (sel) 220 else 110, 170, 200, 255)
            c.drawRoundRect(r, u * 0.4f, u * 0.4f, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.textSize = u * 0.28f
            c.drawText(subNames[i], r.centerX(), r.centerY() + u * 0.1f, paint)
        }
        if (subTab == 2) { drawEmblemEditor(c); return }
        if (subTab == 4) {
            paint.textSize = u * 0.4f
            paint.color = Color.argb(230, 200, 220, 255)
            c.drawText("Escuchando: ${MusicThemes.all[previewTheme].name}", width / 2f, cardsTop - u * 0.5f, paint)
        }
        if (subTab == 0 && skins[preview].classified) {
            paint.style = Paint.Style.FILL
            paint.color = Color.BLACK
            c.drawCircle(ccx, ccy, planetR * planetSc * 1.08f, paint)
        } else if (subTab == 0 && cache[preview] == null) {
            paint.textSize = u * 0.4f
            paint.color = Color.argb(220, 200, 220, 255)
            c.drawText("Cargando vista previa...", width / 2f, cardsTop - u * 0.5f, paint)
        }
        if (subTab == 1) {
            val tb = shipThumbs[previewShip]
            if (factions[previewShip].classified) {
                paint.style = Paint.Style.FILL
                paint.color = Color.BLACK
                rect.set(ccx - u * 4.4f, ccy - u * 3f, ccx + u * 4.4f, ccy + u * 3f)
                c.drawRoundRect(rect, u * 0.4f, u * 0.4f, paint)
            } else if (tb != null) {
                val pw = u * 8.4f
                val ph = pw * 2f / 3f
                rect.set(ccx - pw / 2f, ccy - ph / 2f, ccx + pw / 2f, ccy + ph / 2f)
                c.drawBitmap(tb, null, rect, bmpPaint)
            }
            if (shipBusy) {
                paint.textSize = u * 0.4f
                paint.color = Color.argb(230, 255, 225, 150)
                c.drawText("Aplicando modelo...", width / 2f, cardsTop - u * 0.5f, paint)
            }
        }
        for (i in 0 until shopCount()) {
            val x = cardX(i)
            if (x + cardW < 0f || x > width) continue
            rect.set(x, cardsTop, x + cardW, cardsTop + cardH)
            if (shopClassified(i)) {
                // tarjeta totalmente negra: no se ve nada
                paint.style = Paint.Style.FILL
                paint.color = Color.BLACK
                c.drawRoundRect(rect, u * 0.35f, u * 0.35f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = if (shopPreview() == i) 3f else 1.5f
                paint.color = Color.argb(if (shopPreview() == i) 140 else 60, 120, 120, 120)
                c.drawRoundRect(rect, u * 0.35f, u * 0.35f, paint)
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(150, 120, 120, 120)
                paint.textSize = u * 0.34f
                c.drawText("Clasificado", x + cardW / 2f, cardsTop + cardW * 0.98f, paint)
                continue
            }
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(205, 10, 14, 34)
            c.drawRoundRect(rect, u * 0.35f, u * 0.35f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (shopPreview() == i) 4f else 2f
            paint.color = if (shopPreview() == i) Color.rgb(130, 195, 255) else Color.argb(110, 170, 200, 255)
            c.drawRoundRect(rect, u * 0.35f, u * 0.35f, paint)
            val sz = cardW * 0.66f
            val cx = x + cardW / 2f
            val cy = cardsTop + cardW * 0.46f
            val tb = shopThumb(i)
            if (subTab == 3) {
                val bgd = Backgrounds.all[i]
                rect.set(cx - sz / 2f, cy - sz / 2f, cx + sz / 2f, cy + sz / 2f)
                paint.style = Paint.Style.FILL
                paint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, intArrayOf(bgd.top, bgd.mid, bgd.bottom), null, Shader.TileMode.CLAMP)
                c.drawRoundRect(rect, u * 0.2f, u * 0.2f, paint)
                paint.shader = RadialGradient(rect.left + sz * 0.3f, rect.top + sz * 0.3f, sz * 0.7f, intArrayOf(bgd.nebA or (0xC0 shl 24), Color.argb(0, 0, 0, 0)), null, Shader.TileMode.CLAMP)
                c.drawRoundRect(rect, u * 0.2f, u * 0.2f, paint)
                paint.shader = null
                paint.color = Color.argb(220, 255, 255, 255)
                for (k in 0 until 14) c.drawCircle(rect.left + ((k * 37) % 100) / 100f * sz, rect.top + ((k * 61) % 100) / 100f * sz, 1.5f + (k % 3), paint)
            } else if (subTab == 4) {
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(230, 150, 190, 255)
                c.drawCircle(cx - sz * 0.15f, cy + sz * 0.18f, sz * 0.12f, paint)
                c.drawCircle(cx + sz * 0.22f, cy + sz * 0.1f, sz * 0.12f, paint)
                rect.set(cx - sz * 0.05f, cy - sz * 0.3f, cx + sz * 0.0f, cy + sz * 0.18f); c.drawRect(rect, paint)
                rect.set(cx + sz * 0.32f, cy - sz * 0.38f, cx + sz * 0.37f, cy + sz * 0.1f); c.drawRect(rect, paint)
                rect.set(cx - sz * 0.05f, cy - sz * 0.38f, cx + sz * 0.37f, cy - sz * 0.28f); c.drawRect(rect, paint)
            } else if (tb != null) {
                if (subTab == 0) rect.set(cx - sz / 2f, cy - sz / 2f, cx + sz / 2f, cy + sz / 2f)
                else rect.set(cx - sz / 2f * 1.15f, cy - sz / 3f * 1.15f, cx + sz / 2f * 1.15f, cy + sz / 3f * 1.15f)
                c.drawBitmap(tb, null, rect, bmpPaint)
            } else {
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(90, 120, 140, 180)
                c.drawCircle(cx, cy, sz / 2f, paint)
            }
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.textSize = u * (if (subTab == 0) 0.4f else 0.32f)
            c.drawText(shopName(i), cx, cardsTop + cardW * 0.98f, paint)
            val ty = cardsTop + cardW * 1.18f
            paint.textSize = u * 0.34f
            if (shopOwned(i)) {
                paint.color = if (shopSel() == i) Color.rgb(120, 230, 150) else Color.argb(220, 200, 215, 240)
                c.drawText(if (shopSel() == i) "Equipada" else "Tuya", cx, ty, paint)
            } else {
                paint.color = Color.WHITE
                val pl = "${shopPrice(i)}"
                val pw = paint.measureText(pl)
                drawIngot(c, cx - pw / 2f - u * 0.3f, ty - u * 0.11f, u * 0.5f)
                c.drawText(pl, cx + u * 0.15f, ty, paint)
            }
        }
        val pv = shopPreview()
        val own = shopOwned(pv)
        val eq = pv == shopSel()
        val enough = totalIngots >= shopPrice(pv)
        val text: String
        val col: Int
        if (shopClassified(pv)) { text = "Clasificado"; col = Color.rgb(20, 20, 20) }
        else if (own && eq) { text = "Equipada"; col = Color.argb(160, 70, 90, 110) }
        else if (own) { text = "Equipar"; col = Color.rgb(50, 120, 200) }
        else { text = "Comprar · ${shopPrice(pv)}"; col = if (enough) Color.rgb(40, 150, 90) else Color.rgb(150, 60, 60) }
        paint.style = Paint.Style.FILL
        paint.color = col
        c.drawRoundRect(buyRect, u * 0.45f, u * 0.45f, paint)
        paint.color = Color.WHITE
        paint.textSize = u * 0.55f
        c.drawText(text, buyRect.centerX(), buyRect.centerY() + u * 0.2f, paint)
        if (msgT > 0f) {
            paint.textSize = u * 0.4f
            paint.color = Color.argb((255 * min(1f, msgT)).toInt(), 255, 220, 140)
            c.drawText(msg, width / 2f, buyRect.bottom + u * 0.75f, paint)
        }
    }

    private fun say(t: String) { msg = t; msgT = 2f }

    private fun actShop() {
        val i = shopPreview()
        if (shopClassified(i)) { say("Acceso restringido"); return }
        if (shopOwned(i)) {
            if (shopSel() != i) equipCurrent(i)
        } else {
            val price = shopPrice(i)
            if (totalIngots < price) { say("Te faltan ${price - totalIngots} lingotes"); return }
            totalIngots -= price
            when (subTab) {
                0 -> owned = owned or (1 shl i)
                1 -> shipOwned = shipOwned or (1 shl i)
                3 -> bgOwned = bgOwned or (1 shl i)
                else -> themeOwned = themeOwned or (1 shl i)
            }
            try {
                saveWallet(); prefs.edit().putInt("owned", owned).putInt("shipOwned", shipOwned)
                    .putInt("bgOwned", bgOwned).putInt("themeOwned", themeOwned).apply()
            } catch (e: Exception) { }
            equipCurrent(i)
            say(arrayOf("Superficie comprada", "Modelo comprado", "", "Fondo comprado", "Tema comprado")[subTab])
            stats.put("owned", ownedCount())
            checkAch()
        }
    }

    private fun equipCurrent(i: Int) {
        when (subTab) {
            0 -> equip(i)
            1 -> equipShip(i)
            3 -> { bgSel = i; try { prefs.edit().putInt("bg", i).apply() } catch (e: Exception) { } }
            4 -> { themeSel = i; try { prefs.edit().putInt("theme", i).apply() } catch (e: Exception) { } }
        }
    }

    // ---------- editor de emblema ----------
    private fun symUnlocked(i: Int): Boolean {
        val req = Achievements.symbolLocks[i] ?: return true
        return stats.unlocked(req)
    }

    private fun drawEmblemEditor(c: Canvas) {
        val ey = emRows[0].top - u * 3.3f
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(200, 4, 6, 16)
        c.drawCircle(ccx, ey, u * 3.1f, paint)
        Emblem.draw(c, paint, ccx, ey, u * 4.9f, emShape, emSym, Emblem.palette[emBg], Emblem.palette[emC1], Emblem.palette[emC2])
        val labels = arrayOf("Forma", "Símbolo", "Fondo", "Color forma", "Color símbolo")
        val values = arrayOf(Emblem.shapeNames[emShape], Emblem.symbolNames[emSym], "", "", "")
        val cols = intArrayOf(0, 0, emBg, emC1, emC2)
        for (i in 0 until 5) {
            val r = emRows[i]
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(205, 10, 14, 34)
            c.drawRoundRect(r, u * 0.35f, u * 0.35f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = Color.argb(70, 170, 200, 255)
            c.drawRoundRect(r, u * 0.35f, u * 0.35f, paint)
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.LEFT
            paint.color = Color.WHITE
            paint.textSize = u * 0.46f
            c.drawText(labels[i], r.left + u * 0.4f, r.centerY() + u * 0.16f, paint)
            val lx = r.right - u * 6.2f
            val rx = r.right - u * 0.7f
            // botones de flecha grandes
            for (bx in floatArrayOf(lx, rx)) {
                paint.color = Color.argb(150, 40, 60, 120)
                c.drawCircle(bx, r.centerY(), u * 0.5f, paint)
            }
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = u * 0.56f
            paint.color = Color.argb(240, 190, 220, 255)
            c.drawText("◀", lx, r.centerY() + u * 0.2f, paint)
            c.drawText("▶", rx, r.centerY() + u * 0.2f, paint)
            val mxx = (lx + rx) / 2f
            if (i < 2) {
                paint.color = Color.WHITE
                paint.textSize = u * 0.44f
                c.drawText(values[i], mxx, r.centerY() + u * 0.16f, paint)
            } else {
                paint.color = Emblem.palette[cols[i]]
                rect.set(mxx - u * 1.6f, r.centerY() - u * 0.36f, mxx + u * 1.6f, r.centerY() + u * 0.36f)
                c.drawRoundRect(rect, u * 0.16f, u * 0.16f, paint)
                paint.style = Paint.Style.STROKE
                paint.color = Color.argb(150, 255, 255, 255)
                c.drawRoundRect(rect, u * 0.16f, u * 0.16f, paint)
                paint.style = Paint.Style.FILL
                paint.textSize = u * 0.26f
                paint.color = Color.argb(200, 0, 0, 0)
                c.drawText("tocar", mxx + 1f, r.centerY() + u * 0.1f + 1f, paint)
                paint.color = Color.argb(230, 255, 255, 255)
                c.drawText("tocar", mxx, r.centerY() + u * 0.1f, paint)
            }
        }
        val unlockedN = Emblem.symbolNames.indices.count { symUnlocked(it) }
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = u * 0.34f
        paint.color = Color.argb(200, 190, 215, 255)
        c.drawText("Símbolos: $unlockedN / ${Emblem.symbolNames.size} · consigue logros para más", width / 2f, emRows[4].bottom + u * 0.7f, paint)
    }

    /** Cuadrícula de 8×4 colores para elegir directamente. */
    private fun drawPalette(c: Canvas) {
        palAnim += ((if (palPick >= 0) 1f else 0f) - palAnim) * min(1f, frameDt * 12f)
        if (palAnim < 0.01f) return
        val k = palAnim
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((170 * k).toInt(), 0, 0, 0)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        val sw = u * 1.5f; val gp = u * 0.22f
        val pw = 8 * sw + 7 * gp + u * 0.8f
        val ph = 4 * sw + 3 * gp + u * 2f
        val cy = height * 0.5f + (1f - k) * u * 2f
        palRect.set(width / 2f - pw / 2f, cy - ph / 2f, width / 2f + pw / 2f, cy + ph / 2f)
        val al = (255 * k).toInt()
        paint.color = Color.argb((235 * k).toInt(), 12, 16, 30)
        c.drawRoundRect(palRect, u * 0.5f, u * 0.5f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
        paint.color = Color.argb((120 * k).toInt(), 170, 200, 255)
        c.drawRoundRect(palRect, u * 0.5f, u * 0.5f, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = u * 0.42f
        paint.color = Color.argb(al, 255, 255, 255)
        val title = when (palPick) { 2 -> "Color de fondo"; 3 -> "Color de la forma"; else -> "Color del símbolo" }
        c.drawText(title, palRect.centerX(), palRect.top + u * 0.85f, paint)
        val cur = when (palPick) { 2 -> emBg; 3 -> emC1; 4 -> emC2; else -> -1 }
        for (i in Emblem.palette.indices) {
            val x = palRect.left + u * 0.4f + (i % 8) * (sw + gp)
            val y = palRect.top + u * 1.4f + (i / 8) * (sw + gp)
            rect.set(x, y, x + sw, y + sw)
            val col = Emblem.palette[i]
            paint.color = Color.argb(al, Color.red(col), Color.green(col), Color.blue(col))
            c.drawRoundRect(rect, u * 0.22f, u * 0.22f, paint)
            if (i == cur) {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = u * 0.09f
                paint.color = Color.argb(al, 255, 255, 255)
                rect.inset(-u * 0.08f, -u * 0.08f)
                c.drawRoundRect(rect, u * 0.26f, u * 0.26f, paint)
                paint.style = Paint.Style.FILL
            }
        }
    }

    private fun paletteTap(x: Float, y: Float) {
        val sw = u * 1.5f; val gp = u * 0.22f
        for (i in Emblem.palette.indices) {
            val x0 = palRect.left + u * 0.4f + (i % 8) * (sw + gp)
            val y0 = palRect.top + u * 1.4f + (i / 8) * (sw + gp)
            if (x >= x0 - gp / 2 && x <= x0 + sw + gp / 2 && y >= y0 - gp / 2 && y <= y0 + sw + gp / 2) {
                when (palPick) { 2 -> emBg = i; 3 -> emC1 = i; else -> emC2 = i }
                saveEmblem()
                break
            }
        }
        palPick = -1
    }

    private fun saveEmblem() {
        try { prefs.edit().putInt("em_s", emShape).putInt("em_y", emSym).putInt("em_b", emBg).putInt("em_c1", emC1).putInt("em_c2", emC2).apply() } catch (e: Exception) { }
    }

    private fun emblemTap(x: Float, y: Float) {
        for (i in 0 until 5) {
            val r = emRows[i]
            if (y < r.top - u * 0.1f || y > r.bottom + u * 0.1f || x < r.right - u * 7f) continue
            val lx = r.right - u * 6.2f
            val rx = r.right - u * 0.7f
            val mxx = (lx + rx) / 2f
            if (i >= 2 && abs(x - mxx) < u * 1.8f) { palPick = i; return }
            val d = if (x < mxx) -1 else 1
            when (i) {
                0 -> emShape = (emShape + d + Emblem.shapeNames.size) % Emblem.shapeNames.size
                1 -> {
                    var s2 = emSym
                    for (k in 0 until Emblem.symbolNames.size) {
                        s2 = (s2 + d + Emblem.symbolNames.size) % Emblem.symbolNames.size
                        if (symUnlocked(s2)) break
                    }
                    emSym = s2
                }
                2 -> emBg = (emBg + d + Emblem.palette.size) % Emblem.palette.size
                3 -> emC1 = (emC1 + d + Emblem.palette.size) % Emblem.palette.size
                else -> emC2 = (emC2 + d + Emblem.palette.size) % Emblem.palette.size
            }
            saveEmblem()
        }
    }

    // ---------- perfil ----------
    /** Diálogo propio del juego para el nombre de piloto: tarjeta oscura redondeada, campo con contador y botones píldora. */
    private fun editPilotName() {
        post {
            try {
                val ctx = context
                val dp = resources.displayMetrics.density
                val accent = Color.rgb(90, 150, 255)
                val dlg = android.app.Dialog(ctx)
                dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
                val card = android.widget.LinearLayout(ctx).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding((22 * dp).toInt(), (20 * dp).toInt(), (22 * dp).toInt(), (18 * dp).toInt())
                    background = android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(Color.rgb(22, 28, 52), Color.rgb(9, 12, 26))).apply {
                        cornerRadius = 22 * dp
                        setStroke((1.5f * dp).toInt(), Color.argb(150, 120, 170, 255))
                    }
                    elevation = 18 * dp
                }
                val header = android.widget.LinearLayout(ctx).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                val emb = object : View(ctx) {
                    val pp = Paint(Paint.ANTI_ALIAS_FLAG)
                    override fun onDraw(cv: Canvas) {
                        Emblem.draw(cv, pp, width / 2f, height / 2f, width * 0.95f, emShape, emSym, Emblem.palette[emBg], Emblem.palette[emC1], Emblem.palette[emC2])
                    }
                }
                header.addView(emb, android.widget.LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()))
                val titles = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding((12 * dp).toInt(), 0, 0, 0) }
                titles.addView(android.widget.TextView(ctx).apply {
                    text = "NOMBRE DE PILOTO"; setTextColor(Color.WHITE); textSize = 17f
                    typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.08f
                })
                titles.addView(android.widget.TextView(ctx).apply {
                    text = "Así te verán en tu perfil"; setTextColor(Color.argb(170, 190, 210, 255)); textSize = 12.5f
                })
                header.addView(titles)
                card.addView(header)
                val et = android.widget.EditText(ctx).apply {
                    setText(pilot); setSelection(text.length)
                    setSingleLine(); imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                    filters = arrayOf(android.text.InputFilter.LengthFilter(16))
                    setTextColor(Color.WHITE); setHintTextColor(Color.argb(110, 200, 210, 240)); hint = "Escribe tu nombre"
                    textSize = 18f; typeface = Typeface.DEFAULT_BOLD
                    setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 14 * dp; setColor(Color.argb(255, 6, 9, 20)); setStroke((1.5f * dp).toInt(), accent)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= 29) textCursorDrawable = android.graphics.drawable.GradientDrawable().apply {
                        setColor(accent); setSize((2 * dp).toInt(), (20 * dp).toInt())
                    }
                }
                card.addView(et, android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = (18 * dp).toInt() })
                val counter = android.widget.TextView(ctx).apply {
                    setTextColor(Color.argb(150, 190, 210, 255)); textSize = 11.5f
                    gravity = android.view.Gravity.END; text = "${pilot.length}/16"
                }
                card.addView(counter, android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = (4 * dp).toInt() })
                et.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                    override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                    override fun afterTextChanged(ed: android.text.Editable?) { counter.text = "${ed?.length ?: 0}/16" }
                })
                fun pill(label: String, filled: Boolean) = android.widget.TextView(ctx).apply {
                    text = label; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.04f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(if (filled) Color.WHITE else Color.argb(230, 190, 210, 255))
                    setPadding(0, (12 * dp).toInt(), 0, (12 * dp).toInt())
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 40 * dp
                        if (filled) { setColor(Color.rgb(56, 110, 230)) } else { setColor(Color.TRANSPARENT); setStroke((1.5f * dp).toInt(), Color.argb(140, 150, 180, 255)) }
                    }
                    isClickable = true
                }
                val row = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.HORIZONTAL }
                val cancel = pill("Cancelar", false)
                val save = pill("Guardar", true)
                row.addView(cancel, android.widget.LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = (6 * dp).toInt() })
                row.addView(save, android.widget.LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = (6 * dp).toInt() })
                card.addView(row, android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = (16 * dp).toInt() })
                val commit = {
                    val n = et.text.toString().trim()
                    if (n.isNotEmpty()) { pilot = n; try { prefs.edit().putString("pilot", n).apply() } catch (e: Exception) { } }
                    dlg.dismiss(); invalidate()
                }
                cancel.setOnClickListener { dlg.dismiss() }
                save.setOnClickListener { commit() }
                et.setOnEditorActionListener { _, id, _ -> if (id == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) { commit(); true } else false }
                val wrap = android.widget.FrameLayout(ctx).apply { setPadding((24 * dp).toInt(), 0, (24 * dp).toInt(), 0) }
                wrap.addView(card, android.widget.FrameLayout.LayoutParams(-1, -2, android.view.Gravity.CENTER))
                dlg.setContentView(wrap)
                dlg.window?.let { wdw ->
                    wdw.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
                    wdw.setLayout(-1, -2)
                    wdw.setDimAmount(0.65f)
                    wdw.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    wdw.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                        android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                }
                card.alpha = 0f; card.scaleX = 0.9f; card.scaleY = 0.9f
                dlg.setOnShowListener {
                    card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220)
                        .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                    et.requestFocus()
                }
                dlg.show()
            } catch (e: Exception) { }
        }
    }

    /** Letras con cuerpo: relieve en capas, degradado metálico, contorno y un brillo que las recorre. */
    private fun drawTitleText(c: Canvas, t: String, x: Float, y: Float, a: Float) {
        val al = (255 * a).toInt()
        val depth = (u * 0.09f).toInt().coerceAtLeast(2)
        paint.style = Paint.Style.FILL
        for (k in depth downTo 1) {  // extrusión
            paint.color = Color.argb(al, 20 + k * 3, 30 + k * 4, 70 + k * 6)
            c.drawText(t, x + k * 0.8f, y + k, paint)
        }
        val tw = paint.measureText(t)
        val top = y - paint.textSize * 0.8f
        paint.shader = LinearGradient(0f, top, 0f, y, intArrayOf(Color.argb(al, 255, 255, 255), Color.argb(al, 170, 200, 255), Color.argb(al, 90, 130, 220)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawText(t, x, y, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = u * 0.035f
        paint.color = Color.argb(al * 200 / 255, 20, 30, 70)
        c.drawText(t, x, y, paint)
        paint.style = Paint.Style.FILL
        // brillo que cruza el nombre cada pocos segundos
        val ph = (time % 4f) / 1.2f
        if (ph < 1f) {
            val bx = x - u + (tw + u * 2f) * ph
            paint.shader = LinearGradient(bx - u * 0.6f, 0f, bx + u * 0.6f, 0f, intArrayOf(Color.argb(0, 255, 255, 255), Color.argb(al * 170 / 255, 255, 255, 255), Color.argb(0, 255, 255, 255)), null, Shader.TileMode.CLAMP)
            c.drawText(t, x, y, paint)
            paint.shader = null
        }
    }

    private fun fmtTime(sec: Int): String = if (sec >= 3600) "${sec / 3600} h ${(sec % 3600) / 60} min" else "${sec / 60} min ${sec % 60} s"

    private fun drawProfileTab(c: Canvas, a: Float) {
        val top = mtop + u * 1.75f
        val bottom = height - u * 0.3f
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((215 * a).toInt(), 6, 9, 22)
        rect.set(u * 0.3f, top, width - u * 0.3f, bottom)
        c.drawRoundRect(rect, u * 0.4f, u * 0.4f, paint)
        c.save()
        c.clipRect(rect)
        var y = top + u * 0.3f - profScroll
        val x0 = u * 0.8f
        // cabecera: emblema, nombre y rango
        Emblem.draw(c, paint, x0 + u * 1.2f, y + u * 1.3f, u * 2.4f, emShape, emSym, Emblem.palette[emBg], Emblem.palette[emC1], Emblem.palette[emC2], a)
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.color = Color.argb((255 * a).toInt(), 255, 255, 255)
        paint.textSize = u * 0.78f
        drawTitleText(c, pilot, x0 + u * 2.8f, y + u * 1.0f, a)
        nameRect.set(x0 + u * 2.6f, y + u * 0.1f, width - u * 0.5f, y + u * 1.3f)
        val rk = Ranks.all[Ranks.indexFor(totalBars)]
        paint.textSize = u * 0.42f
        paint.color = Color.argb((220 * a).toInt(), 190, 215, 255)
        c.drawText(rk.name, x0 + u * 3.9f, y + u * 1.8f, paint)
        drawInsignia(c, x0 + u * 3.3f, y + u * 1.65f, u * 0.9f, rk, a)
        paint.textSize = u * 0.28f
        paint.color = Color.argb((150 * a).toInt(), 190, 215, 255)
        c.drawText("Toca tu nombre para cambiarlo", x0 + u * 2.8f, y + u * 2.4f, paint)
        y += u * 3.2f
        // estadísticas
        paint.textSize = u * 0.5f
        paint.color = Color.argb((255 * a).toInt(), 255, 225, 150)
        c.drawText("Estadísticas", x0, y, paint)
        y += u * 0.7f
        val st = arrayOf(
            "Partidas" to "${stats.get("games")}", "Tiempo jugado" to fmtTime(stats.get("time")),
            "Naves colocadas" to "${stats.get("ships")}", "Líneas" to "${stats.get("lines")}",
            "Mejor puntaje" to "$best", "Mejor nivel" to "${stats.get("level")}",
            "Mejor racha" to "${stats.get("streak")}", "Bombas detonadas" to "${stats.get("bombs")}",
            "Barras llenadas" to "$totalBars", "Lingotes ganados" to "${stats.get("ingots")}",
            "Devastaciones" to "${stats.get("quad")}", "Al límite" to "${stats.get("limit")}"
        )
        for ((i, pr) in st.withIndex()) {
            val col = i % 2
            val cx2 = x0 + col * (width - u * 1.6f) / 2f
            paint.textSize = u * 0.32f
            paint.color = Color.argb((180 * a).toInt(), 190, 215, 255)
            c.drawText(pr.first, cx2, y, paint)
            paint.textSize = u * 0.42f
            paint.color = Color.argb((255 * a).toInt(), 255, 255, 255)
            c.drawText(pr.second, cx2, y + u * 0.5f, paint)
            if (col == 1) y += u * 1.05f
        }
        y += u * 0.5f
        // medallas
        paint.textSize = u * 0.5f
        paint.color = Color.argb((255 * a).toInt(), 255, 225, 150)
        c.drawText("Medallas", x0, y, paint)
        y += u * 1.0f
        val per = 5
        val cellW = (width - u * 1.6f) / per
        for (i in Medals.names.indices) {
            val cx2 = x0 + (i % per) * cellW + cellW / 2f
            val cy2 = y + (i / per) * u * 2.0f
            val n = stats.get("md_$i")
            drawMedal(c, cx2, cy2, u * 1.0f, i, if (n > 0) a else a * 0.3f)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = u * 0.24f
            paint.color = Color.argb((210 * a).toInt(), 230, 235, 245)
            c.drawText(Medals.names[i], cx2, cy2 + u * 0.8f, paint)
            paint.color = Color.argb((255 * a).toInt(), 255, 225, 150)
            c.drawText("x$n", cx2, cy2 + u * 1.1f, paint)
        }
        y += ((Medals.names.size + per - 1) / per) * u * 2.0f + u * 0.2f
        // logros
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = u * 0.5f
        paint.color = Color.argb((255 * a).toInt(), 255, 225, 150)
        val done = Achievements.all.count { stats.unlocked(it.id) }
        c.drawText("Logros  $done / ${Achievements.all.size}", x0, y, paint)
        y += u * 0.5f
        for (ach in Achievements.all) {
            val un = stats.unlocked(ach.id)
            rect.set(x0, y, width - u * 0.8f, y + u * 1.1f)
            paint.color = if (un) Color.argb((200 * a).toInt(), 30, 60, 40) else Color.argb((170 * a).toInt(), 16, 20, 40)
            c.drawRoundRect(rect, u * 0.2f, u * 0.2f, paint)
            paint.textSize = u * 0.38f
            paint.color = Color.argb((255 * a).toInt(), 255, 255, 255)
            c.drawText(ach.name, x0 + u * 0.3f, y + u * 0.45f, paint)
            paint.textSize = u * 0.28f
            paint.color = Color.argb((190 * a).toInt(), 190, 215, 255)
            c.drawText(ach.desc, x0 + u * 0.3f, y + u * 0.85f, paint)
            paint.textAlign = Paint.Align.RIGHT
            if (un) {
                paint.textSize = u * 0.34f
                paint.color = Color.argb((255 * a).toInt(), 120, 230, 150)
                c.drawText("Completado", rect.right - u * 0.3f, y + u * 0.62f, paint)
            } else {
                val cur = min(stats.get(ach.stat), ach.goal)
                paint.textSize = u * 0.3f
                paint.color = Color.argb((220 * a).toInt(), 255, 225, 150)
                c.drawText("$cur / ${ach.goal}  ·  +${ach.reward}", rect.right - u * 0.3f, y + u * 0.62f, paint)
            }
            paint.textAlign = Paint.Align.LEFT
            y += u * 1.25f
        }
        c.restore()
        profMax = max(0f, (y + profScroll) - bottom + u * 0.5f)
        profScroll = profScroll.coerceIn(0f, profMax)
    }



    private fun equip(i: Int) {
        skinSel = i
        try { prefs.edit().putInt("skin", i).apply() } catch (e: Exception) { }
        requestSkin(i)
    }

    /** Cambia el aspecto de las naves: las placas de la facción se cargan del disco o se generan en segundo plano. */
    private fun equipShip(i: Int) {
        shipSel = i
        try { prefs.edit().putInt("ship", i).apply() } catch (e: Exception) { }
        val f = Factions.all[i]
        val dir = context.cacheDir
        shipBusy = true
        Thread {
            val tiles = DiskCache.loadSprites(dir, i, 28)
                ?: Textures.makeShipSprites(BASE, f, spriteCp()).also { t -> saveAsync { DiskCache.saveSprites(dir, i, t) } }
            glows = Array(7) { Textures.makeGlow(f.accent(BASE[it])) }
            flameA = Textures.makeGlow(f.flameA)
            flameB = Textures.makeGlow(f.flameB)
            ships = tiles
            shipBusy = false
            postInvalidate()
        }.start()
    }

    // ---------- rangos ----------
    private fun chevron(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, th: Float) {
        path.reset()
        path.moveTo(cx - w, cy + h); path.lineTo(cx, cy); path.lineTo(cx + w, cy + h)
        path.lineTo(cx + w, cy + h + th); path.lineTo(cx, cy + th); path.lineTo(cx - w, cy + h + th)
        path.close()
        c.drawPath(path, paint)
    }

    private fun star(c: Canvas, cx: Float, cy: Float, r: Float) {
        path.reset()
        for (i in 0 until 10) {
            val ang = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
            val rr = if (i % 2 == 0) r else r * 0.42f
            val px = cx + cos(ang) * rr
            val py = cy + sin(ang) * rr
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        c.drawPath(path, paint)
    }

    /** Insignia del rango: escudo con galones, barras, estrellas, alas o corona según el grupo. */
    private fun drawInsignia(c: Canvas, cx: Float, cy: Float, sz: Float, rk: Rank, a: Float = 1f) {
        val pal = intArrayOf(
            Color.rgb(150, 155, 165), Color.rgb(176, 120, 70), Color.rgb(190, 200, 215), Color.rgb(190, 200, 215),
            Color.rgb(200, 205, 220), Color.rgb(230, 190, 70), Color.rgb(230, 190, 70), Color.rgb(230, 190, 70),
            Color.rgb(120, 200, 240), Color.rgb(120, 200, 240), Color.rgb(180, 130, 255), Color.rgb(180, 130, 255),
            Color.rgb(255, 100, 100), Color.rgb(255, 100, 100)
        )
        val col = if (rk.group < pal.size) pal[rk.group] else Color.rgb(90 + (rk.group * 37) % 160, 220, 255 - (rk.group * 23) % 120)
        val al = (255 * a).toInt()
        paint.style = Paint.Style.FILL
        paint.color = Color.argb((200 * a).toInt(), 12, 16, 38)
        c.drawCircle(cx, cy, sz * 0.5f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sz * 0.05f
        paint.color = Color.argb(al, Color.red(col), Color.green(col), Color.blue(col))
        c.drawCircle(cx, cy, sz * 0.5f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(al, Color.red(col), Color.green(col), Color.blue(col))
        val g = rk.group
        val n = rk.grade
        when {
            g == 0 -> c.drawCircle(cx, cy, sz * 0.09f, paint)
            g == 1 -> chevron(c, cx, cy - sz * 0.12f, sz * 0.26f, sz * 0.2f, sz * 0.1f)
            g in 2..5 -> {
                for (i in 0 until n) chevron(c, cx, cy - sz * 0.2f + i * sz * 0.14f, sz * 0.26f, sz * 0.16f, sz * 0.07f)
                if (g >= 4) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = sz * 0.05f
                    rect.set(cx - sz * 0.28f, cy + sz * 0.02f, cx + sz * 0.28f, cy + sz * 0.38f)
                    c.drawArc(rect, 20f, 140f, false, paint)
                    paint.style = Paint.Style.FILL
                }
            }
            g in 6..7 -> for (i in 0 until n) {
                rect.set(cx - sz * 0.26f, cy - sz * 0.2f + i * sz * 0.14f, cx + sz * 0.26f, cy - sz * 0.1f + i * sz * 0.14f)
                c.drawRoundRect(rect, sz * 0.02f, sz * 0.02f, paint)
            }
            g in 8..9 -> for (i in 0 until n) star(c, cx + (i - (n - 1) / 2f) * sz * 0.3f, cy, sz * 0.16f)
            g in 10..11 -> {
                path.reset()
                path.moveTo(cx - sz * 0.38f, cy - sz * 0.08f); path.lineTo(cx - sz * 0.08f, cy); path.lineTo(cx - sz * 0.3f, cy + sz * 0.16f)
                path.moveTo(cx + sz * 0.38f, cy - sz * 0.08f); path.lineTo(cx + sz * 0.08f, cy); path.lineTo(cx + sz * 0.3f, cy + sz * 0.16f)
                c.drawPath(path, paint)
                for (i in 0 until n) star(c, cx + (i - (n - 1) / 2f) * sz * 0.2f, cy - sz * 0.1f, sz * 0.1f)
            }
            g in 12..13 -> {
                for (i in 0 until n) star(c, cx + (i - (n - 1) / 2f) * sz * 0.22f, cy - sz * 0.14f, sz * 0.1f)
                path.reset()
                path.moveTo(cx - sz * 0.3f, cy + sz * 0.22f); path.lineTo(cx - sz * 0.3f, cy + sz * 0.02f); path.lineTo(cx - sz * 0.15f, cy + sz * 0.12f)
                path.lineTo(cx, cy - sz * 0.02f); path.lineTo(cx + sz * 0.15f, cy + sz * 0.12f); path.lineTo(cx + sz * 0.3f, cy + sz * 0.02f)
                path.lineTo(cx + sz * 0.3f, cy + sz * 0.22f); path.close()
                c.drawPath(path, paint)
            }
            else -> {
                star(c, cx, cy, sz * 0.3f)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = sz * 0.03f
                for (i in 0 until 8) {
                    val ang = i * PI.toFloat() / 4f
                    c.drawLine(cx + cos(ang) * sz * 0.34f, cy + sin(ang) * sz * 0.34f, cx + cos(ang) * sz * 0.46f, cy + sin(ang) * sz * 0.46f, paint)
                }
                paint.style = Paint.Style.FILL
            }
        }
    }

    private fun drawRankBlock(c: Canvas, a: Float) {
        val idx = Ranks.indexFor(totalBars)
        val rk = Ranks.all[idx]
        val next = if (idx + 1 < Ranks.all.size) Ranks.all[idx + 1] else null
        val cy = mtop + u * 6.4f
        drawInsignia(c, width / 2f - u * 3.2f, cy, u * 1.9f, rk, a)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.argb((255 * a).toInt(), 255, 255, 255)
        paint.textSize = u * 0.62f
        c.drawText(rk.name, width / 2f - u * 2.0f, cy - u * 0.1f, paint)
        paint.textSize = u * 0.32f
        paint.color = Color.argb((200 * a).toInt(), 190, 215, 255)
        val bx0 = width / 2f - u * 2.0f
        val bx1 = width / 2f + u * 4.0f
        if (next != null) {
            val frac = ((totalBars - rk.bars).toFloat() / (next.bars - rk.bars)).coerceIn(0f, 1f)
            rect.set(bx0, cy + u * 0.15f, bx1, cy + u * 0.4f)
            paint.color = Color.argb((150 * a).toInt(), 10, 12, 30)
            c.drawRoundRect(rect, u * 0.12f, u * 0.12f, paint)
            rect.set(bx0, cy + u * 0.15f, bx0 + (bx1 - bx0) * frac, cy + u * 0.4f)
            paint.color = Color.argb((235 * a).toInt(), 120, 190, 255)
            c.drawRoundRect(rect, u * 0.12f, u * 0.12f, paint)
            paint.color = Color.argb((200 * a).toInt(), 190, 215, 255)
            paint.textSize = u * 0.3f
            c.drawText("Barras $totalBars / ${next.bars}  ·  siguiente: ${next.name}", bx0, cy + u * 0.82f, paint)
        } else {
            paint.textSize = u * 0.34f
            c.drawText("Rango máximo · $totalBars barras", bx0, cy + u * 0.5f, paint)
        }
    }

    private fun drawHud(c: Canvas, g: Game) {
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.style = Paint.Style.FILL
        val top = topInset + u * 0.95f
        paint.textSize = u * 0.5f
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.argb(235, 255, 255, 255)
        c.drawText("NIVEL ${g.level}", u * 0.4f, top, paint)
        paint.textAlign = Paint.Align.RIGHT
        c.drawText("$totalIngots", width - u * 0.4f, top, paint)
        val tw = paint.measureText("$totalIngots")
        drawIngot(c, width - u * 0.4f - tw - u * 0.5f, top - u * 0.17f, u * 0.7f)

        paint.color = Color.argb(150, 10, 12, 30)
        c.drawCircle(pauseRect.centerX(), top - u * 0.2f, u * 0.42f, paint)
        paint.color = Color.WHITE
        rect.set(pauseRect.centerX() - u * 0.17f, top - u * 0.38f, pauseRect.centerX() - u * 0.06f, top - u * 0.02f)
        c.drawRoundRect(rect, u * 0.03f, u * 0.03f, paint)
        rect.set(pauseRect.centerX() + u * 0.06f, top - u * 0.38f, pauseRect.centerX() + u * 0.17f, top - u * 0.02f)
        c.drawRoundRect(rect, u * 0.03f, u * 0.03f, paint)

        val ck = if (celebT > 0f) 1f - celebT / CELEB else 0f
        val pulseE = if (celebT > 0f) sin(PI.toFloat() * min(1f, ck * 1.5f)) else 0f
        val bsx = 1f + 0.012f * pulseE
        val bsy = 1f + 0.22f * pulseE
        c.save()
        c.scale(bsx, bsy, (barX0 + barX1) / 2f, (barY0 + barY1) / 2f)
        rect.set(barX0, barY0, barX1, barY1)
        paint.color = Color.argb(150, 10, 12, 30)
        c.drawRoundRect(rect, u * 0.21f, u * 0.21f, paint)
        val fw = (barX1 - barX0) * dispFrac.coerceIn(0f, 1f)
        if (fw > u * 0.2f) {
            rect.set(barX0, barY0, barX0 + fw, barY1)
            paint.shader = barShader
            c.drawRoundRect(rect, u * 0.21f, u * 0.21f, paint)
            paint.shader = null
            val sh = ((time * 0.5f) % 1f) * (barX1 - barX0 + u * 2f) - u
            rect.set(max(barX0, sh), barY0, min(barX0 + fw, sh + u), barY1)
            if (rect.width() > 1f) {
                paint.color = Color.argb(110, 255, 255, 255)
                c.drawRoundRect(rect, u * 0.2f, u * 0.2f, paint)
            }
        }
        if (celebT > 0f) drawShieldRecharge(c, ck)
        if (barFlash > 0f) {
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = u * 0.5f
            paint.color = Color.argb((255 * barFlash).toInt(), 255, 240, 160)
            c.drawText("+3 lingotes", width / 2f, barY1 + u * 0.8f, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(140, 170, 200, 255)
        rect.set(barX0, barY0, barX1, barY1)
        c.drawRoundRect(rect, u * 0.21f, u * 0.21f, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = u * 0.3f
        paint.color = Color.argb(230, 255, 255, 255)
        c.drawText("hierro ${g.barProgress} / ${g.barNeed()}", width / 2f, barY1 - u * 0.1f, paint)
        c.restore()
        if (celebT > 0f) drawWeld(c, ck, pulseE, bsx, bsy)

        if (phaseT < 7f && !g.over && !paused) {
            paint.textSize = u * 0.42f
            paint.color = Color.argb((255 * min(1f, (7f - phaseT))).toInt(), 255, 255, 255)
            c.drawText("Arrastra: mueve la nave lejos de la franja roja", width / 2f, height - u * 1.8f, paint)
            c.drawText("Toca: gira   ·   Empuja rápido: la lanzas lejos", width / 2f, height - u * 1.2f, paint)
        }
        if (g.over) {
            // velo que sube despacio para ver la explosión; luego la tarjeta entra con escala, rebote y deslizamiento
            paint.color = Color.argb((205 * (g.overTime / 1.6f).coerceIn(0f, 1f)).toInt(), 6, 0, 16)
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            val ta = ((g.overTime - 1.0f) / 0.55f).coerceIn(0f, 1f)
            if (ta > 0f) {
                val ease = 1f + 2.2f * (ta - 1f) * (ta - 1f) * (ta - 1f) + 1.2f * (ta - 1f) * (ta - 1f)  // con leve rebote
                val cy0 = height / 2f
                c.save()
                c.translate(0f, (1f - ta) * u * 3f)
                c.scale(0.85f + 0.15f * ease, 0.85f + 0.15f * ease, width / 2f, cy0)
                val card = RectF(u * 0.8f, cy0 - u * 5.4f, width - u * 0.8f, cy0 + u * 6.2f)
                paint.style = Paint.Style.FILL
                paint.shader = LinearGradient(0f, card.top, 0f, card.bottom, Color.argb((240 * ta).toInt(), 30, 16, 40), Color.argb((240 * ta).toInt(), 10, 8, 22), Shader.TileMode.CLAMP)
                c.drawRoundRect(card, u * 0.6f, u * 0.6f, paint)
                paint.shader = null
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
                paint.color = Color.argb((200 * ta).toInt(), 255, 110, 110)
                c.drawRoundRect(card, u * 0.6f, u * 0.6f, paint)
                paint.style = Paint.Style.FILL
                paint.textAlign = Paint.Align.CENTER
                Emblem.draw(c, paint, width / 2f, card.top + u * 1.4f, u * 1.8f, emShape, emSym, Emblem.palette[emBg], Emblem.palette[emC1], Emblem.palette[emC2], ta)
                paint.textSize = u * 0.4f
                paint.color = Color.argb((220 * ta).toInt(), 230, 235, 245)
                c.drawText(pilot, width / 2f, card.top + u * 2.75f, paint)
                paint.textSize = u * 1.0f
                paint.color = Color.argb((255 * ta).toInt(), 255, 80, 80)
                paint.setShadowLayer(u * 0.15f, 0f, u * 0.06f, Color.argb((200 * ta).toInt(), 80, 0, 0))
                c.drawText("ESCAPE FALLIDO", width / 2f, card.top + u * 4.0f, paint)
                paint.clearShadowLayer()
                paint.textSize = u * 0.6f
                paint.color = Color.argb((255 * ta).toInt(), 255, 255, 255)
                c.drawText("Puntos: ${g.score}", width / 2f, card.top + u * 5.1f, paint)
                paint.color = if (newRecord) Color.argb((255 * ta).toInt(), 255, 225, 100) else Color.argb((220 * ta).toInt(), 255, 255, 255)
                c.drawText(if (newRecord) "¡Nuevo récord! $best" else "Récord: $best", width / 2f, card.top + u * 5.9f, paint)
                val nowIdx = Ranks.indexFor(totalBars)
                val promoted = nowIdx > rankStart
                paint.textSize = u * 0.5f
                paint.color = if (promoted) Color.argb((255 * ta).toInt(), 255, 225, 100) else Color.argb((220 * ta).toInt(), 190, 215, 255)
                c.drawText(if (promoted) "¡ASCENSO! ${Ranks.all[nowIdx].name}" else "Rango: ${Ranks.all[nowIdx].name}", width / 2f, card.top + u * 6.8f, paint)
                // botones
                overRegen.set(card.left + u * 0.7f, card.top + u * 7.5f, card.right - u * 0.7f, card.top + u * 8.9f)
                overMenu.set(card.left + u * 0.7f, card.top + u * 9.3f, card.right - u * 0.7f, card.top + u * 10.6f)
                val can = puRegen > 0
                val pr = overRegen.height() / 2f
                paint.shader = if (can) LinearGradient(0f, overRegen.top, 0f, overRegen.bottom, Color.argb((255 * ta).toInt(), 90, 200, 140), Color.argb((255 * ta).toInt(), 30, 120, 80), Shader.TileMode.CLAMP) else null
                if (!can) paint.color = Color.argb((160 * ta).toInt(), 50, 60, 80)
                c.drawRoundRect(overRegen, pr, pr, paint)
                paint.shader = null
                paint.color = Color.argb((255 * ta).toInt(), 255, 255, 255)
                paint.textSize = u * 0.48f
                c.drawText(if (can) "Usar Regeneración (tienes $puRegen)" else "Regeneración: cómprala en MEJORAS", width / 2f, overRegen.centerY() + u * 0.17f, paint)
                paint.color = Color.argb((220 * ta).toInt(), 40, 60, 110)
                c.drawRoundRect(overMenu, pr, pr, paint)
                paint.color = Color.argb((255 * ta).toInt(), 255, 255, 255)
                c.drawText("Volver al menú", width / 2f, overMenu.centerY() + u * 0.17f, paint)
                c.restore()
            }
        }
        if (reviveHold > 0f && !g.over) {
            // un momento de pausa para ver cómo quedó la partida antes de seguir
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = u * 0.9f
            paint.color = Color.argb((255 * min(1f, reviveHold * 2f)).toInt(), 120, 255, 170)
            c.drawText("¡REGENERADO!", width / 2f, height * 0.42f, paint)
            paint.textSize = u * 0.45f
            paint.color = Color.argb((230 * min(1f, reviveHold * 2f)).toInt(), 255, 255, 255)
            c.drawText("La partida sigue en un instante…", width / 2f, height * 0.42f + u * 0.8f, paint)
        }
    }

    private fun drawPause(c: Canvas) {
        val an = pauseAnim.coerceIn(0f, 1f)
        c.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (255 * an).toInt())
        val sc = 0.9f + 0.1f * an
        c.save()
        c.scale(sc, sc, width / 2f, height / 2f)
        drawPauseBody(c)
        c.restore()
        c.restore()
    }

    private fun drawPauseBody(c: Canvas) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = Color.argb(248, 4, 6, 16)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.color = Color.WHITE
        paint.textSize = u * 1.1f
        c.drawText("PAUSA", width / 2f, height / 2f - u * 1.0f, paint)
        paint.color = Color.argb(255, 40, 60, 110)
        c.drawRoundRect(resumeRect, u * 0.4f, u * 0.4f, paint)
        paint.color = Color.WHITE
        paint.textSize = u * 0.6f
        c.drawText("Continuar", width / 2f, resumeRect.centerY() + u * 0.22f, paint)
        paint.color = if (confirmExit) Color.rgb(170, 50, 50) else Color.argb(255, 60, 40, 50)
        c.drawRoundRect(menuRect, u * 0.4f, u * 0.4f, paint)
        paint.color = Color.WHITE
        paint.textSize = u * 0.45f
        c.drawText(if (confirmExit) "¿Seguro? Se pierde la partida" else "Salir al menú", width / 2f, menuRect.centerY() + u * 0.16f, paint)
    }

    // ---------- entrada ----------
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val g = game ?: return true
        if (phase < 0) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                sx = e.x; sy = e.y; lx = e.x; ly = e.y
                t0 = System.currentTimeMillis(); moved = false
                downOnPause = phase == 2 && pauseRect.contains(e.x, e.y)
            }
            MotionEvent.ACTION_MOVE -> {
                if (phase == 0 && tab == 2) {
                    profScroll = (profScroll - (e.y - ly)).coerceIn(0f, profMax)
                    lx = e.x; ly = e.y
                    if (hypot(e.x - sx, e.y - sy) > u * 0.3f) moved = true
                    return true
                }
                if (phase == 0 && tab == 1 && subTab != 2 && sy > cardsTop - u && sy < cardsTop + cardH + u) {
                    carouselOff += e.x - lx
                    clampCarousel()
                    lx = e.x; ly = e.y
                    if (hypot(e.x - sx, e.y - sy) > u * 0.3f) moved = true
                    return true
                }
                if (phase != 2 || paused || downOnPause || g.over || celebT > 0f) { lx = e.x; ly = e.y; return true }
                g.drag((e.x - lx) / s * 1.2f, (e.y - ly) / s * 1.2f)
                lx = e.x; ly = e.y
                if (hypot(e.x - sx, e.y - sy) > s * 0.4f) moved = true
            }
            MotionEvent.ACTION_UP -> {
                val dt = System.currentTimeMillis() - t0
                val tx = e.x - sx
                val ty = e.y - sy
                val dist = hypot(tx, ty)
                if (phase == 2 && !settingsOpen && !g.over && gameGear.contains(sx, sy) && gameGear.contains(e.x, e.y)) {
                    // configuración en plena partida: se pausa mientras está abierta
                    settingsOpen = true; settingsFromGame = !paused; paused = true
                    return true
                }
                if ((phase == 0 || phase == 2) && settingsOpen) {
                    if (musicSw.contains(e.x, e.y)) {
                        music.turn(!music.enabled)
                        try { prefs.edit().putBoolean("music", music.enabled).apply() } catch (ex: Exception) { }
                    } else if (sfxSw.contains(e.x, e.y)) {
                        sfx.enabled = !sfx.enabled
                        try { prefs.edit().putBoolean("sfx", sfx.enabled).apply() } catch (ex: Exception) { }
                    } else if (voiceSw.contains(e.x, e.y)) {
                        announcer.enabled = !announcer.enabled
                        try { prefs.edit().putBoolean("voice", announcer.enabled).apply() } catch (ex: Exception) { }
                        if (announcer.enabled) announcer.say(Announcer.DEVAST)
                    } else if (fpsRect.any { it.contains(e.x, e.y) }) {
                        fpsMode = fpsRect.indexOfFirst { it.contains(e.x, e.y) }
                        try { prefs.edit().putInt("fps", fpsMode).apply() } catch (ex: Exception) { }
                        applyRefresh()
                    } else if (gfxRect.any { it.contains(e.x, e.y) }) {
                        gfxMode = gfxRect.indexOfFirst { it.contains(e.x, e.y) }
                        try { prefs.edit().putInt("gfx", gfxMode).apply() } catch (ex: Exception) { }
                    } else if (closeRect.contains(e.x, e.y) || !panelRect.contains(e.x, e.y)) {
                        settingsOpen = false
                        if (phase == 2 && settingsFromGame) { paused = false; settingsFromGame = false; last = 0L }
                    }
                } else if (phase == 0 && palPick >= 0) {
                    paletteTap(e.x, e.y)
                } else if (phase == 0) {
                    // deslizar a los lados cambia de pestaña (fuera del carrusel de la tienda)
                    val inCarousel = tab == 1 && subTab != 2 && sy > cardsTop - u && sy < cardsTop + cardH + u
                    if (!inCarousel && abs(tx) > u * 2.2f && abs(ty) < abs(tx) * 0.6f && dt < 600) {
                        val nt = TAB_ORDER[(TAB_ORDER.indexOf(tab) + (if (tx < 0) 1 else -1)).coerceIn(0, 3)]
                        if (nt != tab) { switchTab(nt); if (nt == 2) profScroll = 0f }
                        return true
                    }
                    if (gearC.contains(sx, sy) && gearC.contains(e.x, e.y)) { settingsOpen = true; return true }
                    if (tabRect0.contains(sx, sy) && tabRect0.contains(e.x, e.y)) { switchTab(0); return true }
                    if (tabRect1.contains(sx, sy) && tabRect1.contains(e.x, e.y)) { switchTab(1); return true }
                    if (tabRect2.contains(sx, sy) && tabRect2.contains(e.x, e.y)) { switchTab(2); profScroll = 0f; return true }
                    if (tabRect3.contains(sx, sy) && tabRect3.contains(e.x, e.y)) { switchTab(3); return true }
                    if (tab == 3) {
                        for (i in 0 until 2) if (puBuy[i].contains(sx, sy) && puBuy[i].contains(e.x, e.y)) buyPower(i)
                        return true
                    }
                    if (tab == 0) {
                        if (achIcon.contains(sx, sy) && achIcon.contains(e.x, e.y)) achOpen = !achOpen
                        else if (achOpen) achOpen = false
                        else if (escapeRect.contains(sx, sy) && escapeRect.contains(e.x, e.y)) startEscape()
                        else for (i in 0 until 3) if (diffRect[i].contains(sx, sy) && diffRect[i].contains(e.x, e.y) && diff != i) {
                            diff = i
                            try { prefs.edit().putInt("diff", i).apply() } catch (ex: Exception) { }
                            layoutGame()
                            say(arrayOf("Normal: como siempre", "Heroico: cuadrícula más corta y sin naves bomba", "Legendario: naves cada 2 segundos sin esperar")[i])
                        }
                    } else if (tab == 2) {
                        if (!moved && nameRect.contains(e.x, e.y)) editPilotName()
                    } else {
                        var hitSub = -1
                        for (k in 0 until 5) if (subRects[k].contains(sx, sy) && subRects[k].contains(e.x, e.y)) hitSub = k
                        if (hitSub >= 0) switchSub(hitSub)
                        else if (subTab == 2) emblemTap(e.x, e.y)
                        else if (buyRect.contains(sx, sy) && buyRect.contains(e.x, e.y)) actShop()
                        else if (!moved && e.y > cardsTop && e.y < cardsTop + cardH) {
                            for (i in 0 until shopCount()) {
                                val x = cardX(i)
                                if (e.x >= x && e.x <= x + cardW) {
                                    when (subTab) {
                                        0 -> preview = i
                                        1 -> { previewShip = i; if (!factions[i].classified) sfx.place(factions[i].sfx) }
                                        3 -> previewBg = i
                                        4 -> previewTheme = i
                                    }
                                }
                            }
                        }
                    }
                } else if (phase == 1) {
                    // la explosión no se interrumpe
                } else if (paused) {
                    if (resumeRect.contains(e.x, e.y) || pauseRect.contains(e.x, e.y)) { paused = false; confirmExit = false; last = 0L }
                    else if (menuRect.contains(e.x, e.y)) {
                        if (confirmExit) layoutGame() else confirmExit = true
                    } else confirmExit = false
                } else if (downOnPause && pauseRect.contains(e.x, e.y)) {
                    paused = true
                } else if (g.over) {
                    if (g.overTime > 1.6f) {
                        if (overRegen.contains(e.x, e.y) && puRegen > 0) {
                            puRegen--; saveWallet()
                            g.revive()
                            overHandled = false
                            reviveHold = 1.2f
                            shake = 1.2f; flashWhite = max(flashWhite, 0.4f)
                        } else if (overMenu.contains(e.x, e.y)) layoutGame()
                    }
                } else if (celebT > 0f) {
                    // la barra completada congela el juego un instante
                } else if (!moved && dist < s * 0.6f && dt < 300) {
                    g.rotate()
                } else if (dt < 180 && dist > s * 3f && (tx * g.gx + ty * g.gy) > 0.7f * dist * hypot(g.gx.toFloat(), g.gy.toFloat())) {
                    g.hardDrop()
                }
            }
        }
        return true
    }
}
