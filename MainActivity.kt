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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
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
import android.text.TextUtils
import android.util.Size
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
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
import androidx.core.widget.TextViewCompat
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
 * Lens Mind + Remote – "Vissza a jövőbe" időáramkör-dizájn, matt, opak panelekkel.
 * Kompakt radar-kameraablak, kitöltő távirányító, LED lámpa (vaku), debug napló.
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
    private lateinit var content: LinearLayout
    private lateinit var preview: PreviewView
    private lateinit var radar: RadarView
    private lateinit var radarBox: FrameLayout
    private lateinit var led: LedView
    private lateinit var topBar: LinearLayout
    private lateinit var chips: TextView
    private lateinit var flux: FluxView
    private lateinit var panelScroll: ScrollView
    private lateinit var destTv: TextView
    private lateinit var presentTv: TextView
    private lateinit var lastTv: TextView
    private lateinit var brandBtn: Button
    private lateinit var scanBtn: Button
    private lateinit var torchBtn: Button
    private lateinit var updateBtn: Button
    private lateinit var hintTv: TextView
    private var hasIr = false

    // ---- állapot ----
    private val insets = intArrayOf(0, 0, 0, 0) // bal, felső, jobb, alsó
    private var previewUc: Preview? = null
    private var analysisUc: ImageAnalysis? = null
    private var camera: Camera? = null
    private var torchOn = false
    private var lastCamErr: String? = null
    private var radarText = "KAMERA KI"
    private var lastLabels: List<String> = emptyList()
    private var ir: ConsumerIrManager? = null
    private var brand = Brand.LG
    private var topLabel: String? = null
    private var tvSeen = false
    private var scanning = false
    private var scanIdx = 0
    private var lastAnalysis = 0L
    private var frames = 0
    private var pending: Updater.Info? = null
    private var lastQr = ""
    private var lastQrAt = 0L

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

    private val clockFmt = SimpleDateFormat("MMM dd yyyy  HH:mm:ss", Locale.US)
    private val clock = object : Runnable {
        override fun run() {
            presentTv.text = clockFmt.format(Date()).uppercase(Locale.US)
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

        checkUpdate(silent = true)
    }

    override fun onResume() { super.onResume(); handler.post(clock) }

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

    /** Matt, opak panel – nincs áttetszőség. */
    private fun glassBg(radiusDp: Int = 24): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(0xFF10151F.toInt(), 0xFF07090E.toInt())
    ).apply {
        cornerRadius = dp(radiusDp).toFloat()
        setStroke(dp(1), 0x33FFFFFF)
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
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = neon(color, strong)
            stateListAnimator = null
            minHeight = dp(44); minimumHeight = dp(44)
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

    /** Súlyozott sor: a magassága a szülőből jön, a gombok kitöltik. */
    private fun rowW(weight: Float, vararg v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        v.forEach { addView(it, LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }) }
        layoutParams = LinearLayout.LayoutParams(-1, 0, weight)
    }

    /** Időáramkör-cella: kis címke + világító kijelző. */
    private fun circuit(label: String, color: Int): Pair<View, TextView> {
        val lab = TextView(this).apply {
            text = label
            textSize = 9f
            setTextColor(0xFFE8E8E8.toInt())
            letterSpacing = 0.03f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val disp = TextView(this).apply {
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(color)
            setShadowLayer(dp(8).toFloat(), 0f, 0f, color)
            gravity = Gravity.CENTER
            maxLines = 1
            letterSpacing = 0.04f
            setPadding(dp(6), 0, dp(6), 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(0xFF0B0505.toInt())
                setStroke(dp(1), (color and 0x00FFFFFF) or 0x66000000)
            }
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this, 8, 16, 1, TypedValue.COMPLEX_UNIT_SP
            )
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(lab)
            addView(disp, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)).apply { topMargin = dp(3) })
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { setMargins(dp(2), dp(4), dp(2), 0) }
        }
        return col to disp
    }

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
        root = FrameLayout(this).apply { setBackgroundColor(0xFF05070B.toInt()) }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // ---- fejléc ----
        val title = TextView(this).apply {
            text = "LENS MIND"
            textSize = 24f
            typeface = Typeface.create("sans-serif-black", Typeface.ITALIC)
            letterSpacing = 0.1f
            setTextColor(Color.WHITE)
            setShadowLayer(dp(8).toFloat(), 0f, 0f, cOrange)
        }
        chrome(title)
        val sub = TextView(this).apply {
            text = "TV-TÁVIRÁNYÍTÓ KAMERÁVAL  ·  ${BuildConfig.VERSION_NAME}"
            textSize = 9f
            letterSpacing = 0.08f
            setTextColor(0xFFFFB27A.toInt())
        }
        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(StripesView(this@MainActivity), LinearLayout.LayoutParams(dp(150), dp(10)).apply { topMargin = dp(2) })
            addView(sub, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(3) })
        }
        led = LedView(this)
        flux = FluxView(this)
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(led, LinearLayout.LayoutParams(dp(12), dp(12)).apply { rightMargin = dp(8) })
            addView(titleBlock, LinearLayout.LayoutParams(0, -2, 1f))
            addView(flux, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(iconBtn("?", cGreen) { showHelp() })
            addView(iconBtn("🐞", cAmber) { showDebug() })
            addView(iconBtn("QR", cBlue) { showQr() })
        }

        // ---- radar kameraablak (kompakt, opak keret) ----
        preview = PreviewView(this)
        radar = RadarView(this)
        radarBox = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(0xFF04070C.toInt())
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), 0xFF1B3554.toInt())
            }
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
            addView(preview, FrameLayout.LayoutParams(-1, -1))
            addView(radar, FrameLayout.LayoutParams(-1, -1))
        }

        chips = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = glassBg(14)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            maxLines = 2
            text = "🔍 Még nem látok semmit – irányítsd a kamerát a tévére"
        }
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(6))
            addView(headerRow)
            addView(radarBox)
            addView(chips, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }

        // ---- időáramkörök (egy sorban) ----
        val (destRow, dest) = circuit("CÉL", cRed)
        val (presRow, pres) = circuit("MOST", cGreen)
        val (lastRow, last) = circuit("UTOLSÓ JEL", cAmber)
        destTv = dest; presentTv = pres; lastTv = last
        lastTv.text = "— NINCS —"
        val circuits = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(destRow); addView(presRow); addView(lastRow)
        }

        hintTv = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(0xFF1A1408.toInt())
                setStroke(dp(1), 0x88FFB000.toInt())
            }
        }

        val powerBtn = neonBtn("⏻  BE / KI", cRed, strong = true) { send(Key.POWER) }.apply {
            textSize = 20f
            minHeight = dp(48); minimumHeight = dp(48)
        }
        brandBtn = neonBtn("", cOrange) { cycleBrand() }
        scanBtn = neonBtn("🔎 Márka keresése", cBlue) { if (!scanning) startScan() else stopScan(true) }
        torchBtn = neonBtn("💡 LED lámpa", cBlue) { toggleTorch() }
        val volUp = neonBtn("🔊 Hangosabb", cBlue) { send(Key.VOL_UP) }
        val volDn = neonBtn("🔉 Halkabb", cBlue) { send(Key.VOL_DOWN) }
        val mute = neonBtn("🔇 Némítás", cBlue) { send(Key.MUTE) }
        updateBtn = neonBtn("⬆  Frissítés keresése", cGreen, strong = true) { onUpdateClick() }

        // A panel belső tartalma: a távirányító-gombok súlyozva kitöltik a rendelkezésre álló helyet.
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            addView(hintTv, LinearLayout.LayoutParams(-1, -2))
            addView(powerBtn, LinearLayout.LayoutParams(-1, 0, 1.3f).apply { setMargins(dp(4), dp(6), dp(4), dp(4)) })
            addView(rowW(1f, volDn, volUp))
            addView(rowW(1f, mute, brandBtn))
            addView(rowW(1f, scanBtn, torchBtn))
            addView(label("ÁLLAPOT", 10f, 0xFF9AA3B2.toInt(), true), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
            addView(circuits, LinearLayout.LayoutParams(-1, -2))
            addView(updateBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
        panelScroll = ScrollView(this).apply {
            background = glassBg(26)
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            addView(inner)
        }

        content.addView(topBar, LinearLayout.LayoutParams(-1, -2))
        content.addView(panelScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, wi ->
            val b = wi.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            insets[0] = b.left; insets[1] = b.top; insets[2] = b.right; insets[3] = b.bottom
            applyLayout()
            wi
        }
        applyLayout()
        refreshUi()
    }

    /**
     * Reszponzív elrendezés. Álló: radar + infók felül, a távirányító a maradék teljes helyet kitölti.
     * Fekvő/tablet: bal oldalon a radar és infók, jobb oldalon a teljes magasságú távirányító.
     */
    private fun applyLayout() {
        val cfg = resources.configuration
        val d = resources.displayMetrics.density
        val wPx = (cfg.screenWidthDp * d).toInt()
        val hPx = (cfg.screenHeightDp * d).toInt()
        val (l, t, r, b) = insets
        val sideDock = cfg.screenWidthDp > cfg.screenHeightDp && cfg.screenWidthDp >= 560

        content.orientation = if (sideDock) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        content.setPadding(l + dp(12), t + dp(8), r + dp(12), b + dp(10))

        val avail = if (sideDock) (wPx * 0.55f).toInt() else wPx
        val radarW = min(dp(320), avail - dp(24)).coerceAtLeast(dp(200))
        val radarH = (hPx * 0.22f).toInt().coerceIn(dp(140), dp(220))
        radarBox.layoutParams = LinearLayout.LayoutParams(radarW, radarH).apply { gravity = Gravity.CENTER_HORIZONTAL }

        if (sideDock) {
            val panelW = min(dp(400), (wPx * 0.40f).toInt())
            topBar.layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
            topBar.gravity = Gravity.CENTER_VERTICAL
            panelScroll.layoutParams = LinearLayout.LayoutParams(panelW, -1).apply { leftMargin = dp(8) }
        } else {
            topBar.layoutParams = LinearLayout.LayoutParams(-1, -2)
            topBar.gravity = Gravity.TOP
            panelScroll.layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
    }

    private fun refreshUi() {
        brandBtn.text = "📺 TV márka: ${brand.label} ▸"
        destTv.text = "${(topLabel ?: "NINCS TALÁLAT").uppercase(Locale.forLanguageTag("hu"))} › ${brand.label.uppercase(Locale.US)}"
        updateBtn.text = pending?.let { "⬆  Új verzió elérhető – build ${it.build} (koppints)" } ?: "⬆  Frissítés keresése"
        hintTv.text = when {
            !hasIr -> "⚠️ Ezen a telefonon nincs infravörös (IR) adó, ezért a gombok nem tudnak jelet küldeni."
            scanning -> "👉 Irányítsd a telefon TETEJÉT a tévé felé. Márkánként küldök egy Be/Ki jelet. Amelyiknél a tévé reagált, nyomd meg az „Ez reagált – mentés!” gombot."
            tvSeen -> "📺 Tévét látok. Irányítsd a telefon TETEJÉT a tévé felé, és nyomd meg a BE / KI gombot."
            else -> "Irányítsd a telefon TETEJÉT a tévé felé, és nyomj egy gombot. Ha a tévé nem reagál, használd a márka automatikus keresését."
        }
        if (scanning) scanBtn.text = "✅ Ez reagált – mentés!" else scanBtn.text = "🔎 Márka keresése"
        updateBtn.background = neon(if (pending != null) cAmber else cGreen, strong = true)

        val cam = camera != null
        led.set(cam, if (torchOn) cAmber else cGreen)
        torchBtn.text = if (torchOn) "💡 LED lámpa: BE" else "💡 LED lámpa: KI"
        torchBtn.background = neon(if (torchOn) cAmber else cBlue)
        refreshRadar()
        panelScroll.alpha = if (tvSeen) 1f else 0.92f
        flux.active = scanning || tvSeen
    }

    /** Radar állapota a kamera és a felismerés alapján. */
    private fun refreshRadar() {
        val cam = camera != null
        radarText = when {
            !cam -> "KAMERA KI"
            tvSeen -> "AZONOSÍTVA"
            lastLabels.isNotEmpty() -> "FELISMERÉS"
            else -> "KÉSZÜLÉK KERESÉSE"
        }
        val color = when {
            !cam -> 0xFF7A8AA3.toInt()
            tvSeen -> cGreen
            lastLabels.isNotEmpty() -> cAmber
            else -> cBlue
        }
        radar.setState(radarText, color, tvSeen && cam)
    }

    // =====================================================================
    //  Üveg-dialógus (matt, opak)
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
                brand = b; saveBrand(); refreshUi(); flux.pulse()
                root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                toast("QR importálva: ${b.label}")
            }
        } else toast("QR: $raw")
    }

    // ---- Súgó ----
    private fun showHelp() {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val d = glassDialog(content)
        d.setOnDismissListener { getPreferences(MODE_PRIVATE).edit().putBoolean("help_seen", true).apply() }
        content.addView(label("MIRE VALÓ EZ AZ APP?", 18f, cOrange, true))
        content.addView(
            label(
                "A telefonodat tévé-távirányítóvá változtatja. A telefon felső élén van egy infravörös (IR) adó, " +
                    "ezzel ugyanúgy vezérelhetsz tévét, mint a hagyományos távirányítóval.\n\n" +
                    "MIT TUD\n" +
                    "• Be/Ki, hangerő és némítás LG, Samsung és Sony tévéken\n" +
                    "• A radar a kamerával nézi, mit lát (pl. Televízió)\n" +
                    "• LED lámpa: a telefon hátlapi LED-jét kapcsolja (a kamera indítása után)\n" +
                    "• Automata márkakeresés: kiderül, melyik márkájú a tévéd\n" +
                    "• QR: megosztod az appot vagy a beállításodat\n\n" +
                    "HOGYAN HASZNÁLD\n" +
                    "1. Irányítsd a telefon felső élét a tévé felé (1–3 méterről)\n" +
                    "2. Nyomd meg a BE / KI gombot\n" +
                    "3. Ha nem történik semmi, nyomd meg a „Márka keresése” gombot, és figyeld a tévét. " +
                    "Amikor reagál, nyomd meg az „Ez reagált – mentés!” gombot\n\n" +
                    "FELSŐ GOMBOK\n" +
                    "? – ez a súgó  •  🐞 – hibakereső napló  •  QR – megosztás\n\n" +
                    "Egyelőre csak tévét vezérel (LG, Samsung, Sony). Klíma, lámpa és más eszközök később jöhetnek.",
                13f, 0xFFE6EAF0.toInt()
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
            append("Elrendezés: ${if (this@MainActivity.content.orientation == LinearLayout.HORIZONTAL) "oldalsó" else "álló"}, panel ${panelScroll.height}px, radar ${radarBox.width}x${radarBox.height}px\n")
            append("IR adó: ${runCatching { ir?.hasIrEmitter() }.getOrNull()}  ($freqs)\n")
            append("Kamera engedély: ${ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED}\n")
            append("Kamera aktív: ${camera != null}, utolsó kamerahiba: ${lastCamErr ?: "nincs"}\n")
            append("LED lámpa: ${if (torchOn) "BE" else "KI"}, vaku elérhető: ${runCatching { camera?.cameraInfo?.hasFlashUnit() }.getOrNull()}\n")
            append("Radar: $radarText, célkeret: $tvSeen, címkék: ${lastLabels.take(4)}\n")
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
            neonBtn("Kamera újra", cGreen) {
                Logger.d("cam", "kézi újraindítás")
                startCamera()
                handler.postDelayed({ tv.text = body() }, 800)
            },
            neonBtn("Napló törlése", cOrange) {
                Logger.clear(); prefs.edit().remove("crash").apply(); tv.text = body()
            }
        ))
        content.addView(row(neonBtn("Bezár", cRed) { d.dismiss() }))
        d.show()
    }

    // ---- Frissítés ----
    private fun onUpdateClick() {
        val p = pending
        if (p != null) showUpdateDialog(p) else checkUpdate(silent = false)
    }

    private fun checkUpdate(silent: Boolean) {
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

    private fun showUpdateDialog(info: Updater.Info) {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val d = glassDialog(content)
        content.addView(label("⬆ ÚJ VERZIÓ", 18f, cGreen, true))
        content.addView(
            label("Jelenlegi: build ${BuildConfig.BUILD_NUMBER}\nElérhető: ${info.tag}", 14f),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        )
        content.addView(row(
            neonBtn("Letöltés és telepítés", cGreen, strong = true) {
                d.dismiss()
                if (!Updater.canInstall(this)) {
                    toast("Engedélyezd a telepítést ennek az appnak, majd nyomd meg újra a Frissítést.")
                    Updater.openInstallPermission(this)
                } else {
                    toast("Letöltés indul – az értesítésekben követheted.")
                    Updater.downloadAndInstall(this, info)
                }
            },
            neonBtn("Később", cRed) { d.dismiss() }
        ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        d.show()
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
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, previewUc, analysisUc)
                torchOn = false
                lastCamErr = null
                updateRotation()
                Logger.d("cam", "kamera él")
            }.onFailure {
                Logger.e("cam", "indítás", it)
                lastCamErr = it.message
                camera = null
                toast("Kamera hiba: ${it.message}")
            }
            refreshUi()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun updateRotation() {
        val rot = preview.display?.rotation ?: Surface.ROTATION_0
        previewUc?.targetRotation = rot
        analysisUc?.targetRotation = rot
    }

    /** LED lámpa (vaku) be/ki. A kamera indítása után működik. */
    private fun toggleTorch() {
        val c = camera
        if (c == null) {
            toast("Előbb indítsd el a kamerát (kell hozzá engedély).")
            Logger.d("led", "nincs aktív kamera")
            return
        }
        if (!c.cameraInfo.hasFlashUnit()) {
            toast("Ezen a telefonon nincs LED lámpa.")
            Logger.d("led", "nincs vaku")
            return
        }
        torchOn = !torchOn
        c.cameraControl.enableTorch(torchOn)
        Logger.d("led", "LED lámpa=$torchOn")
        refreshUi()
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
        lastLabels = names
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
        runCatching { m.transmit(b.carrier, IrCodes.pattern(b, k)) }
            .onFailure { Logger.e("ir", "küldés", it); toast("IR hiba: ${it.message}") }
            .onSuccess {
                Logger.d("ir", "${b.label} $k")
                lastTv.text = "${b.label.uppercase(Locale.US)} ${k.name.replace('_', ' ')}  " +
                    SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                flux.pulse()
            }
    }

    private fun cycleBrand() {
        brand = Brand.values()[(brand.ordinal + 1) % Brand.values().size]
        saveBrand(); refreshUi()
    }

    private fun saveBrand() = getPreferences(MODE_PRIVATE).edit().putString("brand", brand.name).apply()

    private val scanStep = object : Runnable {
        override fun run() {
            if (!scanning) return
            brand = Brand.values()[scanIdx % Brand.values().size]
            scanIdx++
            refreshUi()
            send(Key.POWER, brand)
            handler.postDelayed(this, 1800)
        }
    }

    private fun startScan() {
        scanning = true
        toast("Irányítsd a TV felé – márkánként küldök egy Power jelet.")
        Logger.d("ir", "automata keresés indul")
        refreshUi()
        handler.post(scanStep)
    }

    private fun stopScan(saveChoice: Boolean) {
        scanning = false
        handler.removeCallbacks(scanStep)
        if (saveChoice) { saveBrand(); toast("Mentve: ${brand.label}") }
        Logger.d("ir", "automata keresés leáll, márka=${brand.name}, mentve=$saveChoice")
        refreshUi()
    }
}

/**
 * Lámpa-jelző (LED): zöld = kamera fut, sárga = LED lámpa BE, szürke = nincs kamera.
 */
private class LedView(ctx: Context) : View(ctx) {
    private var lit = false
    private var col = 0xFF3CFF6A.toInt()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    fun set(on: Boolean, color: Int) {
        if (on != lit || color != col) { lit = on; col = color; invalidate() }
    }

    override fun onDraw(c: Canvas) {
        val r = min(width, height) / 2f
        val cx = width / 2f
        val cy = height / 2f
        p.style = Paint.Style.FILL
        p.color = if (lit) col else 0xFF2A2F38.toInt()
        if (lit) p.setShadowLayer(r * 0.9f, 0f, 0f, col)
        c.drawCircle(cx, cy, r * 0.6f, p)
        p.clearShadowLayer()
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.16f
        p.color = 0x44FFFFFF
        c.drawCircle(cx, cy, r * 0.8f, p)
    }
}
