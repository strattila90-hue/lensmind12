package hu.lensmind

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import kotlin.random.Random

/**
 * Fekete, szálcsiszolt fém felület lekerekített sarkokkal, fényes felső éllel
 * (iPhone 5 jellegű kemény, csiszolt él). Csak kirajzolás, nincs áttetszőség.
 */
class BrushedDrawable(
    private val density: Float,
    private val radiusDp: Float,
    private val top: Int,
    private val bottom: Int,
    private val rim: Int,
    private val glow: Int = 0,
    private val glowWidthDp: Float = 1.5f
) : Drawable() {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grain = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val rect = RectF()

    override fun draw(c: Canvas) {
        val b = bounds
        val inset = density * 1f
        rect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset)
        val r = radiusDp * density

        // alap: függőleges fekete átmenet
        fill.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, top, bottom, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, r, r, fill)

        // szálcsiszolás: vízszintes, finom csíkok
        grain.shader = BitmapShader(grainTexture, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        grain.alpha = 110
        c.drawRoundRect(rect, r, r, grain)

        // fényes felső él (ív)
        edge.shader = LinearGradient(
            0f, rect.top, 0f, rect.top + rect.height() * 0.35f,
            0x80FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP
        )
        c.drawRoundRect(rect, r, r, edge)
        edge.shader = null

        // sötét alsó él és keret
        edge.color = rim
        c.drawRoundRect(rect, r, r, edge)

        // színes izzás (ha van)
        if (glow != 0) {
            edge.color = glow
            edge.strokeWidth = glowWidthDp * density
            c.drawRoundRect(rect, r, r, edge)
            edge.strokeWidth = density
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        /** Egyszer generált szálcsiszolás-textúra. */
        private val grainTexture: Bitmap by lazy {
            val size = 256
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint()
            val rnd = Random(7)
            for (y in 0 until size) {
                var x = 0f
                while (x < size) {
                    val len = rnd.nextInt(18, 90).toFloat()
                    p.color = (rnd.nextInt(6, 26) shl 24) or 0xFFFFFF
                    c.drawLine(x, y.toFloat(), x + len, y.toFloat(), p)
                    x += len
                }
            }
            bmp
        }
    }
}

/** Fekete panel (távirányító háttér, dobozok). */
fun metalPanel(density: Float, radiusDp: Float): Drawable =
    BrushedDrawable(
        density, radiusDp,
        top = 0xFF121418.toInt(), bottom = 0xFF000000.toInt(),
        rim = 0xFF2A2E36.toInt()
    )

/** Fekete, csiszolt gomb. Nyomásra világosabb, a megadott szín izzik az élén. */
fun metalKey(density: Float, color: Int, strong: Boolean): Drawable {
    val izzas = (color and 0x00FFFFFF) or (if (strong) 0xFF000000.toInt() else 0x99000000.toInt())
    val normal = BrushedDrawable(
        density, 16f,
        top = 0xFF1C1F25.toInt(), bottom = 0xFF050506.toInt(),
        rim = 0xFF30353E.toInt(), glow = izzas
    )
    val pressed = BrushedDrawable(
        density, 16f,
        top = 0xFF2C3038.toInt(), bottom = 0xFF0A0B0D.toInt(),
        rim = 0xFF4A505A.toInt(), glow = color or 0xFF000000.toInt(), glowWidthDp = 2.5f
    )
    return StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), pressed)
        addState(IntArray(0), normal)
    }
}
