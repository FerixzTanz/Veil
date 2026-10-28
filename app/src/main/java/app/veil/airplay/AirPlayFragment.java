package app.veil.airplay;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.List;

import app.veil.R;
import app.veil.VeilApp;
import app.veil.dim.Overlays;
import app.veil.settings.Settings;
import app.veil.ui.VeilDialogs;

/** The AirPlay page: on/off, what the receiver is doing, the receiver name, and how to mirror. */
public class AirPlayFragment extends Fragment {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Settings settings;
    private TextView state;
    private TextView problem;
    private TextView name;
    private TextView steps;
    private TextView addresses;
    private ImageView icon;
    private SwitchCompat enabled;
    private View permissionCard;
    /** True while paint() moves the switch, so that does not start or stop the receiver again. */
    private boolean painting;

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
        return inflater.inflate(R.layout.fragment_airplay, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        settings = VeilApp.settings();
        state = view.findViewById(R.id.airplay_state);
        problem = view.findViewById(R.id.airplay_problem);
        name = view.findViewById(R.id.airplay_name);
        steps = view.findViewById(R.id.airplay_steps);
        addresses = view.findViewById(R.id.airplay_addresses);
        icon = view.findViewById(R.id.airplay_icon);
        enabled = view.findViewById(R.id.airplay_enabled);
        permissionCard = view.findViewById(R.id.airplay_permission_card);
        view.findViewById(R.id.airplay_permission_button).setOnClickListener(v -> requestPermission());
        enabled.setChecked(settings.airPlayOn());
        enabled.setOnCheckedChangeListener((b, on) -> {
            if (painting) {
                return;
            }
            AirPlayService.setEnabled(requireContext(), on);
            handler.postDelayed(this::paint, 300);
        });
        view.findViewById(R.id.airplay_rename).setOnClickListener(v -> rename());
        SwitchCompat casting = view.findViewById(R.id.airplay_casting);
        casting.setChecked(settings.castVideo());
        casting.setOnCheckedChangeListener((b, on) -> {
            settings.setCastVideo(on);
            AirPlayService.castingChanged(on);
        });
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

    private void paint() {
        if (state == null || !isAdded()) {
            return;
        }
        boolean on = AirPlayService.isRunning();
        String deviceName = AirPlayService.deviceName();
        state.setText(on ? stateText(AirPlayService.state(), deviceName) : getString(R.string.airplay_state_off));
        String why = AirPlayService.problem();
        problem.setText(why);
        problem.setVisibility(why == null ? View.GONE : View.VISIBLE);
        name.setText(getString(R.string.airplay_name_line, deviceName));
        steps.setText(getString(R.string.airplay_steps, deviceName));
        List<String> where = AirPlayService.addresses();
        if (where.isEmpty()) {
            addresses.setVisibility(View.GONE);
        } else {
            StringBuilder sb = new StringBuilder(getString(R.string.airplay_address_title));
            for (String line : where) {
                sb.append('\n').append(line);
            }
            String first = where.get(0).substring(where.get(0).lastIndexOf(' ') + 1);
            sb.append("\n\n").append(getString(R.string.airplay_address_test, first));
            addresses.setText(sb);
            addresses.setVisibility(View.VISIBLE);
        }
        icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(),
                on ? R.color.energy : R.color.text_secondary)));
        permissionCard.setVisibility(Overlays.canDraw(requireContext()) ? View.GONE : View.VISIBLE);
        boolean saved = settings.airPlayOn();
        if (enabled.isChecked() != saved) {
            painting = true;
            enabled.setChecked(saved);
            painting = false;
        }
    }

    private String stateText(int value, String deviceName) {
        switch (value) {
            case AirPlayService.STATE_WAITING:
                return getString(R.string.airplay_state_waiting, deviceName);
            case AirPlayService.STATE_CONNECTED:
                return getString(R.string.airplay_state_connected);
            case AirPlayService.STATE_MIRRORING:
                return getString(R.string.airplay_state_mirroring);
            case AirPlayService.STATE_CASTING:
                return getString(R.string.airplay_state_casting);
            default:
                return getString(R.string.airplay_state_starting);
        }
    }

    private void rename() {
        EditText input = VeilDialogs.input(requireContext());
        input.setText(settings.receiverName());
        input.setSelection(input.getText().length());
        input.setSingleLine(true);
        VeilDialogs.show(VeilDialogs.builder(requireContext())
                .setTitle(R.string.airplay_rename_title)
                .setMessage(R.string.airplay_rename_message)
                .setView(VeilDialogs.wrap(input))
                .setPositiveButton(R.string.action_ok, (d, w) -> {
                    settings.setReceiverName(input.getText().toString());
                    AirPlayService.nameChanged();
                    paint();
                })
                .setNegativeButton(R.string.action_cancel, null), false);
    }

    private void requestPermission() {
        if (!Overlays.openPermissionPage(requireContext())) {
            VeilDialogs.inform(requireContext(), getString(R.string.overlay_needed_title),
                    getString(R.string.overlay_needed_adb, Overlays.adbGrant(requireContext().getPackageName())));
        }
    }
}
