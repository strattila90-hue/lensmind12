package hu.onda;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * LG webOS vezérlés Wi-Fin (SSAP WebSocket). Itt csak a szövegbeírás kell: ssap://com.webos.service.ime/insertText.
 * A TV önaláírt tanúsítványt használ, ezért ellenőrzés nélküli kapcsolat megy, de kizárólag helyi (privát) IP-címre.
 */
final class LgTv {

    interface Listener { void onState(String ip, String state, String msg); }

    private static final Pattern PRIVATE_IP = Pattern.compile(
            "^(192\\.168\\.\\d{1,3}\\.\\d{1,3}|10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|172\\.(1[6-9]|2\\d|3[01])\\.\\d{1,3}\\.\\d{1,3})$");

    private static final String[] PERMS = {
            "LAUNCH", "LAUNCH_WEBAPP", "APP_TO_APP", "CLOSE", "TEST_OPEN", "TEST_PROTECTED", "CONTROL_AUDIO",
            "CONTROL_DISPLAY", "CONTROL_INPUT_JOYSTICK", "CONTROL_INPUT_MEDIA_RECORDING", "CONTROL_INPUT_MEDIA_PLAYBACK",
            "CONTROL_INPUT_TV", "CONTROL_POWER", "READ_APP_STATUS", "READ_CURRENT_CHANNEL", "READ_INPUT_DEVICE_LIST",
            "READ_NETWORK_STATE", "READ_RUNNING_APPS", "READ_TV_CHANNEL_LIST", "WRITE_NOTIFICATION_TOAST",
            "READ_POWER_STATE", "READ_COUNTRY_INFO", "CONTROL_INPUT_TEXT", "CONTROL_MOUSE_AND_KEYBOARD"
    };

    private final SharedPreferences prefs;
    private final Listener listener;
    private final OkHttpClient http;
    private final Map<String, Conn> conns = new HashMap<>();

    LgTv(Context ctx, Listener l) {
        prefs = ctx.getSharedPreferences("lg", Context.MODE_PRIVATE);
        listener = l;
        OkHttpClient.Builder b = new OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS)
                .connectTimeout(6, TimeUnit.SECONDS);
        try {
            final X509TrustManager tm = new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] c, String a) { }
                @Override public void checkServerTrusted(X509Certificate[] c, String a) { }
                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, new TrustManager[]{tm}, new SecureRandom());
            b.sslSocketFactory(sc.getSocketFactory(), tm).hostnameVerifier((h, s) -> PRIVATE_IP.matcher(h).matches());
        } catch (Exception ignored) { }
        http = b.build();
    }

    static boolean valid(String ip) { return ip != null && PRIVATE_IP.matcher(ip).matches(); }

    synchronized void connect(String ip) {
        if (!valid(ip)) { listener.onState(ip, "error", "Érvénytelen IP-cím"); return; }
        Conn old = conns.get(ip);
        if (old != null && (old.ready || old.connecting)) return;
        if (old != null) old.close();
        Conn c = new Conn(ip);
        conns.put(ip, c);
        c.open(true, true);
    }

    /** Csak elérhetőséget néz (nincs párosítás): fut-e a TV szolgáltatása a hálózaton. */
    void ping(String ip) {
        if (!valid(ip)) return;
        new Thread(() -> {
            boolean on = false;
            for (int port : new int[]{3001, 3000}) {
                try (java.net.Socket sk = new java.net.Socket()) {
                    sk.connect(new java.net.InetSocketAddress(ip, port), 1500);
                    on = true;
                    break;
                } catch (Exception ignored) { }
            }
            listener.onState(ip, on ? "online" : "offline", "");
        }).start();
    }

    synchronized void text(String ip, String text) {
        Conn c = conns.get(ip);
        if (c == null || !c.ready) return;
        try {
            JSONObject p = new JSONObject();
            p.put("text", text);
            p.put("replace", 1);
            c.request("ssap://com.webos.service.ime/insertText", p);
        } catch (Exception ignored) { }
    }

    synchronized void enter(String ip) {
        Conn c = conns.get(ip);
        if (c == null || !c.ready) return;
        try { c.request("ssap://com.webos.service.ime/sendEnterKey", new JSONObject()); } catch (Exception ignored) { }
    }

    private final class Conn {
        final String ip;
        WebSocket ws;
        volatile boolean ready, connecting;
        int n;
        boolean secure, signed;

        Conn(String ip) { this.ip = ip; }

        void open(boolean secure, boolean signed) {
            this.secure = secure;
            this.signed = signed;
            connecting = true;
            listener.onState(ip, "connecting", "");
            Request r = new Request.Builder().url((secure ? "wss://" : "ws://") + ip + (secure ? ":3001" : ":3000")).build();
            ws = http.newWebSocket(r, new WebSocketListener() {
                @Override public void onOpen(WebSocket w, Response resp) { register(w); }

                @Override public void onMessage(WebSocket w, String msg) { handle(msg); }

                @Override public void onFailure(WebSocket w, Throwable t, Response resp) {
                    if (w != ws) return;
                    if (Conn.this.secure) { open(false, true); return; } // régebbi webOS: sima ws:3000
                    ready = false;
                    connecting = false;
                    listener.onState(ip, "error", "A TV nem érhető el (" + ip + "). Be van kapcsolva, és ugyanazon a Wi-Fi-n van?");
                }

                @Override public void onClosed(WebSocket w, int code, String reason) {
                    if (w != ws) return;
                    ready = false;
                    connecting = false;
                    listener.onState(ip, "closed", "A kapcsolat megszakadt");
                }
            });
        }

        private void register(WebSocket w) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("forcePairing", false);
                payload.put("pairingType", "PROMPT");
                String key = prefs.getString("key_" + ip, null);
                if (key != null) payload.put("client-key", key);
                JSONObject manifest = new JSONObject();
                manifest.put("manifestVersion", 1);
                manifest.put("permissions", new JSONArray(PERMS));
                if (signed) {
                    manifest.put("appVersion", "1.1");
                    JSONObject sg = new JSONObject();
                    sg.put("created", "20140509");
                    sg.put("appId", "com.lge.test");
                    sg.put("vendorId", "com.lge");
                    sg.put("localizedAppNames", new JSONObject().put("", "Onda Haptic"));
                    sg.put("localizedVendorNames", new JSONObject().put("", "Onda"));
                    sg.put("permissions", new JSONArray(new String[]{"TEST_SECURE", "CONTROL_INPUT_TEXT", "CONTROL_MOUSE_AND_KEYBOARD", "CONTROL_POWER"}));
                    sg.put("serial", "2f930e2d2cfe083771f68e4fe8c71e3e");
                    manifest.put("signed", sg);
                }
                payload.put("manifest", manifest);
                JSONObject m = new JSONObject();
                m.put("type", "register");
                m.put("id", "register_0");
                m.put("payload", payload);
                w.send(m.toString());
            } catch (Exception e) {
                listener.onState(ip, "error", "Regisztrációs hiba: " + e.getMessage());
            }
        }

        private void handle(String raw) {
            try {
                JSONObject j = new JSONObject(raw);
                String type = j.optString("type");
                String id = j.optString("id");
                JSONObject p = j.optJSONObject("payload");
                if ("registered".equals(type)) {
                    String key = p == null ? null : p.optString("client-key", null);
                    if (key != null) prefs.edit().putString("key_" + ip, key).apply();
                    ready = true;
                    connecting = false;
                    listener.onState(ip, "ready", "");
                } else if ("response".equals(type) && id.startsWith("register") && p != null && "PROMPT".equals(p.optString("pairingType"))) {
                    listener.onState(ip, "pairing", "Fogadd el a párosítást a TV képernyőjén.");
                } else if ("error".equals(type)) {
                    String err = j.optString("error", "hiba");
                    if (id.startsWith("register") && signed) { // aláírás nélküli jegyzékkel újra
                        ws.close(1000, null);
                        open(secure, false);
                    } else if (id.startsWith("register")) {
                        listener.onState(ip, "error", "A TV elutasította a párosítást: " + err);
                    } else {
                        listener.onState(ip, "info", "A TV szerint nincs aktív szövegmező (" + err + ")");
                    }
                }
            } catch (Exception ignored) { }
        }

        void request(String uri, JSONObject payload) throws Exception {
            JSONObject m = new JSONObject();
            m.put("type", "request");
            m.put("id", "r" + (++n));
            m.put("uri", uri);
            m.put("payload", payload);
            ws.send(m.toString());
        }

        void close() {
            try { if (ws != null) ws.close(1000, null); } catch (Exception ignored) { }
            ready = false;
            connecting = false;
        }
    }
}
