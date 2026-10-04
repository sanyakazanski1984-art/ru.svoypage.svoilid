package ru.svoypage.svoilid;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a)
                && !"android.intent.action.LOCKED_BOOT_COMPLETED".equals(a)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(a)) {
            return;
        }

        Session s = new Session(context);
        if (s.getToken() == null || s.getToken().isEmpty()) return;
        if (!s.isShouldRun()) return;

        // 1. Пытаемся сразу поднять сервис
        Intent svc = new Intent(context, ProxyService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc);
        } else {
            context.startService(svc);
        }

        // 2. Планируем watchdog (на случай если сервис убьют позже)
        WatchdogJobService.schedule(context);
    }
}
