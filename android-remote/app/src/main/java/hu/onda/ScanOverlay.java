package hu.onda;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** A mini scan ablak rávetítése: sarokjelek és végigfutó pásztázó vonal. Zöld, ha a TV megvan. */
final class ScanOverlay extends View {
    private final Paint corner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator anim;
    private float t;
    private boolean tv, led;
    private final Paint chip = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);

    ScanOverlay(Context c) {
        super(c);
        corner.setStyle(Paint.Style.STROKE);
        corner.setStrokeCap(Paint.Cap.ROUND);
        corner.setStrokeWidth(dp(3));
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    void setState(boolean tvFound, boolean ledFound) { tv = tvFound; led = ledFound; invalidate(); }

    void run(boolean on) {
        if (anim != null) { anim.cancel(); anim = null; }
        if (!on) return;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(1600);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setRepeatMode(ValueAnimator.REVERSE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> { t = (float) a.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    private float pill(Canvas c, float x, float y, String label, int color) {
        float tw = txt.measureText(label), pw = tw + dp(14), ph = dp(20);
        chip.setColor(color);
        c.drawRoundRect(x, y, x + pw, y + ph, ph / 2, ph / 2, chip);
        txt.setColor(0xFF04140A);
        c.drawText(label, x + dp(7), y + ph - dp(6), txt);
        return x + pw + dp(6);
    }

    @Override
    protected void onDraw(Canvas c) {
        boolean found = tv || led;
        int col = tv ? 0xFF34C759 : led ? 0xFFD946EF : 0xFFFFB347;
        int w = getWidth(), h = getHeight();
        float m = dp(10), l = dp(18);
        corner.setColor(col);
        c.drawLines(new float[]{m, m + l, m, m, m, m, m + l, m}, corner);
        c.drawLines(new float[]{w - m - l, m, w - m, m, w - m, m, w - m, m + l}, corner);
        c.drawLines(new float[]{m, h - m - l, m, h - m, m, h - m, m + l, h - m}, corner);
        c.drawLines(new float[]{w - m - l, h - m, w - m, h - m, w - m, h - m, w - m, h - m - l}, corner);
        float y = m + (h - 2 * m) * t;
        line.setShader(new LinearGradient(0, y - dp(10), 0, y + dp(2), 0x00000000, col, Shader.TileMode.CLAMP));
        c.drawRect(m, y - dp(10), w - m, y + dp(2), line);
        if (found) {
            // lüktető keret: a felismerés látványos jelzése
            float pulse = 0.35f + 0.65f * t;
            Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(dp(4));
            ring.setColor(col);
            ring.setAlpha((int) (255 * pulse));
            c.drawRoundRect(dp(3), dp(3), w - dp(3), h - dp(3), dp(14), dp(14), ring);
            txt.setTextSize(dp(11));
            txt.setFakeBoldText(true);
            float x = m + dp(2), top = m + dp(24);
            if (tv) x = pill(c, x, top, "TV", 0xFF34C759);
            if (led) pill(c, x, top, "LED", 0xFFD946EF);
        }
    }
}
