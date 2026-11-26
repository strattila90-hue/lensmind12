package hu.onda;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends Activity {

    private WebView web;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean busy;
    private boolean askedPermission;
    private String triedTag;
    private long triedAt;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#15171A"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "OndaNative");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
        scheduleBackgroundUpdates();
    }

    /** Háromóránként akkor is frissít, ha az appot nem nyitod meg. */
    private void scheduleBackgroundUpdates() {
        android.app.job.JobScheduler js = (android.app.job.JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        js.schedule(new android.app.job.JobInfo.Builder(7, new android.content.ComponentName(this, UpdateJob.class))
                .setPeriodic(3 * 60 * 60 * 1000L)
                .setRequiredNetworkType(android.app.job.JobInfo.NETWORK_TYPE_ANY)
                .build());
    }


    private final Runnable periodic = new Runnable() {
        @Override public void run() { checkForUpdate(); ui.postDelayed(this, 15 * 60 * 1000L); }
    };

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        ui.post(periodic);
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(periodic);
        web.onPause();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    /** Teljesen önálló frissítés: nincs gomb és nincs kérdés, az app maga tölti le és telepíti az új kiadást. */
    private void checkForUpdate() {
        if (busy) return;
        busy = true;
        new Thread(() -> {
            String msg = null;
            boolean needPermission = false;
            try {
                Updater.Info info = Updater.fetchLatest();
                if (info != null && info.build > BuildConfig.BUILD_NUMBER) {
                    if (!Updater.canInstall(this)) {
                        needPermission = true;
                    } else if (!info.tag.equals(triedTag) || System.currentTimeMillis() - triedAt > 30 * 60 * 1000L) {
                        triedTag = info.tag;
                        triedAt = System.currentTimeMillis();
                        ui.post(() -> Toast.makeText(this, "Új verzió (" + info.tag + "): frissítés letöltése…", Toast.LENGTH_LONG).show());
                        java.io.File f = Updater.downloadApk(this, info, pct -> { });
                        Updater.installSession(this, f);
                        msg = "Telepítés indul…";
                    }
                }
            } catch (Exception ignored) {
                // nincs net: az app működik tovább, a következő kör újra próbálja
            }
            final String m = msg;
            final boolean perm = needPermission;
            ui.post(() -> {
                busy = false;
                if (m != null) Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
                if (perm && !askedPermission) {
                    askedPermission = true;
                    new AlertDialog.Builder(this)
                            .setTitle("Önálló frissítés")
                            .setMessage("Az automatikus frissítéshez engedélyezd az Onda Haptic számára az alkalmazások telepítését. Egyszer kell.")
                            .setPositiveButton("Engedélyezés", (d, w) -> Updater.openInstallPermission(this))
                            .setNegativeButton("Később", null)
                            .show();
                }
            });
        }).start();
    }

    /** A weboldal innen kéri a rezgést: gombnyomásra tompa puffanás, tekeréskor apró kattanás. */
    private final class Bridge {
        @JavascriptInterface
        public void haptic(String kind) {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            VibrationEffect e;
            switch (kind) {
                case "down":
                case "stop":
                    e = VibrationEffect.createOneShot(kind.equals("stop") ? 38 : 30, 255); // tompa puffanás
                    break;
                case "power":
                    e = VibrationEffect.createWaveform(new long[]{0, 30, 50, 22}, new int[]{0, 255, 0, 160}, -1);
                    break;
                case "detent":
                    e = Build.VERSION.SDK_INT >= 29
                            ? VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                            : VibrationEffect.createOneShot(10, 170);
                    break;
                default: // tick, up
                    e = Build.VERSION.SDK_INT >= 29
                            ? VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                            : VibrationEffect.createOneShot(5, 90);
            }
            v.vibrate(e);
        }
    }
}
