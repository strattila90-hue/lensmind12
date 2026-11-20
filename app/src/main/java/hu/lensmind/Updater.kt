package hu.lensmind

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Frissítés a GitHub Releases-ből (a GitHub Actions bot tölti fel az APK-t). */
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
            Logger.d("upd", "GET ${url} -> ${c.responseCode}")
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

    fun downloadAndInstall(ctx: Context, info: Info) {
        val app = ctx.applicationContext
        val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val file = File(app.getExternalFilesDir(null), "update.apk")
        if (file.exists()) file.delete()
        val req = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("Lens Mind ${info.tag}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(file))
        val id = dm.enqueue(req)
        Logger.d("upd", "letöltés indul id=$id ${info.apkUrl}")

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
                runCatching { app.unregisterReceiver(this) }
                dm.query(DownloadManager.Query().setFilterById(id)).use { cur ->
                    val ok = cur.moveToFirst() &&
                        cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) ==
                        DownloadManager.STATUS_SUCCESSFUL
                    if (!ok) {
                        Logger.d("upd", "letöltés sikertelen")
                        Toast.makeText(app, "A letöltés nem sikerült.", Toast.LENGTH_LONG).show()
                        return
                    }
                }
                Logger.d("upd", "letöltve, telepítés indul")
                install(app, file)
            }
        }
        ContextCompat.registerReceiver(
            app, receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun install(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }
}
