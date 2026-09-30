package ru.svoypage.svoilid.ui.earnings;

import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
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

public class EarningsFragment extends Fragment {

    private TextView tvBalance, tvToday, tvPeriod, tvMonth, tvPoolDevices, tvMinHint;
    private LinearLayout payoutsContainer;
    private Button btnWithdraw, periodDay, periodWeek, periodMonth;
    private SwipeRefreshLayout swipe;

    private String period = "month";
    private boolean loading = false;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inflater.inflate(R.layout.fragment_earnings, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        tvBalance        = v.findViewById(R.id.tvBalance);
        tvToday          = v.findViewById(R.id.tvToday);
        tvPeriod         = v.findViewById(R.id.tvPeriod);
        tvMonth          = v.findViewById(R.id.tvMonth);
        tvPoolDevices    = v.findViewById(R.id.tvPoolDevices);
        tvMinHint        = v.findViewById(R.id.tvMinHint);
        payoutsContainer = v.findViewById(R.id.payoutsContainer);
        btnWithdraw      = v.findViewById(R.id.btnWithdraw);
        periodDay        = v.findViewById(R.id.periodDay);
        periodWeek       = v.findViewById(R.id.periodWeek);
        periodMonth      = v.findViewById(R.id.periodMonth);
        swipe            = v.findViewById(R.id.earnSwipe);

        periodDay.setOnClickListener(x -> setPeriod("day"));
        periodWeek.setOnClickListener(x -> setPeriod("week"));
        periodMonth.setOnClickListener(x -> setPeriod("month"));
        btnWithdraw.setOnClickListener(x -> showPayoutDialog());
        swipe.setOnRefreshListener(this::loadEarnings);

        updatePeriodStyles();
        loadEarnings();
    }

    private void setPeriod(String p) {
        if (period.equals(p)) return;
        period = p;
        updatePeriodStyles();
        loadEarnings();
    }

    private void updatePeriodStyles() {
        Button[] btns = {periodDay, periodWeek, periodMonth};
        String[] keys = {"day", "week", "month"};
        for (int i = 0; i < btns.length; i++) {
            boolean active = keys[i].equals(period);
            btns[i].setTextColor(active ? Color.BLACK : 0xFF8b95a8);
            btns[i].setBackgroundColor(active ? 0xFFe8920d : 0x14FFFFFF);
        }
    }

    private void loadEarnings() {
        if (loading) return;
        loading = true;

        Map<String, String> p = new HashMap<>();
        p.put("period", period);

        App.get().api().call("earnings.php", p, (ok, resp, err) -> {
            if (getView() == null) return;
            loading = false;
            swipe.setRefreshing(false);
            if (!ok || resp == null) {
                Toast.makeText(requireContext(), "Ошибка: " + (err != null ? err : "нет ответа"), Toast.LENGTH_SHORT).show();
                return;
            }
            render(resp);
        });
    }

    private void render(JSONObject r) {
        double balance = r.optDouble("balance_rub", 0);
        double today   = r.optDouble("today_rub",   0);
        double periodV = r.optDouble("period_rub",  0);
        double total   = r.optDouble("total_rub",   balance);

        tvBalance.setText(String.format(Locale.US, "%.2f ₽", balance));
        tvToday.setText(String.format(Locale.US, "%.2f ₽", today));
        tvPeriod.setText(String.format(Locale.US, "%.2f ₽", periodV));
        tvMonth.setText(String.format(Locale.US, "%.2f ₽", total));

        boolean canWithdraw = balance >= 500;
        btnWithdraw.setEnabled(canWithdraw);
        btnWithdraw.setAlpha(canWithdraw ? 1.0f : 0.5f);

        if (!canWithdraw) {
            tvMinHint.setVisibility(View.VISIBLE);
            tvMinHint.setText(String.format(Locale.US,
                "До вывода нужно накопить 500 ₽. Осталось %.2f ₽", 500 - balance));
        } else {
            tvMinHint.setVisibility(View.GONE);
        }

        // Устройств в пуле — приблизительно, из общего числа устройств в пуле,
        // но API это не отдаёт. Показываем просто число операций.
int poolDev = r.optInt("pool_devices", 0);
tvPoolDevices.setText(String.valueOf(poolDev));

        // История выплат
        payoutsContainer.removeAllViews();
        JSONArray payouts = r.optJSONArray("payouts");
        if (payouts == null || payouts.length() == 0) {
            TextView empty = new TextView(requireContext());
            empty.setText("Заявок пока нет");
            empty.setTextColor(0xFF5c6370);
            empty.setTextSize(12);
            empty.setPadding(0, 12, 0, 12);
            payoutsContainer.addView(empty);
        } else {
            for (int i = 0; i < payouts.length(); i++) {
                try {
                    payoutsContainer.addView(buildPayoutRow(payouts.getJSONObject(i)));
                } catch (Exception ignored) {}
            }
        }
    }

    private View buildPayoutRow(JSONObject p) throws Exception {
        View v = getLayoutInflater().inflate(R.layout.item_payout, payoutsContainer, false);
        TextView tvId     = v.findViewById(R.id.tvPayoutId);
        TextView tvAmount = v.findViewById(R.id.tvPayoutAmount);
        TextView tvStatus = v.findViewById(R.id.tvPayoutStatus);
        TextView tvDate   = v.findViewById(R.id.tvPayoutDate);

        int id         = p.optInt("id", 0);
        double amount  = p.optDouble("amount_rub", 0);
        String status  = p.optString("status", "");
        String reqAt   = p.optString("requested_at", "");
        String paidAt  = p.optString("paid_at", "");

        tvId.setText("#" + id);
        tvAmount.setText(String.format(Locale.US, "%.0f ₽", amount));

        String label; int color;
        switch (status) {
            case "requested":  label = "ЗАПРОШЕНО"; color = 0xFFe8920d; break;
            case "processing": label = "ОБРАБОТКА"; color = 0xFF60a5fa; break;
            case "paid":       label = "ВЫПЛАЧЕНО"; color = 0xFF4cd964; break;
            case "cancelled":  label = "ОТМЕНЕНО";  color = 0xFFe84057; break;
            default:           label = status.toUpperCase(); color = 0xFF8b95a8;
        }
        tvStatus.setText(label);
        tvStatus.setTextColor(color);
        tvStatus.setBackgroundColor((color & 0x00FFFFFF) | 0x1A000000);

        tvDate.setText(fmtDate(!paidAt.isEmpty() ? paidAt : reqAt));
        return v;
    }

    private String fmtDate(String ts) {
        if (ts == null || ts.isEmpty() || "null".equals(ts)) return "—";
        try {
            SimpleDateFormat src = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            SimpleDateFormat dst = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault());
            return dst.format(src.parse(ts));
        } catch (Exception e) { return ts; }
    }

    private void showPayoutDialog() {
        LinearLayout ll = new LinearLayout(requireContext());
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(60, 40, 60, 20);

        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Сумма, ₽");
        input.setText("500");
        ll.addView(input);

        new AlertDialog.Builder(requireContext())
            .setTitle("Вывод средств")
            .setView(ll)
            .setPositiveButton("Запросить", (d, w) -> {
                int amount = 0;
                try { amount = Integer.parseInt(input.getText().toString().trim()); }
                catch (Exception ignored) {}
                if (amount < 500) {
                    Toast.makeText(requireContext(), "Минимум 500 ₽", Toast.LENGTH_SHORT).show();
                    return;
                }
                submitPayout(amount);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void submitPayout(int amount) {
        Map<String, String> p = new HashMap<>();
        p.put("amount", String.valueOf(amount));

        App.get().api().call("payout_request.php", p, (ok, resp, err) -> {
            if (getView() == null) return;
            if (ok) {
                Toast.makeText(requireContext(), "Заявка на " + amount + " ₽ создана", Toast.LENGTH_LONG).show();
                loadEarnings();
            } else {
                String msg = resp != null ? resp.optString("error", err) : err;
                Toast.makeText(requireContext(), "Ошибка: " + msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadEarnings();
    }
}
