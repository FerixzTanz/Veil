package app.veil.car;

import androidx.annotation.Nullable;

/**
 * The ECARX gear sensor event, as read on the 7X by 7xDash (GPL-3.0): P = 0x00200230,
 * R = 0x00200240, N = 0x00200210, D = 0x00200220. Anything else is unknown.
 */
public final class GearCode {

    /** ISensor type of the gear event. */
    public static final int SENSOR_GEAR = 0x00200200;
    /** ISensor type of the speed value, in metres per second. */
    public static final int SENSOR_SPEED = 0x00100100;

    private GearCode() {
    }

    @Nullable
    public static String letter(int event) {
        switch (event) {
            case 0x00200230:
                return "P";
            case 0x00200240:
                return "R";
            case 0x00200210:
                return "N";
            case 0x00200220:
                return "D";
            default:
                return null;
        }
    }

    /** The speed sensor gives m/s (0.2778 = 1 km/h). Placeholder and negative values are unknown. */
    @Nullable
    public static Float kmh(@Nullable Number metresPerSecond) {
        if (metresPerSecond == null) {
            return null;
        }
        float v = metresPerSecond.floatValue();
        if (Float.isNaN(v) || v < 0f || v > 100f) {
            return null;
        }
        return v * 3.6f;
    }
}
