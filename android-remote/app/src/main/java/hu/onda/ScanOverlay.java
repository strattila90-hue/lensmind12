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
    private boolean found;

    ScanOverlay(Context c) {
        super(c);
        corner.setStyle(Paint.Style.STROKE);
        corner.setStrokeCap(Paint.Cap.ROUND);
        corner.setStrokeWidth(dp(3));
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    void setFound(boolean f) { found = f; invalidate(); }

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

    @Override
    protected void onDraw(Canvas c) {
        int col = found ? 0xFF34C759 : 0xFFFFB347;
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
    }
}
