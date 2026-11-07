package app.veil;

import android.app.Application;

import app.veil.airplay.AirPlayService;
import app.veil.dim.DimService;
import app.veil.dim.Overlays;
import app.veil.settings.PrefsStore;
import app.veil.settings.Settings;

/** Process-wide objects: the settings store, nothing else yet. */
public class VeilApp extends Application {

    private static Settings settings;

    @Override
    public void onCreate() {
        super.onCreate();
        settings = new Settings(new PrefsStore(this));
        // The veil and the moon come back the way they were left.
        boolean autoRule = !"off".equals(settings.autoDimMode());
        if (Overlays.canDraw(this) && (settings.dimOn() || settings.moonShown() || autoRule)) {
            DimService.sync(this);
        }
        // The receiver stays on across restarts if it was left on.
        AirPlayService.applySaved(this);
    }

    public static Settings settings() {
        return settings;
    }
}
