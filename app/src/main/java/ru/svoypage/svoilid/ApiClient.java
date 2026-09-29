package ru.svoypage.svoilid;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Сетевой слой приложения.
 *
 * Все запросы — POST, application/x-www-form-urlencoded.
 * Ответ — JSON. Обработка в фоновом потоке, callback в главном.
 *
 * Base URL: https://svoypage.ru/api/device/
 */
public class ApiClient {

    private static final String TAG = "ApiClient";
    public static final String BASE = "https://svoypage.ru/api/device/";

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        /** ok = true при успешном ответе (HTTP 200 и ok:true) */
        void onResult(boolean ok, JSONObject response, String error);
    }

    private final Session session;

    public ApiClient(Session session) {
        this.session = session;
    }

    /** POST /api/device/register.php — анонимно (по api_key) */
    public void register(String deviceUid, String apiKey,
                         String deviceName, String model, String appVersion,
                         Callback cb) {
        Map<String, String> params = new HashMap<>();
        params.put("device_uid", deviceUid);
        params.put("api_key", apiKey);
        params.put("device_name", deviceName);
        params.put("model", model);
        params.put("app_version", appVersion);
        post("register.php", params, null, cb);
    }

    /** Универсальный вызов с session-token из Session */
    public void call(String endpoint, Map<String, String> params, Callback cb) {
        post(endpoint, params, session.getToken(), cb);
    }

    // =====================================================================
    //  ВНУТРЕННЕЕ
    // =====================================================================

    private void post(String endpoint, Map<String, String> params, String token, Callback cb) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE + endpoint);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(60000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "SvoiLid/1.0 (Android)");
                if (token != null && !token.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + token);
                    conn.setRequestProperty("X-Device-Token", token);
                }

                // Тело
                StringBuilder body = new StringBuilder();
                for (Map.Entry<String, String> e : params.entrySet()) {
                    if (body.length() > 0) body.append('&');
                    body.append(URLEncoder.encode(e.getKey(), "UTF-8"))
                        .append('=')
                        .append(URLEncoder.encode(e.getValue(), "UTF-8"));
                }

                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                os.close();

                int code = conn.getResponseCode();
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                    code >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                    StandardCharsets.UTF_8
                ));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                String raw = sb.toString();

                // Срезаем PHP-варнинги, если проскочили
                int jsonStart = raw.indexOf('{');
                if (jsonStart > 0) raw = raw.substring(jsonStart);

                JSONObject json = null;
                try { json = new JSONObject(raw); } catch (Exception ignored) {}

                final JSONObject fJson = json;
                final int fCode = code;

                if (json == null) {
                    mainHandler.post(() -> cb.onResult(false, null, "Некорректный ответ сервера"));
                    return;
                }

                boolean ok = json.optBoolean("ok", false) && code < 400;
                String err = json.optString("error", null);

                mainHandler.post(() -> cb.onResult(ok, fJson, err));

            } catch (Exception e) {
                Log.w(TAG, "post failed: " + e.getMessage());
                final String msg = e.getMessage() == null ? "Сеть недоступна" : e.getMessage();
                mainHandler.post(() -> cb.onResult(false, null, msg));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }
}
