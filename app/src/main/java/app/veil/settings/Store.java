package app.veil.settings;

/**
 * Where settings live. Strings in, strings out; {@link Settings} does the typing.
 * The app backs it with SharedPreferences, the tests with a map.
 */
public interface Store {

    /** The stored value, or null when the key was never written. */
    String get(String key);

    void put(String key, String value);

    void remove(String key);

    void addListener(Listener listener);

    void removeListener(Listener listener);

    interface Listener {
        void onChanged(String key);
    }
}
