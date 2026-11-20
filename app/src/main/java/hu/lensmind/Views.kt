package hu.lensmind

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.drawable.Drawable
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ScrollView
import kotlin.random.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** FLUX KONDENZÁTOR – a Y alakú, pulzáló fény. Küldéskor és kereséskor felgyullad. */
class FluxView(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var phase = 0f
    private var energy = 0.3f
    var active = false

    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1100
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedFraction
            val base = if (active) 0.85f else 0.3f
            energy += (base - energy) * 0.06f
            invalidate()
        }
    }

    fun pulse() { energy = 1f }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f * 0.82f
        val angles = floatArrayOf(-150f, -30f, 90f)
        val glow = 0xFF4DD2FF.toInt()
        val a = (110 + 145 * energy).toInt().coerceIn(0, 255)

        p.color = (glow and 0x00FFFFFF) or ((a / 3) shl 24)
        p.strokeWidth = r * 0.34f
        for (ang in angles) drawArm(canvas, cx, cy, r, ang)
        p.color = (glow and 0x00FFFFFF) or ((a / 2) shl 24)
        p.strokeWidth = r * 0.18f
        for (ang in angles) drawArm(canvas, cx, cy, r, ang)
        p.color = (0x00FFFFFF) or (a shl 24)
        p.strokeWidth = r * 0.07f
        for (ang in angles) drawArm(canvas, cx, cy, r, ang)

        // végigfutó fénypontok
        dot.color = 0x00FFFFFF or (a shl 24)
        angles.forEachIndexed { i, ang ->
            val t = (phase + i / 3f) % 1f
            val rad = Math.toRadians(ang.toDouble())
            canvas.drawCircle(
                cx + (cos(rad) * r * t).toFloat(), cy + (sin(rad) * r * t).toFloat(),
                r * 0.09f, dot
            )
        }
    }

    private fun drawArm(canvas: Canvas, cx: Float, cy: Float, r: Float, ang: Float) {
        val rad = Math.toRadians(ang.toDouble())
        canvas.drawLine(cx, cy, cx + (cos(rad) * r).toFloat(), cy + (sin(rad) * r).toFloat(), p)
    }
}

/** A "Vissza a jövőbe" logó három csíkja: piros, narancs, sárga. */
class StripesView(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colors = intArrayOf(0xFFFF2D2D.toInt(), 0xFFFF7A00.toInt(), 0xFFFFC400.toInt())

    override fun onDraw(canvas: Canvas) {
        val h = height / 5f
        colors.forEachIndexed { i, col ->
            p.shader = LinearGradient(0f, 0f, width.toFloat(), 0f, 0x00000000, col, Shader.TileMode.CLAMP)
            val top = h * (i * 2)
            canvas.drawRect(0f, top, width.toFloat(), top + h, p)
        }
    }
}

/** ScrollView maximális magassággal – kis kijelzőn görgethető, nagyon nem nő túl. */
class MaxHeightScroll(c: Context) : ScrollView(c) {
    var maxH: Int = Int.MAX_VALUE

    override fun onMeasure(w: Int, h: Int) {
        val mode = MeasureSpec.getMode(h)
        val size = MeasureSpec.getSize(h)
        val spec = if (mode == MeasureSpec.EXACTLY) h
        else MeasureSpec.makeMeasureSpec(if (size == 0) maxH else min(size, maxH), MeasureSpec.AT_MOST)
        super.onMeasure(w, spec)
    }
}

/**
 * "Kis kurzor": koppints egy nyílra (fel/le/balra/jobbra) vagy a közepén az OK-ra,
 * illetve húzd az ujjad – minden ~52 dp elmozdulás egy lépés.
 */
class PadView(c: Context, private val accent: Int, private val onKey: (Key) -> Unit) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val step = 52f * resources.displayMetrics.density
    private var lastX = 0f
    private var lastY = 0f
    private var moved = false
    private var pressed: Key? = null

    private fun fire(k: Key) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        onKey(k)
        pressed = k
        invalidate()
        postDelayed({ pressed = null; invalidate() }, 140)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastX = e.x; lastY = e.y; moved = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - lastX
                val dy = e.y - lastY
                if (abs(dx) >= step || abs(dy) >= step) {
                    moved = true
                    if (abs(dx) > abs(dy)) fire(if (dx > 0) Key.RIGHT else Key.LEFT)
                    else fire(if (dy > 0) Key.DOWN else Key.UP)
                    lastX = e.x; lastY = e.y
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!moved) {
                    val dx = e.x - width / 2f
                    val dy = e.y - height / 2f
                    val r = min(width, height) / 2f
                    when {
                        hypot(dx, dy) < r * 0.3f -> fire(Key.OK)
                        abs(dx) > abs(dy) -> fire(if (dx > 0) Key.RIGHT else Key.LEFT)
                        else -> fire(if (dy > 0) Key.DOWN else Key.UP)
                    }
                }
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) / 2f - 3f
        val d = resources.displayMetrics.density

        // külső fény (háttérvilágítás)
        p.style = Paint.Style.STROKE
        p.shader = null
        p.strokeWidth = 8f * d
        p.color = (accent and 0x00FFFFFF) or 0x30000000
        canvas.drawCircle(cx, cy, r - 4f * d, p)

        // krómgyűrű
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            0f, cy - r, 0f, cy + r,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFB9C0C8.toInt(), 0xFF6C737C.toInt(), 0xFFDDE2E8.toInt()),
            floatArrayOf(0f, 0.45f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r - 6f * d, p)

        // sötét gumi tárcsa
        p.shader = RadialGradient(cx, cy - r * 0.3f, r, 0xFF3A4049.toInt(), 0xFF121418.toInt(), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r - 10f * d, p)
        p.shader = null

        val keys = arrayOf(Key.UP, Key.RIGHT, Key.DOWN, Key.LEFT)
        for (i in 0..3) {
            canvas.save()
            canvas.rotate(90f * i, cx, cy)
            path.reset()
            path.moveTo(cx, cy - r * 0.78f)
            path.lineTo(cx - r * 0.16f, cy - r * 0.55f)
            path.lineTo(cx + r * 0.16f, cy - r * 0.55f)
            path.close()
            val on = pressed == keys[i]
            p.style = Paint.Style.STROKE
            p.strokeWidth = 6f * d
            p.color = (accent and 0x00FFFFFF) or (if (on) 0xAA000000.toInt() else 0x44000000)
            canvas.drawPath(path, p)
            p.style = Paint.Style.FILL
            p.color = if (on) Color.WHITE else accent
            canvas.drawPath(path, p)
            canvas.restore()
        }

        // középső OK: domború ezüst gomb
        val okR = r * 0.32f
        p.style = Paint.Style.FILL
        p.color = 0x66000000
        canvas.drawCircle(cx, cy + 2f * d, okR, p)
        p.shader = if (pressed == Key.OK)
            RadialGradient(cx, cy, okR, Color.WHITE, accent, Shader.TileMode.CLAMP)
        else LinearGradient(
            0f, cy - okR, 0f, cy + okR,
            intArrayOf(0xFFF7F9FB.toInt(), 0xFFC9CFD6.toInt(), 0xFF8D949D.toInt()),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, okR, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f * d
        p.color = accent
        canvas.drawCircle(cx, cy, okR, p)
        p.style = Paint.Style.FILL
        p.color = 0xFF1B1F25.toInt()
        p.textAlign = Paint.Align.CENTER
        p.textSize = r * 0.2f
        p.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("OK", cx, cy + p.textSize * 0.35f, p)
    }
}

enum class KeyIcon { POWER, VOL_UP, VOL_DOWN, MUTE, CH_UP, CH_DOWN, BACK, HOME, BULB, SUN_UP, SUN_DOWN, SWATCH }

/**
 * Univerzális távirányító-gomb: domború gumi gomb fényes peremmel, háttérvilágítású ikonnal és LED-csíkkal.
 * Lenyomáskor összehúzódik és felizzik. A `repeat` gombok nyomva tartva ismétlődnek (hangerő, fényerő, csatorna).
 */
class RemoteKey(
    c: Context,
    private val icon: KeyIcon,
    private val label: String,
    private val accent: Int,
    private val repeat: Boolean = false,
    private val onPress: () -> Unit
) : View(c) {
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rect = RectF()
    private val oval = RectF()
    private var down = false
    private var repeating = false

    private val startRepeat = object : Runnable {
        override fun run() {
            repeating = true
            fire()
            postDelayed(this, 150)
        }
    }

    private fun fire() {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        onPress()
    }

    private fun withA(c: Int, a: Int) = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    override fun onMeasure(ws: Int, hs: Int) {
        val w = MeasureSpec.getSize(ws)
        val h = if (MeasureSpec.getMode(hs) == MeasureSpec.EXACTLY) MeasureSpec.getSize(hs)
        else (w * 0.9f).coerceIn(68f * d, 96f * d).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                down = true; repeating = false; invalidate()
                if (repeat) postDelayed(startRepeat, 420)
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(startRepeat)
                val inside = e.x in 0f..width.toFloat() && e.y in 0f..height.toFloat()
                if (down && !repeating && inside) fire()
                down = false; repeating = false; invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(startRepeat)
                down = false; repeating = false; invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val sc = if (down) 0.93f else 1f
        canvas.save()
        canvas.scale(sc, sc, w / 2f, h / 2f)

        val inset = 6f * d
        rect.set(inset, inset, w - inset, h - inset)
        val cr = min(w, h) * 0.42f
        val boost = if (down) 2.2f else 1f

        // háttérvilágítás a gomb körül
        p.style = Paint.Style.STROKE
        p.strokeWidth = 8f * d
        p.color = withA(accent, (22 * boost).toInt())
        canvas.drawRoundRect(rect, cr, cr, p)

        // árnyék a ház felé
        p.style = Paint.Style.FILL
        p.color = 0x66000000
        rect.offset(0f, 2.5f * d)
        canvas.drawRoundRect(rect, cr, cr, p)
        rect.offset(0f, -2.5f * d)

        // gumi gombtest
        p.shader = LinearGradient(
            0f, rect.top, 0f, rect.bottom,
            if (down) withA(accent, 140) else 0xFF3C424B.toInt(),
            if (down) 0xFF14171B.toInt() else 0xFF17191D.toInt(), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, cr, cr, p)

        // fényes perem (felül világos, alul sötét)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.4f * d
        p.shader = LinearGradient(
            0f, rect.top, 0f, rect.bottom,
            0xAAFFFFFF.toInt(), 0x22FFFFFF, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, cr, cr, p)
        p.shader = null

        // kis színes LED-csík alul
        p.style = Paint.Style.FILL
        p.color = withA(accent, if (down) 255 else 190)
        val lw = rect.width() * 0.22f
        canvas.drawRoundRect(
            w / 2f - lw / 2f, rect.bottom - 5f * d, w / 2f + lw / 2f, rect.bottom - 3f * d,
            2f * d, 2f * d, p
        )

        // ikon + felirat
        val hasLabel = label.isNotEmpty()
        val iy = if (hasLabel) h * 0.40f else h / 2f
        val isz = min(w, h) * 0.2f
        if (icon != KeyIcon.SWATCH) drawIcon(canvas, w / 2f, iy, isz, withA(accent, if (down) 150 else 90), 2.6f)
        drawIcon(canvas, w / 2f, iy, isz, 0xFFF2F5FA.toInt(), 1f)
        if (hasLabel) {
            p.style = Paint.Style.FILL
            p.color = 0xFFD5DBE6.toInt()
            p.textAlign = Paint.Align.CENTER
            p.typeface = Typeface.DEFAULT_BOLD
            var ts = min(w, h) * 0.15f
            p.textSize = ts
            while (p.measureText(label) > w * 0.8f && ts > 8f * d) { ts -= 1f; p.textSize = ts }
            canvas.drawText(label, w / 2f, h * 0.80f, p)
        }
        canvas.restore()
    }

    private fun drawIcon(canvas: Canvas, cx: Float, cy: Float, s: Float, ink: Int, widthMul: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(2.5f * d, s * 0.2f) * widthMul
        p.color = ink
        when (icon) {
            KeyIcon.POWER -> {
                oval.set(cx - s, cy - s, cx + s, cy + s)
                canvas.drawArc(oval, -60f, 300f, false, p)
                canvas.drawLine(cx, cy - s * 1.15f, cx, cy - s * 0.1f, p)
            }
            KeyIcon.VOL_UP, KeyIcon.VOL_DOWN, KeyIcon.MUTE -> {
                val sx = cx - s * 0.4f
                path.reset()
                path.moveTo(sx - s * 0.6f, cy - s * 0.35f)
                path.lineTo(sx - s * 0.2f, cy - s * 0.35f)
                path.lineTo(sx + s * 0.35f, cy - s * 0.8f)
                path.lineTo(sx + s * 0.35f, cy + s * 0.8f)
                path.lineTo(sx - s * 0.2f, cy + s * 0.35f)
                path.lineTo(sx - s * 0.6f, cy + s * 0.35f)
                path.close()
                canvas.drawPath(path, p)
                val ax = sx + s * 0.35f
                if (icon == KeyIcon.MUTE) {
                    canvas.drawLine(ax + s * 0.3f, cy - s * 0.45f, ax + s * 0.95f, cy + s * 0.45f, p)
                    canvas.drawLine(ax + s * 0.3f, cy + s * 0.45f, ax + s * 0.95f, cy - s * 0.45f, p)
                } else {
                    oval.set(ax - s * 0.5f, cy - s * 0.5f, ax + s * 0.5f, cy + s * 0.5f)
                    canvas.drawArc(oval, -45f, 90f, false, p)
                    if (icon == KeyIcon.VOL_UP) {
                        oval.set(ax - s * 0.95f, cy - s * 0.95f, ax + s * 0.95f, cy + s * 0.95f)
                        canvas.drawArc(oval, -45f, 90f, false, p)
                    }
                }
            }
            KeyIcon.CH_UP, KeyIcon.CH_DOWN -> {
                val dir = if (icon == KeyIcon.CH_UP) -1f else 1f
                for (y in floatArrayOf(cy - s * 0.45f, cy + s * 0.3f)) {
                    path.reset()
                    path.moveTo(cx - s * 0.8f, y - dir * s * 0.3f)
                    path.lineTo(cx, y + dir * s * 0.4f)
                    path.lineTo(cx + s * 0.8f, y - dir * s * 0.3f)
                    canvas.drawPath(path, p)
                }
            }
            KeyIcon.BACK -> {
                canvas.drawLine(cx + s * 0.9f, cy, cx - s * 0.8f, cy, p)
                path.reset()
                path.moveTo(cx - s * 0.2f, cy - s * 0.6f)
                path.lineTo(cx - s * 0.8f, cy)
                path.lineTo(cx - s * 0.2f, cy + s * 0.6f)
                canvas.drawPath(path, p)
            }
            KeyIcon.HOME -> {
                path.reset()
                path.moveTo(cx - s, cy - s * 0.05f)
                path.lineTo(cx, cy - s * 0.95f)
                path.lineTo(cx + s, cy - s * 0.05f)
                canvas.drawPath(path, p)
                path.reset()
                path.moveTo(cx - s * 0.7f, cy - s * 0.1f)
                path.lineTo(cx - s * 0.7f, cy + s * 0.85f)
                path.lineTo(cx + s * 0.7f, cy + s * 0.85f)
                path.lineTo(cx + s * 0.7f, cy - s * 0.1f)
                canvas.drawPath(path, p)
            }
            KeyIcon.BULB -> {
                oval.set(cx - s * 0.62f, cy - s * 0.95f, cx + s * 0.62f, cy + s * 0.29f)
                p.style = Paint.Style.FILL
                p.color = withA(accent, 110)
                canvas.drawOval(oval, p)
                p.style = Paint.Style.STROKE
                p.color = ink
                canvas.drawOval(oval, p)
                canvas.drawLine(cx - s * 0.32f, cy + s * 0.6f, cx + s * 0.32f, cy + s * 0.6f, p)
                canvas.drawLine(cx - s * 0.22f, cy + s * 0.92f, cx + s * 0.22f, cy + s * 0.92f, p)
            }
            KeyIcon.SUN_UP, KeyIcon.SUN_DOWN -> {
                if (icon == KeyIcon.SUN_DOWN) p.color = withA(ink, (ink ushr 24) * 2 / 3)
                canvas.drawCircle(cx, cy, s * 0.4f, p)
                val inner = s * 0.68f
                val outer = if (icon == KeyIcon.SUN_UP) s * 1.0f else s * 0.85f
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    canvas.drawLine(
                        cx + (cos(a) * inner).toFloat(), cy + (sin(a) * inner).toFloat(),
                        cx + (cos(a) * outer).toFloat(), cy + (sin(a) * outer).toFloat(), p
                    )
                }
            }
            KeyIcon.SWATCH -> {
                p.style = Paint.Style.FILL
                p.color = accent
                canvas.drawCircle(cx, cy, s * 0.85f, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 2f * d
                p.color = 0xCCE6EAF0.toInt()
                canvas.drawCircle(cx, cy, s * 0.85f, p)
            }
        }
    }
}

/**
 * A távirányító beépített kijelzője: kék háttérvilágítású LCD, pásztázó sorokkal.
 * Felül: üzemmód + óra, középen nagy betűvel az utolsó parancs, alul a kamera által látott tárgy és az IR jelző.
 */
class LcdView(c: Context) : View(c) {
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val ink = 0xFF9FF6FF.toInt()
    private var tx = 0f

    var mode = ""; set(v) { field = v; invalidate() }
    var clock = ""; set(v) { field = v; invalidate() }
    var main = ""; set(v) { field = v; invalidate() }
    var caption = ""; set(v) { field = v; invalidate() }
    var sub = ""; set(v) { field = v; invalidate() }

    /** 0..1 – az IR jelző és a térerő-csíkok fényessége, küldéskor 1-re ugrik és elhalványul. */
    private var signal = 0f
    private val fade = ValueAnimator.ofFloat(1f, 0f).apply {
        duration = 700
        addUpdateListener { signal = it.animatedValue as Float; invalidate() }
    }
    private val marquee = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 4000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { tx = it.animatedFraction; if (scrolling) invalidate() }
    }
    private var scrolling = false

    fun flash() { fade.cancel(); fade.start() }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); marquee.start() }
    override fun onDetachedFromWindow() { marquee.cancel(); fade.cancel(); super.onDetachedFromWindow() }

    override fun onMeasure(ws: Int, hs: Int) {
        val w = MeasureSpec.getSize(ws)
        val h = if (MeasureSpec.getMode(hs) == MeasureSpec.EXACTLY) MeasureSpec.getSize(hs)
        else (w * 0.36f).coerceIn(104f * d, 140f * d).toInt()
        setMeasuredDimension(w, h)
    }

    private fun glowText(canvas: Canvas, t: String, x: Float, y: Float, size: Float, align: Paint.Align, color: Int = ink) {
        p.textAlign = align
        p.textSize = size
        p.style = Paint.Style.FILL
        p.color = (color and 0x00FFFFFF) or 0x55000000
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.16f
        canvas.drawText(t, x, y, p)
        p.style = Paint.Style.FILL
        p.color = color
        canvas.drawText(t, x, y, p)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // króm keret
        rect.set(0f, 0f, w, h)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(0xFFF4F6F8.toInt(), 0xFF9AA1AA.toInt(), 0xFF5E646C.toInt(), 0xFFCED3D9.toInt()),
            floatArrayOf(0f, 0.4f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, 18f * d, 18f * d, p)

        // fekete üveg
        rect.inset(4f * d, 4f * d)
        p.shader = null
        p.color = 0xFF050708.toInt()
        canvas.drawRoundRect(rect, 14f * d, 14f * d, p)

        // háttérvilágított képernyő
        rect.inset(6f * d, 6f * d)
        val cr = 8f * d
        p.shader = RadialGradient(
            rect.centerX(), rect.centerY(), rect.width() * 0.7f,
            0xFF0E4D63.toInt(), 0xFF041A24.toInt(), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, cr, cr, p)
        p.shader = null

        canvas.save()
        canvas.clipRect(rect)

        // LCD rács / pásztázó sorok
        p.color = 0x26000000
        p.strokeWidth = 1f
        var y = rect.top
        while (y < rect.bottom) { canvas.drawLine(rect.left, y, rect.right, y, p); y += 3f * d }

        p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        val pad = 10f * d
        val small = (rect.height() * 0.14f).coerceAtMost(13f * d)

        // felső sor: üzemmód + óra
        glowText(canvas, mode, rect.left + pad, rect.top + pad + small * 0.8f, small, Paint.Align.LEFT)
        glowText(canvas, clock, rect.right - pad, rect.top + pad + small * 0.8f, small, Paint.Align.RIGHT)

        // vékony elválasztó
        p.color = 0x449FF6FF
        p.strokeWidth = 1f * d
        val sepTop = rect.top + pad + small * 1.35f
        canvas.drawLine(rect.left + pad, sepTop, rect.right - pad, sepTop, p)

        // fő sor: nagy betűs parancs (ha túl hosszú, futófény)
        var big = rect.height() * 0.30f
        p.textSize = big
        val maxW = rect.width() - pad * 2
        while (p.measureText(main) > maxW && big > rect.height() * 0.2f) { big -= 1f; p.textSize = big }
        val mw = p.measureText(main)
        val mainY = rect.centerY() + big * 0.30f
        scrolling = mw > maxW
        if (scrolling) {
            val span = mw + maxW * 0.4f
            val x = rect.left + pad - tx * span
            glowText(canvas, main, x, mainY, big, Paint.Align.LEFT)
            glowText(canvas, main, x + span, mainY, big, Paint.Align.LEFT)
        } else glowText(canvas, main, rect.centerX(), mainY, big, Paint.Align.CENTER)

        // felirat a fő sor alatt
        if (caption.isNotEmpty()) {
            glowText(canvas, caption, rect.centerX(), mainY + small * 1.2f, small * 0.85f, Paint.Align.CENTER, 0xFF6FD3E6.toInt())
        }

        // alsó sor: kamera + IR jelző és térerő-csíkok
        val by = rect.bottom - pad * 0.8f
        glowText(canvas, sub, rect.left + pad, by, small, Paint.Align.LEFT)
        val barsW = 22f * d
        val bx = rect.right - pad - barsW
        for (i in 0 until 4) {
            val bh = small * (0.35f + i * 0.22f)
            val lit = signal > i * 0.2f
            p.style = Paint.Style.FILL
            p.color = if (lit) ink else 0x339FF6FF
            canvas.drawRect(bx + i * 6f * d, by - bh, bx + i * 6f * d + 4f * d, by, p)
        }
        glowText(
            canvas, "IR", bx - 6f * d, by, small, Paint.Align.RIGHT,
            if (signal > 0.05f) 0xFFFF5A5A.toInt() else 0x559FF6FF
        )
        canvas.restore()

        // üveg csillanás
        p.shader = LinearGradient(
            0f, rect.top, 0f, rect.top + rect.height() * 0.5f,
            0x22FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, cr, cr, p)
        p.shader = null
    }
}

/**
 * A távirányító háza: csiszolt ezüst keret, benne sötét grafit előlap, a tetején infravörös ablakkal
 * és kék háttérvilágítású szegéllyel.
 */
class RemoteShellDrawable(private val density: Float) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val lines = Random(7).let { rnd -> FloatArray(160) { rnd.nextFloat() } }
    private val glow = 0xFF4DD2FF.toInt()

    override fun draw(canvas: Canvas) {
        val b = bounds
        val dd = density
        val outerR = 44f * dd
        r.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())

        // csiszolt ezüst test
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            r.left, 0f, r.right, 0f,
            intArrayOf(
                0xFF7D848D.toInt(), 0xFFD3D8DE.toInt(), 0xFFF5F7F9.toInt(),
                0xFFC2C8CF.toInt(), 0xFFE4E8EC.toInt(), 0xFF757C85.toInt()
            ),
            floatArrayOf(0f, 0.12f, 0.35f, 0.6f, 0.85f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(r, outerR, outerR, p)
        p.shader = null

        // csiszolás-csíkok
        canvas.save()
        canvas.clipRect(r)
        p.strokeWidth = 1f
        for (i in lines.indices step 2) {
            val x = r.left + lines[i] * r.width()
            p.color = if (lines[i + 1] > 0.5f) 0x18FFFFFF else 0x14000000
            canvas.drawLine(x, r.top, x, r.bottom, p)
        }
        canvas.restore()

        // perem: felül világos, alul sötét
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * dd
        p.shader = LinearGradient(0f, r.top, 0f, r.bottom, 0xCCFFFFFF.toInt(), 0x88000000.toInt(), Shader.TileMode.CLAMP)
        r.inset(dd, dd)
        canvas.drawRoundRect(r, outerR, outerR, p)
        p.shader = null

        // IR ablak a tetején
        val irW = 54f * dd
        val irH = 9f * dd
        val cx = r.centerX()
        val irTop = r.top + 5f * dd
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, irTop, 0f, irTop + irH, 0xFF5A0C10.toInt(), 0xFF1A0204.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(cx - irW / 2, irTop, cx + irW / 2, irTop + irH, irH / 2, irH / 2, p)
        p.shader = null
        p.color = 0x55FFFFFF
        canvas.drawRoundRect(cx - irW / 2 + 6 * dd, irTop + 1.5f * dd, cx + irW / 2 - 6 * dd, irTop + 3f * dd, dd, dd, p)

        // grafit előlap
        val bezel = 9f * dd
        r.set(b.left + bezel, b.top + bezel + 10f * dd, b.right - bezel, b.bottom - bezel)
        val innerR = outerR - bezel
        p.shader = LinearGradient(0f, r.top, 0f, r.bottom, 0xFF262A31.toInt(), 0xFF101215.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(r, innerR, innerR, p)
        p.shader = null

        // kék háttérvilágítású szegély
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f * dd
        p.color = (glow and 0x00FFFFFF) or 0x2A000000
        canvas.drawRoundRect(r, innerR, innerR, p)
        p.strokeWidth = 1.5f * dd
        p.color = (glow and 0x00FFFFFF) or 0xAA000000.toInt()
        canvas.drawRoundRect(r, innerR, innerR, p)
    }

    override fun setAlpha(alpha: Int) { p.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { p.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
