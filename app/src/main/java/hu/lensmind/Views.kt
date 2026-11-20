package hu.lensmind

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
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
        val r = min(w, h) / 2f
        rect.set(cx - r + 3f, cy - r + 3f, cx + r - 3f, cy + r - 3f)
        p.style = Paint.Style.FILL
        p.color = 0x1AFFFFFF
        canvas.drawRoundRect(rect, r * 0.45f, r * 0.45f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.color = accent
        canvas.drawRoundRect(rect, r * 0.45f, r * 0.45f, p)

        val keys = arrayOf(Key.UP, Key.RIGHT, Key.DOWN, Key.LEFT)
        for (i in 0..3) {
            canvas.save()
            canvas.rotate(90f * i, cx, cy)
            path.reset()
            path.moveTo(cx, cy - r * 0.82f)
            path.lineTo(cx - r * 0.17f, cy - r * 0.56f)
            path.lineTo(cx + r * 0.17f, cy - r * 0.56f)
            path.close()
            p.style = Paint.Style.FILL
            p.color = if (pressed == keys[i]) Color.WHITE else accent
            canvas.drawPath(path, p)
            canvas.restore()
        }

        p.style = Paint.Style.FILL
        p.color = if (pressed == Key.OK) 0x88FFFFFF.toInt() else (accent and 0x00FFFFFF) or 0x33000000
        canvas.drawCircle(cx, cy, r * 0.3f, p)
        p.style = Paint.Style.STROKE
        p.color = accent
        canvas.drawCircle(cx, cy, r * 0.3f, p)
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        p.textAlign = Paint.Align.CENTER
        p.textSize = r * 0.22f
        p.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("OK", cx, cy + p.textSize * 0.35f, p)
    }
}

enum class KeyIcon { POWER, VOL_UP, VOL_DOWN, MUTE, CH_UP, CH_DOWN, BACK, HOME, BULB, SUN_UP, SUN_DOWN, SWATCH }

/**
 * Egyedi dizájnú távirányító-gomb: neon keretes, kézzel rajzolt ikonnal.
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

        val inset = 7f * d
        rect.set(inset, inset, w - inset, h - inset)
        val cr = min(w, h) * 0.28f
        val boost = if (down) 1.7f else 1f

        // fény-gyűrűk (glow)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 9f * d
        p.color = withA(accent, (26 * boost).toInt())
        canvas.drawRoundRect(rect, cr, cr, p)
        p.strokeWidth = 5f * d
        p.color = withA(accent, (60 * boost).toInt())
        canvas.drawRoundRect(rect, cr, cr, p)

        // gomb teste
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            0f, rect.top, 0f, rect.bottom,
            if (down) withA(accent, 110) else 0xFF1C2431.toInt(), 0xFF090C11.toInt(), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, cr, cr, p)
        p.shader = null

        // neon keret
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.2f * d
        p.color = accent
        canvas.drawRoundRect(rect, cr, cr, p)

        // ikon + felirat
        val hasLabel = label.isNotEmpty()
        drawIcon(canvas, w / 2f, if (hasLabel) h * 0.40f else h / 2f, min(w, h) * 0.2f)
        if (hasLabel) {
            p.style = Paint.Style.FILL
            p.color = 0xFFD5DBE6.toInt()
            p.textAlign = Paint.Align.CENTER
            p.typeface = Typeface.DEFAULT_BOLD
            var ts = min(w, h) * 0.15f
            p.textSize = ts
            while (p.measureText(label) > w * 0.8f && ts > 8f * d) { ts -= 1f; p.textSize = ts }
            canvas.drawText(label, w / 2f, h * 0.84f, p)
        }
        canvas.restore()
    }

    private fun drawIcon(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val ink = 0xFFF2F5FA.toInt()
        p.style = Paint.Style.STROKE
        p.strokeWidth = max(2.5f * d, s * 0.2f)
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
                if (icon == KeyIcon.SUN_DOWN) p.color = withA(ink, 170)
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
                p.color = 0x88FFFFFF.toInt()
                canvas.drawCircle(cx, cy, s * 0.85f, p)
            }
        }
    }
}
