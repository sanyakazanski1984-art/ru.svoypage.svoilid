package ru.svoypage.svoilid;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;

/**
 * Хранилище для session_token, device_uid, device_id.
 * Использует обычный SharedPreferences (сессия от чужого приложения не защищена,
 * но токен одноразовый — можно перевыпустить в любой момент).
 */
public class Session {

    private static final String PREFS = "svoi_lid_session";
    private static final String KEY_TOKEN = "session_token";
    private static final String KEY_DEVICE_UID = "device_uid";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_USER_NAME = "user_name";

    private static final String KEY_LAST_NOTIF_ID = "last_notif_id";

public int getLastNotifId() {
    return prefs.getInt(KEY_LAST_NOTIF_ID, 0);
}
public void setLastNotifId(int id) {
    prefs.edit().putInt(KEY_LAST_NOTIF_ID, id).apply();
}

    private final SharedPreferences prefs;

    private static final String KEY_SHOULD_RUN = "should_run";

public boolean isShouldRun() {
    return prefs.getBoolean(KEY_SHOULD_RUN, false);
}

public void setShouldRun(boolean value) {
    prefs.edit().putBoolean(KEY_SHOULD_RUN, value).apply();
}

    public Session(Context ctx) {
        this.prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getToken() { return prefs.getString(KEY_TOKEN, null); }
    public void setToken(String t) { prefs.edit().putString(KEY_TOKEN, t).apply(); }
    public void clearToken() { prefs.edit().remove(KEY_TOKEN).apply(); }

    public String getApiKey() { return prefs.getString(KEY_API_KEY, null); }
    public void setApiKey(String k) { prefs.edit().putString(KEY_API_KEY, k).apply(); }

    public int getDeviceId() { return prefs.getInt(KEY_DEVICE_ID, 0); }
    public void setDeviceId(int id) { prefs.edit().putInt(KEY_DEVICE_ID, id).apply(); }

    public String getUserName() { return prefs.getString(KEY_USER_NAME, null); }
    public void setUserName(String n) { prefs.edit().putString(KEY_USER_NAME, n).apply(); }

    /**
     * Возвращает device_uid. Генерируется один раз при первом вызове.
     * Основан на ANDROID_ID + случайный fallback.
     */
    public String getOrCreateDeviceUid(Context ctx) {
        String uid = prefs.getString(KEY_DEVICE_UID, null);
        if (uid != null && !uid.isEmpty()) return uid;

        String androidId = null;
        try {
            androidId = android.provider.Settings.Secure.getString(
                ctx.getContentResolver(),
                android.provider.Settings.Secure.ANDROID_ID
            );
        } catch (Exception ignored) {}

        boolean bad = androidId == null || androidId.isEmpty()
                || "9774d56d682e549c".equals(androidId)
                || "unknown".equalsIgnoreCase(androidId);

        if (bad) {
            androidId = UUID.randomUUID().toString().replace("-", "");
        }

        String shortId = androidId.substring(0, Math.min(8, androidId.length()));
        uid = "Android_" + shortId;
        prefs.edit().putString(KEY_DEVICE_UID, uid).apply();
        return uid;
    }

    /**
     * Полный logout: чистим всё.
     */
    public void logout() {
        prefs.edit().clear().apply();
    }
}
