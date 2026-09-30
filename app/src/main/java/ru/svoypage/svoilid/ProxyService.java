package ru.svoypage.svoilid;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ProxyService extends Service {

    private static final String TAG = "SvoiLidSvc";
    private static final String CHANNEL_ID = "svoi_lid_channel";
    private static final int NOTIF_ID = 1;

    private static final String BASE = ApiClient.BASE;
    private static final int HEARTBEAT_INTERVAL_MS = 10_000;
    private static final int CLAIM_INTERVAL_MS     = 5_000;
    private static final int CONNECT_TIMEOUT_MS    = 30_000;
    private static final int READ_TIMEOUT_MS       = 60_000;

    // ===== Snapshot для UI (читается HomeFragment) =====
    public static volatile boolean isRunning = false;
    public static volatile boolean isPaused  = false;
    public static volatile String  statusText = "Остановлено";
    public static volatile int     todayDone  = 0;
    public static volatile int     todayFailed = 0;
    public static volatile String  currentAction = "";

    private static final Object logLock = new Object();
    private static final StringBuilder logBuffer = new StringBuilder();

    // ===== Состояние сервиса =====
    private Session session;
    private volatile String token;
    private volatile boolean stopFlag = false;
    private PowerManager.WakeLock wakeLock = null;
    private Thread heartbeatThread;
    private Thread jobsThread;

    private long lastSlotsLog = 0;
    private long lastClaimLog = 0;


    @Override
    public void onCreate() {
        super.onCreate();
        session = new Session(this);
        createNotificationChannel();
        addLog("Service создан");
    }

@Override
public int onStartCommand(Intent intent, int flags, int startId) {
    startForeground(NOTIF_ID, buildNotification("Подготовка…"));
    if (!isRunning) {
        // ВАЖНО: startWork() делает HTTP-запросы, нельзя в главном потоке
        new Thread(this::startWork, "startWork").start();
    }
    return START_STICKY;
}

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        stopFlag = true;
        releaseWakeLock();
        isRunning = false;
        addLog("Service уничтожен");
        super.onDestroy();
    }

    // =====================================================================
    //  УПРАВЛЕНИЕ
    // =====================================================================

    public void startWork() {
        if (isRunning) return;
        isRunning = true;
        stopFlag = false;
        statusText = "Подключение…";
        updateNotification();

        acquireWakeLock();
        requestIgnoreBatteryOptimizations();

        // Регистрируемся, если нет токена
        token = session.getToken();
        if (token == null || token.isEmpty()) {
            addLog("Нет токена — регистрируемся");
            registerDevice();
        }

        if (token == null || token.isEmpty()) {
            statusText = "Ошибка: не удалось получить токен";
            isRunning = false;
            updateNotification();
            return;
        }

        statusText = "Работает";
        updateNotification();

        addLog("▶ Старт heartbeat / state / jobs");
        startHeartbeatLoop();
        startStateLoop();
        startJobsLoop();
    }

    public void stopWork() {
        stopFlag = true;
        isRunning = false;
        statusText = "Остановлено";
        addLog("Остановка");
        releaseWakeLock();
        updateNotification();

        if (heartbeatThread != null) heartbeatThread.interrupt();
        if (jobsThread != null) jobsThread.interrupt();
    }

    // =====================================================================
    //  РЕГИСТРАЦИЯ
    // =====================================================================

    private void registerDevice() {
        try {
            String apiKey = session.getApiKey();
            if (apiKey == null || apiKey.isEmpty()) {
                addLog("❌ Нет API-ключа");
                return;
            }
            String deviceUid = session.getOrCreateDeviceUid(this);
            String deviceName = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL;
            String version = "1.0.0";

            String androidId = null;
            try {
                androidId = android.provider.Settings.Secure.getString(
                    getContentResolver(),
                    android.provider.Settings.Secure.ANDROID_ID
                );
            } catch (Exception ignored) {}
            if (androidId == null) androidId = "";

            Map<String, String> p = new HashMap<>();
            p.put("device_uid",  deviceUid);
            p.put("api_key",     apiKey);
            p.put("device_name", deviceName);
            p.put("model",       android.os.Build.MODEL);
            p.put("app_version", version);
            p.put("android_id", androidId);

            HttpResult r = httpPost(BASE + "register.php", p, null);
            addLog("register → HTTP " + r.code);

            if (r.body != null) {
                try {
                    JSONObject j = new JSONObject(r.body);
                    if (j.optBoolean("ok", false)) {
                        token = j.optString("session_token", null);
                        session.setToken(token);
                        session.setDeviceId(j.optInt("device_id", 0));
                        addLog("✅ Токен получен");
                    } else {
                        String errCode = j.optString("error_code", "");
                        String errMsg  = j.optString("error", "unknown");
                        addLog("❌ " + errMsg + (errCode.isEmpty() ? "" : " [" + errCode + "]"));
                    }
                } catch (Exception e) {
                    addLog("❌ Парсинг: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            addLog("❌ register: " + e.getMessage());
        }
    }

    // =====================================================================
    //  HEARTBEAT
    // =====================================================================

    private void startHeartbeatLoop() {
        heartbeatThread = new Thread(() -> {
            while (!stopFlag && token != null) {
                try {
                    Map<String, String> p = new HashMap<>();
                    p.put("app_version", "1.0.0");
                    p.put("connection_status", "wifi");

                    HttpResult r = httpPost(BASE + "heartbeat.php", p, token);
                    if (r.code == 200 && r.body != null) {
                        JSONObject j = new JSONObject(r.body);
                        if (j.optBoolean("ok", false)) {
                            boolean wasPaused = isPaused;
                            isPaused = j.optBoolean("is_paused", false);
                            if (isPaused) {
                                statusText = "На паузе";
                            } else {
                                statusText = "Работает";
                            }
                            updateNotification();
                            if (wasPaused != isPaused) {
                                addLog("Heartbeat: is_paused=" + isPaused);
                            }
                        } else if ("unauthorized".equals(j.optString("error_code"))) {
                            addLog("⚠️ Токен истёк — нужна перерегистрация");
                            session.clearToken();
                            token = null;
                            break;
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "heartbeat: " + e.getMessage());
                }
                SystemClock.sleep(HEARTBEAT_INTERVAL_MS);
            }
        }, "heartbeat");
        heartbeatThread.start();
    }

    // =====================================================================
    //  JOBS
    // =====================================================================

    /** Раз в 15 сек обновляем State.me — для UI. */
    private void startStateLoop() {
        new Thread(() -> {
            while (!stopFlag && token != null) {
                try {
                    HttpResult r = httpPost(BASE + "me.php", new HashMap<>(), token);
                    if (r.code == 200 && r.body != null) {
                        JSONObject j = new JSONObject(r.body);
                        if (j.optBoolean("ok", false)) {
                            State.me = j;
                            State.meUpdatedAt = System.currentTimeMillis();
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "state: " + e.getMessage());
                }
                SystemClock.sleep(15_000);
            }
        }, "state").start();
    }

    
    private void startJobsLoop() {
        jobsThread = new Thread(() -> {
            while (!stopFlag && token != null) {
                try {
                    if (!isPaused) {
                        processAllSlots();
                    }
                } catch (Exception e) {
                    Log.w(TAG, "jobs loop: " + e.getMessage());
                }
                SystemClock.sleep(CLAIM_INTERVAL_MS);
            }
        }, "jobs");
        jobsThread.start();
    }

    /** Проходит по всем слотам этого устройства и пытается взять задание */
    private void processAllSlots() {
        try {
            // Получаем слоты через me.php — там есть их slot_id
            HttpResult me = httpPost(BASE + "me.php", new HashMap<>(), token);
            if (me.code != 200 || me.body == null) return;
            JSONObject meJson = new JSONObject(me.body);
            if (!meJson.optBoolean("ok")) return;

            JSONArray slots = meJson.optJSONArray("slots");
            if (slots == null) {
                addLog("me: слотов нет");
                return;
            }

            int foundOccupied = 0;
            int foundFree = 0;
            for (int i = 0; i < slots.length(); i++) {
                JSONObject s = slots.getJSONObject(i);
                if ("occupied".equals(s.optString("state"))) {
                    foundOccupied++;
                    if ("free".equals(s.optString("status"))) foundFree++;
                }
            }
            // Логируем раз в минуту, чтобы не спамить
            if (System.currentTimeMillis() - lastSlotsLog > 60_000) {
                lastSlotsLog = System.currentTimeMillis();
                addLog("Слоты: занято " + foundOccupied + ", готовы " + foundFree);
            }

            for (int i = 0; i < slots.length(); i++) {
                if (stopFlag || isPaused) return;

                JSONObject slot = slots.getJSONObject(i);
                String state = slot.optString("state", "");
                if (!"occupied".equals(state)) continue;
                if ("busy".equals(slot.optString("status"))) continue;

                int realSlotId = slot.optInt("slot_id", 0);
                if (realSlotId <= 0) continue;

                processSlot(realSlotId);
            }

        } catch (Exception e) {
            Log.w(TAG, "processAllSlots: " + e.getMessage());
        }
    }

    private void processSlot(int slotId) {
        try {
            Map<String, String> p = new HashMap<>();
            p.put("slot_id", String.valueOf(slotId));

            HttpResult r = httpPost(BASE + "jobs_claim.php", p, token);
            if (r.code != 200 || r.body == null) return;

            JSONObject j = new JSONObject(r.body);
            if (!j.optBoolean("ok", false)) {
                String err = j.optString("error", "");
                addLog("claim slot=" + slotId + ": " + err);
                return;
            }

            JSONObject job = j.optJSONObject("job");
            if (job == null) {
                // нет задач — тихо, раз в 30 сек
                if (System.currentTimeMillis() - lastClaimLog > 30_000) {
                    lastClaimLog = System.currentTimeMillis();
                    addLog("claim slot=" + slotId + ": нет задач");
                }
                return;
            }

            int jobId = job.optInt("id", 0);
            int leaseSec = job.optInt("lease_seconds", 300);
            JSONArray steps = job.optJSONArray("steps");
            if (jobId <= 0 || steps == null || steps.length() == 0) return;

            addLog("▶ Job #" + jobId + " (" + steps.length() + " шаг.)");
            currentAction = "Выполняется #" + jobId;
            updateNotification();

            long t0 = System.currentTimeMillis();
            HttpResult last;
            try {
                last = executeSteps(steps);
            } catch (Exception e) {
                addLog("❌ Ошибка: " + e.getMessage());
                reportJob(jobId, "failed", 0, null, "network", e.getMessage(),
                          (int)(System.currentTimeMillis() - t0));
                todayFailed++;
                currentAction = "";
                return;
            }
            int durationMs = (int)(System.currentTimeMillis() - t0);

            // Разбираем VK-ответ
            String status = "failed";
            String errCode = null;
            String errMsg = null;
            if (last.body != null) {
                try {
                    JSONObject vk = new JSONObject(last.body);
                    if (vk.has("error")) {
                        JSONObject e = vk.getJSONObject("error");
                        int ec = e.optInt("error_code", 0);
                        errMsg = e.optString("error_msg", "");
                        if (ec == 14) errCode = "captcha";
                        else if (ec == 9) errCode = "flood";
                        else if (ec == 5 || ec == 15 || ec == 27) errCode = "auth";
                        else errCode = "vk_api";
                    } else {
                        status = "success";
                    }
                } catch (Exception ignored) {
                    if (last.code >= 200 && last.code < 300) status = "success";
                    else { errCode = "network"; errMsg = "HTTP " + last.code; }
                }
            }

            reportJob(jobId, status, last.code, last.body, errCode, errMsg, durationMs);

            if ("success".equals(status)) {
                todayDone++;
                addLog("✅ Job #" + jobId + " за " + durationMs + "мс");
            } else {
                todayFailed++;
                addLog("❌ Job #" + jobId + ": " + (errCode != null ? errCode : "unknown"));
            }
            currentAction = "";

            // Обновляем State сразу — экраны увидят изменения
            try {
                HttpResult me = httpPost(BASE + "me.php", new HashMap<>(), token);
                if (me.code == 200 && me.body != null) {
                    JSONObject mj = new JSONObject(me.body);
                    if (mj.optBoolean("ok", false)) {
                        State.me = mj;
                        State.meUpdatedAt = System.currentTimeMillis();
                    }
                }
            } catch (Exception ignored) {}

            updateNotification();

        } catch (Exception e) {
            Log.w(TAG, "processSlot: " + e.getMessage());
        }
    }

    /** Проходит по шагам последовательно, подставляет ${var} */
    private HttpResult executeSteps(JSONArray steps) throws Exception {
        Map<String, String> vars = new HashMap<>();
        HttpResult last = new HttpResult(0, "");

        for (int i = 0; i < steps.length(); i++) {
            if (stopFlag) throw new Exception("Остановлено");
            JSONObject step = steps.getJSONObject(i);

            String url    = substitute(step.optString("url", ""), vars);
            String method = step.optString("method", "GET");
            String body   = step.optString("body", null);
            if (body != null && !body.isEmpty()) body = substitute(body, vars);

            Map<String, String> headers = new HashMap<>();
            JSONObject h = step.optJSONObject("headers");
            if (h != null) {
                Iterator<String> it = h.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    headers.put(k, h.optString(k, ""));
                }
            }

            last = httpRaw(url, method, headers, body);

            // Извлекаем значения для следующих шагов
            JSONObject ex = step.optJSONObject("extract");
            if (ex != null && last.body != null) {
                try {
                    JSONObject resp = new JSONObject(last.body);
                    Iterator<String> it = ex.keys();
                    while (it.hasNext()) {
                        String key = it.next();
                        String path = ex.optString(key, "");
                        String val = jsonPath(resp, path);
                        if (val != null) vars.put(key, val);
                    }
                } catch (Exception ignored) {}
            }
        }
        return last;
    }

    private String substitute(String s, Map<String, String> vars) {
        if (s == null) return null;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            s = s.replace("${" + e.getKey() + "}", e.getValue());
        }
        return s;
    }

    private String jsonPath(Object obj, String path) {
        String[] parts = path.split("\\.");
        Object cur = obj;
        for (String p : parts) {
            if (cur == null) return null;
            if (cur instanceof JSONObject) {
                cur = ((JSONObject) cur).opt(p);
            } else if (cur instanceof JSONArray) {
                int idx;
                try { idx = Integer.parseInt(p); } catch (Exception e) { return null; }
                cur = ((JSONArray) cur).opt(idx);
            } else return null;
        }
        if (cur == null || cur == JSONObject.NULL) return null;
        if (cur instanceof String) return (String) cur;
        return String.valueOf(cur);
    }

    // =====================================================================
    //  ОТЧЁТ О РЕЗУЛЬТАТЕ
    // =====================================================================

    private void reportJob(int jobId, String status, int code, String body,
                           String errCode, String errMsg, int durationMs) {
        try {
            Map<String, String> p = new HashMap<>();
            p.put("job_id",      String.valueOf(jobId));
            p.put("status",      status);
            p.put("duration_ms", String.valueOf(durationMs));
            if (code > 0)       p.put("status_code",   String.valueOf(code));
            if (body != null)   p.put("body",          truncate(body, 200_000));
            if (errCode != null) p.put("error_code",   errCode);
            if (errMsg != null)  p.put("error_message", truncate(errMsg, 500));

            HttpResult r = httpPost(BASE + "jobs_result.php", p, token);
            if (r.code == 200 && r.body != null) {
                JSONObject j = new JSONObject(r.body);
                if (!j.optBoolean("ok", false)) {
                    addLog("⚠️ jobs_result: " + j.optString("error", ""));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "reportJob: " + e.getMessage());
        }
    }

    // =====================================================================
    //  HTTP
    // =====================================================================

    private static class HttpResult {
        final int code;
        final String body;
        HttpResult(int code, String body) { this.code = code; this.body = body; }
    }

    private HttpResult httpPost(String url, Map<String, String> params, String token) throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(java.net.URLEncoder.encode(e.getKey(), "UTF-8"))
                .append('=')
                .append(java.net.URLEncoder.encode(e.getValue(), "UTF-8"));
        }
        if (token != null) {
            headers.put("Authorization", "Bearer " + token);
            headers.put("X-Device-Token", token);
        }
        return httpRaw(url, "POST", headers, body.toString());
    }

    private HttpResult httpRaw(String urlStr, String method,
                               Map<String, String> headers, String body) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setInstanceFollowRedirects(false);

        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                conn.setRequestProperty(e.getKey(), e.getValue());
            }
        }

        if (body != null && !body.isEmpty()
            && ("POST".equals(method) || "PUT".equals(method))) {
            conn.setDoOutput(true);
            OutputStream os = conn.getOutputStream();
            os.write(body.getBytes(StandardCharsets.UTF_8));
            os.close();
        }

        int code = conn.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
            code >= 400 ? conn.getErrorStream() : conn.getInputStream(),
            StandardCharsets.UTF_8
        ));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        conn.disconnect();
        return new HttpResult(code, sb.toString());
    }

    // =====================================================================
    //  УВЕДОМЛЕНИЕ / WAKELOCK / BATT
    // =====================================================================

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "СВОЙ.ЛИД", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Работа в фоне");
            NotificationManager m = getSystemService(NotificationManager.class);
            if (m != null) m.createNotificationChannel(ch);
        }
    }

    private void updateNotification() {
        Notification n = buildNotification(statusText);
        NotificationManager m = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (m != null) m.notify(NOTIF_ID, n);
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        String line = "Сегодня: " + todayDone + " задач · " + todayFailed + " ошибок";
        return b.setContentTitle("СВОЙ.ЛИД · " + text)
                .setContentText(line)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pi)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_LOW)
                .build();
    }

    private void acquireWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) return;
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm == null) return;
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SvoiLid::Wake");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire();
        } catch (Exception ignored) {}
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Exception ignored) {}
        wakeLock = null;
    }

    private void requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm == null) return;
            if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                i.setData(Uri.parse("package:" + getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        } catch (Exception ignored) {}
    }

    // =====================================================================
    //  ЛОГИ (для отладочного TextView на главной)
    // =====================================================================

    public static void addLog(String msg) {
        String ts = new SimpleDateFormat("HH:mm:ss").format(new Date());
        synchronized (logLock) {
            logBuffer.append("[").append(ts).append("] ").append(msg).append('\n');
            if (logBuffer.length() > 30_000) {
                logBuffer.delete(0, logBuffer.length() - 30_000);
            }
        }
        Log.d(TAG, msg);
    }

    public static String getLogs() {
        synchronized (logLock) { return logBuffer.toString(); }
    }

    public static void clearLogs() {
        synchronized (logLock) { logBuffer.setLength(0); }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "…(truncated)";
    }
}
