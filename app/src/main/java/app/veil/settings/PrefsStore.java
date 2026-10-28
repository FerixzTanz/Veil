package app.veil.settings;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * The app's {@link Store}: one SharedPreferences file, "veil", written with
 * commit() so a value survives even if the process dies right after.
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
        prefs.edit().putString(key, value).commit();
    }

    @Override
    public void remove(String key) {
        prefs.edit().remove(key).commit();
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
