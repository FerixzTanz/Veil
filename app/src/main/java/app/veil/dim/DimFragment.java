package app.veil.dim;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import app.veil.R;
import app.veil.VeilApp;
import app.veil.settings.Settings;
import app.veil.settings.Store;
import app.veil.ui.VeilDialogs;

/**
 * The Dim overlay page: on/off, the three sliders, pass-through, the moon
 * button and reset. It only writes settings; {@link DimService} follows them.
 */
public class DimFragment extends Fragment {

    private Settings settings;
    private View permissionCard;
    private SwitchCompat onSwitch;
    private SwitchCompat passSwitch;
    private SwitchCompat moonSwitch;
    private Slider opacity;
    private Slider brightness;
    private Slider warmth;
    private Button autoOff;
    private Button autoClock;
    private Button autoNight;
    private View autoHours;
    private TextView autoStart;
    private TextView autoEnd;
    private boolean binding;

    private final Store.Listener onSetting = key -> {
        View v = getView();
        if (v != null) {
            v.post(this::bind);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dim, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        settings = VeilApp.settings();
        permissionCard = v.findViewById(R.id.dim_permission_card);
        v.findViewById(R.id.dim_permission_button).setOnClickListener(x -> requestPermission());

        onSwitch = v.findViewById(R.id.dim_on);
        onSwitch.setOnCheckedChangeListener((b, on) -> {
            if (binding) {
                return;
            }
            if (on && !Overlays.canDraw(requireContext())) {
                onSwitch.setChecked(false);
                requestPermission();
                return;
            }
            settings.setDimOn(on);
            DimService.sync(requireContext());
        });

        opacity = new Slider(v.findViewById(R.id.dim_opacity), settings::setDimOpacity);
        brightness = new Slider(v.findViewById(R.id.dim_brightness), settings::setDimBrightness);
        warmth = new Slider(v.findViewById(R.id.dim_warmth), settings::setDimWarmth);
        opacity.title(R.string.dim_opacity, R.string.dim_opacity_summary);
        brightness.title(R.string.dim_brightness, R.string.dim_brightness_summary);
        warmth.title(R.string.dim_warmth, R.string.dim_warmth_summary);

        passSwitch = v.findViewById(R.id.dim_pass_through);
        passSwitch.setOnCheckedChangeListener((b, on) -> {
            if (!binding) {
                settings.setDimPassThrough(on);
            }
        });

        moonSwitch = v.findViewById(R.id.dim_moon);
        moonSwitch.setOnCheckedChangeListener((b, on) -> {
            if (binding) {
                return;
            }
            if (on && !Overlays.canDraw(requireContext())) {
                moonSwitch.setChecked(false);
                requestPermission();
                return;
            }
            settings.setMoonShown(on);
            DimService.sync(requireContext());
        });

        autoOff = v.findViewById(R.id.dim_auto_off);
        autoClock = v.findViewById(R.id.dim_auto_clock);
        autoNight = v.findViewById(R.id.dim_auto_night);
        autoHours = v.findViewById(R.id.dim_auto_hours);
        autoStart = v.findViewById(R.id.dim_auto_start);
        autoEnd = v.findViewById(R.id.dim_auto_end);
        autoOff.setOnClickListener(x -> setAutoMode(AutoDim.Mode.OFF));
        autoClock.setOnClickListener(x -> setAutoMode(AutoDim.Mode.CLOCK));
        autoNight.setOnClickListener(x -> setAutoMode(AutoDim.Mode.NIGHT_MODE));
        autoStart.setOnClickListener(x -> pickHour(R.string.dim_auto_from, settings.autoDimStartHour(),
                settings::setAutoDimStartHour));
        autoEnd.setOnClickListener(x -> pickHour(R.string.dim_auto_until, settings.autoDimEndHour(),
                settings::setAutoDimEndHour));

        v.findViewById(R.id.dim_reset).setOnClickListener(x -> VeilDialogs.confirmDestructive(
                requireContext(), getString(R.string.dim_reset), getString(R.string.dim_reset_message),
                getString(R.string.dim_reset), () -> {
                    settings.resetDim();
                    bind();
                }));

        bind();
    }

    @Override
    public void onResume() {
        super.onResume();
        settings.addListener(onSetting);
        bind();
        // Coming back from the permission page: the switch the user wanted can now go on.
        if (Overlays.canDraw(requireContext())) {
            DimService.sync(requireContext());
        }
    }

    @Override
    public void onPause() {
        settings.removeListener(onSetting);
        super.onPause();
    }

    private void bind() {
        if (getView() == null) {
            return;
        }
        binding = true;
        permissionCard.setVisibility(Overlays.canDraw(requireContext()) ? View.GONE : View.VISIBLE);
        onSwitch.setChecked(settings.dimOn());
        opacity.set(settings.dimOpacity());
        brightness.set(settings.dimBrightness());
        warmth.set(settings.dimWarmth());
        passSwitch.setChecked(settings.dimPassThrough());
        moonSwitch.setChecked(settings.moonShown());
        AutoDim.Mode mode = AutoDim.Mode.fromKey(settings.autoDimMode());
        choice(autoOff, mode == AutoDim.Mode.OFF);
        choice(autoClock, mode == AutoDim.Mode.CLOCK);
        choice(autoNight, mode == AutoDim.Mode.NIGHT_MODE);
        autoHours.setVisibility(mode == AutoDim.Mode.CLOCK ? View.VISIBLE : View.GONE);
        autoStart.setText(getString(R.string.dim_auto_from_value, hour(settings.autoDimStartHour())));
        autoEnd.setText(getString(R.string.dim_auto_until_value, hour(settings.autoDimEndHour())));
        binding = false;
    }

    private void setAutoMode(AutoDim.Mode mode) {
        if (mode != AutoDim.Mode.OFF && !Overlays.canDraw(requireContext())) {
            requestPermission();
            return;
        }
        settings.setAutoDimMode(mode.key);
        DimService.sync(requireContext());
        bind();
    }

    /** The chosen option is the one orange block on the page; the others are grey. */
    private void choice(Button button, boolean selected) {
        int bg = selected ? R.color.energy : R.color.sunken;
        int fg = selected ? R.color.on_energy : R.color.text_primary;
        button.setBackgroundTintList(androidx.core.content.ContextCompat.getColorStateList(requireContext(), bg));
        button.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), fg));
    }

    private static String hour(int h) {
        return String.format(java.util.Locale.ROOT, "%02d:00", h);
    }

    private void pickHour(int titleRes, int current, java.util.function.IntConsumer onPick) {
        String[] hours = new String[24];
        for (int i = 0; i < 24; i++) {
            hours[i] = hour(i);
        }
        VeilDialogs.show(VeilDialogs.builder(requireContext())
                .setTitle(titleRes)
                .setSingleChoiceItems(hours, current, (d, which) -> {
                    onPick.accept(which);
                    d.dismiss();
                    bind();
                })
                .setNegativeButton(R.string.action_cancel, null), false);
    }

    private void requestPermission() {
        if (!Overlays.openPermissionPage(requireContext())) {
            VeilDialogs.inform(requireContext(), getString(R.string.overlay_needed_title),
                    getString(R.string.overlay_needed_adb, Overlays.adbGrant(requireContext().getPackageName())));
        }
    }

    /** One slider row: title, live value and the bar. */
    private final class Slider {
        private final View row;
        private final TextView value;
        private final SeekBar bar;

        Slider(View row, java.util.function.IntConsumer onChange) {
            this.row = row;
            value = row.findViewById(R.id.slider_value);
            bar = row.findViewById(R.id.slider_bar);
            bar.setMax(100);
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    value.setText(getString(R.string.percent, progress));
                    if (fromUser && !binding) {
                        onChange.accept(progress);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }

        void title(int titleRes, int summaryRes) {
            ((TextView) row.findViewById(R.id.slider_title)).setText(titleRes);
            ((TextView) row.findViewById(R.id.slider_summary)).setText(summaryRes);
        }

        void set(int percent) {
            if (bar.getProgress() != percent) {
                bar.setProgress(percent);
            }
            value.setText(getString(R.string.percent, percent));
        }
    }
}
