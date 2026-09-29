package ru.svoypage.svoilid.ui.slots;

import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;

public class SlotsFragment extends Fragment {
    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        TextView tv = new TextView(requireContext());
        tv.setText("Слоты");
        tv.setTextColor(0xFFe8eaf0);
        tv.setTextSize(22);
        tv.setPadding(48, 200, 48, 48);
        return tv;
    }
}
