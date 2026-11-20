package hu.lensmind

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ScrollView
import kotlin.math.cos
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
