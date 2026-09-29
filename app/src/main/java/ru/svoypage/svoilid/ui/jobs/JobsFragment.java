package ru.svoypage.svoilid.ui.jobs;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import ru.svoypage.svoilid.App;
import ru.svoypage.svoilid.R;
import ru.svoypage.svoilid.State;

public class JobsFragment extends Fragment {

    private LinearLayout container;
    private SwipeRefreshLayout swipe;
    private TextView chipAll, chipSuccess, chipFailed, chipActive;
    private TextView tvSummary;

    private String filter = "all";   // all | success | failed | active
    private boolean loading = false;
    private boolean firstLoadDone = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_jobs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        container    = v.findViewById(R.id.jobsContainer);
        swipe        = v.findViewById(R.id.jobsSwipe);
        chipAll      = v.findViewById(R.id.chipAll);
        chipSuccess  = v.findViewById(R.id.chipSuccess);
        chipFailed   = v.findViewById(R.id.chipFailed);
        chipActive   = v.findViewById(R.id.chipActive);
        tvSummary    = v.findViewById(R.id.tvJobsSummary);

        chipAll.setOnClickListener(x ->     setFilter("all"));
        chipSuccess.setOnClickListener(x -> setFilter("success"));
        chipFailed.setOnClickListener(x ->  setFilter("failed"));
        chipActive.setOnClickListener(x ->  setFilter("active"));

        swipe.setOnRefreshListener(this::loadJobs);

        updateChipStyles();
        loadJobs();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!firstLoadDone) {
            loadJobs();
        } else {
            // Быстрое обновление — раз в 5 сек
            new Handler(Looper.getMainLooper()).postDelayed(this::loadJobs, 2000);
        }
    }

    private void setFilter(String f) {
        if (filter.equals(f)) return;
        filter = f;
        updateChipStyles();
        loadJobs();
    }

    private void updateChipStyles() {
        TextView[] chips = {chipAll, chipSuccess, chipFailed, chipActive};
        String[] keys = {"all", "success", "failed", "active"};
        for (int i = 0; i < chips.length; i++) {
            boolean active = keys[i].equals(filter);
            TextView c = chips[i];
            c.setTextColor(active ? Color.BLACK : 0xFF8b95a8);
            c.setBackgroundColor(active ? 0xFFe8920d : 0x14FFFFFF);
        }
    }

    private void loadJobs() {
        if (loading) return;
        loading = true;

        Map<String, String> p = new HashMap<>();
        p.put("status", filter);
        p.put("limit", "50");

        App.get().api().call("jobs.php", p, (ok, resp, err) -> {
            loading = false;
            swipe.setRefreshing(false);
            firstLoadDone = true;

            if (!ok || resp == null) {
                showError(err);
                return;
            }
            render(resp);
        });
    }

    private void render(JSONObject resp) {
        if (getView() == null) return;

        container.removeAllViews();

        JSONObject counters = resp.optJSONObject("counters");
        String mode = resp.optString("mode", "owner");

        int s = counters != null ? counters.optInt("success", 0) : 0;
        int f = counters != null ? counters.optInt("failed", 0)  : 0;
        int a = counters != null ? counters.optInt("active", 0)  : 0;

        tvSummary.setText("Успех: " + s + " · Ошибки: " + f + " · Активные: " + a);

        JSONArray jobs = resp.optJSONArray("jobs");
        if (jobs == null || jobs.length() == 0) {
            View empty = getLayoutInflater().inflate(R.layout.item_jobs_empty, container, false);
            container.addView(empty);
            return;
        }

        for (int i = 0; i < jobs.length(); i++) {
            try {
                JSONObject j = jobs.getJSONObject(i);
                container.addView(buildJobCard(j, mode));
            } catch (Exception ignored) {}
        }
    }

    private View buildJobCard(JSONObject j, String mode) throws Exception {
        View v = getLayoutInflater().inflate(R.layout.item_job, container, false);

        TextView tvIcon    = v.findViewById(R.id.tvJobIcon);
        TextView tvTitle   = v.findViewById(R.id.tvJobTitle);
        TextView tvSub     = v.findViewById(R.id.tvJobSub);
        TextView tvStatus  = v.findViewById(R.id.tvJobStatus);
        TextView tvTime    = v.findViewById(R.id.tvJobTime);

        String actionType = j.optString("action_type", "");
        String targetKind = j.optString("target_kind", "");
        String status     = j.optString("status", "pending");
        boolean own       = j.optBoolean("is_own", true);

        // Иконка и заголовок
        String emoji = "•";
        String title;
        if (!own || "pool".equals(mode)) {
            // Обезличенное
            title = "Действие";
            emoji = "🔒";
        } else {
            switch (actionType) {
                case "like":    emoji = "❤️"; title = "Лайк"; break;
                case "comment": emoji = "💬"; title = "Комментарий"; break;
                case "friend":  emoji = "👤"; title = "Заявка в друзья"; break;
                case "message": emoji = "✉️"; title = "Сообщение"; break;
                case "invite":  emoji = "⊕"; title = "Приглашение"; break;
                default:        emoji = "•"; title = actionType;
            }
        }

        tvIcon.setText(emoji);
        tvTitle.setText(title);

        // Подзаголовок
        StringBuilder sub = new StringBuilder();
        if (own && !targetKind.isEmpty()) {
            if ("post".equals(targetKind))       sub.append("пост");
            else if ("old_post".equals(targetKind)) sub.append("старый пост");
            else if ("avatar".equals(targetKind)) sub.append("аватар");
            else if ("wall".equals(targetKind))   sub.append("стена");
            else if ("direct".equals(targetKind)) sub.append("директ");
            else if ("group".equals(targetKind))  sub.append("группа");
        }
        int slotIndex = j.optInt("slot_index", 0);
        if (slotIndex > 0) {
            if (sub.length() > 0) sub.append(" · ");
            sub.append("слот ").append(slotIndex);
        }
        tvSub.setText(sub.toString());

        // Статус
        switch (status) {
            case "success":
                tvStatus.setText("✓ УСПЕХ");
                tvStatus.setTextColor(0xFF4cd964);
                tvStatus.setBackgroundColor(0x1a4cd964);
                break;
            case "failed":
                tvStatus.setText("✕ ОШИБКА");
                tvStatus.setTextColor(0xFFe84057);
                tvStatus.setBackgroundColor(0x1ae84057);
                break;
            case "leased":
            case "running":
                tvStatus.setText("⏱ ВЫПОЛНЯЕТСЯ");
                tvStatus.setTextColor(0xFFe8920d);
                tvStatus.setBackgroundColor(0x1ae8920d);
                break;
            case "pending":
            case "retry":
                tvStatus.setText("◌ В ОЧЕРЕДИ");
                tvStatus.setTextColor(0xFF8b95a8);
                tvStatus.setBackgroundColor(0x148b95a8);
                break;
            case "cancelled":
                tvStatus.setText("— ОТМЕНЕНА");
                tvStatus.setTextColor(0xFF5c6370);
                tvStatus.setBackgroundColor(0x105c6370);
                break;
            default:
                tvStatus.setText(status.toUpperCase());
                tvStatus.setTextColor(0xFF8b95a8);
                tvStatus.setBackgroundColor(0x148b95a8);
        }

        // Время
        String completedAt = j.optString("completed_at", "");
        String createdAt   = j.optString("created_at", "");
        tvTime.setText(formatTime(completedAt.isEmpty() ? createdAt : completedAt));

        return v;
    }

    private String formatTime(String ts) {
        if (ts == null || ts.isEmpty() || "null".equals(ts)) return "—";
        // Формат из БД: "2026-09-29 07:24:36"
        try {
            // Оставим только HH:mm
            if (ts.length() >= 16) {
                String date = ts.substring(0, 10);
                String time = ts.substring(11, 16);
                String today = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
                if (date.equals(today)) {
                    return time;
                }
                // Иначе — "29.09 07:24"
                return date.substring(8, 10) + "." + date.substring(5, 7) + " " + time;
            }
        } catch (Exception ignored) {}
        return ts;
    }

    private void showError(String err) {
        container.removeAllViews();
        View v = getLayoutInflater().inflate(R.layout.item_jobs_empty, container, false);
        TextView tv = v.findViewById(R.id.tvEmpty);
        tv.setText("Ошибка: " + (err != null ? err : "неизвестная"));
        tv.setTextColor(0xFFe84057);
        container.addView(v);
    }
}
