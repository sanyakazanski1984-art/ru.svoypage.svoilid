package ru.svoypage.svoilid.ui.home;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.HashMap;
import java.util.Map;

import ru.svoypage.svoilid.App;
import ru.svoypage.svoilid.ProxyService;
import ru.svoypage.svoilid.R;
import ru.svoypage.svoilid.Session;

public class HomeFragment extends Fragment {

    private TextView tvStatus, tvStats, tvLog;
    private TextView tvNotifBadge;      // бейдж на колокольчике
    private View    bellWrap;            // контейнер колокольчика
    private Button  btnStart, btnStop;
    private ScrollView logScroll;

    private Handler uiHandler;
    private Runnable uiTick;

    private Handler notifHandler;        // отдельный для пинга уведомлений
    private Runnable notifTick;

    // ===== Защита кнопки «Старт» =====
    // Пока сервис стартует, ProxyService.isRunning ещё false.
    // Держим кнопку выключенной N секунд после клика.
    private long startPressedAt = 0;
    private static final long START_LOCK_MS = 5000;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        tvStatus      = v.findViewById(R.id.tvStatus);
        tvStats       = v.findViewById(R.id.tvStats);
        tvLog         = v.findViewById(R.id.tvLog);
        logScroll     = v.findViewById(R.id.logScroll);
        btnStart      = v.findViewById(R.id.btnStart);
        btnStop       = v.findViewById(R.id.btnStop);
        tvNotifBadge  = v.findViewById(R.id.tvNotifBadge);
        bellWrap      = v.findViewById(R.id.bellWrap);

        uiHandler    = new Handler(Looper.getMainLooper());
        notifHandler = new Handler(Looper.getMainLooper());

        // ===== Старт =====
        btnStart.setOnClickListener(x -> {
            Session s = App.get().session();
            if (s.getApiKey() == null || s.getApiKey().isEmpty()) {
                showApiKeyDialog();
                return;
            }

            // Блокируем кнопку сразу, чтобы нельзя было кликнуть дважды
            startPressedAt = System.currentTimeMillis();
            btnStart.setEnabled(false);
            btnStart.setText("Запуск…");

            Intent i = new Intent(requireContext(), ProxyService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requireContext().startForegroundService(i);
            } else {
                requireContext().startService(i);
            }
        });

        // ===== Стоп =====
        btnStop.setOnClickListener(x -> requireContext().stopService(
                new Intent(requireContext(), ProxyService.class)));

        // ===== Колокольчик → вкладка «Ещё» =====
        if (bellWrap != null) {
            bellWrap.setOnClickListener(x -> {
                if (getActivity() == null) return;
                BottomNavigationView nav = getActivity().findViewById(R.id.bottomNav);
                if (nav != null) nav.setSelectedItemId(R.id.nav_more);
            });
        }

        // ===== При старте без ключа — сразу диалог ввода =====
        Session s = App.get().session();
        if (s.getApiKey() == null || s.getApiKey().isEmpty()) {
            showApiKeyDialog();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // UI-тик каждые 500 мс
        uiTick = new Runnable() {
            @Override public void run() {
                refreshUi();
                uiHandler.postDelayed(this, 500);
            }
        };
        uiHandler.post(uiTick);

        // Пинг уведомлений раз в 15 сек
        notifTick = new Runnable() {
            @Override public void run() {
                fetchUnreadCount();
                notifHandler.postDelayed(this, 15000);
            }
        };
        notifHandler.post(notifTick);
    }

    @Override
    public void onPause() {
        super.onPause();
        uiHandler.removeCallbacks(uiTick);
        notifHandler.removeCallbacks(notifTick);
    }

    private void refreshUi() {
        if (getView() == null) return;

        tvStatus.setText(ProxyService.statusText);
        tvStats.setText("Сегодня: " + ProxyService.todayDone + " · Ошибок: " + ProxyService.todayFailed);

        // ===== Кнопка Старт =====
        long elapsed = System.currentTimeMillis() - startPressedAt;
        boolean stillStarting = startPressedAt > 0
                && elapsed < START_LOCK_MS
                && !ProxyService.isRunning;

        if (stillStarting) {
            btnStart.setEnabled(false);
            btnStart.setText("Запуск…");
        } else {
            if (startPressedAt > 0) startPressedAt = 0;
            btnStart.setEnabled(!ProxyService.isRunning);
            btnStart.setText("Старт");
        }

        btnStop.setEnabled(ProxyService.isRunning);

        // ===== Лог =====
        String logs = ProxyService.getLogs();
        if (!logs.equals(tvLog.getText().toString())) {
            tvLog.setText(logs);
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    // ===== Непрочитанные уведомления =====
    private void fetchUnreadCount() {
        Map<String, String> p = new HashMap<>();
        p.put("limit", "1");
        p.put("mark_read", "0");

        App.get().api().call("notifications.php", p, (ok, resp, err) -> {
            if (!ok || resp == null || getView() == null) return;

            int unread = resp.optInt("unread", 0);
            if (tvNotifBadge == null) return;

            if (unread > 0) {
                tvNotifBadge.setVisibility(View.VISIBLE);
                tvNotifBadge.setText(unread > 99 ? "99+" : String.valueOf(unread));
            } else {
                tvNotifBadge.setVisibility(View.GONE);
            }
        });
    }

    // ===== Диалог смены ключа (используется из MoreFragment) =====
    public void showApiKeyDialog() {
        Session s = App.get().session();

        LinearLayout ll = new LinearLayout(requireContext());
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(60, 40, 60, 20);

        TextView desc = new TextView(requireContext());
        desc.setText("Введите API-ключ из личного кабинета на svoypage.ru");
        desc.setTextSize(14);
        desc.setPadding(0, 0, 0, 24);
        ll.addView(desc);

        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("Например: abc123…");
        input.setTextSize(16);
        if (s.getApiKey() != null) input.setText(s.getApiKey());
        ll.addView(input);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Привязка к аккаунту")
                .setView(ll)
                .setCancelable(false)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String key = input.getText().toString().trim();
                    if (key.isEmpty()) {
                        Toast.makeText(requireContext(), "Введите ключ", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    s.setApiKey(key);
                    s.clearToken();
                    ProxyService.addLog("🔑 Ключ сохранён, будет регистрация при старте");
                    Toast.makeText(requireContext(), "Ключ сохранён. Нажмите «Старт».", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
}
