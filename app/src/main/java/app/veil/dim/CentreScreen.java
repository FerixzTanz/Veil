package app.veil.dim;

/**
 * The veil belongs on the centre screen only. Android numbers the head unit's
 * main display 0; the HUD and the instrument cluster are other displays, and
 * nothing in Veil ever adds a window to them.
 */
public final class CentreScreen {

    /** android.view.Display.DEFAULT_DISPLAY, kept here so the rule is testable without Android. */
    public static final int DEFAULT_DISPLAY = 0;

    private CentreScreen() {
    }

    public static boolean isCentre(int displayId) {
        return displayId == DEFAULT_DISPLAY;
    }
}
