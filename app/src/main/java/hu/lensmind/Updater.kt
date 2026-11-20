package hu.lensmind

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Frissítés a GitHub Releases-ből – a letöltés az appon belül, folyamatjelzővel történik. */
object Updater {

    data class Info(val build: Int, val tag: String, val apkUrl: String, val notes: String)

    /** null = nincs még kiadás (404). Hálózati hibánál kivételt dob. */
    fun fetchLatest(): Info? {
        val url = URL("https://api.github.com/repos/${BuildConfig.REPO}/releases/latest")
        val c = url.openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.connectTimeout = 8000
        c.readTimeout = 8000
        try {
            Logger.d("upd", "GET $url -> ${c.responseCode}")
            if (c.responseCode == 404) return null
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            val j = JSONObject(c.inputStream.bufferedReader().readText())
            val tag = j.getString("tag_name")
            val build = tag.removePrefix("build-").toIntOrNull() ?: return null
            val assets = j.getJSONArray("assets")
            var apk: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) { apk = a.getString("browser_download_url"); break }
            }
            return apk?.let { Info(build, tag, it, j.optString("name")) }
        } finally {
            c.disconnect()
        }
    }

    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Blokkoló – háttérszálról hívd. A haladást 0..100 közötti százalékban jelzi. */
    fun downloadApk(ctx: Context, info: Info, onProgress: (Int) -> Unit): File {
        val file = File(ctx.getExternalFilesDir(null), "update.apk")
        if (file.exists()) file.delete()
        val c = URL(info.apkUrl).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 20000
        try {
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            val total = c.contentLengthLong
            Logger.d("upd", "letöltés indul, ${total / 1024} KB")
            c.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    var done = 0L
                    var last = -1
                    var n = input.read(buf)
                    while (n != -1) {
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != last) { last = pct; onProgress(pct) }
                        }
                        n = input.read(buf)
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        if (file.length() < 100_000) throw IllegalStateException("a letöltött fájl túl kicsi (${file.length()} bájt)")
        Logger.d("upd", "letöltve: ${file.length() / 1024} KB")
        return file
    }

    fun install(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }
}
