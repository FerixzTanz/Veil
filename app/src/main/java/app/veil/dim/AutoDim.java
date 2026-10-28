package app.veil.dim;

/**
 * When the veil should turn itself on and off.
 *
 * <p>Two rules. <b>Clock</b>: on from the start hour to the end hour, wrapping
 * midnight, as in 7xDash. <b>Night mode</b>: on whenever the head unit itself is
 * in night mode, which the car switches by its own light sensor and clock.</p>
 *
 * <p>The rule is edge-triggered through {@link Follower}: it acts when what it
 * wants changes, not every minute. So a tap on the moon holds until the next
 * sunset or sunrise instead of being undone sixty seconds later.</p>
 */
public final class AutoDim {

    public enum Mode {
        OFF("off"), CLOCK("clock"), NIGHT_MODE("night");

        public final String key;

        Mode(String key) {
            this.key = key;
        }

        /** Anything unrecognised is OFF; the store can be hand-edited. */
        public static Mode fromKey(String key) {
            for (Mode m : values()) {
                if (m.key.equals(key)) {
                    return m;
                }
            }
            return OFF;
        }
    }

    public static final int START_HOUR_DEFAULT = 19;
    public static final int END_HOUR_DEFAULT = 7;

    private AutoDim() {
    }

    /** True when hour is in [start, end) wrapping midnight. Start equal to end is never. */
    public static boolean inWindow(int hour, int startHour, int endHour) {
        int start = clampHour(startHour);
        int end = clampHour(endHour);
        if (start == end) {
            return false;
        }
        if (start < end) {
            return hour >= start && hour < end;
        }
        return hour >= start || hour < end;
    }

    /** What the rule wants right now. OFF wants nothing and returns null. */
    public static Boolean wanted(Mode mode, int hourNow, int startHour, int endHour, boolean carNightMode) {
        switch (mode) {
            case CLOCK:
                return inWindow(hourNow, startHour, endHour);
            case NIGHT_MODE:
                return carNightMode;
            default:
                return null;
        }
    }

    public static int clampHour(int hour) {
        if (hour < 0) {
            return 0;
        }
        return Math.min(hour, 23);
    }

    /** Remembers the last answer and reports only the changes. */
    public static final class Follower {
        private Boolean last;

        /**
         * @return the new state to apply, or null when nothing changed or the rule is off
         */
        public Boolean update(Boolean wanted) {
            if (wanted == null) {
                last = null;
                return null;
            }
            if (last != null && last.equals(wanted)) {
                return null;
            }
            last = wanted;
            return wanted;
        }

        public void reset() {
            last = null;
        }
    }
}
