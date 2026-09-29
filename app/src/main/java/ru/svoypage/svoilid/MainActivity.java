package ru.svoypage.svoilid;

import android.os.Bundle;
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

    private BottomNavigationView bottomNav;

    private static final int RC_NOTIF = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestRuntimePermissions();

        bottomNav = findViewById(R.id.bottomNav);

        bottomNav.setOnItemSelectedListener(item -> {
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
