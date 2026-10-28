package app.veil;

import android.app.Application;

import app.veil.settings.PrefsStore;
import app.veil.settings.Settings;

/** Process-wide objects: the settings store, nothing else yet. */
public class VeilApp extends Application {

    private static Settings settings;

    @Override
    public void onCreate() {
        super.onCreate();
        settings = new Settings(new PrefsStore(this));
    }

    public static Settings settings() {
        return settings;
    }
}
