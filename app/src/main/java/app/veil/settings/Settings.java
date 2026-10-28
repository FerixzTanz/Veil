package app.veil.settings;

/**
 * Every user-visible setting, typed, with its default and its bounds in one place.
 *
 * <p>Plain Java on purpose: the services, the pages and the unit tests all read
 * the same object, and nothing here needs Android. Values are clamped on the way
 * out, so a stale or hand-edited store can never produce an out-of-range value.</p>
 */
public final class Settings {

    // AirPlay
    public static final String AIRPLAY_ON = "airplay.on";
    public static final String AIRPLAY_NAME = "airplay.name";
    public static final String AIRPLAY_CAST_VIDEO = "airplay.cast_video";
    public static final String AIRPLAY_WINDOW = "airplay.window";

    // Dim overlay
    public static final String DIM_ON = "dim.on";
    public static final String DIM_OPACITY = "dim.opacity";
    public static final String DIM_BRIGHTNESS = "dim.brightness";
    public static final String DIM_WARMTH = "dim.warmth";
    public static final String DIM_PASS_THROUGH = "dim.pass_through";
    public static final String MOON_X = "moon.x";
    public static final String MOON_Y = "moon.y";

    public static final String DEFAULT_RECEIVER_NAME = "Veil";
    /** Same starting point as 7xDash, which tuned these on the car. */
    public static final int DIM_OPACITY_DEFAULT = 90;
    public static final int DIM_BRIGHTNESS_DEFAULT = 0;
    public static final int DIM_WARMTH_DEFAULT = 0;
    /** The moon button has no saved position until the user drags it. */
    public static final int POSITION_UNSET = Integer.MIN_VALUE;
    /** Longest receiver name the iPhone's list shows without cutting it. */
    public static final int RECEIVER_NAME_MAX = 32;

    private final Store store;

    public Settings(Store store) {
        this.store = store;
    }

    public Store store() {
        return store;
    }

    public void addListener(Store.Listener listener) {
        store.addListener(listener);
    }

    public void removeListener(Store.Listener listener) {
        store.removeListener(listener);
    }

    // ------------------------------------------------------------------ AirPlay

    /** On means the receiver runs, and starts again after a reboot. */
    public boolean airPlayOn() {
        return getBoolean(AIRPLAY_ON, false);
    }

    public void setAirPlayOn(boolean on) {
        putBoolean(AIRPLAY_ON, on);
    }

    /** The name the iPhone shows in its Screen Mirroring list. Never blank. */
    public String receiverName() {
        String name = store.get(AIRPLAY_NAME);
        String clean = cleanReceiverName(name);
        return clean.isEmpty() ? DEFAULT_RECEIVER_NAME : clean;
    }

    public void setReceiverName(String name) {
        String clean = cleanReceiverName(name);
        if (clean.isEmpty()) {
            store.remove(AIRPLAY_NAME);
        } else {
            store.put(AIRPLAY_NAME, clean);
        }
    }

    /** Trims, collapses whitespace and cuts to {@link #RECEIVER_NAME_MAX}. */
    public static String cleanReceiverName(String name) {
        if (name == null) {
            return "";
        }
        String clean = name.trim().replaceAll("\\s+", " ");
        if (clean.length() > RECEIVER_NAME_MAX) {
            clean = clean.substring(0, RECEIVER_NAME_MAX).trim();
        }
        return clean;
    }

    /** Off keeps the iPhone on screen mirroring; video apps then cannot hand over a stream. */
    public boolean castVideo() {
        return getBoolean(AIRPLAY_CAST_VIDEO, true);
    }

    public void setCastVideo(boolean on) {
        putBoolean(AIRPLAY_CAST_VIDEO, on);
    }

    // ------------------------------------------------------------------ Dim overlay

    public boolean dimOn() {
        return getBoolean(DIM_ON, false);
    }

    public void setDimOn(boolean on) {
        putBoolean(DIM_ON, on);
    }

    /** 0 lets everything through, 100 is a blackout. */
    public int dimOpacity() {
        return Percent.clamp(getInt(DIM_OPACITY, DIM_OPACITY_DEFAULT));
    }

    public void setDimOpacity(int percent) {
        putInt(DIM_OPACITY, Percent.clamp(percent));
    }

    /** Lifts the veil off pure black. */
    public int dimBrightness() {
        return Percent.clamp(getInt(DIM_BRIGHTNESS, DIM_BRIGHTNESS_DEFAULT));
    }

    public void setDimBrightness(int percent) {
        putInt(DIM_BRIGHTNESS, Percent.clamp(percent));
    }

    /** Pulls the grey toward amber. */
    public int dimWarmth() {
        return Percent.clamp(getInt(DIM_WARMTH, DIM_WARMTH_DEFAULT));
    }

    public void setDimWarmth(int percent) {
        putInt(DIM_WARMTH, Percent.clamp(percent));
    }

    /** Touches go to the app underneath. On by default; a veil you cannot tap through is a lockout. */
    public boolean dimPassThrough() {
        return getBoolean(DIM_PASS_THROUGH, true);
    }

    public void setDimPassThrough(boolean on) {
        putBoolean(DIM_PASS_THROUGH, on);
    }

    /** Back to the defaults for the three sliders and pass-through. The on/off state is left alone. */
    public void resetDim() {
        store.remove(DIM_OPACITY);
        store.remove(DIM_BRIGHTNESS);
        store.remove(DIM_WARMTH);
        store.remove(DIM_PASS_THROUGH);
    }

    public int moonX() {
        return getInt(MOON_X, POSITION_UNSET);
    }

    public int moonY() {
        return getInt(MOON_Y, POSITION_UNSET);
    }

    public void setMoonPosition(int x, int y) {
        putInt(MOON_X, x);
        putInt(MOON_Y, y);
    }

    // ------------------------------------------------------------------ typed access

    public boolean getBoolean(String key, boolean def) {
        String v = store.get(key);
        if (v == null) {
            return def;
        }
        return "true".equals(v);
    }

    public void putBoolean(String key, boolean value) {
        store.put(key, value ? "true" : "false");
    }

    public int getInt(String key, int def) {
        String v = store.get(key);
        if (v == null) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public void putInt(String key, int value) {
        store.put(key, Integer.toString(value));
    }

    public String getString(String key, String def) {
        String v = store.get(key);
        return v == null ? def : v;
    }

    public void putString(String key, String value) {
        if (value == null) {
            store.remove(key);
        } else {
            store.put(key, value);
        }
    }
}
