package app.veil.cluster;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

import java.util.List;

import app.veil.R;
import app.veil.ui.VeilDialogs;

/**
 * The Driver display page: what the test does, a button to run it, its result, and the last lines
 * of the cluster log. Nothing on this page changes a setting.
 */
public class ClusterFragment extends Fragment {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MaterialButton run;
    private SwitchCompat askProjection;
    private SwitchCompat askFullLayout;
    private TextView result;
    private TextView log;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            paint();
            handler.postDelayed(this, 1000);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cluster, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        run = view.findViewById(R.id.cluster_run);
        askProjection = view.findViewById(R.id.cluster_ask_projection);
        askProjection.setChecked(true);
        askFullLayout = view.findViewById(R.id.cluster_ask_full_layout);
        askFullLayout.setChecked(true);
        result = view.findViewById(R.id.cluster_result);
        log = view.findViewById(R.id.cluster_log);
        run.setOnClickListener(v -> checkGearThenRun());
        ClusterLog.init(requireContext());
        paint();
    }

    @Override
    public void onResume() {
        super.onResume();
        handler.post(tick);
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    /** Reads the gear off the main thread; runs only in P, or after the driver confirms P. */
    private void checkGearThenRun() {
        if (ClusterTest.isRunning()) {
            return;
        }
        run.setEnabled(false);
        result.setText(R.string.cluster_checking_gear);
        new Thread(() -> {
            StringBuilder why = new StringBuilder();
            String gear = GearReader.read(requireContext(), why);
            handler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                run.setEnabled(true);
                boolean ask = askProjection.isChecked();
                boolean full = askFullLayout.isChecked();
                if ("P".equals(gear)) {
                    ClusterLog.i("gear P");
                    ClusterTest.start(requireContext(), ask, full, this::onResult);
                } else if (gear != null) {
                    ClusterLog.i("gear " + gear + ": not running");
                    result.setText(getString(R.string.cluster_need_park, gear));
                } else {
                    ClusterLog.w("gear unknown (" + why + ")");
                    VeilDialogs.confirm(requireContext(), getString(R.string.cluster_gear_unknown_title),
                            getString(R.string.cluster_gear_unknown_body),
                            getString(R.string.cluster_gear_unknown_action),
                            () -> ClusterTest.start(requireContext(), ask, full, this::onResult));
                }
            });
        }, "VeilCluster-gear").start();
    }

    private void onResult(String text) {
        if (isAdded()) {
            result.setText(text);
            paint();
        }
    }

    private void paint() {
        if (!isAdded()) {
            return;
        }
        run.setEnabled(!ClusterTest.isRunning());
        List<String> lines = ClusterLog.recent();
        List<String> tail = lines.subList(Math.max(0, lines.size() - 40), lines.size());
        log.setText(tail.isEmpty() ? getString(R.string.cluster_log_empty) : String.join("\n", tail));
        String last = ClusterTest.lastResult();
        if (!last.isEmpty() && result.getText().length() == 0) {
            result.setText(last);
        }
    }
}
