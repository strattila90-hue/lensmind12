package hu.lensmind

import android.Manifest
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Shader
import android.graphics.LinearGradient
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.hardware.ConsumerIrManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Size
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * Lens Mind + Remote – "Vissza a jövőbe" fejléc, alatta ezüst univerzális távirányító beépített LCD kijelzővel.
 * Minden kijelzőhöz igazodik: telefon, hajtogatható, tablet, álló/fekvő, kivágás (notch), többablakos mód.
 */
class MainActivity : AppCompatActivity() {

    // ---- színek ----
    private val cRed = 0xFFFF2A2A.toInt()
    private val cAmber = 0xFFFFB000.toInt()
    private val cGreen = 0xFF3CFF6A.toInt()
    private val cBlue = 0xFF4DD2FF.toInt()
    private val cOrange = 0xFFFF6A00.toInt()

    // ---- nézetek ----
    private lateinit var root: FrameLayout
    private lateinit var preview: PreviewView
    private lateinit var topGrad: View
    private lateinit var topBar: LinearLayout
    private lateinit var chips: TextView
    private lateinit var flux: FluxView
    private lateinit var panelScroll: MaxHeightScroll
    private lateinit var lcd: LcdView
    private lateinit var brandBtn: Button
    private lateinit var scanBtn: Button
    private lateinit var updateBtn: Button
    private lateinit var hintTv: TextView
    private lateinit var controls: LinearLayout
    private lateinit var catTvBtn: Button
    private lateinit var catLedBtn: Button
    private var hasIr = false

    // ---- állapot ----
    private val insets = intArrayOf(0, 0, 0, 0) // bal, felső, jobb, alsó
    private var previewUc: Preview? = null
    private var analysisUc: ImageAnalysis? = null
    private var ir: ConsumerIrManager? = null
    private var brand = Brand.LG
    private var topLabel: String? = null
    private var tvSeen = false
    private var scanning = false
    private var scanIdx = 0
    private var lastAnalysis = 0L
    private var frames = 0
    private var pending: Updater.Info? = null
    private var resumeUpdate = false
    private var lastUpdateCheck = -1L
    private var lastQr = ""
    private var lastQrAt = 0L
    private var lastCmd = "KÉSZ"
    private var lastCmdAt = ""

    private val handler = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor()
    private val io = Executors.newSingleThreadExecutor()
    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(0.6f).build()
    )
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    )

    private val tvLabels = setOf(
        "Television", "Display device", "Computer monitor", "Monitor", "Screen", "Flat panel display"
    )
    private val hu = mapOf(
        "Television" to "Televízió", "Display device" to "Kijelző", "Computer monitor" to "Monitor",
        "Lamp" to "Lámpa", "Room" to "Szoba", "Couch" to "Kanapé", "Table" to "Asztal",
        "Plant" to "Növény", "Flower" to "Virág", "Tree" to "Fa", "Dog" to "Kutya", "Cat" to "Macska",
        "Car" to "Autó", "Building" to "Épület", "Sky" to "Égbolt", "Food" to "Étel",
        "Book" to "Könyv", "Laptop" to "Laptop", "Chair" to "Szék", "Window" to "Ablak"
    )

    private val clockFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val clock = object : Runnable {
        override fun run() {
            lcd.clock = clockFmt.format(Date())
            handler.postDelayed(this, 1000)
        }
    }

    // =====================================================================
    //  Életciklus
    // =====================================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        ir = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        hasIr = runCatching { ir?.hasIrEmitter() }.getOrNull() == true
        brand = runCatching {
            Brand.valueOf(getPreferences(MODE_PRIVATE).getString("brand", Brand.LG.name)!!)
        }.getOrDefault(Brand.LG)
        Logger.d("main", "onCreate, IR adó: ${runCatching { ir?.hasIrEmitter() }.getOrNull()}, márka=${brand.name}")

        if (getSharedPreferences("dbg", MODE_PRIVATE).getString("crash", null) != null) {
            toast("Az előző futás összeomlott – részletek: 🐞")
        }

        buildUi()
        if (!getPreferences(MODE_PRIVATE).getBoolean("help_seen", false)) root.post { showHelp() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) startCamera()
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
    }

    override fun onResume() {
        super.onResume()
        handler.post(clock)
        val p = pending
        if (resumeUpdate && p != null && Updater.canInstall(this)) {
            resumeUpdate = false
            showUpdateDialog(p, autoStart = true)
        } else if (lastUpdateCheck < 0 || SystemClock.elapsedRealtime() - lastUpdateCheck > 30 * 60 * 1000L) {
            checkUpdate(silent = true)
        }
    }

    override fun onPause() {
        handler.removeCallbacks(clock)
        if (scanning) stopScan(saveChoice = false)
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Logger.d("main", "konfig változott: ${newConfig.screenWidthDp}x${newConfig.screenHeightDp}dp")
        applyLayout()
        updateRotation()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startCamera()
        else { Logger.d("main", "kamera engedély megtagadva"); toast("Kamera engedély nélkül a Lens Mind nem lát.") }
    }

    override fun onDestroy() {
        scanning = false
        handler.removeCallbacksAndMessages(null)
        labeler.close(); scanner.close()
        exec.shutdown(); io.shutdown()
        super.onDestroy()
    }

    // =====================================================================
    //  UI építés
    // =====================================================================
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    private fun glassBg(radiusDp: Int = 24): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(0xE6141922.toInt(), 0xEB090B10.toInt())
    ).apply {
        cornerRadius = dp(radiusDp).toFloat()
        setStroke(dp(1), 0x40FFFFFF)
    }

    private fun neon(color: Int, strong: Boolean = false): Drawable {
        val g = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor((color and 0x00FFFFFF) or (if (strong) 0x55000000 else 0x1A000000))
            setStroke(dp(2), color)
        }
        return RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), g, null)
    }

    private fun neonBtn(t: String, color: Int, strong: Boolean = false, onClick: (View) -> Unit) =
        Button(this).apply {
            text = t
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = neon(color, strong)
            stateListAnimator = null
            minHeight = dp(52); minimumHeight = dp(52)
            setShadowLayer(dp(6).toFloat(), 0f, 0f, color)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick(it)
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
        }

    private fun iconBtn(t: String, color: Int, onClick: () -> Unit) = TextView(this).apply {
        text = t
        textSize = 15f
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        background = neon(color)
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { leftMargin = dp(8) }
        setOnClickListener { it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); onClick() }
    }

    private fun row(vararg v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        v.forEach { addView(it) }
    }

    /** Sötét gumi gomb a távirányító előlapján, színes LED-szegéllyel. */
    private fun remoteBg(color: Int, strong: Boolean = false): Drawable {
        val g = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (strong) intArrayOf((color and 0x00FFFFFF) or 0x66000000, 0xFF15181C.toInt())
            else intArrayOf(0xFF383E46.toInt(), 0xFF17191D.toInt())
        ).apply {
            cornerRadius = dp(22).toFloat()
            setStroke(dp(if (strong) 2 else 1), if (strong) color else (color and 0x00FFFFFF) or 0x99000000.toInt())
        }
        return RippleDrawable(ColorStateList.valueOf((color and 0x00FFFFFF) or 0x55000000), g, null)
    }

    private fun remoteBtn(t: String, color: Int, onClick: (View) -> Unit) =
        neonBtn(t, color, onClick = onClick).apply { background = remoteBg(color); textSize = 15f }

    private fun chrome(tv: TextView) {
        tv.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            val h = (bottom - top).toFloat()
            if (h > 0 && tv.paint.shader == null) {
                tv.paint.shader = LinearGradient(
                    0f, 0f, 0f, h,
                    intArrayOf(0xFFFFFFFF.toInt(), 0xFFC4CDD8.toInt(), 0xFF69727E.toInt(), 0xFFE8EEF5.toInt()),
                    floatArrayOf(0f, 0.46f, 0.5f, 1f), Shader.TileMode.CLAMP
                )
                tv.invalidate()
            }
        }
    }

    private fun buildUi() {
        root = FrameLayout(this).apply { setBackgroundColor(0xFF05060A.toInt()) }
        preview = PreviewView(this)
        root.addView(preview, FrameLayout.LayoutParams(-1, -1))

        topGrad = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xCC000000.toInt(), 0x00000000)
            )
        }
        root.addView(topGrad, FrameLayout.LayoutParams(-1, dp(170), Gravity.TOP))

        // ---- fejléc ----
        val title = TextView(this).apply {
            text = "LENS MIND"
            textSize = 26f
            typeface = Typeface.create("sans-serif-black", Typeface.ITALIC)
            letterSpacing = 0.1f
            setTextColor(Color.WHITE)
            setShadowLayer(dp(8).toFloat(), 0f, 0f, cOrange)
        }
        chrome(title)
        val sub = TextView(this).apply {
            text = "TV-TÁVIRÁNYÍTÓ KAMERÁVAL  ·  ${BuildConfig.VERSION_NAME}"
            textSize = 10f
            letterSpacing = 0.08f
            setTextColor(0xFFFFB27A.toInt())
        }
        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(StripesView(this@MainActivity), LinearLayout.LayoutParams(dp(150), dp(10)).apply { topMargin = dp(2) })
            addView(sub, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(3) })
        }
        flux = FluxView(this)
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(titleBlock, LinearLayout.LayoutParams(0, -2, 1f))
            addView(flux, LinearLayout.LayoutParams(dp(52), dp(52)))
            addView(iconBtn("?", cGreen) { showHelp() })
            addView(iconBtn("🐞", cAmber) { showDebug() })
            addView(iconBtn("QR", cBlue) { showQr() })
        }
        chips = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            background = glassBg(18)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            maxLines = 2
            text = "🔍 Még nem látok semmit – irányítsd a kamerát a tévére"
        }
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(headerRow)
            addView(chips, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
        root.addView(topBar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        // ---- távirányító: ház + beépített kijelző ----
        lcd = LcdView(this).apply { clock = clockFmt.format(Date()) }

        hintTv = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(0x33FFB000)
                setStroke(dp(1), 0x88FFB000.toInt())
            }
        }
        controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        catTvBtn = remoteBtn("📺 Tévé", cBlue) { setCategory(Cat.TV) }
        catLedBtn = remoteBtn("💡 LED lámpa / szalag", cBlue) { setCategory(Cat.LED) }
        brandBtn = remoteBtn("", cOrange) { cycleBrand() }
        scanBtn = remoteBtn("🔎 Márka keresése automatikusan", cBlue) { if (!scanning) startScan() else stopScan(true) }
        updateBtn = remoteBtn("⬆  Frissítés keresése", cGreen) { onUpdateClick() }
        updateBtn.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(4), dp(10), dp(4), dp(2)) }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(34), dp(22), dp(24))
            addView(lcd, LinearLayout.LayoutParams(-1, -2))
            addView(hintTv, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            addView(row(catTvBtn, catLedBtn), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            addView(controls)
            addView(updateBtn)
        }
        panelScroll = MaxHeightScroll(this).apply {
            background = RemoteShellDrawable(resources.displayMetrics.density)
            isVerticalScrollBarEnabled = false
            addView(inner)
        }
        root.addView(panelScroll, FrameLayout.LayoutParams(-1, -2))

        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, wi ->
            val b = wi.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            insets[0] = b.left; insets[1] = b.top; insets[2] = b.right; insets[3] = b.bottom
            applyLayout()
            wi
        }
        applyLayout()
        buildControls()
        refreshUi()
    }

    /** Reszponzív elrendezés: álló = alul dokkolt panel, fekvő/tablet = oldalsó panel. */
    private fun applyLayout() {
        val cfg = resources.configuration
        val d = resources.displayMetrics.density
        val wPx = (cfg.screenWidthDp * d).toInt()
        val hPx = (cfg.screenHeightDp * d).toInt()
        val (l, t, r, b) = insets
        val sideDock = cfg.screenWidthDp > cfg.screenHeightDp && cfg.screenWidthDp >= 560

        val panelW = if (sideDock) min(dp(400), (wPx * 0.40f).toInt()) else min(wPx - dp(16), dp(560))
        val lp = FrameLayout.LayoutParams(panelW, ViewGroup.LayoutParams.WRAP_CONTENT)
        if (sideDock) {
            lp.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            lp.rightMargin = r + dp(12); lp.topMargin = t + dp(12); lp.bottomMargin = b + dp(12)
            panelScroll.maxH = (hPx - t - b - dp(24)).coerceAtLeast(dp(200))
        } else {
            lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            lp.bottomMargin = b + dp(8)
            panelScroll.maxH = (hPx * 0.58f).toInt().coerceAtLeast(dp(220))
        }
        panelScroll.layoutParams = lp

        topBar.setPadding(l + dp(14), t + dp(10), r + dp(14), dp(6))
        topBar.layoutParams = FrameLayout.LayoutParams(
            if (sideDock) wPx - panelW - r - dp(12) else ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START
        )
        topGrad.layoutParams = FrameLayout.LayoutParams(-1, t + dp(150), Gravity.TOP)
    }

    /** Egyedi dizájnú távirányító-gomb, egyenlő szélességgel egy sorban. */
    private fun rk(icon: KeyIcon, label: String, color: Int, k: Key, repeat: Boolean = false) =
        RemoteKey(this, icon, label, color, repeat) { send(k) }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
        }

    /** Az aktuális eszközcsoport (tévé / LED) gombjai. */
    private fun buildControls() {
        controls.removeAllViews()
        (brandBtn.parent as? ViewGroup)?.removeView(brandBtn)
        (scanBtn.parent as? ViewGroup)?.removeView(scanBtn)
        val grey = 0xFF9AA3B2.toInt()
        if (brand.cat == Cat.TV) {
            controls.addView(
                RemoteKey(this, KeyIcon.POWER, "BE / KI", cRed, false) { send(Key.POWER) },
                LinearLayout.LayoutParams(dp(130), dp(112)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(10) }
            )
            controls.addView(row(
                rk(KeyIcon.VOL_DOWN, "Halkabb", cBlue, Key.VOL_DOWN, true),
                rk(KeyIcon.VOL_UP, "Hangosabb", cBlue, Key.VOL_UP, true),
                rk(KeyIcon.MUTE, "Némítás", cAmber, Key.MUTE)
            ))
            controls.addView(row(brandBtn))
            controls.addView(
                label("KIS KURZOR – koppints a nyilakra, vagy húzd az ujjad", 11f, grey, true),
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(14) }
            )
            controls.addView(
                PadView(this, cBlue) { send(it) },
                LinearLayout.LayoutParams(dp(200), dp(200)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(8) }
            )
            controls.addView(row(
                rk(KeyIcon.BACK, "Vissza", cOrange, Key.BACK),
                rk(KeyIcon.HOME, "Főmenü", cOrange, Key.HOME)
            ))
            controls.addView(row(
                rk(KeyIcon.CH_DOWN, "Csatorna −", cGreen, Key.CH_DOWN, true),
                rk(KeyIcon.CH_UP, "Csatorna +", cGreen, Key.CH_UP, true)
            ))
        } else {
            controls.addView(row(
                rk(KeyIcon.BULB, "BE", cGreen, Key.LED_ON),
                rk(KeyIcon.POWER, "KI", cRed, Key.LED_OFF)
            ))
            controls.addView(row(
                rk(KeyIcon.SUN_DOWN, "Halványabb", cBlue, Key.BRIGHT_DOWN, true),
                rk(KeyIcon.SUN_UP, "Fényesebb", cBlue, Key.BRIGHT_UP, true)
            ))
            controls.addView(
                label("SZÍNEK", 11f, grey, true),
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(12) }
            )
            controls.addView(row(
                rk(KeyIcon.SWATCH, "Piros", 0xFFFF3B30.toInt(), Key.RED),
                rk(KeyIcon.SWATCH, "Zöld", 0xFF34C759.toInt(), Key.GREEN),
                rk(KeyIcon.SWATCH, "Kék", 0xFF3B82FF.toInt(), Key.BLUE),
                rk(KeyIcon.SWATCH, "Fehér", 0xFFFFFFFF.toInt(), Key.WHITE)
            ))
            controls.addView(row(brandBtn))
        }
        controls.addView(row(scanBtn))
    }

    private fun setCategory(cat: Cat) {
        if (brand.cat == cat) return
        if (scanning) stopScan(false)
        val saved = runCatching {
            Brand.valueOf(getPreferences(MODE_PRIVATE).getString("brand_${cat.name}", "")!!)
        }.getOrNull()
        brand = saved?.takeIf { it.cat == cat } ?: Brand.values().first { it.cat == cat }
        saveBrand(); buildControls(); refreshUi()
    }

    private fun refreshUi() {
        brandBtn.text = if (brand.cat == Cat.TV) "📺 TV márka: ${brand.label} ▸" else "💡 Remote típus: ${brand.label} ▸"
        catTvBtn.background = remoteBg(cBlue, strong = brand.cat == Cat.TV)
        catLedBtn.background = remoteBg(cBlue, strong = brand.cat == Cat.LED)
        val huLoc = Locale.forLanguageTag("hu")
        lcd.mode = "${if (brand.cat == Cat.TV) "TV" else "LED"} · ${brand.label.uppercase(Locale.US)}"
        lcd.sub = "CAM: ${(topLabel ?: "—").uppercase(huLoc)}"
        when {
            !hasIr -> { lcd.main = "NINCS IR ADÓ"; lcd.caption = "csak kijelző mód" }
            scanning -> { lcd.main = "KERESÉS…"; lcd.caption = "próba: ${brand.label}" }
            else -> { lcd.main = lastCmd; lcd.caption = lastCmdAt }
        }
        updateBtn.text = pending?.let { "⬆  Új verzió elérhető – build ${it.build} (koppints)" } ?: "⬆  Frissítés keresése"
        hintTv.text = when {
            !hasIr -> "⚠️ Ezen a telefonon nincs infravörös (IR) adó, ezért a gombok nem tudnak jelet küldeni."
            scanning && brand.cat == Cat.LED -> "👉 Irányítsd a telefon TETEJÉT a LED vezérlője (a kis vevőszem) felé. Típusonként egy KI jelet küldök – amelyiknél a lámpa kikapcsolt, nyomd meg az „Ez reagált – mentés!” gombot. (Előbb kapcsold be a lámpát.)"
            scanning -> "👉 Irányítsd a telefon TETEJÉT a tévé felé. Márkánként küldök egy Be/Ki jelet. Amelyiknél a tévé reagált, nyomd meg az „Ez reagált – mentés!” gombot."
            brand.cat == Cat.LED -> "💡 Irányítsd a telefon TETEJÉT a LED lámpa vagy szalag kis vevőszeme felé. Ha nem reagál, váltsd a „Remote típust”, vagy használd az automata keresést."
            tvSeen -> "📺 Tévét látok. Irányítsd a telefon TETEJÉT a tévé felé, és nyomd meg a BE / KI gombot."
            else -> "Irányítsd a telefon TETEJÉT a tévé felé, és nyomj egy gombot. Ha a tévé nem reagál, használd a márka automatikus keresését."
        }
        updateBtn.background = remoteBg(if (pending != null) cAmber else cGreen, strong = pending != null)
        panelScroll.alpha = if (tvSeen) 1f else 0.92f
        flux.active = scanning || tvSeen
    }

    // =====================================================================
    //  Üveg-dialógus
    // =====================================================================
    private fun glassDialog(content: View): Dialog {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dm = resources.displayMetrics
        val card = MaxHeightScroll(this).apply {
            maxH = (dm.heightPixels * 0.88f).toInt()
            background = glassBg(26)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            addView(content)
        }
        d.setContentView(card)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(min(dm.widthPixels - dp(24), dp(460)), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        return d
    }

    private fun label(t: String, sp: Float, color: Int = Color.WHITE, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = sp; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun shareText(text: String) {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                "Megosztás"
            )
        )
    }

    // ---- QR megosztás ----
    private fun showQr() {
        var mode = 0
        var current = ""
        val img = ImageView(this).apply {
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(14).toFloat() }
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        val cap = label("", 12f, 0xFFCCD3DD.toInt()).apply { gravity = Gravity.CENTER }
        fun render() {
            current = if (mode == 0) "https://github.com/${BuildConfig.REPO}/releases/latest"
            else "lensmind://cfg?brand=${brand.name}"
            img.setImageBitmap(Qr.bitmap(current))
            cap.text = if (mode == 0) "Alkalmazás letöltése – a másik telefon kamerájával olvasd be\n$current"
            else "Beállításaim (${brand.label}) – a másik telefon Lens Mind kamerája importálja\n$current"
            Logger.d("qr", "megjelenít: $current")
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val d = glassDialog(content)
        content.addView(label("QR MEGOSZTÁS", 18f, cBlue, true))
        content.addView(img, LinearLayout.LayoutParams(dp(260), dp(260)).apply { topMargin = dp(12) })
        content.addView(cap, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        content.addView(row(
            neonBtn("📲 Alkalmazás", cOrange) { mode = 0; render() },
            neonBtn("⚙️ Beállításaim", cOrange) { mode = 1; render() }
        ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        content.addView(row(
            neonBtn("Link küldése", cBlue) { shareText(current) },
            neonBtn("Bezár", cRed) { d.dismiss() }
        ))
        render()
        d.show()
    }

    private fun onQr(raw: String) {
        val now = SystemClock.elapsedRealtime()
        if (raw == lastQr && now - lastQrAt < 5000) return
        lastQr = raw; lastQrAt = now
        Logger.d("qr", "beolvasva: $raw")
        val uri = runCatching { Uri.parse(raw) }.getOrNull()
        if (uri?.scheme == "lensmind" && uri.host == "cfg") {
            val b = Brand.values().firstOrNull { it.name == uri.getQueryParameter("brand") }
            if (b != null) {
                brand = b; saveBrand(); buildControls(); refreshUi(); flux.pulse()
                root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                toast("QR importálva: ${b.label}")
            }
        } else toast("QR: $raw")
    }

    // ---- Súgó: mire való az app ----
    private fun showHelp() {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val d = glassDialog(content)
        d.setOnDismissListener { getPreferences(MODE_PRIVATE).edit().putBoolean("help_seen", true).apply() }
        content.addView(label("MIRE VALÓ EZ AZ APP?", 18f, cOrange, true))
        content.addView(
            label(
                "A telefonodat univerzális infravörös távirányítóvá változtatja. A Xiaomi 15T Pro felső élén van egy IR adó, " +
                    "ezzel ugyanúgy vezérelhetsz tévét, LED lámpát és LED szalagot, mint a hagyományos távirányítóval.\n\n" +
                    "MIT TUD\n" +
                    "• Tévé (LG, Samsung, Sony): be/ki, hangerő, némítás, csatorna, vissza, főmenü\n" +
                    "• Kis kurzor: koppints a nyilakra, vagy húzd az ujjad a menüben, középen az OK\n" +
                    "• LED lámpa / szalag (infravörös): be, ki, fényerő, piros–zöld–kék–fehér\n" +
                    "• A kamera megmutatja, mit lát (pl. Televízió)\n" +
                    "• Automata keresés: kiderül, melyik márka vagy típus reagál\n" +
                    "• QR: megosztod az appot vagy a beállításodat\n\n" +
                    "HOGYAN HASZNÁLD\n" +
                    "1. Válaszd ki felül, mit vezérelsz (Tévé vagy LED), és irányítsd a telefon felső élét az eszköz felé (1–3 méterről)\n" +
                    "2. Nyomd meg a BE gombot\n" +
                    "3. Ha nem történik semmi, nyomd meg a „Márka keresése automatikusan” gombot, és figyeld a tévét. " +
                    "Amikor reagál, nyomd meg az „Ez reagált – mentés!” gombot\n\n" +
                    "FELSŐ GOMBOK\n" +
                    "? – ez a súgó  •  🐞 – hibakereső napló  •  QR – megosztás\n\n" +
                    "Csak infravörös (IR) eszközöket vezérel. A Bluetooth vagy Wi-Fi okoslámpákhoz (Tuya, Govee, Hue) más app kell. Klíma és hifi később jöhet.",
                14f, 0xFFE6EAF0.toInt()
            ),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        )
        content.addView(row(neonBtn("Értem", cGreen, strong = true) { d.dismiss() }),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        d.show()
    }

    // ---- Debug panel ----
    private fun showDebug() {
        val prefs = getSharedPreferences("dbg", MODE_PRIVATE)
        val dm = resources.displayMetrics
        val cfg = resources.configuration
        val freqs = runCatching {
            ir?.carrierFrequencies?.joinToString { "${it.minFrequency}-${it.maxFrequency}Hz" }
        }.getOrNull()
        fun body(): String = buildString {
            prefs.getString("crash", null)?.let { append("=== ELŐZŐ ÖSSZEOMLÁS ===\n$it\n\n") }
            append("Build: ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_NUMBER})\n")
            append("Repo: ${BuildConfig.REPO}\n")
            append("Eszköz: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            append("Kijelző: ${cfg.screenWidthDp}x${cfg.screenHeightDp}dp, sw${cfg.smallestScreenWidthDp}dp, ${dm.density}x, ${dm.widthPixels}x${dm.heightPixels}px\n")
            append("Inset (b,f,j,a): ${insets.joinToString()}\n")
            append("IR adó: ${runCatching { ir?.hasIrEmitter() }.getOrNull()}  ($freqs)\n")
            append("Kamera engedély: ${ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED}\n")
            append("Telepítés engedélyezve: ${Updater.canInstall(this@MainActivity)}\n")
            append("Képkockák feldolgozva: $frames, TV látva: $tvSeen, márka: ${brand.name}\n")
            append("\n--- napló ---\n${Logger.dump()}")
        }
        val tv = TextView(this).apply {
            setTypeface(Typeface.MONOSPACE)
            textSize = 11f
            setTextColor(0xFFB8FFC8.toInt())
            setTextIsSelectable(true)
            text = body()
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val d = glassDialog(content)
        content.addView(label("🐞 DEBUG", 18f, cAmber, true))
        content.addView(tv, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        content.addView(row(
            neonBtn("Másolás", cBlue) {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("LensMind log", body()))
                toast("Vágólapra másolva")
            },
            neonBtn("Küldés", cBlue) { shareText(body()) }
        ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        content.addView(row(
            neonBtn("Napló törlése", cOrange) {
                Logger.clear(); prefs.edit().remove("crash").apply(); tv.text = body()
            },
            neonBtn("Bezár", cRed) { d.dismiss() }
        ))
        d.show()
    }

    // ---- Frissítés ----
    private fun onUpdateClick() {
        val p = pending
        if (p != null) showUpdateDialog(p) else checkUpdate(silent = false)
    }

    private fun checkUpdate(silent: Boolean) {
        lastUpdateCheck = SystemClock.elapsedRealtime()
        if (!silent) toast("Frissítés keresése…")
        io.execute {
            val res = runCatching { Updater.fetchLatest() }
            runOnUiThread {
                res.onFailure {
                    Logger.e("upd", "ellenőrzés", it)
                    if (!silent) toast("Nem sikerült: ${it.message}")
                }
                res.onSuccess { info ->
                    when {
                        info == null -> if (!silent) toast("Még nincs kiadás a ${BuildConfig.REPO} repóban.")
                        info.build > BuildConfig.BUILD_NUMBER -> {
                            pending = info; refreshUi()
                            if (!silent) showUpdateDialog(info)
                        }
                        else -> {
                            pending = null; refreshUi()
                            if (!silent) toast("Naprakész vagy (build ${BuildConfig.BUILD_NUMBER}).")
                        }
                    }
                }
            }
        }
    }

    private fun showUpdateDialog(info: Updater.Info, autoStart: Boolean = false) {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val d = glassDialog(content)
        val status = label("Jelenlegi: build ${BuildConfig.BUILD_NUMBER}\nElérhető: ${info.tag}", 14f)
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            progressTintList = ColorStateList.valueOf(cGreen)
        }
        lateinit var go: Button

        fun start() {
            if (!Updater.canInstall(this)) {
                d.dismiss()
                resumeUpdate = true
                toast("Engedélyezd a telepítést ennek az appnak, utána visszatérve folytatom.")
                Updater.openInstallPermission(this)
                return
            }
            go.isEnabled = false
            d.setCancelable(false)
            bar.visibility = View.VISIBLE
            bar.progress = 0
            status.text = "Letöltés… 0%"
            io.execute {
                val r = runCatching {
                    Updater.downloadApk(this, info) { pct ->
                        runOnUiThread { bar.progress = pct; status.text = "Letöltés… $pct%" }
                    }
                }
                runOnUiThread {
                    r.onSuccess { f ->
                        status.text = "Kész. Erősítsd meg a telepítést a megjelenő ablakban."
                        Updater.install(this, f)
                        handler.postDelayed({ if (d.isShowing) d.dismiss() }, 1500)
                    }
                    r.onFailure {
                        Logger.e("upd", "letöltés", it)
                        status.text = "Nem sikerült: ${it.message}"
                        go.isEnabled = true
                        d.setCancelable(true)
                        bar.visibility = View.GONE
                    }
                }
            }
        }

        go = neonBtn("Letöltés és telepítés", cGreen, strong = true) { start() }
        content.addView(label("⬆ ÚJ VERZIÓ", 18f, cGreen, true))
        content.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        content.addView(bar, LinearLayout.LayoutParams(-1, dp(10)).apply { topMargin = dp(10) })
        content.addView(
            row(go, neonBtn("Később", cRed) { if (go.isEnabled) d.dismiss() }),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        )
        d.show()
        if (autoStart) start()
    }

    // =====================================================================
    //  Kamera + Lens Mind
    // =====================================================================
    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            runCatching {
                val provider = future.get()
                previewUc = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                val rs = ResolutionSelector.Builder().setResolutionStrategy(
                    ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                ).build()
                analysisUc = ImageAnalysis.Builder()
                    .setResolutionSelector(rs)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().also { it.setAnalyzer(exec) { p -> analyze(p) } }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, previewUc, analysisUc)
                updateRotation()
                Logger.d("cam", "kamera él")
            }.onFailure { Logger.e("cam", "indítás", it); toast("Kamera hiba: ${it.message}") }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun updateRotation() {
        val rot = preview.display?.rotation ?: Surface.ROTATION_0
        previewUc?.targetRotation = rot
        analysisUc?.targetRotation = rot
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun analyze(proxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        val img = proxy.image
        if (img == null || now - lastAnalysis < 600) { proxy.close(); return }
        lastAnalysis = now
        val input = InputImage.fromMediaImage(img, proxy.imageInfo.rotationDegrees)
        val t1 = labeler.process(input)
            .addOnSuccessListener { labels -> runOnUiThread { showLabels(labels.map { it.text }) } }
            .addOnFailureListener { Logger.e("lens", "címkézés", it) }
        val t2 = scanner.process(input)
            .addOnSuccessListener { codes ->
                codes.firstOrNull()?.rawValue?.let { raw -> runOnUiThread { onQr(raw) } }
            }
            .addOnFailureListener { Logger.e("qr", "olvasás", it) }
        Tasks.whenAllComplete(t1, t2).addOnCompleteListener {
            frames++
            if (frames % 20 == 0) Logger.d("lens", "képkocka #$frames, ${SystemClock.elapsedRealtime() - now} ms")
            proxy.close()
        }
    }

    private fun showLabels(names: List<String>) {
        topLabel = names.firstOrNull()?.let { hu[it] ?: it }
        chips.text = if (names.isEmpty()) "🔍 Még nem látok semmit – irányítsd a kamerát a tévére"
        else "🔍 A kamera ezt látja: " + names.take(4).joinToString("  •  ") { hu[it] ?: it }
        val wasTv = tvSeen
        tvSeen = names.any { it in tvLabels }
        if (tvSeen && !wasTv) { flux.pulse(); Logger.d("lens", "TV észlelve") }
        refreshUi()
    }

    // =====================================================================
    //  Remote (IR)
    // =====================================================================
    private fun send(k: Key, b: Brand = brand) {
        val m = ir
        if (m == null || !runCatching { m.hasIrEmitter() }.getOrDefault(false)) {
            Logger.d("ir", "nincs IR adó"); toast("Ezen a telefonon nincs IR adó."); return
        }
        val pat = IrCodes.pattern(b, k)
        if (pat == null) { toast("Ehhez az eszközhöz ez a gomb nincs beállítva."); return }
        runCatching { m.transmit(b.carrier, pat) }
            .onFailure { Logger.e("ir", "küldés", it); toast("IR hiba: ${it.message}") }
            .onSuccess {
                Logger.d("ir", "${b.label} $k")
                lastCmd = lcdName(k)
                lastCmdAt = "${b.label} · elküldve ${clockFmt.format(Date())}"
                if (!scanning) { lcd.main = lastCmd; lcd.caption = lastCmdAt }
                lcd.flash()
                flux.pulse()
            }
    }

    /** Rövid, nagybetűs parancsnév a kijelzőre. */
    private fun lcdName(k: Key) = when (k) {
        Key.POWER -> "BE / KI"; Key.VOL_UP -> "HANGERŐ +"; Key.VOL_DOWN -> "HANGERŐ −"
        Key.MUTE -> "NÉMÍTÁS"; Key.UP -> "▲ FEL"; Key.DOWN -> "▼ LE"; Key.LEFT -> "◀ BALRA"
        Key.RIGHT -> "JOBBRA ▶"; Key.OK -> "OK"; Key.BACK -> "VISSZA"; Key.HOME -> "FŐMENÜ"
        Key.CH_UP -> "CSATORNA +"; Key.CH_DOWN -> "CSATORNA −"; Key.LED_ON -> "LÁMPA BE"
        Key.LED_OFF -> "LÁMPA KI"; Key.BRIGHT_UP -> "FÉNYERŐ +"; Key.BRIGHT_DOWN -> "FÉNYERŐ −"
        Key.RED -> "PIROS"; Key.GREEN -> "ZÖLD"; Key.BLUE -> "KÉK"; Key.WHITE -> "FEHÉR"
    }

    private fun cycleBrand() {
        val list = Brand.values().filter { it.cat == brand.cat }
        brand = list[(list.indexOf(brand) + 1) % list.size]
        saveBrand(); refreshUi()
    }

    private fun saveBrand() = getPreferences(MODE_PRIVATE).edit()
        .putString("brand", brand.name).putString("brand_${brand.cat.name}", brand.name).apply()

    private val scanStep = object : Runnable {
        override fun run() {
            if (!scanning) return
            val list = Brand.values().filter { it.cat == brand.cat }
            brand = list[scanIdx % list.size]
            scanIdx++
            refreshUi()
            send(if (brand.cat == Cat.LED) Key.LED_OFF else Key.POWER, brand)
            handler.postDelayed(this, 1800)
        }
    }

    private fun startScan() {
        scanning = true
        scanIdx = 0
        scanBtn.text = "✅ Ez reagált – mentés!"
        toast("Irányítsd az eszköz felé – típusonként küldök egy jelet.")
        Logger.d("ir", "automata keresés indul")
        refreshUi()
        handler.post(scanStep)
    }

    private fun stopScan(saveChoice: Boolean) {
        scanning = false
        handler.removeCallbacks(scanStep)
        scanBtn.text = "🔎 Márka keresése automatikusan"
        if (saveChoice) { saveBrand(); toast("Mentve: ${brand.label}") }
        Logger.d("ir", "automata keresés leáll, márka=${brand.name}, mentve=$saveChoice")
        refreshUi()
    }
}
