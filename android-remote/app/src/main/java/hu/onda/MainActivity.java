package hu.onda;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
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
    private Updater.Info pending;
    private AlertDialog dialog;
    private boolean busy;

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
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        if (pending != null) {
            showForced(pending); // pl. az "ismeretlen források" engedélyezése után
        } else {
            checkForUpdate();
        }
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    /** Kötelező frissítés: új Release esetén addig nem lehet használni az appot, amíg fel nem telepíted. */
    private void checkForUpdate() {
        if (busy) return;
        busy = true;
        new Thread(() -> {
            Updater.Info info = null;
            try {
                info = Updater.fetchLatest();
            } catch (Exception ignored) {
                // nincs net: az app működik tovább
            }
            final Updater.Info found = info;
            ui.post(() -> {
                busy = false;
                if (found != null && found.build > BuildConfig.BUILD_NUMBER) {
                    pending = found;
                    showForced(found);
                }
            });
        }).start();
    }

    private void showForced(Updater.Info info) {
        if (dialog != null && dialog.isShowing()) return;
        if (!Updater.canInstall(this)) {
            dialog = new AlertDialog.Builder(this)
                    .setTitle("Új verzió: " + info.tag)
                    .setMessage("A frissítéshez engedélyezd az Onda Haptic számára az alkalmazások telepítését, majd lépj vissza.")
                    .setCancelable(false)
                    .setPositiveButton("Engedélyezés", (d, w) -> Updater.openInstallPermission(this))
                    .show();
            return;
        }
        dialog = new AlertDialog.Builder(this)
                .setTitle("Kötelező frissítés")
                .setMessage("Elérhető az új verzió: " + info.tag + ". A folytatáshoz telepítsd.")
                .setCancelable(false)
                .setPositiveButton("Telepítés", null)
                .create();
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> download(info));
    }

    private void download(Updater.Info info) {
        final AlertDialog d = dialog;
        d.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
        d.setMessage("Letöltés: 0%");
        new Thread(() -> {
            try {
                File f = Updater.downloadApk(this, info, pct -> ui.post(() -> d.setMessage("Letöltés: " + pct + "%")));
                ui.post(() -> {
                    d.setMessage("Letöltve. A telepítő megnyílik.");
                    Updater.install(this, f);
                    d.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                    d.getButton(DialogInterface.BUTTON_POSITIVE).setText("Újra telepítés");
                });
            } catch (Exception e) {
                ui.post(() -> {
                    Toast.makeText(this, "Hiba a letöltésnél: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    d.setMessage("A letöltés nem sikerült. Próbáld újra.");
                    d.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                    d.getButton(DialogInterface.BUTTON_POSITIVE).setText("Újra");
                });
            }
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
