package ru.svoypage.svoilid.ui.home;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import ru.svoypage.svoilid.App;
import ru.svoypage.svoilid.ProxyService;
import ru.svoypage.svoilid.R;
import ru.svoypage.svoilid.Session;

public class HomeFragment extends Fragment {

    private TextView tvStatus, tvStats, tvLog;
    private Button btnStart, btnStop, btnClear, btnChangeKey;
    private ScrollView logScroll;
    private Handler uiHandler;
    private Runnable uiTick;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        tvStatus   = v.findViewById(R.id.tvStatus);
        tvStats    = v.findViewById(R.id.tvStats);
        tvLog      = v.findViewById(R.id.tvLog);
        logScroll  = v.findViewById(R.id.logScroll);
        btnStart   = v.findViewById(R.id.btnStart);
        btnStop    = v.findViewById(R.id.btnStop);
        btnClear   = v.findViewById(R.id.btnClear);
        btnChangeKey = v.findViewById(R.id.btnChangeKey);

        uiHandler = new Handler(Looper.getMainLooper());

        btnStart.setOnClickListener(x -> {
            Session s = App.get().session();
            if (s.getApiKey() == null || s.getApiKey().isEmpty()) {
                showApiKeyDialog();
                return;
            }
            android.content.Intent i = new android.content.Intent(requireContext(), ProxyService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                requireContext().startForegroundService(i);
            } else {
                requireContext().startService(i);
            }
        });

        btnStop.setOnClickListener(x -> {
            requireContext().stopService(
                new android.content.Intent(requireContext(), ProxyService.class));
        });

        btnClear.setOnClickListener(x -> ProxyService.clearLogs());

        btnChangeKey.setOnClickListener(x -> showApiKeyDialog());

        // Первый показ диалога, если ключа нет
        Session s = App.get().session();
        if (s.getApiKey() == null || s.getApiKey().isEmpty()) {
            showApiKeyDialog();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        uiTick = new Runnable() {
            @Override public void run() {
                refreshUi();
                uiHandler.postDelayed(this, 500);
            }
        };
        uiHandler.post(uiTick);
    }

    @Override
    public void onPause() {
        super.onPause();
        uiHandler.removeCallbacks(uiTick);
    }

    private void refreshUi() {
        if (getView() == null) return;

        tvStatus.setText(ProxyService.statusText);
        tvStats.setText("Сегодня: " + ProxyService.todayDone + " · Ошибок: " + ProxyService.todayFailed);

        btnStart.setEnabled(!ProxyService.isRunning);
        btnStop.setEnabled(ProxyService.isRunning);

        String logs = ProxyService.getLogs();
        if (!logs.equals(tvLog.getText().toString())) {
            tvLog.setText(logs);
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    // =====================================================================
    //  ДИАЛОГ ВВОДА API-КЛЮЧА
    // =====================================================================

    private void showApiKeyDialog() {
        Session s = App.get().session();

        LinearLayout ll = new LinearLayout(requireContext());
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(60, 40, 60, 20);

        TextView desc = new TextView(requireContext());
        desc.setText("Введите API-ключ из личного кабинета на svoypage.ru");
        desc.setTextSize(14);
        desc.setPadding(0, 0, 0, 24);
        ll.addView(desc);

        EditText input = new EditText(requireContext());
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
                // Сохраняем ключ, чистим старый токен — сервис зарегистрируется заново
                s.setApiKey(key);
                s.clearToken();
                ProxyService.addLog("🔑 Ключ сохранён, будет регистрация при старте");
                Toast.makeText(requireContext(), "Ключ сохранён. Нажмите «Старт».", Toast.LENGTH_LONG).show();
            })
            .setNegativeButton("Отмена", null)
            .show();
    }
}
