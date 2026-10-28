package app.veil.dim;

import app.veil.settings.Percent;

/**
 * Colour of the full-screen veil.
 *
 * <p>Opacity is how much of the screen shows through. Brightness lifts the veil
 * off pure black. Warmth pulls that grey toward amber so a night map is less harsh.</p>
 *
 * <p>From 7xDash (GPL-3.0), where the Android 12 rules below were found on the car.</p>
 */
public final class DimShade {

    private DimShade() {
    }

    /** Packed ARGB. Each argument is a percent and is clamped to 0..100. */
    public static int argb(int opacityPercent, int brightnessPercent, int warmthPercent) {
        int alpha = Percent.clamp(opacityPercent) * 255 / 100;
        int gray = Percent.clamp(brightnessPercent) * 255 / 100;
        int warmth = Percent.clamp(warmthPercent);
        int red = mix(gray, 255, warmth);
        int green = mix(gray, 176, warmth);
        int blue = mix(gray, 64, warmth);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int mix(int from, int to, int percent) {
        return from + (to - from) * percent / 100;
    }

    /**
     * Touches pass through the veil only while it is not a full blackout.
     * Android 12 clamps a not-touchable overlay to 80% alpha, so 100% never
     * goes black unless the shade takes the touches itself.
     */
    public static boolean passTouches(boolean passThrough, int opacityPercent) {
        return passThrough && Percent.clamp(opacityPercent) < 100;
    }

    /**
     * Android 12 treats a not-touchable overlay whose window alpha is above 0.8
     * as a full blackout and drops the taps underneath. The colour alpha is not
     * what it looks at. Pass-through therefore lives in the window alpha, and
     * stops at 0.8. A 100% veil is not pass-through, so it can stay fully opaque.
     */
    public static float windowAlpha(boolean passThrough, int opacityPercent) {
        if (!passTouches(passThrough, opacityPercent)) {
            return 1f;
        }
        float amount = Percent.clamp(opacityPercent) / 100f;
        return amount > 0.8f ? 0.8f : amount;
    }

    /** The colour the view paints when the dim lives in the window alpha: the tint, fully opaque. */
    public static int opaqueTint(int packedArgb) {
        return 0xFF000000 | (packedArgb & 0x00FFFFFF);
    }
}
