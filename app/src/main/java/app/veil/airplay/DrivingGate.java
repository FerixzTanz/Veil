package app.veil.airplay;

import androidx.annotation.Nullable;

/**
 * When the mirrored picture must hide: the car is in gear (D, R or N) or rolling. Sound keeps
 * playing. Unknown readings (no car link, emulator) count as parked so the feature still works
 * off the car. From 7xDash (GPL-3.0). There is no switch to show the picture while driving.
 */
public final class DrivingGate {

    /** Above this the car is moving even if the gear reading is late. */
    public static final float ROLLING_KMH = 3f;

    private DrivingGate() {
    }

    public static boolean blocked(@Nullable String gear, @Nullable Float speedKmh) {
        if ("D".equals(gear) || "R".equals(gear) || "N".equals(gear)) {
            return true;
        }
        return speedKmh != null && speedKmh > ROLLING_KMH;
    }
}
