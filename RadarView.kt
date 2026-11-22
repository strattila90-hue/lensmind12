package hu.lensmind

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min

/**
 * Radar-keret a kameraablak fölött: pásztázóvonal, célkereszt, sarokjelölők,
 * állapotfelirat és célkeret. CSAK megjelenítés – a valódi felismerés
 * (ML Kit) a MainActivity-ben fut, és a setState() hívással jelzi az eredményt.
 */
class RadarView(ctx: Context) : View(ctx) {

    private val dens = ctx.resources.displayMetrics.density
    private val lineP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dens }
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        isFakeBoldText = true
        textSize = 10f * dens
        letterSpacing = 0.14f
    }
    private val rect = RectF()
    private val corners = FloatArray(32)

    private var label = "KÉSZÜLÉK KERESÉSE"
    private var accent = 0xFF22E6FF.toInt()
    private var showTarget = false
    private var angle = 0f

    private val sweep = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 3400
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { angle = it.animatedValue as Float; invalidate() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!sweep.isStarted) sweep.start()
    }

    override fun onDetachedFromWindow() {
        sweep.cancel()
        super.onDetachedFromWindow()
    }

    /** Állapot frissítése: felirat, szín, és hogy látszódjon-e a célkeret. */
    fun setState(text: String, color: Int, target: Boolean) {
        if (text == label && color == accent && target == showTarget) return
        label = text
        accent = color
        showTarget = target
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) * 0.46f

        // körös skála
        lineP.strokeWidth = dens
        lineP.color = withAlpha(0x2F7CFF, 0x44)
        for (i in 1..3) c.drawCircle(cx, cy, r * i / 3f, lineP)

        // keresztvonal
        lineP.color = withAlpha(0x22E6FF, 0x55)
        c.drawLine(cx, 0f, cx, h, lineP)
        c.drawLine(0f, cy, w, cy, lineP)

        // pásztázó ék és vonal
        c.save()
        c.rotate(angle, cx, cy)
        fillP.color = withAlpha(0x22E6FF, 0x33)
        rect.set(cx - r, cy - r, cx + r, cy + r)
        c.drawArc(rect, -60f, 60f, true, fillP)
        lineP.color = withAlpha(0x22E6FF, 0xCC)
        c.drawLine(cx, cy, cx + r, cy, lineP)
        c.restore()

        // sarokjelölők
        val m = 8 * dens
        val k = 16 * dens
        lineP.strokeWidth = 2 * dens
        lineP.color = 0xFF22E6FF.toInt()
        val seg = floatArrayOf(
            m, m, m + k, m, m, m, m, m + k,
            w - m, m, w - m - k, m, w - m, m, w - m, m + k,
            m, h - m, m + k, h - m, m, h - m, m, h - m - k,
            w - m, h - m, w - m - k, h - m, w - m, h - m, w - m, h - m - k
        )
        c.drawLines(seg, lineP)

        // célkeret (ha a felismerés TV-t lát)
        if (showTarget) {
            rect.set(cx - w * 0.25f, cy - h * 0.25f, cx + w * 0.25f, cy + h * 0.25f)
            lineP.color = accent
            lineP.strokeWidth = 2 * dens
            c.drawRect(rect, lineP)
        }

        // állapotfelirat
        textP.color = accent
        val tw = textP.measureText(label)
        val pad = 8 * dens
        val boxH = 18 * dens
        val boxCy = h - 14 * dens
        rect.set(cx - tw / 2 - pad, boxCy - boxH / 2, cx + tw / 2 + pad, boxCy + boxH / 2)
        fillP.color = 0xDD05090F.toInt()
        c.drawRoundRect(rect, 9 * dens, 9 * dens, fillP)
        lineP.strokeWidth = dens
        lineP.color = accent
        c.drawRoundRect(rect, 9 * dens, 9 * dens, lineP)
        c.drawText(label, cx - tw / 2, boxCy + textP.textSize / 3, textP)
    }

    private fun withAlpha(rgb: Int, alpha: Int): Int = (alpha shl 24) or (rgb and 0xFFFFFF)
}
