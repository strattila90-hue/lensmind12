package hu.onda;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** Frissítés a GitHub Releases-ből. Csak az "onda-" kezdetű címkéket nézi, a Lens Mind kiadásait nem. */
final class Updater {

    interface Progress { void on(int pct); }

    static final class Info {
        final int build; final String tag; final String apkUrl;
        Info(int build, String tag, String apkUrl) { this.build = build; this.tag = tag; this.apkUrl = apkUrl; }
    }

    private Updater() {}

    /** null = még nincs kiadás. Hálózati hibánál kivételt dob. */
    static Info fetchLatest() throws Exception {
        URL url = new URL("https://api.github.com/repos/" + BuildConfig.REPO + "/releases?per_page=30");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try {
            if (c.getResponseCode() == 404) return null;
            if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            JSONArray list = new JSONArray(sb.toString());
            Info best = null;
            for (int i = 0; i < list.length(); i++) {
                JSONObject j = list.getJSONObject(i);
                String tag = j.getString("tag_name");
                if (!tag.startsWith("onda-") || j.optBoolean("draft")) continue;
                int n;
                try { n = Integer.parseInt(tag.substring(5)); } catch (NumberFormatException e) { continue; }
                JSONArray assets = j.getJSONArray("assets");
                for (int k = 0; k < assets.length(); k++) {
                    JSONObject a = assets.getJSONObject(k);
                    if (a.getString("name").endsWith(".apk")) {
                        if (best == null || n > best.build) best = new Info(n, tag, a.getString("browser_download_url"));
                        break;
                    }
                }
            }
            return best;
        } finally {
            c.disconnect();
        }
    }

    static boolean canInstall(Context ctx) {
        return ctx.getPackageManager().canRequestPackageInstalls();
    }

    static void openInstallPermission(Context ctx) {
        ctx.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + ctx.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /** Blokkoló, háttérszálról hívd. */
    static File downloadApk(Context ctx, Info info, Progress p) throws Exception {
        File file = new File(ctx.getExternalFilesDir(null), "update.apk");
        if (file.exists()) file.delete();
        HttpURLConnection c = (HttpURLConnection) new URL(info.apkUrl).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        try {
            if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
            long total = c.getContentLengthLong();
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(file)) {
                byte[] buf = new byte[32 * 1024];
                long done = 0; int last = -1, n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    done += n;
                    if (total > 0) {
                        int pct = (int) (done * 100 / total);
                        if (pct != last) { last = pct; p.on(pct); }
                    }
                }
            }
        } finally {
            c.disconnect();
        }
        if (file.length() < 100_000) throw new IllegalStateException("a letöltött fájl túl kicsi");
        return file;
    }

    static void install(Context ctx, File file) {
        Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", file);
        ctx.startActivity(new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
