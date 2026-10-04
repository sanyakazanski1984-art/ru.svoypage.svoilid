package ru.svoypage.svoilid;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PersistableBundle;

public class WatchdogJobService extends JobService {

    private static final int JOB_ID = 0x5D01; // уникальный id
    private static final long PERIOD_MS = 15 * 60 * 1000L; // 15 минут

    @Override
    public boolean onStartJob(JobParameters params) {
        // Выполняем в фоне — но проверка быстрая, делаем синхронно
        try {
            Session s = new Session(this);
            if (s.isShouldRun() && s.getToken() != null && !s.getToken().isEmpty()) {
                if (!ProxyService.isRunning) {
                    ProxyService.addLog("🔄 Watchdog: сервис не работает, перезапуск");
                    Intent svc = new Intent(this, ProxyService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(svc);
                    } else {
                        startService(svc);
                    }
                }
            }
        } catch (Exception ignored) {}

        // false = работа завершена синхронно
        jobFinished(params, false);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // true = перезапустить, если система прервала
        return true;
    }

    /** Планирует периодическую задачу. Вызывать при старте приложения/сервиса. */
    public static void schedule(Context ctx) {
        try {
            JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null) return;

            // Проверим, не запланирована ли уже
            for (JobInfo ji : js.getAllPendingJobs()) {
                if (ji.getId() == JOB_ID) return;
            }

            JobInfo job = new JobInfo.Builder(
                    JOB_ID,
                    new ComponentName(ctx, WatchdogJobService.class))
                .setPeriodic(PERIOD_MS)
                .setPersisted(true)                        // переживёт перезагрузку
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setOverrideDeadline(PERIOD_MS)            // не откладывать дольше 15 мин
                .build();

            js.schedule(job);
        } catch (Exception ignored) {}
    }
}
