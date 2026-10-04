package ru.svoypage.svoilid.ui.more;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import ru.svoypage.svoilid.App;
import ru.svoypage.svoilid.R;

public class NotificationsFragment extends Fragment {

    private LinearLayout container;
    private SwipeRefreshLayout swipe;
    private Button btnMarkAll;
    private boolean loading = false;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inflater.inflate(R.layout.fragment_notifications, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        container  = v.findViewById(R.id.notifContainer);
        swipe      = v.findViewById(R.id.notifSwipe);
        btnMarkAll = v.findViewById(R.id.btnMarkAll);

        swipe.setOnRefreshListener(this::loadNotifications);
        btnMarkAll.setOnClickListener(x -> markAllRead());

        loadNotifications();
    }

    private void loadNotifications() {
        if (loading) return;
        loading = true;

        Map<String, String> p = new HashMap<>();
        p.put("limit", "50");
        p.put("mark_read", "0");

        App.get().api().call("notifications.php", p, (ok, resp, err) -> {
            if (getView() == null) return;
            loading = false;
            swipe.setRefreshing(false);
            if (!ok || resp == null) {
                Toast.makeText(requireContext(), "Ошибка загрузки", Toast.LENGTH_SHORT).show();
                return;
            }
            render(resp);
        });
    }

 private void render(JSONObject r) {
    container.removeAllViews();
    JSONArray list = r.optJSONArray("notifications");
    if (list == null || list.length() == 0) {
        TextView empty = new TextView(requireContext());
        empty.setText("Уведомлений пока нет");
        empty.setTextColor(0xFF5c6370);
        empty.setTextSize(13);
        empty.setPadding(0, 60, 0, 0);
        empty.setGravity(android.view.Gravity.CENTER);
        container.addView(empty);
        return;
    }

    // ИСПРАВЛЕНО: новые уведомления — сверху.
    // Сортируем по id DESC (id — автоинкремент или timestamp,
    // в любом случае чем больше id, тем свежее запись).
    java.util.List<JSONObject> sorted = new java.util.ArrayList<>();
    for (int i = 0; i < list.length(); i++) {
        try { sorted.add(list.getJSONObject(i)); } catch (Exception ignored) {}
    }
    sorted.sort((a, b) -> Integer.compare(
        b.optInt("id", 0),
        a.optInt("id", 0)
    ));

    for (JSONObject n : sorted) {
        try {
            container.addView(buildRow(n));
        } catch (Exception ignored) {}
    }
}

    private View buildRow(JSONObject n) throws Exception {
        View v = getLayoutInflater().inflate(R.layout.item_notification, container, false);
        TextView tvT = v.findViewById(R.id.tvNotifTitle);
        TextView tvB = v.findViewById(R.id.tvNotifBody);
        TextView tvM = v.findViewById(R.id.tvNotifTime);

        int id = n.optInt("id", 0);
        String title = n.optString("title", "");
        String body  = n.optString("body", "");
        String ts    = n.optString("created_at", "");
        boolean read = n.optInt("is_read", 0) == 1;

        tvT.setText(title);
        if (!body.isEmpty()) {
            tvB.setText(body);
            tvB.setVisibility(View.VISIBLE);
        } else {
            tvB.setVisibility(View.GONE);
        }
        tvM.setText(fmtTime(ts));

        if (!read) {
            tvT.setTextColor(0xFFe8920d);
        }

        v.setOnClickListener(x -> markRead(id));
        return v;
    }

    private String fmtTime(String ts) {
        if (ts == null || ts.isEmpty() || "null".equals(ts)) return "";
        try {
            SimpleDateFormat src = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            SimpleDateFormat dst = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault());
            return dst.format(src.parse(ts));
        } catch (Exception e) { return ts; }
    }

    private void markRead(int id) {
        Map<String, String> p = new HashMap<>();
        p.put("ids[]", String.valueOf(id));

        App.get().api().call("notifications_ack.php", p, (ok, resp, err) -> {
            if (ok) loadNotifications();
        });
    }

    private void markAllRead() {
        Map<String, String> p = new HashMap<>();
        p.put("all", "1");

        App.get().api().call("notifications_ack.php", p, (ok, resp, err) -> {
            if (getView() == null) return;
            if (ok) {
                Toast.makeText(requireContext(), "Отмечено прочитанными", Toast.LENGTH_SHORT).show();
                loadNotifications();
            }
        });
    }
}
