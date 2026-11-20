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
