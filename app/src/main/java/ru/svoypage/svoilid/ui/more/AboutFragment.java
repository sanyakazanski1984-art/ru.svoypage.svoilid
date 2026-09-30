package ru.svoypage.svoilid.ui.more;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONObject;

import java.util.HashMap;

import ru.svoypage.svoilid.ApiClient;
import ru.svoypage.svoilid.App;
import ru.svoypage.svoilid.ProxyService;
import ru.svoypage.svoilid.R;
import ru.svoypage.svoilid.Session;

public class AboutFragment extends Fragment {

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inflater.inflate(R.layout.fragment_about, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        TextView tvVer  = v.findViewById(R.id.tvAppVersion);
        TextView tvApi  = v.findViewById(R.id.tvApiUrl);
        TextView tvId   = v.findViewById(R.id.tvDeviceId);
        Button btnUpd   = v.findViewById(R.id.btnCheckUpdate);
        Button btnClear = v.findViewById(R.id.btnClearLogs);

        tvApi.setText(ApiClient.BASE);

        Session sess = App.get().session();
        tvId.setText(sess.getOrCreateDeviceUid(requireContext()));

        btnUpd.setOnClickListener(x -> checkUpdate());
        btnClear.setOnClickListener(x -> {
            ProxyService.clearLogs();
            Toast.makeText(requireContext(), "Логи очищены", Toast.LENGTH_SHORT).show();
        });
    }

    private void checkUpdate() {
        App.get().api().call("settings.php", new HashMap<>(), (ok, resp, err) -> {
            if (getView() == null) return;
            if (!ok || resp == null) {
                Toast.makeText(requireContext(), "Не удалось проверить", Toast.LENGTH_SHORT).show();
                return;
            }
            JSONObject appUpdate = resp.optJSONObject("app_update");
            if (appUpdate == null) return;

            boolean hasUpdate = appUpdate.optBoolean("has_update", false);
            boolean force = appUpdate.optBoolean("force_update", false);
            String latest = appUpdate.optString("latest_version", "");
            String url = appUpdate.optString("update_url", "");

            if (!hasUpdate) {
                Toast.makeText(requireContext(), "Версия актуальна", Toast.LENGTH_SHORT).show();
                return;
            }

            String msg = "Доступна версия " + latest
                       + (force ? "\n\nОбязательное обновление" : "\n\nСкачать APK?");

            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Обновление")
                .setMessage(msg)
                .setPositiveButton("Скачать", (d, w) -> {
                    if (!url.isEmpty()) {
                        android.content.Intent i = new android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(url));
                        startActivity(i);
                    }
                })
                .setNegativeButton("Позже", null)
                .show();
        });
    }
}
