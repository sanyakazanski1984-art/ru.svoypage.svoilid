package ru.svoypage.svoilid;

import org.json.JSONObject;

/**
 * Глобальный кэш последнего ответа me.php и прочих сводок.
 * Пишет только ProxyService. Читают все фрагменты.
 */
public class State {

    /** Последний успешный ответ /me.php */
    public static volatile JSONObject me = null;

    /** Timestamp последнего обновления */
    public static volatile long meUpdatedAt = 0;

    /** Последние ошибки — для бейджа на главной */
    public static volatile int unreadNotifications = 0;

    /** Режим: owner / pool / mixed (из me.mode) */
    public static String mode() {
        if (me == null) return "owner";
        return me.optString("mode", "owner");
    }

    /** Онлайн ли устройство */
    public static boolean online() {
        if (me == null) return false;
        JSONObject dev = me.optJSONObject("device");
        if (dev == null) return false;
        return dev.optBoolean("online", false);
    }

    /** Слоты — массив или null */
    public static org.json.JSONArray slots() {
        if (me == null) return null;
        return me.optJSONArray("slots");
    }

    public static void reset() {
        me = null;
        meUpdatedAt = 0;
        unreadNotifications = 0;
    }
}
