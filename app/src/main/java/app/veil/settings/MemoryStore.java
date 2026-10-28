package app.veil.settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A {@link Store} in a map: for unit tests and for previews with nothing persisted. */
public final class MemoryStore implements Store {

    private final Map<String, String> values = new HashMap<>();
    private final List<Listener> listeners = new ArrayList<>();

    @Override
    public String get(String key) {
        return values.get(key);
    }

    @Override
    public void put(String key, String value) {
        values.put(key, value);
        notifyChanged(key);
    }

    @Override
    public void remove(String key) {
        if (values.remove(key) != null) {
            notifyChanged(key);
        }
    }

    @Override
    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public int size() {
        return values.size();
    }

    private void notifyChanged(String key) {
        for (Listener l : new ArrayList<>(listeners)) {
            l.onChanged(key);
        }
    }
}
