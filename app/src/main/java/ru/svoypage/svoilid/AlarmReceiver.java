package ru.svoypage.svoilid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Session s = new Session(context);
        if (!s.isShouldRun()) return;
        if (s.getToken() == null || s.getToken().isEmpty()) return;

        Intent svc = new Intent(context, ProxyService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc);
        } else {
            context.startService(svc);
        }
    }
}
