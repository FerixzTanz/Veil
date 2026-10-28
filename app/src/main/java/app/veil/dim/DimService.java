package app.veil.dim;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import app.veil.R;
import app.veil.VeilApp;
import app.veil.settings.Settings;
import app.veil.settings.Store;

/**
 * Owns the two overlay windows of the dim feature on the centre screen: the
 * full-screen shade and the small floating moon button. Both are
 * {@code TYPE_APPLICATION_OVERLAY} windows on {@link Display#DEFAULT_DISPLAY}
 * and nothing else; the HUD and the instrument cluster never get a window.
 *
 * <p>The service reads everything from {@link Settings} and follows its
 * changes, so the page and the moon only ever write settings. It stops itself
 * when neither window is wanted.</p>
 *
 * <p>Shade behaviour adapted from 7xDash's DimOverlayService (GPL-3.0),
 * including the Android 12 pass-through rules in {@link DimShade}.</p>
 */
public class DimService extends Service {

    private static final String TAG = "DimService";
    private static final long LONG_PRESS_MS = 500;
    private static final long BADGE_MS = 700;
    private static final int SLOP_DP = 8;
    private static final int STEP_DP = 6;
    /** The clock rule looks at the hour this often. */
    private static final long AUTO_TICK_MS = 60_000L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private Settings settings;
    private WindowManager windowManager;
    private Context displayContext;

    private View shade;
    private WindowManager.LayoutParams shadeParams;

    private View moon;
    private ImageView moonIcon;
    private TextView moonBadge;
    private WindowManager.LayoutParams moonParams;
    private MoonGesture gesture;
    private final Runnable armMoon = this::armMoon;
    private final Runnable hideBadge = () -> {
        if (moonBadge != null) {
            moonBadge.setVisibility(View.GONE);
        }
    };

    private final AutoDim.Follower autoDim = new AutoDim.Follower();
    private final Runnable autoTick = this::followAutoDim;

    private final Store.Listener onSetting = key -> main.post(() -> {
        if (Settings.AUTO_DIM.equals(key) || Settings.AUTO_DIM_START.equals(key)
                || Settings.AUTO_DIM_END.equals(key)) {
            // A changed rule starts afresh and applies at once.
            autoDim.reset();
        }
        sync();
    });

    /** Make the windows match the settings. Safe to call at any time. */
    public static void sync(Context context) {
        Intent intent = new Intent(context, DimService.class);
        try {
            context.startService(intent);
        } catch (Exception e) {
            Log.e(TAG, "cannot start", e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        settings = VeilApp.settings();
        DisplayManager displays = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        Display centre = displays.getDisplay(Display.DEFAULT_DISPLAY);
        if (centre == null || !CentreScreen.isCentre(centre.getDisplayId())) {
            Log.e(TAG, "no centre display; nothing will be drawn");
            displayContext = null;
            windowManager = null;
        } else {
            displayContext = createDisplayContext(centre);
            windowManager = (WindowManager) displayContext.getSystemService(WINDOW_SERVICE);
        }
        settings.addListener(onSetting);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        main.post(this::sync);
        return START_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // The head unit switched day and night: the night-mode rule follows at once.
        main.post(this::followAutoDim);
    }

    @Override
    public void onDestroy() {
        settings.removeListener(onSetting);
        main.removeCallbacksAndMessages(null);
        detachShade();
        detachMoon();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ------------------------------------------------------------------ state

    private void sync() {
        followAutoDim();
        boolean allowed = windowManager != null && Overlays.canDraw(this);
        boolean wantShade = allowed && settings.dimOn();
        boolean wantMoon = allowed && settings.moonShown();

        if (wantShade) {
            if (shade == null) {
                attachShade();
                // The shade was added after the moon and would cover it.
                if (moon != null) {
                    detachMoon();
                }
            } else {
                paintShade();
            }
        } else {
            detachShade();
        }

        if (wantMoon) {
            if (moon == null) {
                attachMoon();
            } else {
                paintMoon();
            }
        } else {
            detachMoon();
        }

        if (!wantShade && !wantMoon && autoMode() == AutoDim.Mode.OFF) {
            stopSelf();
        }
    }

    // ------------------------------------------------------------------ automatic rule

    private AutoDim.Mode autoMode() {
        return AutoDim.Mode.fromKey(settings.autoDimMode());
    }

    /** Applies the automatic rule's edges and keeps the clock ticking while a rule is on. */
    private void followAutoDim() {
        main.removeCallbacks(autoTick);
        AutoDim.Mode mode = autoMode();
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        boolean carNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        Boolean change = autoDim.update(AutoDim.wanted(mode, hour,
                settings.autoDimStartHour(), settings.autoDimEndHour(), carNight));
        if (change != null && change != settings.dimOn()) {
            // Writing the setting brings sync() back through the listener.
            settings.setDimOn(change);
        }
        if (mode == AutoDim.Mode.CLOCK) {
            main.postDelayed(autoTick, AUTO_TICK_MS);
        }
    }

    // ------------------------------------------------------------------ shade

    private void attachShade() {
        shade = new View(displayContext);
        shade.setClickable(false);
        shadeParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                shadeFlags(),
                PixelFormat.TRANSLUCENT);
        shadeParams.gravity = Gravity.TOP | Gravity.START;
        Point real = realSize();
        shadeParams.width = real.x;
        shadeParams.height = real.y;
        shadeParams.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        applyShadeLook();
        try {
            windowManager.addView(shade, shadeParams);
        } catch (Exception e) {
            Log.e(TAG, "shade add failed", e);
            shade = null;
            shadeParams = null;
        }
    }

    private void paintShade() {
        if (shade == null) {
            return;
        }
        Point real = realSize();
        shadeParams.width = real.x;
        shadeParams.height = real.y;
        applyShadeLook();
        try {
            windowManager.updateViewLayout(shade, shadeParams);
        } catch (Exception e) {
            Log.e(TAG, "shade update failed", e);
        }
    }

    private void detachShade() {
        if (shade != null && windowManager != null) {
            try {
                windowManager.removeView(shade);
            } catch (Exception e) {
                Log.e(TAG, "shade remove failed", e);
            }
        }
        shade = null;
        shadeParams = null;
    }

    /**
     * Pass-through puts the dim in the window alpha and keeps the colour opaque,
     * so Android 12 still delivers taps. A full blackout keeps alpha at 1 and
     * takes the touches itself.
     */
    private void applyShadeLook() {
        int opacity = settings.dimOpacity();
        boolean pass = DimShade.passTouches(settings.dimPassThrough(), opacity);
        int packed = DimShade.argb(opacity, settings.dimBrightness(), settings.dimWarmth());
        shadeParams.flags = shadeFlags();
        if (pass) {
            shadeParams.alpha = DimShade.windowAlpha(true, opacity);
            shade.setBackgroundColor(DimShade.opaqueTint(packed));
        } else {
            shadeParams.alpha = 1f;
            shade.setBackgroundColor(packed);
        }
    }

    private int shadeFlags() {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (DimShade.passTouches(settings.dimPassThrough(), settings.dimOpacity())) {
            flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        return flags;
    }

    private Point realSize() {
        Point point = new Point();
        windowManager.getDefaultDisplay().getRealSize(point);
        if (point.x <= 0 || point.y <= 0) {
            point.x = getResources().getDisplayMetrics().widthPixels;
            point.y = getResources().getDisplayMetrics().heightPixels;
        }
        return point;
    }

    // ------------------------------------------------------------------ moon

    private void attachMoon() {
        float density = getResources().getDisplayMetrics().density;
        moon = LayoutInflater.from(displayContext).inflate(R.layout.view_moon, null);
        moonIcon = moon.findViewById(R.id.moon_icon);
        moonBadge = moon.findViewById(R.id.moon_badge);
        gesture = new MoonGesture(Math.round(SLOP_DP * density), Math.round(STEP_DP * density), LONG_PRESS_MS);

        moonParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        moonParams.gravity = Gravity.TOP | Gravity.START;
        Point screen = realSize();
        int size = getResources().getDimensionPixelSize(R.dimen.moon_size);
        int x = settings.moonX();
        int y = settings.moonY();
        if (x == Settings.POSITION_UNSET || y == Settings.POSITION_UNSET) {
            // Bottom-right corner, clear of the edge, where the dashcam's cluster is not.
            x = screen.x - size - Math.round(24 * density);
            y = screen.y - size - Math.round(96 * density);
        }
        moonParams.x = MoonGesture.clampPosition(x, size, screen.x);
        moonParams.y = MoonGesture.clampPosition(y, size, screen.y);

        moon.setOnTouchListener(this::onMoonTouch);
        paintMoon();
        try {
            windowManager.addView(moon, moonParams);
        } catch (Exception e) {
            Log.e(TAG, "moon add failed", e);
            moon = null;
            moonParams = null;
        }
    }

    private void paintMoon() {
        if (moonIcon == null) {
            return;
        }
        boolean on = settings.dimOn();
        moonIcon.setImageTintList(ContextCompat.getColorStateList(this,
                on ? R.color.energy : R.color.text_secondary));
        moon.setContentDescription(getString(on ? R.string.moon_turn_off : R.string.moon_turn_on));
    }

    private void detachMoon() {
        main.removeCallbacks(armMoon);
        main.removeCallbacks(hideBadge);
        if (moon != null && windowManager != null) {
            try {
                windowManager.removeView(moon);
            } catch (Exception e) {
                Log.e(TAG, "moon remove failed", e);
            }
        }
        moon = null;
        moonIcon = null;
        moonBadge = null;
        moonParams = null;
        gesture = null;
    }

    private boolean onMoonTouch(View v, MotionEvent event) {
        if (gesture == null || moonParams == null) {
            return false;
        }
        long now = event.getEventTime();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                gesture.down(event.getRawX(), event.getRawY(), now, settings.dimOpacity(),
                        moonParams.x, moonParams.y);
                main.postDelayed(armMoon, LONG_PRESS_MS);
                return true;
            case MotionEvent.ACTION_MOVE:
                act(gesture.move(event.getRawX(), event.getRawY(), now), false);
                return true;
            case MotionEvent.ACTION_UP:
                main.removeCallbacks(armMoon);
                act(gesture.up(now), true);
                settleMoon();
                return true;
            case MotionEvent.ACTION_CANCEL:
                main.removeCallbacks(armMoon);
                gesture.cancel();
                settleMoon();
                return true;
            default:
                return false;
        }
    }

    private void armMoon() {
        if (gesture != null && moon != null && gesture.arm(android.os.SystemClock.uptimeMillis())) {
            moon.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            moon.animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).start();
        }
    }

    private void settleMoon() {
        if (moon != null) {
            moon.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
        }
        main.postDelayed(hideBadge, BADGE_MS);
    }

    private void act(MoonGesture.Result result, boolean finished) {
        switch (result.kind) {
            case TAP:
                settings.setDimOn(!settings.dimOn());
                break;
            case ADJUST:
                if (moonBadge != null) {
                    main.removeCallbacks(hideBadge);
                    moonBadge.setText(getString(R.string.percent, result.level));
                    moonBadge.setVisibility(View.VISIBLE);
                }
                if (!finished) {
                    if (!settings.dimOn()) {
                        settings.setDimOn(true);
                    }
                    settings.setDimOpacity(result.level);
                }
                break;
            case MOVE:
                if (moon == null || moonParams == null) {
                    break;
                }
                if (finished) {
                    settings.setMoonPosition(moonParams.x, moonParams.y);
                } else {
                    Point screen = realSize();
                    moonParams.x = MoonGesture.clampPosition(result.x, moon.getWidth(), screen.x);
                    moonParams.y = MoonGesture.clampPosition(result.y, moon.getHeight(), screen.y);
                    try {
                        windowManager.updateViewLayout(moon, moonParams);
                    } catch (Exception e) {
                        Log.e(TAG, "moon move failed", e);
                    }
                }
                break;
            default:
                break;
        }
    }
}
