package ru.svoypage.svoilid.ui.slots;

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

import ru.svoypage.svoilid.App;
import ru.svoypage.svoilid.ProxyService;
import ru.svoypage.svoilid.R;
import ru.svoypage.svoilid.State;

public class SlotsFragment extends Fragment {

    private LinearLayout container;
    private SwipeRefreshLayout swipe;
    private TextView tvMode;
    private Handler uiHandler;
    private Runnable tick;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_slots, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        container = v.findViewById(R.id.slotsContainer);
        swipe     = v.findViewById(R.id.swipe);
        tvMode    = v.findViewById(R.id.tvMode);

        swipe.setOnRefreshListener(this::forceRefresh);

        uiHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onResume() {
        super.onResume();
        tick = new Runnable() {
            @Override public void run() {
                render();
                uiHandler.postDelayed(this, 500);
            }
        };
        uiHandler.post(tick);
    }

    @Override
    public void onPause() {
        super.onPause();
        uiHandler.removeCallbacks(tick);
    }

    private void forceRefresh() {
        // Пинаем сервис — просто перерегистрация в State не нужна, следующий tick сам покажет
        // Но для UX — через 1 секунду снимаем "крутилку"
        uiHandler.postDelayed(() -> swipe.setRefreshing(false), 1200);
    }

    private void render() {
        if (getView() == null) return;

        JSONArray slots = State.slots();
        String mode = State.mode();

        tvMode.setText("Режим: " + mode);

        // Если данных нет — показываем 3 пустых плейсхолдера
        if (slots == null) {
            if (container.getChildCount() == 0) {
                for (int i = 1; i <= 3; i++) {
                    container.addView(buildCard(i, "waiting", mode));
                }
            } else {
                // ничего не делаем, ждём данных
            }
            return;
        }

        // Полная перерисовка
        container.removeAllViews();
        for (int i = 0; i < slots.length(); i++) {
            try {
                JSONObject s = slots.getJSONObject(i);
                container.addView(buildCardFromJson(s, mode));
            } catch (Exception ignored) {}
        }
    }

    /** Плейсхолдер на время загрузки */
    private View buildCard(int index, String state, String mode) {
        View v = getLayoutInflater().inflate(R.layout.item_slot, container, false);
        TextView tvIndex   = v.findViewById(R.id.tvSlotIndex);
        TextView tvStatus  = v.findViewById(R.id.tvSlotStatus);
        TextView tvTitle   = v.findViewById(R.id.tvAccountTitle);
        TextView tvSub     = v.findViewById(R.id.tvAccountSub);
        TextView tvHint    = v.findViewById(R.id.tvAccountHint);

        tvIndex.setText("СЛОТ " + index);
        tvStatus.setText("ожидание");
        tvStatus.setTextColor(0xFF8b95a8);
        tvTitle.setText("Загрузка…");
        tvSub.setText("");
        tvHint.setVisibility(View.GONE);
        return v;
    }

    /** Реальная карточка из данных me.php */
    private View buildCardFromJson(JSONObject s, String mode) throws Exception {
        View v = getLayoutInflater().inflate(R.layout.item_slot, container, false);
        TextView tvIndex   = v.findViewById(R.id.tvSlotIndex);
        TextView tvStatus  = v.findViewById(R.id.tvSlotStatus);
        TextView tvTitle   = v.findViewById(R.id.tvAccountTitle);
        TextView tvSub     = v.findViewById(R.id.tvAccountSub);
        TextView tvHint    = v.findViewById(R.id.tvAccountHint);

        int slotId  = s.optInt("slot_id", 0);
        int index   = s.optInt("index", 0);
        String st   = s.optString("status", "free");
        String state= s.optString("state", "free");
        boolean own = s.optBoolean("is_own", false);

        // Мгновенный локальный флаг: если сервис прямо сейчас выполняет job на этом слоте —
        // рисуем "ВЫПОЛНЯЕТ", не дожидаясь обновления State.me.
        if (slotId > 0 && ru.svoypage.svoilid.ProxyService.busySlots.contains(slotId)) {
            st = "busy";
        }

        tvIndex.setText("СЛОТ " + index);

        // Статус
        if ("free".equals(st)) {
            tvStatus.setText("СВОБОДЕН");
            tvStatus.setTextColor(0xFF4cd964);
            tvStatus.setBackgroundColor(0x1a4cd964);
        } else if ("busy".equals(st)) {
            tvStatus.setText("ВЫПОЛНЯЕТ");
            tvStatus.setTextColor(0xFFe8920d);
            tvStatus.setBackgroundColor(0x1ae8920d);
        } else if ("error".equals(st)) {
            tvStatus.setText("ОШИБКА");
            tvStatus.setTextColor(0xFFe84057);
            tvStatus.setBackgroundColor(0x1ae84057);
        } else {
            tvStatus.setText(st.toUpperCase());
            tvStatus.setTextColor(0xFF8b95a8);
            tvStatus.setBackgroundColor(0x148b95a8);
        }

        if ("free".equals(state)) {
            // Пусто
            tvTitle.setText("Пусто");
            tvSub.setText("Слот без аккаунта");
            tvHint.setVisibility(View.VISIBLE);
            tvHint.setText("Привяжите аккаунт в личном кабинете на svoypage.ru");
        } else if (own) {
            // Своё
            String title = s.optString("account_title", "");
            String vkId  = s.optString("vk_user_id", "");
            tvTitle.setText(title.isEmpty() ? ("@" + vkId) : title);
            tvSub.setText("@" + vkId);
            tvHint.setVisibility(View.GONE);
        } else {
            // Чужое — скрываем детали
            tvTitle.setText("Занято");
            tvSub.setText("Аккаунт недоступен для просмотра");
            tvHint.setVisibility(View.GONE);
        }

        return v;
    }
}
