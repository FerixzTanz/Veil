package app.veil.airplay;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

import app.veil.R;
import app.veil.dim.Overlays;
import app.veil.share.FileShareServer;
import app.veil.share.PhoneShare;
import app.veil.ui.VeilDialogs;

/**
 * The Network page: which network and address the receiver is on, why the iPhone might not
 * see it, the car's networks, the diagnostics export, and the last lines of the AirPlay log.
 */
public class NetworkFragment extends Fragment {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView receiver;
    private TextView advice;
    private TextView networks;
    private TextView log;
    private NetworkReport report;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            paint();
            handler.postDelayed(this, 2000);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_network, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        receiver = view.findViewById(R.id.net_receiver);
        advice = view.findViewById(R.id.net_advice);
        networks = view.findViewById(R.id.net_networks);
        log = view.findViewById(R.id.net_log);
        report = new NetworkReport(requireContext());
        view.findViewById(R.id.net_export_send).setOnClickListener(v -> export(file ->
                PhoneShare.show(requireContext(), getViewLifecycleOwner(), file)));
        view.findViewById(R.id.net_export_save).setOnClickListener(v -> export(file ->
                VeilDialogs.inform(requireContext(), getString(R.string.net_export_saved_title),
                        getString(R.string.net_export_saved, file.getAbsolutePath(),
                                FileShareServer.readableSize(file.length())))));
    }

    /** Writes the export off the main thread, then hands it on if the page is still there. */
    private void export(Consumer<File> then) {
        Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            File file = null;
            String error = null;
            try {
                file = DiagnosticsExport.write(app);
            } catch (Exception e) {
                AirPlayLog.w("export failed: " + e);
                error = e.getMessage() != null ? e.getMessage() : e.toString();
            }
            File done = file;
            String why = error;
            handler.post(() -> {
                if (!isAdded() || getView() == null) {
                    return;
                }
                if (done != null) {
                    then.accept(done);
                } else {
                    VeilDialogs.inform(requireContext(), getString(R.string.net_export_title),
                            getString(R.string.net_export_failed, why));
                }
            });
        }, "diagnostics-export").start();
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

    private void paint() {
        if (!isAdded()) {
            return;
        }
        List<String> addresses = AirPlayService.addresses();
        boolean running = AirPlayService.isRunning();
        if (!running) {
            receiver.setText(R.string.net_receiver_off);
        } else if (addresses.isEmpty()) {
            receiver.setText(R.string.net_receiver_no_address);
        } else {
            receiver.setText(getString(R.string.net_receiver_on, AirPlayService.deviceName(),
                    String.join("\n", addresses)));
        }

        Visibility.Facts f = new Visibility.Facts();
        f.receiverRunning = running;
        f.onWifiOrHotspot = Visibility.anyWifi(addresses);
        f.onIphoneHotspot = report.onIphoneHotspot();
        f.ownMdns = AirPlayService.ownMdns();
        f.multicast = AirPlayService.wifiMulticast();
        f.canDrawOverlay = Overlays.canDraw(requireContext());
        List<Visibility.Reason> reasons = Visibility.reasons(f);
        if (reasons.isEmpty()) {
            advice.setText(R.string.net_advice_ok);
        } else {
            StringBuilder sb = new StringBuilder();
            for (Visibility.Reason r : reasons) {
                if (sb.length() > 0) {
                    sb.append("\n\n");
                }
                sb.append(getString(text(r)));
            }
            advice.setText(sb);
        }

        networks.setText(String.join("\n", report.lines()));

        List<String> lines = AirPlayLog.recent();
        List<String> tail = lines.subList(Math.max(0, lines.size() - 60), lines.size());
        log.setText(tail.isEmpty() ? getString(R.string.net_log_empty) : String.join("\n", tail));
    }

    private static int text(Visibility.Reason r) {
        switch (r) {
            case RECEIVER_OFF:
                return R.string.net_reason_off;
            case NO_WIFI:
                return R.string.net_reason_no_wifi;
            case SYSTEM_MDNS:
                return R.string.net_reason_system_mdns;
            case NO_MULTICAST:
                return R.string.net_reason_no_multicast;
            case NO_OVERLAY:
                return R.string.net_reason_no_overlay;
            default:
                return R.string.net_reason_hotspot_hint;
        }
    }
}
