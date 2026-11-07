package app.veil.settings;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * The app's {@link Store}: one SharedPreferences file, "veil". Writes use apply(): the value
 * is in memory at once and Android finishes the disk write before the process is allowed to
 * go, so nothing is lost, and a slider or a drag no longer blocks the main thread on the disk
 * for every step.
 */
public final class PrefsStore implements Store {

    public static final String FILE = "veil";

    private final SharedPreferences prefs;
    private final List<Listener> listeners = new ArrayList<>();
    // Held strongly: SharedPreferences keeps its listeners in a weak map.
    private final SharedPreferences.OnSharedPreferenceChangeListener bridge =
            (sharedPreferences, key) -> {
                if (key == null) {
                    return;
                }
                for (Listener l : new ArrayList<>(listeners)) {
                    l.onChanged(key);
                }
            };

    public PrefsStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(bridge);
    }

    @Override
    public String get(String key) {
        return prefs.getString(key, null);
    }

    @Override
    public void put(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    @Override
    public void remove(String key) {
        prefs.edit().remove(key).apply();
    }

    @Override
    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }
}
