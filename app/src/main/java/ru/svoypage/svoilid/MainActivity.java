package ru.svoypage.svoilid;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import ru.svoypage.svoilid.ui.home.HomeFragment;
import ru.svoypage.svoilid.ui.slots.SlotsFragment;
import ru.svoypage.svoilid.ui.jobs.JobsFragment;
import ru.svoypage.svoilid.ui.earnings.EarningsFragment;
import ru.svoypage.svoilid.ui.more.MoreFragment;

public class MainActivity extends AppCompatActivity {

    private static final int RC_NOTIF = 1001;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestRuntimePermissions();

        bottomNav = findViewById(R.id.bottomNav);

        bottomNav.setOnItemSelectedListener(item -> {
            // Если открыт суб-фрагмент (в backstack) — очищаем стек
            getSupportFragmentManager().popBackStackImmediate(null,
                androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);

            Fragment f = null;
            int id = item.getItemId();
            if (id == R.id.nav_home)          f = new HomeFragment();
            else if (id == R.id.nav_slots)    f = new SlotsFragment();
            else if (id == R.id.nav_jobs)     f = new JobsFragment();
            else if (id == R.id.nav_earnings) f = new EarningsFragment();
            else if (id == R.id.nav_more)     f = new MoreFragment();
            if (f != null) {
                getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, f)
                    .commit();
                return true;
            }
            return false;
        });

        // Обработка аппаратной кнопки "назад":
        // если открыт суб-экран — вернуться к More, иначе — стандартное поведение.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    getSupportFragmentManager().popBackStack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

if (Build.VERSION.SDK_INT >= 31) {
    android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
    if (am != null && !am.canScheduleExactAlarms()) {
        Intent i = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
        i.setData(android.net.Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }
}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    android.os.PowerManager pm =
        (android.os.PowerManager) getSystemService(POWER_SERVICE);
    if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
        Intent i = new Intent(
            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        i.setData(android.net.Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }
}
        
        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }
    }

    private void requestRuntimePermissions() {
        java.util.List<String> need = new java.util.ArrayList<>();
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                   != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            need.add("android.permission.POST_NOTIFICATIONS");
        }
        if (!need.isEmpty()) {
            requestPermissions(need.toArray(new String[0]), RC_NOTIF);
        }
    }
}
