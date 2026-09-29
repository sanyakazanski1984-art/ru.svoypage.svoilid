package ru.svoypage.svoilid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        // Не запускаем сервис, если нет session_token — пользователь ещё не привязал
        Session s = new Session(context);
        if (s.getToken() == null || s.getToken().isEmpty()) return;

        Intent svc = new Intent(context, ProxyService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc);
        } else {
            context.startService(svc);
        }
    }
}
