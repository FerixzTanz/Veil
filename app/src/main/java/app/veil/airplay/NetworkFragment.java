package app.veil.airplay;

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

import java.util.List;

import app.veil.R;
import app.veil.car.CarSignals;
import app.veil.dim.Overlays;

/**
 * The Network page: which network and address the receiver is on, why the iPhone might not
 * see it, the car's networks, the car link, and the last lines of the AirPlay log.
 */
public class NetworkFragment extends Fragment {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView receiver;
    private TextView advice;
    private TextView networks;
    private TextView car;
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
        car = view.findViewById(R.id.net_car);
        log = view.findViewById(R.id.net_log);
        report = new NetworkReport(requireContext());
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

        CarSignals signals = CarSignals.get();
        String gear = signals.gear();
        Float speed = signals.speedKmh();
        String reading = gear == null && speed == null ? getString(R.string.net_car_unknown)
                : getString(R.string.net_car_reading, gear == null ? "?" : gear,
                speed == null ? "?" : String.format(java.util.Locale.US, "%.0f", speed));
        car.setText(getString(R.string.net_car_line, reading, signals.status()));

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
