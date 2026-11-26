package hu.onda;

import androidx.activity.ComponentActivity;
import androidx.annotation.OptIn;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.app.AlertDialog;
import android.graphics.Color;
import android.hardware.ConsumerIrManager;
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

public class MainActivity extends ComponentActivity {

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
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "OndaNative");
        lg = new LgTv(this, this::jsTvLink);
        buildLayout();
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
        stopScan("szünet");
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

    // ===================== Mini scan ablak: a TV azonosítása a kamerával =====================
    private static final Set<String> TV_LABELS = new HashSet<>(Arrays.asList(
            "Television", "Display device", "Computer monitor", "Monitor", "Screen", "Flat panel display"));
    private static final Set<String> LED_LABELS = new HashSet<>(Arrays.asList(
            "Lamp", "Light", "Lighting", "Light fixture", "Neon", "Light-emitting diode", "LED", "Light bulb",
            "Christmas lights", "Chandelier", "Lantern", "Lights", "Stage lighting", "Spotlight", "Strip lighting"));
    private static final Map<String, String> HU = new HashMap<>();
    static {
        HU.put("Television", "Televízió"); HU.put("Display device", "Kijelző"); HU.put("Computer monitor", "Monitor");
        HU.put("Monitor", "Monitor"); HU.put("Screen", "Képernyő"); HU.put("Flat panel display", "Lapos kijelző");
        HU.put("Lamp", "Lámpa"); HU.put("Light", "Fény"); HU.put("Lighting", "Világítás"); HU.put("Light fixture", "Lámpatest");
        HU.put("Neon", "Neon"); HU.put("Light-emitting diode", "LED"); HU.put("Light bulb", "Izzó"); HU.put("Christmas lights", "Fényfüzér"); HU.put("Room", "Szoba"); HU.put("Couch", "Kanapé"); HU.put("Table", "Asztal");
        HU.put("Laptop", "Laptop"); HU.put("Chair", "Szék"); HU.put("Window", "Ablak"); HU.put("Book", "Könyv");
    }

    private FrameLayout scanBox;
    private PreviewView preview;
    private ScanOverlay overlay;
    private TextView scanText;
    private boolean scanning;
    private boolean tvSeen;
    private boolean ledSeen;
    private long lastAnalysis;
    private ExecutorService camExec;
    private ImageLabeler labeler;
    private final Runnable scanTimeout = () -> stopScan("Idő lejárt");

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));

        scanBox = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF050506);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(2), 0xFF3A3E46);
        scanBox.setBackground(bg);
        scanBox.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(16)); }
        });
        scanBox.setClipToOutline(true);
        scanBox.setElevation(dp(10));
        scanBox.setVisibility(View.GONE);

        preview = new PreviewView(this);
        preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        scanBox.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        overlay = new ScanOverlay(this);
        scanBox.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        scanText = new TextView(this);
        scanText.setTextColor(0xFFFFB347);
        scanText.setTextSize(11);
        scanText.setTypeface(android.graphics.Typeface.MONOSPACE);
        scanText.setGravity(Gravity.CENTER);
        scanText.setBackgroundColor(0xCC050506);
        scanText.setPadding(dp(4), dp(4), dp(4), dp(4));
        scanText.setText("TV / LED KERESÉSE…");
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, -2);
        tp.gravity = Gravity.BOTTOM;
        scanBox.addView(scanText, tp);

        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(dp(116), dp(156));
        sp.gravity = Gravity.BOTTOM | Gravity.END;
        sp.setMargins(dp(12), dp(12), dp(12), dp(24));
        root.addView(scanBox, sp);

        // az ablak áthúzható
        final float[] d = new float[2];
        scanBox.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: d[0] = v.getX() - e.getRawX(); d[1] = v.getY() - e.getRawY(); return true;
                case MotionEvent.ACTION_MOVE: v.setX(e.getRawX() + d[0]); v.setY(e.getRawY() + d[1]); return true;
                default: return false;
            }
        });
        setContentView(root);
    }

    private void jsScanState(boolean on) {
        web.evaluateJavascript("window.onTvScanState&&window.onTvScanState(" + on + ")", null);
    }

    private void startScan() {
        if (scanning) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 11);
            return;
        }
        scanning = true;
        tvSeen = false;
        ledSeen = false;
        if (camExec == null) camExec = Executors.newSingleThreadExecutor();
        if (labeler == null) labeler = ImageLabeling.getClient(new ImageLabelerOptions.Builder().setConfidenceThreshold(0.6f).build());
        scanBox.setVisibility(View.VISIBLE);
        scanText.setText("TV / LED KERESÉSE…");
        overlay.setState(false, false);
        overlay.run(true);
        ui.removeCallbacks(scanTimeout);
        ui.postDelayed(scanTimeout, 60_000L);
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                ProcessCameraProvider provider = ProcessCameraProvider.getInstance(this).get();
                Preview p = new Preview.Builder().build();
                p.setSurfaceProvider(preview.getSurfaceProvider());
                ImageAnalysis ia = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
                ia.setAnalyzer(camExec, this::analyze);
                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, p, ia);
            } catch (Exception e) {
                stopScan("Kamera hiba");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void stopScan(String why) {
        ui.removeCallbacks(scanTimeout);
        if (!scanning && scanBox.getVisibility() != View.VISIBLE) return;
        scanning = false;
        overlay.run(false);
        scanBox.setVisibility(View.GONE);
        try { ProcessCameraProvider.getInstance(this).get().unbindAll(); } catch (Exception ignored) { }
        jsScanState(false);
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyze(ImageProxy proxy) {
        long now = SystemClock.elapsedRealtime();
        if (proxy.getImage() == null || now - lastAnalysis < 600 || !scanning) { proxy.close(); return; }
        lastAnalysis = now;
        InputImage in = InputImage.fromMediaImage(proxy.getImage(), proxy.getImageInfo().getRotationDegrees());
        labeler.process(in)
                .addOnSuccessListener(labels -> ui.post(() -> onLabels(labels)))
                .addOnCompleteListener(t -> proxy.close());
    }

    private void onLabels(List<ImageLabel> labels) {
        if (!scanning) return;
        boolean tv = false, led = false;
        String top = null;
        JSONArray names = new JSONArray();
        for (ImageLabel l : labels) {
            String n = l.getText();
            String hu = HU.containsKey(n) ? HU.get(n) : n;
            names.put(hu);
            if (top == null) top = hu;
            if (TV_LABELS.contains(n)) { tv = true; top = hu; }
            if (LED_LABELS.contains(n)) { led = true; if (!tv) top = hu; }
        }
        overlay.setState(tv, led);
        scanText.setTextColor(tv ? 0xFF34C759 : led ? 0xFFD946EF : 0xFFFFB347);
        scanText.setText(tv && led ? "TV + LED AZONOSÍTVA" : tv ? "TV AZONOSÍTVA" : led ? "LED AZONOSÍTVA"
                : (top == null ? "TV / LED KERESÉSE…" : top.toUpperCase()));
        if (tv != tvSeen || led != ledSeen || tv || led) {
            tvSeen = tv;
            ledSeen = led;
            try {
                JSONObject o = new JSONObject();
                o.put("tv", tv);
                o.put("led", led);
                o.put("label", top == null ? "" : top);
                o.put("labels", names);
                web.evaluateJavascript("window.onTvScan&&window.onTvScan(" + o + ")", null);
            } catch (Exception ignored) { }
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == 11) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) startScan();
            else { Toast.makeText(this, "A TV-kereséshez kamera engedély kell.", Toast.LENGTH_LONG).show(); jsScanState(false); }
        }
    }


    /** A weboldal innen kéri a rezgést: gombnyomásra tompa puffanás, tekeréskor apró kattanás. */
    private LgTv lg;

    private void jsTvLink(String ip, String state, String msg) {
        try {
            JSONObject o = new JSONObject();
            o.put("ip", ip);
            o.put("state", state);
            o.put("msg", msg);
            ui.post(() -> web.evaluateJavascript("window.onTvLink&&window.onTvLink(" + o + ")", null));
        } catch (Exception ignored) { }
    }

    private final ExecutorService irExec = Executors.newSingleThreadExecutor();
    private final AtomicInteger irPending = new AtomicInteger();

    private final class Bridge {
        @JavascriptInterface
        public void tvConnect(String ip) { lg.connect(ip); }

        @JavascriptInterface
        public void tvText(String ip, String text) { lg.text(ip, text); }

        @JavascriptInterface
        public void tvEnter(String ip) { lg.enter(ip); }

        @JavascriptInterface
        public boolean hasIr() {
            ConsumerIrManager m = (ConsumerIrManager) getSystemService(CONSUMER_IR_SERVICE);
            return m != null && m.hasIrEmitter();
        }

        /** Valódi IR jel a telefon adójából: az LG TV-nek vagy a LED-szalagnak. count = ismétlés (pl. hangerő lépések). */
        @JavascriptInterface
        public void ir(String key, int count, String fam) {
            final ConsumerIrManager m = (ConsumerIrManager) getSystemService(CONSUMER_IR_SERVICE);
            if (m == null || !m.hasIrEmitter()) return;
            final String dev = IrCodes.isLedKey(key) ? "LED_" + ("B".equals(fam) ? "B" : "A") : "LG";
            final int[] pat = IrCodes.pattern(dev, key);
            if (pat == null) return;
            final int n = Math.max(1, Math.min(count, 20));
            if (irPending.get() > 40) return; // gyors húzásnál ne torlódjon a sor
            irPending.addAndGet(n);
            irExec.execute(() -> {
                for (int i = 0; i < n; i++) {
                    try { m.transmit(IrCodes.CARRIER, pat); Thread.sleep(95); } catch (Exception ignored) { }
                    irPending.decrementAndGet();
                }
            });
        }

        @JavascriptInterface
        public void scan(boolean on) {
            ui.post(() -> { if (on) startScan(); else stopScan("kézzel"); });
        }

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
