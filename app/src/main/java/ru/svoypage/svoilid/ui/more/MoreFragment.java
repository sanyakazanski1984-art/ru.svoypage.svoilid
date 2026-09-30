package ru.svoypage.svoilid.ui.more;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import ru.svoypage.svoilid.App;
import ru.svoypage.svoilid.ProxyService;
import ru.svoypage.svoilid.R;

public class MoreFragment extends Fragment {

    private SwitchCompat swPause, swPool;
    private TextView tvNotifBadge;
    private boolean suppressListeners = false;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inflater.inflate(R.layout.fragment_more, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        swPause       = v.findViewById(R.id.swPause);
        swPool        = v.findViewById(R.id.swPool);
        tvNotifBadge  = v.findViewById(R.id.tvNotifBadge);

        v.findViewById(R.id.rowNotifications).setOnClickListener(x ->
            openSubFragment(new NotificationsFragment()));

        v.findViewById(R.id.rowAbout).setOnClickListener(x ->
            openSubFragment(new AboutFragment()));

        v.findViewById(R.id.rowLogout).setOnClickListener(x -> confirmLogout());

        swPause.setOnCheckedChangeListener((btn, checked) -> {
            if (suppressListeners) return;
            updateSettings("is_paused", checked);
        });

        swPool.setOnCheckedChangeListener((btn, checked) -> {
            if (suppressListeners) return;
            if (checked) {
                // Включение — без диалога
                updateSettings("is_pool_optin", true);
                return;
            }
            // Выключение — предупреждаем про активные аренды
            new AlertDialog.Builder(requireContext())
                .setTitle("Отключить общий пул?")
                .setMessage("Свободные слоты перестанут сдаваться в аренду.\n\n"
                          + "Если сейчас на ваших слотах работают чужие аккаунты — "
                          + "система попробует перенести их на другие устройства. "
                          + "Если свободных слотов в пуле нет, аренда приостановится.")
                .setPositiveButton("Отключить", (d, w) -> {
                    updateSettings("is_pool_optin", false);
                })
                .setNegativeButton("Отмена", (d, w) -> {
                    suppressListeners = true;
                    swPool.setChecked(true);
                    suppressListeners = false;
                })
                .show();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadSettings();
    }

    private void openSubFragment(Fragment f) {
        getParentFragmentManager()
            .beginTransaction()
            .replace(R.id.fragmentContainer, f)
            .addToBackStack(null)
            .commit();
    }

    private void loadSettings() {
        App.get().api().call("settings.php", new HashMap<>(), (ok, resp, err) -> {
            if (getView() == null || !ok || resp == null) return;

            JSONObject dev = resp.optJSONObject("device");
            boolean paused = dev != null && dev.optBoolean("is_paused", false);
            boolean pool   = dev != null && dev.optBoolean("is_pool_optin", false);

            suppressListeners = true;
            swPause.setChecked(paused);
            swPool.setChecked(pool);
            suppressListeners = false;
        });

        // Непрочитанные уведомления
        App.get().api().call("notifications.php",
            new HashMap<String, String>() {{ put("limit", "1"); put("mark_read", "0"); }},
            (ok, resp, err) -> {
                if (getView() == null || !ok || resp == null) return;
                int unread = resp.optInt("unread", 0);
                if (unread > 0) {
                    tvNotifBadge.setVisibility(View.VISIBLE);
                    tvNotifBadge.setText(String.valueOf(unread));
                } else {
                    tvNotifBadge.setVisibility(View.GONE);
                }
            });
    }

    private void updateSettings(String key, boolean value) {
        Map<String, String> p = new HashMap<>();
        p.put(key, value ? "1" : "0");
        if (key.equals("is_paused") && value) {
            p.put("paused_reason", "manual");
        }

        App.get().api().call("update_settings.php", p, (ok, resp, err) -> {
            if (getView() == null) return;
            if (!ok) {
                Toast.makeText(requireContext(),
                    "Ошибка: " + (err != null ? err : "не сохранилось"),
                    Toast.LENGTH_SHORT).show();
                suppressListeners = true;
                if (key.equals("is_paused")) swPause.setChecked(!value);
                else                          swPool.setChecked(!value);
                suppressListeners = false;
                return;
            }

            // Если сервер мигрировал аренды — показать провайдеру
            int migrated = resp != null ? resp.optInt("migrated_rentals", 0) : 0;
            int suspended = resp != null ? resp.optInt("suspended_rentals", 0) : 0;

            String msg;
            if (key.equals("is_paused")) {
                msg = value ? "Поставлено на паузу" : "Возобновлено";
            } else {
                msg = value ? "Общий пул включён" : "Общий пул выключен";
            }
            if (migrated > 0)  msg += ". Аренд перенесено: " + migrated;
            if (suspended > 0) msg += ". Приостановлено: " + suspended;

            Toast.makeText(requireContext(), msg,
                migrated > 0 || suspended > 0 ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
        });
    }

    private void confirmLogout() {
        new AlertDialog.Builder(requireContext())
            .setTitle("Выйти?")
            .setMessage("Устройство отвяжется от аккаунта. Для повторного подключения потребуется API-ключ.")
            .setPositiveButton("Выйти", (d, w) -> {
                App.get().api().call("logout.php", new HashMap<>(), (ok, resp, err) -> {
                    App.get().session().logout();
                    ProxyService.addLog("Выход выполнен");

                    // Останавливаем сервис
                    requireContext().stopService(
                        new android.content.Intent(requireContext(), ProxyService.class));

                    // Возвращаемся на главный экран приложения
                    if (getActivity() != null) {
                        getActivity().finishAffinity();
                    }
                });
            })
            .setNegativeButton("Отмена", null)
            .show();
    }
}
