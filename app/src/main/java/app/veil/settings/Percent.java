package app.veil.settings;

/** 0..100, the unit every slider in the app uses. */
public final class Percent {

    private Percent() {
    }

    public static int clamp(int value) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, 100);
    }
}
